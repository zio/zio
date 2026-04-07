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

import zio.internal.UnboundedMpmcQueue
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
 * If you need functionality that `Semaphore` doesn't provide, use a
 * [[TSemaphore]] and define it in a [[zio.stm.ZSTM]] transaction.
 */
sealed abstract class Semaphore extends Serializable {

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
    def make(initialPermits: Long)(implicit unsafe: Unsafe): Semaphore =
      new ConcurrentSemaphore(initialPermits)
  }

  /**
   * A waiter in the semaphore queue, waiting for `needed` permits.
   */
  private final class Waiter(val needed: Long, val promise: Promise[Nothing, Unit])

  private sealed abstract class Acquisition {
    def waitUntilAcquired(implicit trace: Trace): UIO[Unit]
    def release(implicit trace: Trace): UIO[Any]
  }

  /**
   * Semaphore implementation using separated concerns:
   *
   *   - `AtomicLong` for the permit counter (fast path: 1 CAS, zero allocation)
   *   - `UnboundedMpmcQueue` for the waiter queue (slow path only)
   *
   * Design inspired by:
   *   - JDK AQS: separated permit counter from wait queue
   *   - Kyo Meter: AtomicLong + lock-free queue, skip-on-release cancellation
   *   - Tokio Semaphore: partial permit allocation (consume available, queue
   *     deficit)
   *
   * Fast path (permits available, no waiters): single CAS on AtomicLong, zero
   * allocation. Slow path (contended): one Promise allocation +
   * queue offer.
   */
  private[zio] final class ConcurrentSemaphore(initialPermits: Long)(implicit u: Unsafe) extends Semaphore {

    /** Permit counter. Always >= 0 on quiescent state. */
    private[this] val permits = new AtomicLong(initialPermits)

    /** FIFO queue of waiters. Only touched on the slow path. */
    private[this] val waiters = UnboundedMpmcQueue[Waiter](8)

    override def available(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(permits.get())

    override def awaiting(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(waiters.size().toLong)

    override def withPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      withPermits(1L)(zio)

    override def withPermitScoped(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
      withPermitsScoped(1L)

    override def withPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      ZIO.acquireReleaseWith(acquire(n))(_.release)(_.waitUntilAcquired *> zio)

    override def withPermitsScoped(n: Long)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
      ZIO.acquireRelease(acquire(n))(_.release).flatMap(_.waitUntilAcquired)

    override def tryWithPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Option[A]] =
      ZIO.acquireReleaseWith(tryAcquire(n)) {
        case Some(release) => release
        case _             => Exit.unit
      } {
        case _: Some[?] => zio.asSome
        case _          => Exit.none
      }

    /** Fast-path acquisition: permits were immediately available. */
    private final class ImmediateAcquisition(n: Long) extends Acquisition {
      def waitUntilAcquired(implicit trace: Trace): UIO[Unit] = Exit.unit
      def release(implicit trace: Trace): UIO[Any]            = releaseN(n)
    }

    /**
     * Slow-path acquisition: fiber must wait for permits.
     *
     * Release handler uses the promise as a coordination point:
     *   - If we can complete the promise first (cancel path): the releaser
     *     hasn't given us the deficit permits yet. Return only the consumed
     *     portion. The deficit will be returned by the releaser when it
     *     encounters our completed promise (skip-on-release).
     *   - If the releaser already completed the promise (normal path): we hold
     *     all n permits. Return all n.
     */
    private final class PendingAcquisition(
      n: Long,
      consumed: Long,
      waiter: Waiter
    ) extends Acquisition {
      def waitUntilAcquired(implicit trace: Trace): UIO[Unit] = waiter.promise.await

      def release(implicit trace: Trace): UIO[Any] = ZIO.succeed {
        val weClaimed = waiter.promise.unsafe.completeWith(Exit.unit)
        if (weClaimed) {
          permits.getAndAdd(consumed)
        } else {
          permits.getAndAdd(n)
        }
        pollWaiters()
      }
    }

    private[this] val zeroAcquisition = Exit.succeed(new ImmediateAcquisition(0L))

    /**
     * Try to acquire `n` permits. Returns an Acquisition that must be released.
     */
    private def acquire(n: Long)(implicit trace: Trace): UIO[Acquisition] =
      if (n < 0L) ZIO.die(new IllegalArgumentException(s"Unexpected negative `$n` permits requested."))
      else if (n == 0L) zeroAcquisition
      else
        ZIO.fiberIdWith { fiberId =>
          Exit.succeed {
            if (tryDecrementPermits(n)) {
              new ImmediateAcquisition(n)
            } else {
              val consumed = drainPermits(n)
              val deficit  = n - consumed
              val promise  = Promise.unsafe.make[Nothing, Unit](fiberId)
              val waiter   = new Waiter(deficit, promise)
              waiters.offer(waiter)
              pollWaiters()
              new PendingAcquisition(n, consumed, waiter)
            }
          }
        }

    /**
     * Try to acquire `n` permits without waiting.
     */
    private def tryAcquire(n: Long)(implicit trace: Trace): UIO[Option[UIO[Any]]] =
      if (n < 0L) ZIO.die(new IllegalArgumentException(s"Unexpected negative `$n` permits requested."))
      else if (n == 0L) Exit.succeed(Some(Exit.unit))
      else
        ZIO.succeed {
          if (tryDecrementPermits(n)) Some(releaseN(n))
          else None
        }

    /**
     * CAS loop: try to deduct `n` permits. Succeeds only if `permits >= n`.
     */
    @tailrec
    private def tryDecrementPermits(n: Long): Boolean = {
      val current = permits.get()
      if (current >= n) {
        if (permits.compareAndSet(current, current - n)) true
        else tryDecrementPermits(n)
      } else false
    }

    /**
     * Drain up to `n` permits from the counter. Returns the number actually
     * consumed (0 to n).
     */
    @tailrec
    private def drainPermits(n: Long): Long = {
      val current = permits.get()
      val consume = math.min(current, n)
      if (consume <= 0L) 0L
      else if (permits.compareAndSet(current, current - consume)) consume
      else drainPermits(n)
    }

    /**
     * Release `n` permits back and wake any satisfied waiters.
     */
    private def releaseN(n: Long)(implicit trace: Trace): UIO[Any] =
      if (n <= 0L) Exit.unit
      else
        ZIO.succeed {
          permits.getAndAdd(n)
          pollWaiters()
        }

    /**
     * Walk the waiter queue FIFO, waking waiters whose permit needs can be
     * satisfied. Uses skip-on-release cancellation: if a waiter's promise is
     * already completed (interrupted or claimed by cancel handler), return its
     * permits and try the next waiter.
     *
     * Synchronized to prevent a race where two concurrent callers both peek
     * the same waiter, both CAS permits, and then poll different elements
     * (losing a waiter). This is the same approach Tokio uses (mutex on the
     * release path). The lock is only held during queue manipulation — no
     * fiber suspension or I/O occurs under the lock.
     *
     * `completeWith(Exit.unit)` both completes the promise and triggers all
     * registered callbacks (resuming the waiting fiber), so no additional
     * action is needed after completion.
     */
    private def pollWaiters(): Unit = pollLock.synchronized {
      @tailrec def loop(): Unit = {
        val waiter = waiters.peek()
        if (waiter ne null) {
          val available = permits.get()
          if (available >= waiter.needed) {
            if (permits.compareAndSet(available, available - waiter.needed)) {
              waiters.poll()
              val woke = waiter.promise.unsafe.completeWith(Exit.unit)
              if (!woke) {
                permits.getAndAdd(waiter.needed)
              }
              loop()
            } else {
              loop()
            }
          }
        }
      }
      loop()
    }
    private[this] val pollLock = new AnyRef
  }
}
