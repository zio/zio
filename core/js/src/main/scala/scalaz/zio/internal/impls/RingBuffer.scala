package scalaz.zio.internal.impls

import java.util.concurrent.atomic.{ AtomicLong, AtomicLongArray }
import scalaz.zio.internal.MutableConcurrentQueue

/**
 * See [[coreJVM/scalaz.zio.internal.impls.RingBuffer]] for details
 * on design, tradeoffs, etc.
 *
 * This is a scalajs-compatible version that uses [[AtomicLong]]
 * `head` and `tail` counters instead of [[AtomicLongFieldUpdater]]
 * since those are not supported by scala-js.
 */
class RingBuffer[A](val desiredCapacity: Int) extends MutableConcurrentQueue[A] {
  final val capacity: Int         = nextPow2(desiredCapacity)
  private[this] val idxMask: Long = (capacity - 1).toLong

  private[this] val buf: Array[AnyRef]   = new Array[AnyRef](capacity)
  private[this] val seq: AtomicLongArray = new AtomicLongArray(capacity)
  0.until(capacity).foreach(i => seq.set(i, i.toLong))

  private[this] val head: AtomicLong = new AtomicLong(0L)
  private[this] val tail: AtomicLong = new AtomicLong(0L)

  private[this] final val STATE_LOOP     = 0
  private[this] final val STATE_EMPTY    = -1
  private[this] final val STATE_FULL     = -2
  private[this] final val STATE_RESERVED = 1

  override final def size(): Int = (tail.get() - head.get()).toInt

  override final def enqueuedCount(): Long = tail.get()

  override final def dequeuedCount(): Long = head.get()

  override final def offer(a: A): Boolean = {
    // Loading all instance fields locally. Otherwise JVM will reload
    // them after every volatile read in a loop below.
    val aCapacity = capacity
    val aMask     = idxMask

    val aSeq   = seq
    var curSeq = 0L

    val aHead   = head
    var curHead = 0L

    val aTail   = tail
    var curTail = aTail.get()
    var curIdx  = 0

    var state = STATE_LOOP

    while (state == STATE_LOOP) {
      curIdx = posToIdx(curTail, aMask)
      curSeq = aSeq.get(curIdx)

      if (curSeq < curTail) {
        // This means we're about to wrap around the buffer, i.e. the
        // queue is likely full. But there may be a dequeuing
        // happening at the moment, so we need to check for this.
        curHead = aHead.get()
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
        if (aTail.compareAndSet(curTail, curTail + 1)) {
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
        curTail = aTail.get()
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
    val aMask     = idxMask
    val aBuf      = buf

    val aSeq   = seq
    var curSeq = 0L

    val aHead   = head
    var curHead = aHead.get()
    var curIdx  = 0

    val aTail   = tail
    var curTail = 0L

    var state = STATE_LOOP

    while (state == STATE_LOOP) {
      curIdx = posToIdx(curHead, aMask)
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

        curTail = aTail.get()
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
        if (aHead.compareAndSet(curHead, curHead + 1)) {
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
        curHead = aHead.get()
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

  override final def isEmpty(): Boolean = tail.get() == head.get()

  override final def isFull(): Boolean = tail.get() == head.get() + capacity

  private def posToIdx(pos: Long, mask: Long): Int = (pos & mask).toInt

  /*
   * Used only once during queue creation. Doesn't need to be
   * performant or anything.
   */
  private def nextPow2(n: Int): Int = {
    val nextPow = (Math.log(n.toDouble) / Math.log(2.0)).ceil.toInt
    Math.pow(2.0, nextPow.toDouble).toInt.max(2)
  }
}
