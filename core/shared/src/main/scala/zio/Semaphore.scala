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
import zio.stm.TSemaphore
import java.util.concurrent.atomic.AtomicLong
import scala.annotation.tailrec

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
   * Returns the number of available permits.
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
   */
  def make(permits: => Long)(implicit trace: Trace): UIO[Semaphore] =
    ZIO.succeed(unsafe.make(permits)(Unsafe.unsafe))

  object unsafe {
    def make(permits: Long)(implicit unsafe: Unsafe): Semaphore =
      if (permits < 0)
        throw new IllegalArgumentException(s"Unexpected negative `$permits` permits specified.")
      else new Internal(permits)
  }

  private final val DisableAssertions = BuildInfo.optimizationsEnabled

  private sealed trait Waiter {
    def promise: Promise[Nothing, Unit]
    def permits: Long
    def reducedBy(n: Long): Waiter
  }

  private object Waiter {
    final class Single(val promise: Promise[Nothing, Unit]) extends Waiter {
      def permits: Long = 1L
      def reducedBy(n: Long): Waiter = {
        assert(DisableAssertions)
        this
      }
    }
    final class Multi(val promise: Promise[Nothing, Unit], initial: Long) extends AtomicLong(initial) with Waiter {
      def permits: Long              = get()
      def reducedBy(n: Long): Waiter = { addAndGet(-n); this }
    }
  }

  private[zio] object State {
    private final val LowerMask: Long = 0xffffffffL
    private final val UpperShift: Int = 32

    final val MaxWaiters: Long = Int.MaxValue.toLong
    final val MaxDemand: Long  = LowerMask

    @inline def apply(waiters: Long, demand: Long): Long =
      (-waiters << UpperShift) | (demand & LowerMask)

    @inline def available(state: Long): Long  = if (state > 0) state else 0L
    @inline def waiters(state: Long): Long    = if (state >= 0) 0L else -(state >> UpperShift)
    @inline def demand(state: Long): Long     = if (state >= 0) 0L else state & LowerMask
    @inline def awaited(state: Long): Boolean = state < 0

    @inline def addWaiter(state: Long)(requested: Long): Long =
      if (state >= 0) State(waiters = 1, demand = requested - state)
      else State(waiters = waiters(state) + 1, demand = demand(state) + requested)

    @inline def removeWaiter(state: Long)(requested: Long): Long = {
      val currentWaiters = waiters(state)
      if (currentWaiters <= 1) 0L
      else State(waiters = currentWaiters - 1, demand = demand(state) - requested)
    }

    @inline def reduceDemand(state: Long)(permits: Long): Long =
      if (state >= 0) 0
      else State(waiters = waiters(state), demand = demand(state) - permits)

    @inline def release(state: Long)(permits: Long, maxPermits: Long): Long =
      Math.min(maxPermits, state + permits)
  }

  private final class Internal(permits: Long) extends AtomicLong(permits) with Semaphore {
    private val waiters: internal.MutableConcurrentQueue[Waiter] =
      internal.MutableConcurrentQueue.unbounded

    override def available(implicit trace: Trace): UIO[Long] = ZIO.succeed(State.available(get()))

    override def awaiting(implicit trace: Trace): UIO[Long] = ZIO.succeed(State.waiters(get()))

    override def withPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      withPermits(1L)(zio)

    override def withPermitScoped(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
      withPermitsScoped(1L)

    override def withPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      ZIO.suspendSucceed {
        if (isZero(n)) zio
        else if (tryAcquire(n)) ensuringRelease(n)(zio)
        else
          ZIO.uninterruptibleMask { restore =>
            acquire(n, restore) *> restore(zio).foldCauseZIO(
              cause => { releaseUnsafe(n); ZIO.failCause(cause) },
              a => { releaseUnsafe(n); ZIO.succeed(a) }
            )
          }
      }

    override def withPermitsScoped(n: Long)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
      ZIO.suspendSucceed {
        if (isZero(n)) ZIO.unit
        else
          ZIO.uninterruptibleMask { restore =>
            ZIO.acquireRelease(acquire(n, restore))(_ => ZIO.succeed(releaseUnsafe(n)))
          }
      }

    override def tryWithPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Option[A]] =
      ZIO.suspendSucceed {
        if (n < 0) ZIO.die(new IllegalArgumentException(s"Unexpected negative `$n` permits requested."))
        else if (n == 0L) zio.asSome
        else if (n > permits) Exit.none
        else if (tryAcquire(n)) ensuringRelease(n)(zio).asSome
        else
          ZIO.uninterruptibleMask { restore =>
            ZIO.suspendSucceed {
              if (tryAcquire(n)) {
                restore(zio).foldCauseZIO(
                  cause => { releaseUnsafe(n); ZIO.failCause(cause) },
                  a => { releaseUnsafe(n); ZIO.succeed(Some(a)) }
                )
              } else Exit.none
            }
          }
      }

    private def ensuringRelease[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      ZIO.uninterruptibleMask { restore =>
        restore(zio).foldCauseZIO(
          cause => { releaseUnsafe(n); ZIO.failCause(cause) },
          a => { releaseUnsafe(n); ZIO.succeed(a) }
        )
      }

    private def isZero(n: Long): Boolean =
      if (n < 0) throw new IllegalArgumentException(s"Unexpected negative `$n` permits requested.")
      else if (n > permits)
        throw new IllegalArgumentException(
          s"Cannot acquire `$n` permits from a semaphore with only `$permits` permits."
        )
      else n == 0L

    @tailrec
    private def tryAcquire(n: Long): Boolean = {
      val state = get()
      if (state < n) false
      else if (compareAndSet(state, state - n)) true
      else tryAcquire(n)
    }

    private def acquire(n: Long, restore: ZIO.InterruptibilityRestorer)(implicit trace: Trace): UIO[Unit] =
      ZIO.suspendSucceed {
        @tailrec
        def loop(n: Long): UIO[Unit] = {
          val state = get()
          if (state >= n) {
            if (compareAndSet(state, state - n)) Exit.unit
            else loop(n)
          } else {
            val updated = State.addWaiter(state)(n)
            val demand  = n - State.available(state)
            if (compareAndSet(state, updated)) {
              val promise = Promise.unsafe.make[Nothing, Unit](FiberId.None)(Unsafe)
              val waiter =
                if (demand == 1L) new Waiter.Single(promise)
                else new Waiter.Multi(promise, demand)

              waiters.offer(waiter)

              restore(promise.await).onInterrupt {
                ZIO.succeed {
                  promise.unsafe.completeWith(Exit.unit)(Unsafe)
                  // FIX: Always refund the full original request, NOT just the remaining demand!
                  releaseUnsafe(n)
                }
              }
            } else loop(n)
          }
        }
        loop(n)
      }

    private def releaseUnsafe(n: Long): Unit = {
      @tailrec
      def loop(remaining: Long): Unit =
        if (remaining <= 0L) ()
        else {
          val state = get()
          if (state >= permits) assert(DisableAssertions)
          else if (state < 0L) {
            val waiter = waiters.poll(null)
            if (waiter eq null) loop(remaining)
            else {
              val waiterPermits = waiter.permits
              if (waiterPermits <= remaining) {
                fulfillWaiter(state, waiterPermits)
                waiter.promise.unsafe.completeWith(Exit.unit)(Unsafe)
                loop(remaining - waiterPermits)
              } else {
                if (tryReduceDemand(state, remaining, retries = 2)) {
                  waiters.offer(waiter.reducedBy(remaining))
                } else {
                  waiters.offer(waiter)
                  loop(remaining)
                }
              }
            }
          } else if (compareAndSet(state, State.release(state)(remaining, permits))) ()
          else loop(remaining)
        }
      loop(n)
    }

    @tailrec
    private def fulfillWaiter(state: Long, permits: Long): Unit =
      if (state >= 0) assert(DisableAssertions)
      else if (compareAndSet(state, State.removeWaiter(state)(permits))) ()
      else fulfillWaiter(get(), permits)

    private def tryReduceDemand(state: Long, permits: Long, retries: Int): Boolean =
      if (retries <= 0) false
      else if (compareAndSet(state, State.reduceDemand(state)(permits))) true
      else tryReduceDemand(get(), permits, retries - 1)
  }
}
