package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stm.TSemaphore

import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import java.util.concurrent.ConcurrentLinkedQueue

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
      new SemaphoreImpl(permits)
  }

  private final class Waiter(val n: Long, val promise: Promise[Nothing, Unit]) {
    @volatile var cancelled: Boolean = false
    val completed = new AtomicBoolean(false)
  }

  private final class SemaphoreImpl(initialPermits: Long) extends Semaphore {
    // Tracks available permits. Can go negative when there are waiters
    // waiting for more permits than currently available? No — we track
    // only available count; waiters are kept separately.
    private[this] val permitsRef = new AtomicLong(initialPermits)
    private[this] val waiters    = new ConcurrentLinkedQueue[Waiter]()
    // Count of waiters (for `awaiting` and to know if we need to go slow path)
    private[this] val waiterCount = new AtomicLong(0L)

    def available(implicit trace: Trace): UIO[Long] =
      ZIO.succeed {
        val p = permitsRef.get()
        if (p < 0L) 0L else p
      }

    override def awaiting(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(waiterCount.get())

    def withPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      withPermits(1L)(zio)

    def withPermitScoped(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
      withPermitsScoped(1L)

    def withPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      if (n < 0L)
        ZIO.die(new IllegalArgumentException(s"Unexpected negative `$n` permits requested."))
      else if (n == 0L)
        zio
      else
        ZIO.uninterruptibleMask { restore =>
          acquire(n, restore).foldCauseZIO(
            cause => {
              releaseN(n)
              Exit.failCause(cause)
            },
            _ =>
              restore(zio).foldCauseZIO(
                cause => {
                  releaseN(n)
                  Exit.failCause(cause)
                },
                a => {
                  releaseN(n)
                  Exit.succeed(a)
                }
              )
          )
        }

    def withPermitsScoped(n: Long)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
      if (n < 0L)
        ZIO.die(new IllegalArgumentException(s"Unexpected negative `$n` permits requested."))
      else if (n == 0L)
        ZIO.unit
      else
        ZIO.uninterruptibleMask { restore =>
          (acquire(n, restore) *> ZIO.addFinalizer(ZIO.succeed(releaseN(n)))).unit
        }

    override def tryWithPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Option[A]] =
      if (n < 0L)
        ZIO.die(new IllegalArgumentException(s"Unexpected negative `$n` permits requested."))
      else if (n == 0L)
        zio.asSome
      else
        ZIO.suspendSucceed {
          if (tryAcquire(n))
            zio.asSome.onExit(_ => ZIO.succeed(releaseN(n)))
          else
            Exit.none
        }

    // Fast-path try-acquire. Only succeeds if there are no waiters and enough permits.
    private def tryAcquire(n: Long): Boolean = {
      // If there are waiters queued, we should not jump ahead (preserve ordering for acquirers).
      if (waiterCount.get() != 0L) return false
      var loop = true
      var acquired = false
      while (loop) {
        val current = permitsRef.get()
        if (current < n) {
          loop = false
        } else if (permitsRef.compareAndSet(current, current - n)) {
          loop = false
          acquired = true
        }
      }
      acquired
    }

    // Acquire n permits; may suspend. Must be called in uninterruptible context.
    private def acquire(n: Long, restore: ZIO.InterruptibilityRestorer)(implicit trace: Trace): UIO[Unit] = {
      // Fast path: try to immediately acquire if no waiters
      if (tryAcquire(n)) ZIO.unit
      else {
        Promise.make[Nothing, Unit].flatMap { promise =>
          val waiter = new Waiter(n, promise)
          waiterCount.incrementAndGet()
          waiters.offer(waiter)
          // After enqueueing, try to drain so we don't block unnecessarily
          // in case permits were released between fast-path check and enqueue.
          drainWaiters()
          restore(promise.await).onInterrupt(ZIO.succeed(cancelWaiter(waiter, n)))
        }
      }
    }

    private def cancelWaiter(waiter: Waiter, n: Long): Unit = {
      if (!waiter.cancelled) {
        waiter.cancelled = true
        waiterCount.decrementAndGet()
        // We race with the drainer to complete the promise.
        // If we win, we complete the promise and no permits were granted.
        // If we lose, the drainer completed the promise and we must release the permits.
        if (waiter.completed.compareAndSet(false, true)) {
          // We won the race, so we are responsible for completing the promise.
          // Since this is from an interruption, no permits were acquired.
          waiter.promise.unsafe.done(Exit.unit)(Unsafe.unsafe)
        } else {
          // The promise was already completed by a releaser, which means
          // permits were granted. We must release them back.
          releaseN(n)
        }
      }
    }

    // Release n permits back to the semaphore and try to wake up waiters.
    private def releaseN(n: Long): Unit = {
      permitsRef.addAndGet(n)
      drainWaiters()
    }

    // Tries to wake up waiters in FIFO order while enough permits are available.
    private def drainWaiters(): Unit = {
      var continue = true
      while (continue) {
        val head = waiters.peek()
        if (head eq null) {
          continue = false
        } else if (head.cancelled) {
          waiters.poll()
          // continue loop
        } else {
          val needed = head.n
          var acquired = false
          var loop = true
          while (loop) {
            val current = permitsRef.get()
            if (current < needed) {
              loop = false
            } else if (permitsRef.compareAndSet(current, current - needed)) {
              loop = false
              acquired = true
            }
          }
          if (acquired) {
            // Try to remove this waiter from the queue and signal it.
            if (waiters.remove(head)) {
              if (head.cancelled) {
                // Waiter was cancelled between acquisition and removal; return permits.
                permitsRef.addAndGet(needed)
              } else {
                waiterCount.decrementAndGet()
                // Race to complete the promise. If we lose, it means the waiter was
                // cancelled concurrently and we must return the permits.
                if (head.completed.compareAndSet(false, true)) {
                  // We won the race, we can complete the promise.
                  head.promise.unsafe.done(Exit.unit)(Unsafe.unsafe)
                } else {
                  // Promise was already completed by cancellation; return permits.
                  permitsRef.addAndGet(needed)
                }
              }
            } else {
              // Another thread concurrently removed it; return the permits.
              permitsRef.addAndGet(needed)
            }
          } else {
            continue = false
          }
        }
      }
    }
  }
}