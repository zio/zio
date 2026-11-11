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

/**
 * An asynchronous semaphore, which is a generalization of a mutex. Semaphores
 * have a certain number of permits, which can be held and released concurrently
 * by different parties. Attempts to acquire more permits than available result
 * in the acquiring fiber being suspended until the specified number of permits
 * become available.
 *
 * If you need functionality that `Semaphore` doesnt' provide, use a
 * [[TSemaphore]] and define it in a [[zio.stm.ZSTM]] transaction.
 *
 * Only a semaphore with no queued fibers can be Java-serialized; serializing
 * one that has waiters throws a `NotSerializableException`.
 */
sealed trait Semaphore extends Serializable {

  /**
   * Returns the number of available permits. For a semaphore created with
   * [[Semaphore.make]], this is 0 while any fiber is queued for permits. For
   * one created with [[Semaphore.makeUnfair]], it is the number of free
   * permits, even when fibers are queued.
   */
  def available(implicit trace: Trace): UIO[Long]

  /**
   * Returns the number of fibers currently waiting for permits. For a semaphore
   * created with [[Semaphore.makeUnfair]], this includes the one waiter that
   * has been woken and is retrying for its permits, since it is still waiting.
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
   * `acquire` action and releasing it as the `release` action. If the scope is
   * closed while the workflow is still waiting for the permit, the workflow is
   * interrupted.
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
   * permits and releasing them when the scope is closed. If the scope is closed
   * while the workflow is still waiting for the permits, the workflow is
   * interrupted.
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
   *
   * Queued fibers are woken one at a time: until a woken fiber has run and
   * retried for its permits, no other queued fiber is woken, even if permits
   * are free. A woken fiber that is slow to be scheduled therefore delays the
   * rest of the queue, and permits released together reach queued fibers one
   * after another rather than all at once.
   *
   * Finding the fiber to wake means walking the queue from the front past every
   * request that asks for more permits than are free. When requests vary in
   * size, large ones tend to collect at the front, because smaller ones keep
   * getting past them, so each wakeup costs time proportional to the number of
   * large requests waiting. [[make]] only ever looks at the front of the queue.
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
   * either because it was granted its permits (fair), because it should retry
   * for them (unfair), or because its scope closed first; `holding` is set once
   * the waiter owns its permits; `cancelled` is set by the first `abandon`,
   * which makes later ones no-ops and stops the waiter from taking permits.
   * `prev` and `next` link the waiter into the semaphore's queue, and are both
   * null while it is not in it.
   */
  private final class Waiter(val n: Long) {
    var callback: ZIO[Any, Nothing, Unit] => Unit = null
    var woken: Boolean                            = false
    var holding: Boolean                          = false
    var cancelled: Boolean                        = false
    var prev: Waiter                              = null
    var next: Waiter                              = null
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
     * Replaces the `toString` that `Compact` inherits from `AtomicLong`, which
     * prints the free-permit count as if the semaphore were a number.
     */
    final override def toString: String =
      s"Semaphore@${Integer.toHexString(java.lang.System.identityHashCode(this))}"

    /**
     * True whenever the queue may be non-empty. Written under the lock, read
     * without it. A waiter publishes `true` before re-reading the permits, and
     * a releaser adds permits before reading this flag, so one of the two
     * always observes the other and no wakeup is lost.
     */
    @volatile private[this] var hasWaiters: Boolean = false

    /**
     * Guarded by `this`. The first queued waiter, or null if none is queued.
     * The queue is a circular doubly linked list threaded through the waiters,
     * so `head.prev` is the last one. Linking the waiters directly makes
     * removing one from anywhere in the queue O(1) and leaves nothing behind
     * once they are gone, and one field keeps the semaphore no larger than a
     * reference to a separate queue would.
     */
    private[this] var head: Waiter = null

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

    /**
     * The number of queued waiters, counted by walking the queue: only
     * `awaiting` needs it, and a counter would make every semaphore larger. The
     * caller holds the lock.
     */
    private[this] def queued: Long =
      if (head eq null) 0L
      else {
        var count  = 1L
        var waiter = head.next
        while (waiter ne head) {
          count += 1L
          waiter = waiter.next
        }
        count
      }

    /**
     * Adds an unlinked waiter at the back of the queue. The caller holds the
     * lock.
     */
    private[this] def enqueue(waiter: Waiter): Unit =
      if (head eq null) {
        waiter.prev = waiter
        waiter.next = waiter
        head = waiter
      } else {
        val last = head.prev
        waiter.prev = last
        waiter.next = head
        last.next = waiter
        head.prev = waiter
      }

    /**
     * Adds an unlinked waiter at the front of the queue. The caller holds the
     * lock.
     */
    private[this] def enqueueFirst(waiter: Waiter): Unit = {
      enqueue(waiter)
      head = waiter
    }

    /**
     * Removes the waiter from the queue if it is in it, and does nothing
     * otherwise. Clears `hasWaiters` when that empties the queue, so every
     * removal keeps the flag in step with the queue. The caller holds the lock.
     */
    private[this] def unlink(waiter: Waiter): Unit =
      if (waiter.next ne null) {
        if (waiter.next eq waiter) {
          head = null
          hasWaiters = false
        } else {
          waiter.prev.next = waiter.next
          waiter.next.prev = waiter.prev
          if (head eq waiter) head = waiter.next
        }
        waiter.prev = null
        waiter.next = null
      }

    // `hasWaiters` is also true for an instant while a fiber decides whether to
    // queue, so where it would change the answer the queue is consulted under
    // the lock instead.
    final def available(implicit trace: Trace): UIO[Long] =
      ZIO.succeed {
        if (fair && hasWaiters) synchronized(if (head eq null) freeGet() else 0L)
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
            cause => { undoReserve(waiter, n); Exit.failCause(cause) },
            a => { release(n); Exit.succeed(a) }
          )
        }

    final def withPermitsScoped(n: Long)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
      if (n < 0L) negative(n)
      else if (n == 0L) ZIO.unit
      else
        ZIO.uninterruptibleMask { restore =>
          ZIO
            .acquireRelease(ZIO.succeed(reserve(n))) { waiter =>
              // The scope can close while its fiber still waits, so the
              // finalizer wakes that fiber, which then ends interrupted.
              ZIO.succeed(if (waiter eq null) release(n) else abandon(waiter, wakeOwner = true))
            }
            .flatMap { waiter =>
              // The scope may outlive the wait, so a fiber that stops waiting
              // cleans up now rather than leaving its waiter for the finalizer.
              // Only the wait is interruptible, so there is no point at which
              // a waiter exists without this cleanup in place.
              if (waiter eq null) ZIO.unit
              else restore(await(waiter)).onInterrupt(ZIO.succeed(abandon(waiter, wakeOwner = false)))
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
            if (fair && hasWaiters) synchronized((head eq null) && takePermits(n))
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
          if ((!fair || (head eq null)) && takePermits(n)) {
            if (head eq null) hasWaiters = false
            null
          } else {
            val waiter = new Waiter(n)
            enqueue(waiter)
            if (!fair && n < smallestQueued) smallestQueued = n
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
    private[this] def undoReserve(waiter: Waiter, n: Long)(implicit trace: Trace): Unit =
      if (waiter eq null) release(n) else abandon(waiter, wakeOwner = false)

    /**
     * Cleans up after a waiter whose owner is done with it: a waiter still
     * queued is removed, one that holds permits returns them.
     *
     * `wakeOwner` is for a caller other than the owner, a scope closing while
     * its fiber waits: unless the waiter holds its permits, the owner is woken
     * and ends interrupted (see `suspend`) instead of waiting forever for a
     * waiter nobody will grant.
     */
    private[this] def abandon(waiter: Waiter, wakeOwner: Boolean)(implicit trace: Trace): Unit = {
      var first                                     = false
      var holding                                   = false
      var wasRetrying                               = false
      var callback: ZIO[Any, Nothing, Unit] => Unit = null
      synchronized {
        if (!waiter.cancelled) {
          waiter.cancelled = true
          first = true
          holding = waiter.holding
          if (!holding) {
            wasRetrying = retrying eq waiter
            if (wasRetrying) retrying = null
            unlink(waiter)
            // A woken owner is already resuming and sees `cancelled` itself.
            if (wakeOwner && !waiter.woken) {
              waiter.woken = true
              callback = waiter.callback
            }
          }
        }
      }
      if (callback ne null) callback(ZIO.interrupt)
      if (first) {
        // A fair waiter may have been blocking the ones behind it, so wake
        // again. An unfair queued waiter blocks nobody, since a wakeup considers
        // every waiter, so only a retrying one, which held the wakeup gate,
        // needs its wakeup passed on.
        if (holding) release(waiter.n)
        else if (hasWaiters && (fair || wasRetrying)) wake()
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
              // Its scope has closed, so nothing would return permits taken
              // now.
              cancelled = true
              false
            } else {
              waiter.woken = false
              waiter.callback = null
              // Publish the flag before re-reading the permits (see `hasWaiters`).
              hasWaiters = true
              if (takePermits(waiter.n)) {
                waiter.holding = true
                if (head eq null) hasWaiters = false
                true
              } else {
                enqueueFirst(waiter)
                if (waiter.n < smallestQueued) smallestQueued = waiter.n
                false
              }
            }
          }
          // Releases that arrived while this waiter was retrying woke nobody,
          // so pass the wakeup on: permits may be left for another waiter.
          if (hasWaiters) wakeOne()
          if (acquired) ZIO.unit
          else if (cancelled) ZIO.interrupt
          else await(waiter)
        }

    /**
     * Suspends until the semaphore wakes the waiter, and ends interrupted if
     * its scope closed before it was granted its permits. A wakeup that races
     * with interruption is dropped, so the owner must call `abandon`
     * afterwards.
     */
    private[this] def suspend(waiter: Waiter)(implicit trace: Trace): UIO[Unit] =
      ZIO.asyncMaybe[Any, Nothing, Unit] { callback =>
        var closed = false
        val done = synchronized {
          if (waiter.woken) {
            closed = waiter.cancelled && !waiter.holding
            true
          } else {
            waiter.callback = callback
            false
          }
        }
        // No interrupt handler: `abandon` is the cleanup.
        if (!done) None
        else if (closed) Some(ZIO.interrupt)
        else Some(Exit.unit)
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
        val first = head
        if ((first ne null) && takePermits(first.n)) {
          unlink(first)
          first.holding = true
          first.woken = true
          granted = true
          first.callback
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
     *
     * The scan is linear in the number of waiters ahead of the first that fits,
     * and runs under the lock. With mixed request sizes the waiters that do not
     * fit are mostly large requests that smaller ones have passed, so they
     * collect at the front and every wakeup walks past them. Indexing waiters
     * by request size would remove the walk.
     */
    private[this] def wakeOne(): Unit = {
      val callback = synchronized {
        val free = freeGet()
        if ((retrying ne null) || free < smallestQueued || (head eq null)) null
        else {
          // One pass: the first waiter that fits, or else the exact smallest
          // request, which is only needed when nobody fits.
          var found: Waiter = null
          var smallest      = Long.MaxValue
          var waiter        = head
          var more          = true
          while (more) {
            if (waiter.n <= free) {
              found = waiter
              more = false
            } else {
              if (waiter.n < smallest) smallest = waiter.n
              waiter = waiter.next
              more = waiter ne head
            }
          }
          if (found eq null) {
            smallestQueued = smallest
            null
          } else {
            unlink(found)
            retrying = found
            found.woken = true
            found.callback
          }
        }
      }
      if (callback ne null) callback(Exit.unit)
    }
  }
}
