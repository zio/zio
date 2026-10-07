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
import zio.internal.SemaphorePermits
import zio.stm.TSemaphore

import java.util.concurrent.atomic.AtomicLong
import scala.annotation.tailrec
import scala.collection.mutable

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
   * Creates a new `Semaphore` with the specified number of permits. Fibers
   * waiting for permits are served in FIFO order.
   */
  def make(permits: => Long)(implicit trace: Trace): UIO[Semaphore] =
    ZIO.succeed(unsafe.make(permits)(Unsafe))

  /**
   * Creates a new unfair `Semaphore` with the specified number of permits.
   *
   * A fiber that finds free permits takes them, whether or not other fibers are
   * queued for them. A fiber that releases and immediately re-acquires
   * therefore usually keeps its permits without suspending, which gives higher
   * throughput under contention than [[make]]. A queued fiber may wait
   * indefinitely.
   */
  def makeUnfair(permits: => Long)(implicit trace: Trace): UIO[Semaphore] =
    ZIO.succeed(unsafe.makeUnfair(permits)(Unsafe))

  object unsafe {
    def make(permits: Long)(implicit unsafe: Unsafe): Semaphore =
      create(permits, fair = true)

    def makeUnfair(permits: Long)(implicit unsafe: Unsafe): Semaphore =
      create(permits, fair = false)
  }

  /**
   * Padding the counter pays only when several permits are contended across
   * cores; with one permit it measured as no gain, and one-permit semaphores
   * are the ones created in large quantities (every `Ref.Synchronized` has
   * one).
   */
  private def create(permits: Long, fair: Boolean): Semaphore =
    if (permits <= 1L) new Compact(permits, fair) else new Padded(permits, fair)

  /** A semaphore whose counter is an ordinary field. */
  private final class Compact(initial: Long, protected val fair: Boolean) extends AtomicLong(initial) with Impl {
    override protected def freeGet(): Long                                           = get()
    override protected def freeCompareAndSet(expected: Long, updated: Long): Boolean = compareAndSet(expected, updated)
    override protected def freeAddAndGet(delta: Long): Long                          = addAndGet(delta)
  }

  /** A semaphore whose counter has a cache line to itself. */
  private final class Padded(initial: Long, protected val fair: Boolean) extends SemaphorePermits(initial) with Impl {
    override protected def freeGet(): Long = permitsGet()
    override protected def freeCompareAndSet(expected: Long, updated: Long): Boolean =
      permitsCompareAndSet(expected, updated)
    override protected def freeAddAndGet(delta: Long): Long = permitsAddAndGet(delta)
  }

  /**
   * A fiber waiting for `n` permits. All fields are guarded by the owning
   * semaphore's lock. `woken` is set when the semaphore resumes the waiter,
   * either because it was granted its permits (fair) or because it should retry
   * for them (unfair); `holding` is set once the waiter owns its permits;
   * `cancelled` is set by the first `cancelOrRelease`, which makes later ones
   * no-ops and stops the waiter from taking permits.
   */
  private final class Waiter(val n: Long) {
    var callback: ZIO[Any, Nothing, Unit] => Unit = null
    var woken: Boolean                            = false
    var holding: Boolean                          = false
    var cancelled: Boolean                        = false
  }

  /**
   * The semaphore, over a counter of free permits supplied by the class that
   * fixes its memory layout. Both policies live here, chosen by `fair`, so that
   * the two layout classes are the only implementations a call site can see.
   *
   * Fair: `hasWaiters` gates the fast path, so once a fiber is queued, every
   * fiber that asks for permits after it queues behind it, and a release hands
   * permits to the head of the queue directly.
   *
   * Unfair: the fast path ignores waiters, so a fiber that finds free permits
   * takes them even while others are queued. In particular, a fiber that
   * releases and immediately re-acquires keeps running instead of handing its
   * permits over and queuing, which is where the throughput comes from. A
   * release wakes one waiter to retry, but only if no woken waiter is already
   * retrying, so a fiber that keeps the permits hot pays for at most one wakeup
   * at a time. A waiter whose retry resolves wakes the next, and one that loses
   * goes back to the front of the queue.
   */
  private sealed trait Impl extends Semaphore {

    /**
     * The counter of free permits: one CAS to take, one atomic add to return.
     */
    protected def freeGet(): Long
    protected def freeCompareAndSet(expected: Long, updated: Long): Boolean
    protected def freeAddAndGet(delta: Long): Long

    protected def fair: Boolean

    /**
     * True whenever the queue may be non-empty. Written under the lock, read
     * without it. A waiter publishes `true` before re-reading the permits, and
     * a releaser adds permits before reading this flag, so one of the two
     * always observes the other and no wakeup is lost.
     */
    @volatile private[this] var hasWaiters: Boolean = false

    /**
     * Guarded by `this`. Allocated when a fiber first queues, so a semaphore
     * that is never contended never pays for it.
     */
    private[this] var queue: mutable.Queue[Waiter] = null

    /**
     * Guarded by `this`, unfair only: a waiter has been woken and has not yet
     * retried.
     */
    private[this] var retrying: Waiter = null

    /**
     * Guarded by `this`, unfair only: no queued waiter asks for fewer permits
     * than this. It is lowered when a waiter queues and made exact when a scan
     * of the queue finds nobody to wake, so a release that can satisfy nobody
     * skips the scan. It may be lower than the true minimum after a waiter
     * leaves, which costs one scan and nothing else.
     */
    private[this] var smallestQueued: Long = Long.MaxValue

    /** The waiter queue. The caller holds the lock. */
    private[this] def waiters: mutable.Queue[Waiter] = {
      if (queue eq null) queue = new mutable.Queue[Waiter]
      queue
    }

    /** The number of queued waiters. The caller holds the lock. */
    private[this] def queued: Long =
      if (queue eq null) 0L else queue.size.toLong

    // `hasWaiters` is also true for an instant while a fiber decides whether to
    // queue, so where it would change the answer the queue is consulted under
    // the lock instead.
    final def available(implicit trace: Trace): UIO[Long] =
      ZIO.succeed {
        if (fair && hasWaiters) synchronized(if (queued == 0L) freeGet() else 0L)
        else freeGet()
      }

    final override def awaiting(implicit trace: Trace): UIO[Long] =
      ZIO.succeed {
        if (fair && !hasWaiters) 0L
        else synchronized(queued + (if (retrying eq null) 0L else 1L))
      }

    final def withPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      withPermits(1L)(zio)

    final def withPermitScoped(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
      withPermitsScoped(1L)

    final def withPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      if (n < 0L) negative(n)
      else if (n == 0L) zio
      else
        ZIO.uninterruptibleMask { restore =>
          val waiter = reserve(n)
          val body   = if (waiter eq null) zio else await(waiter).flatMap(_ => zio)
          restore(body).foldCauseZIO(
            cause => { releaseOrCancel(waiter, n); Exit.failCause(cause) },
            a => { release(n); Exit.succeed(a) }
          )
        }

    final def withPermitsScoped(n: Long)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
      if (n < 0L) negative(n)
      else if (n == 0L) ZIO.unit
      else
        ZIO.uninterruptibleMask { restore =>
          ZIO
            .acquireRelease(ZIO.succeed(reserve(n)))(waiter => ZIO.succeed(releaseOrCancel(waiter, n)))
            .flatMap { waiter =>
              // The scope may outlive the wait, so a fiber that stops waiting
              // cleans up now rather than leaving its waiter for the finalizer.
              // Only the wait is interruptible, so there is no point at which
              // a waiter exists without this cleanup in place.
              if (waiter eq null) ZIO.unit
              else restore(await(waiter)).onInterrupt(ZIO.succeed(cancelOrRelease(waiter)))
            }
        }

    final override def tryWithPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit
      trace: Trace
    ): ZIO[R, E, Option[A]] =
      if (n < 0L) negative(n)
      else if (n == 0L) zio.asSome
      else
        ZIO.uninterruptibleMask { restore =>
          val acquired =
            if (fair && hasWaiters) synchronized(queued == 0L && takePermits(n))
            else takePermits(n)
          if (acquired)
            restore(zio).foldCauseZIO(
              cause => { release(n); Exit.failCause(cause) },
              a => { release(n); Exit.succeed(Some(a)) }
            )
          else Exit.none
        }

    private[this] def negative(n: Long)(implicit trace: Trace): UIO[Nothing] =
      ZIO.die(new IllegalArgumentException(s"Unexpected negative `$n` permits requested."))

    /** Takes `n` permits if available, ignoring any waiters. */
    @tailrec
    private[this] def takePermits(n: Long): Boolean = {
      val current = freeGet()
      if (current < n) false
      else if (freeCompareAndSet(current, current - n)) true
      else takePermits(n)
    }

    /** Takes `n` permits now if the policy allows. */
    private[this] def tryAcquire(n: Long): Boolean =
      (!fair || !hasWaiters) && takePermits(n)

    /** Takes `n` permits now, returning null, or queues a waiter for them. */
    private[this] def reserve(n: Long): Waiter =
      if (tryAcquire(n)) null
      else
        synchronized {
          // Publish the flag before re-reading the permits (see `hasWaiters`).
          hasWaiters = true
          if ((!fair || queued == 0L) && takePermits(n)) {
            if (queued == 0L) hasWaiters = false
            null
          } else {
            val waiter = new Waiter(n)
            waiters += waiter
            if (n < smallestQueued) smallestQueued = n
            waiter
          }
        }

    /** Returns `n` permits and wakes queued fibers as the policy dictates. */
    private[this] def release(n: Long): Unit = {
      freeAddAndGet(n)
      if (hasWaiters) wake()
    }

    /**
     * Undoes a `reserve`: returns the permits it took, or cleans up its waiter.
     */
    private[this] def releaseOrCancel(waiter: Waiter, n: Long): Unit =
      if (waiter eq null) release(n) else cancelOrRelease(waiter)

    /**
     * Cleans up after a waiter whose owner is done with it: a waiter still
     * queued is removed, one that holds permits returns them.
     */
    private[this] def cancelOrRelease(waiter: Waiter): Unit = {
      var first   = false
      var holding = false
      synchronized {
        if (!waiter.cancelled) {
          waiter.cancelled = true
          first = true
          holding = waiter.holding
          if (!holding) {
            if (retrying eq waiter) retrying = null
            waiters.dequeueFirst(_ eq waiter)
            if (queued == 0L) hasWaiters = false
          }
        }
      }
      if (first) {
        // A removed waiter may have been blocking others, so wake again.
        if (holding) release(waiter.n) else if (hasWaiters) wake()
      }
    }

    /**
     * Suspends until the waiter holds its permits. A fair waiter is woken only
     * once it does; an unfair one is woken to retry, and a lost retry re-queues
     * at the front and sleeps again.
     */
    private[this] def await(waiter: Waiter)(implicit trace: Trace): UIO[Unit] =
      if (fair) suspend(waiter)
      else
        suspend(waiter).flatMap { _ =>
          var cancelled = false
          val acquired = synchronized {
            if (retrying eq waiter) retrying = null
            if (waiter.cancelled) {
              // Its owner has already cleaned up, so nothing would return
              // permits taken now.
              cancelled = true
              false
            } else {
              waiter.woken = false
              waiter.callback = null
              // Publish the flag before re-reading the permits (see `hasWaiters`).
              hasWaiters = true
              if (takePermits(waiter.n)) {
                waiter.holding = true
                if (queued == 0L) hasWaiters = false
                true
              } else {
                waiter +=: waiters
                if (waiter.n < smallestQueued) smallestQueued = waiter.n
                false
              }
            }
          }
          // Releases that arrived while this waiter was retrying woke nobody,
          // so pass the wakeup on: permits may be left for another waiter.
          if (hasWaiters) wakeOne()
          if (acquired) ZIO.unit
          else if (cancelled) ZIO.never // as a cancelled fair waiter, which is never woken
          else await(waiter)
        }

    /**
     * Suspends until the semaphore wakes the waiter. A wakeup that races with
     * interruption is dropped, so the owner must call `cancelOrRelease`
     * afterwards.
     */
    private[this] def suspend(waiter: Waiter)(implicit trace: Trace): UIO[Unit] =
      ZIO.asyncMaybe[Any, Nothing, Unit] { callback =>
        val done = synchronized {
          if (waiter.woken) true
          else {
            waiter.callback = callback
            false
          }
        }
        // No interrupt handler: `cancelOrRelease` is the cleanup.
        if (done) Some(Exit.unit) else None
      }

    private[this] def wake(): Unit =
      if (fair) drain() else wakeOne()

    /**
     * Fair: grants as many queued fibers as the free permits satisfy, in order.
     */
    @tailrec
    private[this] def drain(): Unit = {
      var granted = false
      // The callback is read under the lock: a waiter that registers after this
      // sees `woken` and resumes itself instead.
      val callback = synchronized {
        if (queued > 0L && takePermits(waiters.head.n)) {
          val head = waiters.dequeue()
          head.holding = true
          head.woken = true
          granted = true
          if (queued == 0L) hasWaiters = false
          head.callback
        } else null
      }
      if (granted) {
        if (callback ne null) callback(Exit.unit)
        drain()
      }
    }

    /**
     * Unfair: wakes the first queued waiter that the free permits can satisfy,
     * unless a waiter is already retrying; that waiter calls this again once
     * its retry resolves, so the queue keeps draining while permits are free.
     */
    private[this] def wakeOne(): Unit = {
      val callback = synchronized {
        val free = freeGet()
        if ((retrying ne null) || free < smallestQueued || queued == 0L) null
        else
          waiters.dequeueFirst(_.n <= free) match {
            case Some(found) =>
              retrying = found
              found.woken = true
              if (queued == 0L) hasWaiters = false
              found.callback
            case None =>
              var smallest = Long.MaxValue
              waiters.foreach(waiter => if (waiter.n < smallest) smallest = waiter.n)
              smallestQueued = smallest
              null
          }
      }
      if (callback ne null) callback(Exit.unit)
    }
  }
}
