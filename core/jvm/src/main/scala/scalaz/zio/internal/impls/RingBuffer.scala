package scalaz.zio.internal.impls

import java.util.concurrent.atomic.AtomicLongArray
import scalaz.zio.internal.impls.padding.MutableQueueFieldsPadding
import scalaz.zio.internal.impls.padding.MutableQueueFieldsPadding.{ headUpdater, tailUpdater }

object RingBuffer {

  /**
   * @note mimimum supported capacity is 2
   */
  def apply[A](requestedCapacity: Int): RingBuffer[A] = {
    val effectiveCapacity = Math.max(requestedCapacity, 2)

    if (nextPow2(effectiveCapacity) == effectiveCapacity)
      RingBufferPow2(effectiveCapacity)
    else
      RingBufferArb(effectiveCapacity)
  }

  /*
   * Used only once during queue creation. Doesn't need to be
   * performant or anything.
   */
  def nextPow2(n: Int): Int = {
    val nextPow = (Math.log(n.toDouble) / Math.log(2.0)).ceil.toInt
    Math.pow(2.0, nextPow.toDouble).toInt.max(2)
  }
}

/**
 * A lock-free array based bounded queue. It is thread-safe and can be
 * used in multiple-producer/multiple-consumer (MPMC) setting.
 *
 * =Main concepts=
 *
 * A simple array based queue of size N uses an array `buf` of size N
 * as an underlying storage. There are 2 pointers `head` and
 * `tail`. The element is enqueued into `buf` at position `tail % N`
 * and dequeued from `head % N`. Each time an enqueue happens `tail`
 * is incremented, similarly when dequeue happens `head` is
 * incremented.
 *
 * Since pointers wrap around the array as they get incremented such
 * data structure is also called a
 * [[https://en.wikipedia.org/wiki/Circular_buffer circular buffer]]
 * or a ring buffer.
 *
 * Because queue is bounded, enqueue and dequeue may fail, which is
 * captured in the semantics of `offer` and `poll` methods.
 *
 * Using `offer` as an example, the algorithm can be broken down
 * roughly into three steps:
 *  1. Find a place to insert an element.
 *  2. Reserve this place, put an element and make it visible to
 *     other threads (store and publish).
 *  3. If there was no place on step 1 return false, otherwise
 *     returns true.
 *
 * Steps 1 and 2 are usually done in a loop to accommodate the
 * possibility of failure due to race. Depending on the
 * implementation of these steps the resulting queue will have
 * different characteristics. For instance, the more sub-steps are
 * between reserve and publish in step 2, the higher is the chance
 * that one thread will delay other threads due to being descheduled.
 *
 * =Notes on the design=
 *
 * The queue uses a `buf` array to store elements. It uses `seq`
 * array to store longs which serve as:
 * 1. an indicator to producer/consumer threads whether the slot is
 *    right for enqueue/dequeue,
 * 2. an indicator whether the queue is empty/full,
 * 3. a mechanism to ''publish'' changes to `buf` via volatile write
 *    (can even be relaxed to ordered store).
 * See comments in `offer`/`poll` methods for more details on `seq`.
 *
 * The benefit of using `seq` + `head`/`tail` counters is that there
 * are no allocations during enqueue/dequeue and very little
 * overhead. The downside is it doubles (on 64bit) or triples
 * (compressed OOPs) the amount of memory needed for queue.
 *
 * Concurrent enqueues and concurrent dequeues are possible. However
 * there is no ''helping'', so threads can delay other threads, and
 * thus the queue doesn't provide full set of lock-free
 * guarantees. In practice it's usually not a problem, since benefits
 * are simplicity, zero GC pressure and speed.
 *
 * There are 2 implementations of a RingBuffer:
 * 1. `RingBufferArb` that supports queues with arbitrary capacity;
 * 2. `RingBufferPow2` that supports queues with only power of 2
 *     capacities.
 *
 * The reason is `head % N` and `tail % N` are rather cheap when can
 * be done as a simple mask (N is pow 2), and pretty expensive when
 * involve an `idiv` instruction. The difference is especially
 * pronounced in tight loops (see. RoundtripBenchmark).
 *
 * To ensure good performance reads/writes to `head` and `tail`
 * fields need to be independant, e.g. they shouldn't fall on the
 * same (adjacent) cache-line.
 *
 * We can make those counters regular volatile long fields and space
 * them out, but we still need a way to do CAS on them. The only way
 * to do this except `Unsafe` is to use `AtomicLongFieldUpdater`,
 * which is exactly what we have here.
 *
 * @see [[scalaz.zio.internal.impls.padding.MutableQueueFieldsPadding]] for more details on padding
 * and object's memory layout.
 *
 * The design is heavily inspired by such libraries as
 * [[https://github.com/LMAX-Exchange/disruptor]] and
 * [[https://github.com/JCTools/JCTools]] which is based off
 * D. Vyukov's design
 * [[http://www.1024cores.net/home/lock-free-algorithms/queues/bounded-mpmc-queue]]
 *
 * Compared to JCTools this implementation doesn't rely on
 * `sun.misc.Unsafe`, so it is arguably more portable, and should be
 * easier to read. It's also very extensively commented, including
 * reasoning, assumptions, and hacks.
 *
 * =Alternative designs=
 *
 * There is an alternative design described in
 * [[http://pirkelbauer.com/papers/icapp16.pdf the paper]] A Portable
 * Lock-Free Bounded Queue by Pirkelbauer et al.
 *
 * It provides full lock-free guarantees, which generally means that
 * one out of many contending threads is guaranteed to make progress
 * in a finite number of steps. The design thus is not susceptible to
 * threads delaying other threads.
 *
 * However the helping scheme is rather involved and cannot be
 * implemented without allocations (at least I couldn't come up with
 * a way yet). This translates into worse performance on average, and
 * better performance in some very specific situations.
 */
abstract class RingBuffer[A](override final val capacity: Int) extends MutableQueueFieldsPadding[A] with Serializable {
  private val buf: Array[AnyRef]   = new Array[AnyRef](capacity)
  private val seq: AtomicLongArray = new AtomicLongArray(capacity)
  0.until(capacity).foreach(i => seq.set(i, i.toLong))

  private[this] final val STATE_LOOP     = 0
  private[this] final val STATE_EMPTY    = -1
  private[this] final val STATE_FULL     = -2
  private[this] final val STATE_RESERVED = 1

  protected def posToIdx(pos: Long, capacity: Int): Int

  override final def size(): Int = (tailUpdater.get(this) - headUpdater.get(this)).toInt

  override final def enqueuedCount(): Long = tailUpdater.get(this)

  override final def dequeuedCount(): Long = headUpdater.get(this)

  override final def offer(a: A): Boolean = {
    // Loading all instance fields locally. Otherwise JVM will reload
    // them after every volatile read in a loop below.
    val aCapacity = capacity

    val aSeq   = seq
    var curSeq = 0L

    val aHead   = headUpdater
    var curHead = 0L

    val aTail   = tailUpdater
    var curTail = aTail.get(this)
    var curIdx  = 0

    var state = STATE_LOOP

    while (state == STATE_LOOP) {
      curIdx = posToIdx(curTail, aCapacity)
      curSeq = aSeq.get(curIdx)

      if (curSeq < curTail) {
        // This means we're about to wrap around the buffer, i.e. the
        // queue is likely full. But there may be a dequeuing
        // happening at the moment, so we need to check for this.
        curHead = aHead.get(this)
        if (curTail >= curHead + aCapacity) {
          // This case implies that there is no in-progress dequeue,
          // we can just report that the queue is full.
          state = STATE_FULL
        } else {
          // This means that the consumer moved the head of the queue
          // (i.e. reserved a place to dequeue from), but hasn't yet
          // loaded an element from `buf` and hasn't updated the
          // `seq`. However, this should happen momentarity, so we can
          // just spin for a little while.
          state = STATE_LOOP
        }
      } else if (curSeq == curTail) {
        // We're at the right spot. At this point we can try to
        // reserve the place for enqueue by doing CAS on tail.
        if (aTail.compareAndSet(this, curTail, curTail + 1)) {
          // We successfuly reserved a place to enqueue.
          state = STATE_RESERVED
        } else {
          // There was a concurrent offer that won CAS. We need to try again at the next location.
          curTail += 1
          state = STATE_LOOP
        }
      } else { // curSeq > curTail
        // Either some other thread beat us enqueued an right element
        // or this thread got delayed. We need to resynchronize with
        // `tail` and try again.
        curTail = aTail.get(this)
        state = STATE_LOOP
      }
    }

    if (state == STATE_RESERVED) {
      // To add an element into the queue we do
      // 1. plain store into `buf`,
      // 2. volatile write of a `seq` value.
      // Following volatile read of `curTail + 1` guarantees
      // that plain store will be visible as it happens before in
      // program order.
      //
      // The volatile write can actually be relaxed to ordered store
      // (`lazySet`).  See Doug Lea's response in
      // [[http://cs.oswego.edu/pipermail/concurrency-interest/2011-October/008296.html]].
      buf(curIdx) = a.asInstanceOf[AnyRef]
      aSeq.lazySet(curIdx, curTail + 1)
      true
    } else { // state == STATE_FULL
      false
    }
  }

  override final def poll(default: A): A = {
    // Loading all instance fields locally. Otherwise JVM will reload
    // them after every volatile read in a loop below.
    val aCapacity = capacity

    val aBuf = buf

    val aSeq   = seq
    var curSeq = 0L

    val aHead   = headUpdater
    var curHead = aHead.get(this)
    var curIdx  = 0

    val aTail   = tailUpdater
    var curTail = 0L

    var state = STATE_LOOP

    while (state == STATE_LOOP) {
      curIdx = posToIdx(curHead, aCapacity)
      curSeq = aSeq.get(curIdx)

      if (curSeq <= curHead) {
        // There may be two distinct cases:
        // 1. curSeq == curHead
        //    This means there is no item available to dequeue. However
        //    there may be in-flight enqueue, and we need to check for
        //    that.
        // 2. curSeq < curHead
        //    This is a tricky case. Polling thread T1 can observe
        //    `curSeq < curHead` if thread T0 started dequeing at
        //    position `curSeq` but got descheduled. Meantime enqueing
        //    threads enqueued another (capacity - 1) elements, and other
        //    dequeueing threads dequeued all of them. So, T1 wrapped
        //    around the buffer and cannot proceed until T0 finishes its
        //    dequeue.
        //
        //    It may sound surprising that a thread get descheduled
        //    during dequeue for `capacity` number of operations, but
        //    it's actually pretty easy to observe such situations even
        //    at queue capacity of 4096 elements.
        //
        //    Anyway, in this case we can report that the queue is empty.

        curTail = aTail.get(this)
        if (curHead >= curTail) {
          // There is no concurrent enqueue happening. We can report
          // that that queue is empty.
          state = STATE_EMPTY
        } else {
          // There is an ongoing enqueue. A producer had reserved the
          // place, but hasn't published an element just yet. Let's
          // spin for a little while, as publishing should happen
          // momentarily.
          state = STATE_LOOP
        }
      } else if (curSeq == curHead + 1) {
        // We're at the right spot, and can try to reserve the spot
        // for dequeue.
        if (aHead.compareAndSet(this, curHead, curHead + 1)) {
          // Successfully reserved the spot and can proceed to dequeueing.
          state = STATE_RESERVED
        } else {
          // Another concurrent dequeue won. Let's try again at the next location.
          curHead += 1
          state = STATE_LOOP
        }
      } else { // curSeq > curHead + 1
        // Either some other thread beat us or this thread got
        // delayed. We need to resyncronize with `head` and try again.
        curHead = aHead.get(this)
        state = STATE_LOOP
      }
    }

    if (state == STATE_RESERVED) {
      // See the comment in offer method about volatile writes and
      // visibility guarantees.
      val deqElement = aBuf(curIdx)
      aBuf(curIdx) = null

      aSeq.lazySet(curIdx, curHead + aCapacity)

      deqElement.asInstanceOf[A]
    } else {
      default
    }
  }

  override final def isEmpty(): Boolean = tailUpdater.get(this) == headUpdater.get(this)

  override final def isFull(): Boolean = tailUpdater.get(this) == headUpdater.get(this) + capacity
}
