/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.internal.SemaphorePlatform
import zio.stm.TSemaphore

/**
 * An asynchronous semaphore, which is a generalization of a mutex. Semaphores
 * have a certain number of permits, which can be held and released concurrently
 * by different parties. Attempts to acquire more permits than available result
 * in the acquiring fiber being suspended until the specified number of permits
 * become available.
 *
 * If you need functionality that `Semaphore` doesnt' provide, use a
 * [[TSemaphore]] and define it in a [[zio.stm.ZSTM]] transaction.
 */
sealed trait Semaphore extends Serializable {

  /**
   * Returns the number of permits that are available to be acquired.
   *
   * For a fair semaphore this is `0` whenever another fiber is already waiting
   * for permits, since a waiting fiber must be served first and no other fiber
   * may acquire ahead of it, even if the semaphore is holding permits that the
   * waiting fiber is not yet able to use.
   */
  def available(implicit trace: Trace): UIO[Long]

  /**
   * Returns the number of tasks currently waiting for permits. The default
   * implementation returns 0.
   */
  def awaiting(implicit trace: Trace): UIO[Long] = ZIO.succeed(0L)

  /**
   * Executes the effect, acquiring a permit if available and releasing it after
   * execution. Returns `None` if no permits were available.
   */
  final def tryWithPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Option[A]] =
    tryWithPermits(1L)(zio)

  /**
   * Executes the effect, acquiring `n` permits if available and releasing them
   * after execution. Returns `None` if no permits were available.
   */
  def tryWithPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Option[A]] =
    ZIO.none

  /**
   * Executes the specified workflow, acquiring a permit immediately before the
   * workflow begins execution and releasing it immediately after the workflow
   * completes execution, whether by success, failure, or interruption.
   */
  def withPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]

  /**
   * Returns a scoped workflow that describes acquiring a permit as the
   * `acquire` action and releasing it as the `release` action.
   */
  def withPermitScoped(implicit trace: Trace): ZIO[Scope, Nothing, Unit]

  /**
   * Executes the specified workflow, acquiring the specified number of permits
   * immediately before the workflow begins execution and releasing them
   * immediately after the workflow completes execution, whether by success,
   * failure, or interruption.
   */
  def withPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]

  /**
   * Returns a scoped workflow that describes acquiring the specified number of
   * permits and releasing them when the scope is closed.
   */
  def withPermitsScoped(n: Long)(implicit trace: Trace): ZIO[Scope, Nothing, Unit]

}

object Semaphore {

  /**
   * Creates a new `Semaphore` with the specified number of permits.
   *
   * The returned semaphore is fair: permits are granted to fibers in the order
   * in which they were requested, and a fiber will not acquire a permit while
   * another fiber is already waiting for one.
   */
  def make(permits: => Long)(implicit trace: Trace): UIO[Semaphore] =
    ZIO.succeed(unsafe.make(permits)(Unsafe.unsafe))

  /**
   * Creates a new unfair `Semaphore` with the specified number of permits.
   *
   * An unfair semaphore allows a fiber to acquire an available permit even when
   * other fibers are already waiting, a policy commonly known as "barging",
   * trading the FIFO ordering guarantee of [[make]] for the chance to skip a
   * suspend/reschedule round trip.
   *
   * Note that with an unfair semaphore a waiting fiber is not guaranteed to
   * make progress if permits are continuously acquired by other fibers.
   *
   * Be aware that unfairness buys much less here than it does for a semaphore
   * that parks threads: under `withPermit` the cost of contention is dominated
   * by suspending and rescheduling fibers through the runtime rather than by
   * the queueing policy. Benchmarks show this within noise of [[make]]
   * uncontended, and ahead of it in only one measured contended configuration,
   * ten fibers over five permits. For comparison, barging is worth nearly 2x to
   * `java.util.concurrent.Semaphore` at ten threads over one permit. Reach for
   * this when you specifically do not need ordering, and measure before
   * assuming it is faster for your workload.
   */
  def makeUnfair(permits: => Long)(implicit trace: Trace): UIO[Semaphore] =
    ZIO.succeed(unsafe.makeUnfair(permits)(Unsafe.unsafe))

  object unsafe {
    def make(permits: Long)(implicit unsafe: Unsafe): Semaphore =
      new ConcurrentSemaphore(permits, fair = true)

    def makeUnfair(permits: Long)(implicit unsafe: Unsafe): Semaphore =
      new ConcurrentSemaphore(permits, fair = false)
  }

  private final class ConcurrentSemaphore(permits: Long, fair: Boolean) extends Semaphore {
    private[this] val state = new SemaphorePlatform(permits, fair)

    def available(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(state.available())

    override def awaiting(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(state.awaiting())

    def withPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      withPermits(1L)(zio)

    def withPermitScoped(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
      withPermitsScoped(1L)

    def withPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      if (n < 0L) die(n)
      else if (n == 0L) zio
      else
        ZIO.AcquireReleaseInline(
          trace,
          () => state.tryAcquire(n),
          zio,
          () => state.release(n),
          // Only the queueing path needs the uninterruptible region. The fast
          // path takes its permits and installs their release inside a single
          // dispatch of the run loop, which cannot be interrupted partway, so it
          // pays for no flag changes at all. Queueing has to suspend, and a
          // suspension is interruptible by construction, so the waiter has to be
          // enqueued and awaited under a mask that `enqueueAndAwait` restores
          // around the suspension itself.
          //
          // By name: on the path this exists to make cheap, it is never built.
          () =>
            ZIO.uninterruptibleMask { restore =>
              val body = ZIO.OnExitEffect(trace, restore(zio), () => state.release(n))

              if (state.tryAcquire(n)) body
              else enqueueAndAwait(n, restore).flatMap(_ => body)
            }
        )

    def withPermitsScoped(n: Long)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
      if (n < 0L) die(n)
      else if (n == 0L) Exit.unit
      else
        ZIO.uninterruptibleMask { restore =>
          def register = ZIO.addFinalizer(ZIO.succeed(state.release(n))).unit

          if (state.tryAcquire(n)) register
          else enqueueAndAwait(n, restore).flatMap(_ => register)
        }

    override def tryWithPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Option[A]] =
      if (n < 0L) die(n)
      else if (n == 0L) zio.asSome
      else
        ZIO.uninterruptibleMask { restore =>
          if (!state.tryAcquire(n)) Exit.none
          else
            restore(zio).asSome.exitWith { exit =>
              state.release(n)
              exit
            }
        }

    /**
     * Enqueues a waiter for `n` permits and suspends until it is granted them.
     *
     * The two are one method because a waiter that is enqueued and then not
     * awaited strands its permits: nothing else will ever claim them, and the
     * fiber that queued it never learns it was granted.
     *
     * Both halves run while this is being built, in the same block of
     * interpreter work as the `tryAcquire` that failed. Splitting them across
     * effect nodes would put a yield point between them, and two fibers
     * arriving in order could then be queued out of order, breaking the FIFO
     * guarantee of a fair semaphore.
     *
     * The suspension itself runs interruptibly, since a fiber blocked on a
     * semaphore has to remain interruptible, and must return its permits if it
     * is interrupted after having been granted them.
     */
    private def enqueueAndAwait(n: Long, restore: ZIO.InterruptibilityRestorer)(implicit
      trace: Trace
    ): ZIO[Any, Nothing, Unit] = {
      val waiter = state.enqueue(n)
      restore {
        ZIO.async[Any, Nothing, Unit](
          cb => if (!waiter.register(cb)) cb(Exit.unit),
          FiberId.None
        )
      }.onInterrupt(ZIO.succeed(state.cancel(waiter)))
    }

    private def die(n: Long)(implicit trace: Trace): UIO[Nothing] =
      ZIO.die(new IllegalArgumentException(s"Unexpected negative `$n` permits requested."))
  }
}
