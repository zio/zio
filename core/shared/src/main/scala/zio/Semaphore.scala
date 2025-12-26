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
 * [[zio.stm.TSemaphore]] and define it in a [[zio.stm.ZSTM]] transaction.
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

  /**
   * For Scala 3, `-X-elide-below` is ignored, and therefore we need to use an
   * '''inlinable''' build-time constant to disable assertions
   */
  private final val DisableAssertions = BuildInfo.optimizationsEnabled

  /**
   * State encoding: {{ state > 0: Available number of permits state == 0: No
   * permits and no waiters state < 0: Waiters present (bit-packed) }}
   *
   * Bit layout for negative state (64 bits total):
   * {{{
   * ┌─────────────────────────────────┬─────────────────────────────────┐
   * │     Upper 32 bits (bits 32-63)  │     Lower 32 bits (bits 0-31)   │
   * ├─────────────────────────────────┼─────────────────────────────────┤
   * │          -numWaiters            │         permitsAwaited          │
   * └─────────────────────────────────┴─────────────────────────────────┘
   * }}}
   *
   * Constraints:
   *   - Max waiters: Int.MaxValue (~2.1 billion)
   *   - Max demand: 0xffffffffL (~4.3 billion)
   */
  private[zio] object State {
    private final val LowerMask: Long = 0xffffffffL // Lower 32 bits mask
    private final val UpperShift: Int = 32

    // Maximum values are constrained by signed arithmetic in the encoding.
    // The formula (-waiters << 32) only produces a negative result when
    // waiters <= Int.MaxValue, because the lower 32 bits of -waiters must
    // have bit 31 set for the result to be negative.
    final val MaxWaiters: Long = Int.MaxValue.toLong
    final val MaxDemand: Long  = LowerMask

    /**
     * Pack waiters count and permits count into a negative state value.
     */
    @inline
    def apply(waiters: Long, demand: Long): Long = {
      assert(
        DisableAssertions || waiters >= 0 && waiters <= MaxWaiters,
        s"waiters must be in [0, $MaxWaiters], got $waiters"
      )
      assert(
        DisableAssertions || demand >= 0 && demand <= MaxDemand,
        s"demand must be in [0, $MaxDemand], got $demand"
      )
      assert(DisableAssertions || demand >= waiters, s"demand ($demand) must be >= waiters ($waiters)")
      (-waiters << UpperShift) | (demand & LowerMask)
    }

    /**
     * Extract the number of available permits. Returns 0 if state is negative
     * (waiters present).
     */
    @inline
    def available(state: Long): Long =
      if (state > 0) state else 0L

    /**
     * Extract the number of waiters from a packed state. Returns 0 if state is
     * non-negative (no waiters).
     */
    @inline
    def waiters(state: Long): Long =
      if (state >= 0) 0L else -(state >> UpperShift)

    /**
     * Extract the total permits demanded by waiters. Returns 0 if state is
     * non-negative (no waiters).
     */
    @inline
    def demand(state: Long): Long =
      if (state >= 0) 0L else state & LowerMask

    /**
     * Returns true if there are waiters.
     */
    @inline
    def awaited(state: Long): Boolean = state < 0

    /**
     * Add a new waiter requesting n permits. Consumes any currently available
     * permits from state, then records only the remainder as "still needed".
     */
    @inline
    def addWaiter(state: Long)(requested: Long): Long =
      if (state >= 0) State(waiters = 1, demand = requested - state)
      else State(waiters = waiters(state) + 1, demand = demand(state) + requested)

    /**
     * Remove a waiter and subtract permits from the waiting total. Called when
     * a waiter is fulfilled.
     */
    @inline
    def removeWaiter(state: Long)(requested: Long): Long = {
      val currentWaiters = waiters(state)
      if (currentWaiters <= 1) 0L // Last waiter removed, state becomes 0
      else State(waiters = currentWaiters - 1, demand = demand(state) - requested)
    }

    /**
     * Reduce the demand without changing waiter count. Used when partially
     * satisfying a waiter's permit request.
     */
    @inline
    def reduceDemand(state: Long)(permits: Long): Long =
      if (state >= 0) 0 // { assert(DisableAssertions); 0 }
      else State(waiters = waiters(state), demand = demand(state) - permits)

    /**
     * Release permits back to the available pool, capped at maxPermits. Only
     * valid when state >= 0 (no waiters present).
     */
    @inline
    def release(state: Long)(permits: Long, maxPermits: Long): Long =
      Math.min(maxPermits, state + permits)
  }

  private final class Internal(permits: Long) extends AtomicLong(permits) with Semaphore {

    private val waiters: internal.MutableConcurrentQueue[Waiter] =
      internal.MutableConcurrentQueue.unbounded

    override def available(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(State.available(get()))

    override def awaiting(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(State.waiters(get()))

    override def withPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      withPermits(1L)(zio)

    override def withPermitScoped(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
      withPermitsScoped(1L)

    override def withPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      ZIO.suspendSucceed {
        if (isZero(n)) zio
        else if (tryAcquire(n)) ensuringRelease(n)(zio)
        else ZIO.acquireReleaseWith(acquire(n))(_ => ZIO.succeed(releaseUnsafe(n)))(_ => zio)
      }

    override def withPermitsScoped(n: Long)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
      ZIO.suspendSucceed {
        if (isZero(n)) Exit.unit
        else ZIO.acquireRelease(acquire(n))(_ => ZIO.succeed(releaseUnsafe(n)))
      }

    override def tryWithPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Option[A]] =
      ZIO.suspendSucceed {
        if (n < 0) ZIO.die(new IllegalArgumentException(s"Unexpected negative `$n` permits requested."))
        else if (n == 0L) zio.asSome
        else if (n > permits) Exit.none
        else if (tryAcquire(n)) ensuringRelease(n)(zio).asSome
        else Exit.none
      }

    private def ensuringRelease[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      ZIO.uninterruptibleMask { restore =>
        restore(zio).foldCauseZIO(
          cause => { releaseUnsafe(n); Exit.failCause(cause) },
          a => { releaseUnsafe(n); Exit.succeed(a) }
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

    private def acquire(n: Long)(implicit trace: Trace): UIO[Unit] = {

      /**
       * Attempts to acquire n permits. If all n permits are available, acquires
       * them and returns Exit.unit. Otherwise, creates a Waiter entry, adds it
       * to the queue, and awaits the waiter's promise.
       */
      @tailrec
      def loop(n: Long): UIO[Unit] = {
        val state = get()
        if (state >= n) {
          // All available: attempt to acquire immediately
          if (compareAndSet(state, state - n)) Exit.unit
          else loop(n)
        } else {
          // Not enough permits available, so we must wait
          val updated = State.addWaiter(state)(n)
          val demand  = n - State.available(state)

          if (compareAndSet(state, updated)) {
            val promise = Promise.unsafe.make[Nothing, Unit](FiberId.None)(Unsafe)
            val waiter =
              if (demand == 1L) new Waiter.Single(promise)
              else new Waiter.Multi(promise, demand)
            waiters.offer(waiter)
            promise.await.onInterrupt {
              ZIO.succeed {
                // On interrupt, try to complete the promise ourselves.
                // If we succeed, it means we weren't yet fulfilled, so we
                // need to release our waiter slot and any permits we might
                // have been holding. waiter.permits reads the current value,
                // which may have been reduced by partial fulfillment.
                if (promise.unsafe.completeWith(Exit.unit)(Unsafe)) {
                  releaseUnsafe(waiter.permits)
                }
              }
            }
          } else loop(n)
        }
      }
      loop(n)
    }

    /**
     * Releases n permits back to the semaphore. This method handles fulfilling
     * waiters that can now proceed with their requested permits.
     *
     * Logic:
     *   1. If there were waiters (state was negative), we need to fulfill them
     *   1. Poll waiters from the queue and check if we have enough permits for
     *      them
     *   1. If we have enough, fulfill the waiter and continue with remaining
     *      permits
     *   1. If not enough, we need to put the waiter back and add permits to the
     *      state
     *   1. If there were no waiters, atomically add permits back to the state
     */
    private def releaseUnsafe(n: Long): Unit = {
      @tailrec
      def loop(remaining: Long): Unit =
        if (remaining <= 0L) ()
        else {
          val state = get()
          if (state >= permits) assert(DisableAssertions) // Already at max permits, should be unreachable
          else if (state < 0L) {
            // There are waiters - try to get one from the queue
            // Note: There's a race between acquireUnsafe setting state and offering to queue,
            // so the queue might be momentarily empty even though state says there are waiters.
            // We will loop and keep trying to poll until we get a waiter, or just release the permits.
            val waiter = waiters.poll(null)
            if (waiter eq null) loop(remaining)
            else {
              val waiterPermits = waiter.permits

              if (waiterPermits <= remaining) {
                // We have enough permits to fulfill this waiter completely
                fulfillWaiter(state, waiterPermits)
                waiter.promise.unsafe.completeWith(Exit.unit)(Unsafe)
                loop(remaining - waiterPermits)
              } else {
                // Not enough permits for this waiter, update state, then put back
                if (tryReduceDemand(state, remaining, retries = 2)) {
                  // we can only be operating on a Multi-waiter since (0 < remaining < waiter.permits)
                  waiters.offer(waiter.reducedBy(remaining))
                } else {
                  // State changed due to race, put original back and retry
                  // this is slightly unfair, as it puts the waiter back in the queue
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

    /**
     * Fulfills a waiter by removing it and its permits from the state.
     */
    @tailrec
    private def fulfillWaiter(state: Long, permits: Long): Unit =
      if (state >= 0) assert(DisableAssertions)
      else if (compareAndSet(state, State.removeWaiter(state)(permits))) ()
      else fulfillWaiter(get(), permits)

    /**
     * Reduces the demand without changing waiter count. Returns true if the CAS
     * succeeded, false if state changed.
     */
    private def tryReduceDemand(state: Long, permits: Long, retries: Int): Boolean =
      if (retries <= 0) false
      else if (compareAndSet(state, State.reduceDemand(state)(permits))) true
      else tryReduceDemand(get(), permits, retries - 1)
  }
}
