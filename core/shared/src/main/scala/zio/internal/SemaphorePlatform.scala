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

package zio.internal

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import scala.annotation.tailrec

/**
 * Implementation of [[zio.Semaphore]] built on an `AtomicLong` permit counter
 * plus a queue of waiters, rather than a `Ref` holding a boxed `Either[Queue,
 * Long]`.
 *
 * The design goal is that the uncontended path (acquiring a permit that is
 * immediately available) allocates nothing at all. In that case the whole
 * acquisition is a single successful CAS on the counter, and release is a
 * `getAndAdd` plus a check for waiters. Only a fiber that actually has to wait
 * allocates a [[SemaphoreWaiter]].
 *
 * Two fairness policies are supported:
 *
 *   - `fair`: a fiber may take permits from the counter only when no other
 *     fiber is already queued ahead of it, giving FIFO ordering and preventing
 *     barging. This matches the semantics of the original `Semaphore`.
 *   - unfair: a fiber may always take an available permit, even when others are
 *     queued. This allows barging, at the cost of ordering guarantees, matching
 *     `java.util.concurrent.Semaphore(n, false)`. Measurements show it on par
 *     with the fair policy uncontended and when permits are scarce, and ahead
 *     of it only when several fibers contend for several permits: under
 *     `withPermit` the cost of contention is dominated by fiber suspension
 *     rather than by queueing, so barging has much less to win than it does for
 *     a semaphore that parks threads.
 */
private[zio] final class SemaphorePlatform(initialPermits: Long, fair: Boolean) extends Serializable {
  import SemaphorePlatform._

  private[this] val permits = new AtomicLong(initialPermits)
  private[this] val waiters = new ConcurrentLinkedQueue[SemaphoreWaiter]

  /**
   * Guards the handing out of permits to queued waiters. Acquiring and
   * releasing permits never touches this lock, since both are a single CAS on
   * `permits`, so the uncontended path stays entirely lock-free. It is taken
   * only when there are waiters to serve, and never held across user code.
   */
  private[this] val drainLock = new ReentrantLock()

  /**
   * Counts requests to hand out permits that have not yet been served. A thread
   * that cannot take [[drainLock]] records its request here rather than
   * leaving, so its permits cannot be stranded by a lock holder that has
   * already taken its last look at the counter.
   */
  private[this] val drainRequests = new AtomicLong(0L)

  /**
   * Serializes [[enqueue]] against itself, so that fibers are queued in the
   * order they arrive. Held only across the counter bump and the insertion,
   * never across a drain or any user code, and never taken by the uncontended
   * acquire/release path.
   */
  private[this] val enqueueLock = new ReentrantLock()

  /**
   * The number of waiters that are still live: enqueued, and neither granted
   * nor cancelled. Incremented in [[enqueue]] and decremented by whichever side
   * wins a waiter's terminal CAS, so it is exactly the number of fibers still
   * waiting for permits. This keeps [[awaiting]] O(1).
   */
  private[this] val liveCount = new AtomicLong(0L)

  /**
   * The number of nodes physically present in [[waiters]], tombstones included.
   * `ConcurrentLinkedQueue.size` is itself O(n), so we track this ourselves in
   * order to learn in O(1) how many tombstones are outstanding, which is
   * `queuedCount - liveCount`.
   */
  private[this] val queuedCount = new AtomicLong(0L)

  /**
   * The number of permits that can actually be acquired right now.
   *
   * In fair mode a queued waiter blocks every other fiber from taking permits,
   * so while any fiber is waiting this reports `0` even though the counter may
   * be non-zero: those permits are spoken for by the queued waiters and no
   * other fiber may take them. This preserves the semantics of the original
   * `Ref`-based `Semaphore`, which reported `0` whenever fibers were queued.
   *
   * In unfair mode barging is allowed, so a queued waiter does not prevent
   * anybody from acquiring, and the raw count is reported as-is.
   */
  def available(): Long =
    if (fair && hasLiveWaiter) 0L else permits.get()

  /**
   * The number of fibers currently waiting for permits.
   *
   * This is O(1): cancelled waiters can linger in the queue as tombstones, so
   * rather than walking the queue we keep a live count that both of a waiter's
   * terminal transitions maintain.
   */
  def awaiting(): Long = liveCount.get()

  /**
   * The number of nodes the waiter queue is physically holding on to, live
   * waiters and not-yet-reaped tombstones alike. Exposed for tests, which use
   * it to check that tombstones do not accumulate without bound.
   */
  private[zio] def queueSize(): Long = queuedCount.get()

  /**
   * Attempts to take `n` permits without queueing. Returns `true` if the
   * permits were taken.
   *
   * In fair mode this refuses to take permits while any fiber is queued, so
   * that a fiber cannot barge ahead of one that is already waiting.
   */
  @tailrec
  def tryAcquire(n: Long): Boolean =
    if (fair && hasLiveWaiter) false
    else {
      val current = permits.get()
      if (current < n) false
      else if (permits.compareAndSet(current, current - n)) true
      else tryAcquire(n)
    }

  /**
   * Whether any fiber is waiting for permits.
   *
   * This counts rather than inspecting the queue. Cancelled waiters are left in
   * the queue as tombstones, so "the head is dead" and "nobody is waiting" are
   * not the same question: a dead head can have live waiters behind it. Every
   * tombstone is in practice reaped at the head by the `drain` that [[cancel]]
   * runs, so peeking would usually agree, but it agrees by relying on that
   * timing rather than on the thing being asked.
   *
   * The original `Ref`-based `Semaphore` held its state in a single
   * `Either[Queue, Long]` and dropped a cancelled waiter from the queue
   * outright, so any queued waiter at all put the state into `Left`: no other
   * fiber could take permits, and `available` read as `0`. `liveCount` counts
   * exactly the waiters that implementation would have kept queued, so testing
   * it against zero reproduces those semantics directly, and in O(1).
   */
  private def hasLiveWaiter: Boolean = liveCount.get() > 0L

  /**
   * Enqueues a waiter for `n` permits, which `Semaphore.enqueueAndAwait` then
   * awaits. A waiter that is enqueued and never awaited strands its permits.
   *
   * After enqueueing we re-run the release loop, because permits may have been
   * returned between our failed [[tryAcquire]] and the enqueue, in which case
   * nobody else would wake us.
   */
  def enqueue(n: Long): SemaphoreWaiter = {
    val waiter = new SemaphoreWaiter(n, liveCount)
    // The counter bump and the insertion are done together under `enqueueLock`,
    // so that two fibers arriving in order are queued in that order. They are
    // not one atomic step by themselves: `liveCount` has to go up before the
    // node is published (so that no observer can see a queued waiter that is
    // not yet counted, which is what fair mode's gating and `available` rely
    // on), which leaves a window in which a fiber that has been counted has not
    // yet been inserted. A second fiber that then runs the whole of `enqueue`
    // inside that window lands in the queue ahead of the first, inverting FIFO
    // order. The lock is uncontended except between simultaneous enqueues, and
    // is never taken on the fast path, which does not enqueue at all.
    enqueueLock.lock()
    try {
      liveCount.incrementAndGet()
      queuedCount.incrementAndGet()
      waiters.add(waiter)
    } finally enqueueLock.unlock()
    drain()
    waiter
  }

  /**
   * Returns `n` permits and hands as many as possible to queued waiters.
   */
  def release(n: Long): Unit = {
    permits.getAndAdd(n)
    drain()
    ()
  }

  /**
   * Cancels a waiter that is no longer interested in its permits, as happens
   * when the acquiring fiber is interrupted. If the waiter had already been
   * granted its permits, they are returned to the semaphore.
   */
  def cancel(waiter: SemaphoreWaiter): Unit =
    if (waiter.cancel()) {
      // We cancelled before any permits were granted to us. We deliberately do
      // not remove ourselves from the queue here: `remove(Object)` scans the
      // whole queue, and the cancelled waiter is already inert because
      // `drainLoop` discards any waiter that is done. It is dropped in O(1) the
      // next time it reaches the head.
      //
      // Draining is still necessary: in fair mode nobody may take permits while
      // a waiter is queued, so if we were at the head there could be permits
      // sitting free with no one to hand them out.
      drain()
    } else {
      // We had already been granted the permits, so they are ours to return.
      release(waiter.n)
    }

  /**
   * Hands out permits, ensuring that the work happens even when several threads
   * ask at once.
   *
   * Only one thread hands out permits at a time: the work is a handful of CAS
   * operations on the head of the queue, the counter, and the waiter state
   * together, and a thread that cannot get in has already done its part by
   * returning its permits to the counter.
   *
   * When the queue is empty there is nothing to hand out at all and the whole
   * dance is skipped, so an uncontended release is a `getAndAdd` plus a `peek`.
   *
   * A thread that fails to take the lock must not simply leave, or its permits
   * could be stranded: the holder may have already made its last read of the
   * counter. Instead it bumps `drainRequests`, and the holder re-runs until it
   * sees no new requests, so the permits are always eventually handed out.
   *
   * Neither path holds the lock across the wake-ups; see [[tryDrainAndWake]].
   */
  private def drain(): Unit = {
    // Nothing is queued, so there is nobody to hand permits to and no
    // tombstones to sweep. This has to come before the increment below: an
    // early-out taken after it would leave `drainRequests` permanently
    // non-zero and send the next real drain round its loop for nothing.
    //
    // A waiter enqueued just after this read cannot be stranded, because
    // `enqueue` drains itself after publishing the node, and `release` adds its
    // permits to the counter before calling us, so that drain sees them.
    if (waiters.peek() eq null) return

    // Fast path: nobody else is draining, so there is nobody to signal. Taking
    // the lock first makes the whole request-counter protocol unnecessary in
    // the common case, where drains do not overlap.
    //
    // This is not limited to a single waiter. `drainLoop` re-reads the head and
    // the permit counter on every iteration and only ever grants by CAS, so a
    // thread holding the lock correctly serves as many waiters as the permits
    // allow, picking up concurrent releases as it goes. What the counter
    // protects against is a *second drainer* being turned away, not a backlog
    // of waiters.
    if (tryDrainAndWake(clearRequests = false)) {
      // Somebody may have been turned away while we held the lock, after we had
      // already made our last read of the counter. Their request is recorded,
      // so serve it.
      if (drainRequests.get() != 0L) drainContended()
    } else drainContended()
  }

  /**
   * The slow half of [[drain]], taken once a `tryLock` has failed or a request
   * arrived too late for the drain that was in flight.
   *
   * The increment must happen before the `tryLock` attempt. A thread that
   * incremented and then failed to take the lock is guaranteed to be served:
   * either the holder has yet to run `drainLoop`, in which case it will see the
   * permits, or it has already cleared the counter, in which case our increment
   * lands after that clear and its exit re-check sees it.
   */
  private def drainContended(): Unit = {
    var continue = true
    while (continue) {
      // Signal that there is work to do. A thread that cannot take the lock has
      // still recorded its request here, and the lock holder will see it.
      drainRequests.incrementAndGet()

      if (tryDrainAndWake(clearRequests = true)) {
        // Re-check after releasing: if somebody asked while we were inside,
        // their permits still need handing out.
        continue = drainRequests.get() != 0L
      } else {
        // Somebody else holds the lock. Our request is recorded, so they (or
        // whoever takes the lock next) will do our work for us.
        continue = false
      }
    }
  }

  /**
   * Takes [[drainLock]], hands out permits, releases the lock, and only then
   * wakes whoever was granted. Returns `false` if the lock was not free.
   *
   * This is the only place the lock is taken, so the deferred wake is a
   * property of the drain rather than something each caller has to remember:
   * waking runs `FiberRuntime.tell`, which offers to the scheduler's run queue
   * and may `LockSupport.unpark` a parked worker, so waking under the lock
   * would hold it across a syscall per granted waiter.
   *
   * `clearRequests` is set by the contended path, which must take ownership of
   * the outstanding requests inside the lock: any request arriving after that
   * bumps the counter again and sends it round its loop once more.
   */
  private def tryDrainAndWake(clearRequests: Boolean): Boolean =
    if (drainLock.tryLock()) {
      val wakes =
        try {
          if (clearRequests) drainRequests.set(0L)
          drainLoop()
        } finally drainLock.unlock()
      wake(wakes)
      true
    } else false

  /**
   * Hands permits to queued waiters for as long as the waiter at the head of
   * the queue can be satisfied. Must be called while holding [[drainLock]].
   *
   * Only the head is ever considered, which preserves FIFO order and prevents a
   * waiter requesting many permits from being starved by later waiters
   * requesting few.
   *
   * Returns the wake-ups the caller must perform once it has released the lock:
   * `null` if there are none, a single callback if exactly one waiter was
   * granted, and otherwise a `WakeList` of them in the order they were granted.
   * The permits themselves are handed out here, under the lock, so who gets
   * what is fully decided before any of these run; all that is deferred is
   * telling the fibers about it.
   */
  private def drainLoop(): AnyRef = {
    // The overwhelmingly common case is granting zero or one waiter, so the
    // first callback is kept in a local and no list is allocated unless a
    // single drain actually grants more than one.
    var firstWake: AnyRef   = null
    var moreWakes: WakeList = null

    var continue = true
    while (continue) {
      val head = waiters.peek()
      if (head eq null) continue = false
      else if (head.isDone) {
        // Cancelled, or already granted. Drop it and carry on.
        if (waiters.poll() ne null) queuedCount.decrementAndGet()
      } else {
        val n       = head.n
        val current = permits.get()
        if (current < n) continue = false
        else if (permits.compareAndSet(current, current - n)) {
          // We hold the drain lock, so `head` is still the head and this poll
          // returns it. `poll` rather than `remove(Object)` keeps this O(1):
          // `remove` scans the queue, making a drain of k waiters O(k^2).
          if (waiters.poll() ne null) queuedCount.decrementAndGet()
          // A concurrent cancellation may still beat us to the waiter, in which
          // case the permits are ours to return.
          head.claim() match {
            case SemaphorePlatform.Claim.Cancelled => permits.getAndAdd(n)
            case SemaphorePlatform.Claim.NoWaiter  => ()
            case cb =>
              if (firstWake eq null) firstWake = cb
              else {
                if (moreWakes eq null) {
                  moreWakes = new WakeList
                  moreWakes += firstWake
                }
                moreWakes += cb
              }
          }
        }
        // else: a concurrent release or fast-path acquire changed the counter
      }
    }

    sweepIfNeeded()

    if (moreWakes eq null) firstWake else moreWakes
  }

  /**
   * Runs the wake-ups returned by [[drainLoop]]. Must be called only after
   * [[drainLock]] has been released: each of these hands a fiber back to the
   * runtime, which offers to the scheduler's run queue and may `unpark` a
   * worker thread.
   */
  private def wake(wakes: AnyRef): Unit =
    wakes match {
      case null => ()
      case list: WakeList =>
        list.runAllButLast()
        wakeOne(list.last)
      case cb => wakeOne(cb)
    }

  /**
   * Wakes one granted waiter by completing the callback it registered, which
   * hands its fiber to the scheduler.
   */
  private def wakeOne(cb: AnyRef): Unit =
    cb.asInstanceOf[Exit[Nothing, Unit] => Unit](Exit.unit)

  /**
   * Drops tombstones from the middle of the queue when too many have piled up.
   * Must be called while holding [[drainLock]].
   *
   * Cancelled waiters are normally reaped in O(1) as they reach the head, which
   * costs nothing and is all that is needed while the queue is draining. But
   * the head only advances when it can be satisfied: a head waiter asking for
   * more permits than will be free for a while pins the queue, and every
   * cancellation behind it is then retained indefinitely. Without this sweep a
   * workload that interrupts heavily behind a stuck head waiter grows the queue
   * without bound.
   *
   * The sweep is a single O(k) pass with `Iterator.remove`, rather than the
   * O(k^2) that removing tombstones one at a time would cost, and it runs only
   * once tombstones both outnumber the live waiters and exceed
   * [[SweepThreshold]]. That leaves the common case untouched: an isolated
   * cancellation never scans, and the amortized cost per tombstone is O(1).
   */
  private def sweepIfNeeded(): Unit = {
    val queued = queuedCount.get()
    val dead   = queued - liveCount.get()
    if (dead >= SweepThreshold && dead * 2 >= queued) {
      var removed = 0L
      val it      = waiters.iterator()
      while (it.hasNext) {
        if (it.next().isDone) {
          it.remove()
          removed += 1L
        }
      }
      if (removed != 0L) queuedCount.addAndGet(-removed)
    }
  }
}

private[zio] object SemaphorePlatform {

  /**
   * The number of unreaped tombstones that must accumulate before a drain will
   * sweep them out of the middle of the queue. Small enough that memory stays
   * bounded in absolute terms, large enough that the O(k) sweep is amortized
   * over many cancellations.
   */
  private final val SweepThreshold = 32L

  /**
   * A fiber waiting for permits.
   *
   * The state machine is `Pending -> (Granted | Cancelled)`, with both terminal
   * states reached by a single CAS, so that a grant racing a cancellation has
   * exactly one winner and the permits are never lost or double-counted. The
   * winner of that CAS is also the one that decrements `liveCount`, so the
   * semaphore's count of waiting fibers drops exactly once per waiter.
   */
  final class SemaphoreWaiter(val n: Long, liveCount: AtomicLong) extends AtomicReference[AnyRef](Pending) {

    def isDone: Boolean = {
      val s = get()
      (s eq Granted) || (s eq Cancelled)
    }

    /**
     * Registers the continuation to run when permits are granted.
     *
     * Returns `false` if the callback was not installed, in which case the
     * permits are already this waiter's and the caller must proceed immediately
     * rather than suspend.
     *
     * A single CAS decides it. `Pending` is the only state a callback can be
     * installed from, and every transition out of it is terminal, so a lost CAS
     * is final and there is nothing to retry: whoever moved the waiter out of
     * `Pending` did so to grant it. Publishing the callback and observing a
     * grant therefore go through the same CAS, giving a registration racing a
     * grant exactly one winner and invoking the callback exactly once, by
     * whichever side loses the race.
     */
    def register(cb: Exit[Nothing, Unit] => Unit): Boolean =
      compareAndSet(Pending, cb)

    /**
     * Takes this waiter for a grant, without waking it.
     *
     * Returns [[Claim.Cancelled]] if the waiter had already been cancelled, in
     * which case the caller must return the permits to the semaphore;
     * [[Claim.NoWaiter]] if it was taken but there is nobody to wake (already
     * granted, or granted before it suspended); and otherwise the callback the
     * caller must invoke to wake the fiber.
     *
     * The wake is kept separate so that a drain can do all of its state
     * transitions under the drain lock and wake the fibers after releasing it.
     * Waking runs `FiberRuntime.tell`, which offers to the scheduler's run
     * queue and can `LockSupport.unpark` a parked worker thread, so waking
     * inline would hold the drain lock across a syscall per granted waiter.
     */
    @tailrec
    def claim(): AnyRef =
      get() match {
        case Cancelled => Claim.Cancelled
        case Granted   => Claim.NoWaiter
        case state =>
          if (compareAndSet(state, Granted)) {
            liveCount.decrementAndGet()
            if (state eq Pending) Claim.NoWaiter else state
          } else claim()
      }

    @tailrec
    def cancel(): Boolean =
      get() match {
        case Granted   => false
        case Cancelled => true
        case state =>
          if (compareAndSet(state, Cancelled)) {
            liveCount.decrementAndGet()
            true
          } else cancel()
      }
  }

  private val Pending: AnyRef   = new Object
  private val Granted: AnyRef   = new Object
  private val Cancelled: AnyRef = new Object

  /**
   * Sentinels distinguishing the outcomes of [[SemaphoreWaiter.claim]] from the
   * callback it returns when there is a fiber to wake.
   */
  private[internal] object Claim {
    val Cancelled: AnyRef = new Object
    val NoWaiter: AnyRef  = new Object
  }

  /**
   * The pending wake-ups of a drain that granted more than one waiter, in the
   * order they were granted.
   */
  private[internal] final class WakeList {
    private[this] val elems = new scala.collection.mutable.ArrayBuffer[AnyRef](8)

    def +=(cb: AnyRef): Unit = {
      elems += cb
      ()
    }

    /** Wakes everything except the last, which the caller handles. */
    def runAllButLast(): Unit = {
      val n = elems.size
      var i = 0
      while (i < n - 1) {
        elems(i).asInstanceOf[Exit[Nothing, Unit] => Unit](Exit.unit)
        i += 1
      }
    }

    def last: AnyRef = elems(elems.size - 1)
  }
}
