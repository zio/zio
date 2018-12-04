package scalaz.zio.internal.impls

import scalaz.zio.internal.MutableConcurrentQueue

/**
 * See [[scalaz.zio.internal.impls.RingBuffer]] for details
 * on design, tradeoffs, etc.
 *
 * This is a scalajs-compatible version that uses `AtomicLong`
 * `head` and `tail` counters instead of `AtomicLongFieldUpdater`
 * since those are not supported by scala-js.
 */
class RingBuffer[A](val desiredCapacity: Int) extends MutableConcurrentQueue[A] {
  final val capacity: Int         = nextPow2(desiredCapacity)
  private[this] val idxMask: Long = (capacity - 1).toLong

  private[this] val buf: Array[AnyRef] = new Array[AnyRef](capacity)
  private[this] val seq: Array[Long]   = new Array[Long](capacity)
  0.until(capacity).foreach(i => seq.update(i, i.toLong))

  private[this] var head: Long = 0L
  private[this] var tail: Long = 0L

  private[this] final val STATE_LOOP     = 0
  private[this] final val STATE_EMPTY    = -1
  private[this] final val STATE_FULL     = -2
  private[this] final val STATE_RESERVED = 1

  override final def size(): Int = (tail - head).toInt

  override final def enqueuedCount(): Long = tail

  override final def dequeuedCount(): Long = head

  override final def offer(a: A): Boolean = {
    var curSeq  = 0L
    var curHead = 0L
    var curTail = tail
    var curIdx  = 0
    var state   = STATE_LOOP

    while (state == STATE_LOOP) {
      curIdx = posToIdx(curTail, idxMask)
      curSeq = seq(curIdx)

      if (curSeq < curTail) {
        // This means we're about to wrap around the buffer, i.e. the
        // queue is likely full. But there may be a dequeuing
        // happening at the moment, so we need to check for this.
        curHead = head
        if (curTail >= curHead + capacity) {
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
        if (tail == curTail) {
          // We successfuly reserved a place to enqueue.
          tail = curTail + 1
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
        curTail = tail
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
      seq(curIdx) = curTail + 1
      true
    } else { // state == STATE_FULL
      false
    }
  }

  override final def poll(default: A): A = {
    var curSeq  = 0L
    var curHead = head
    var curIdx  = 0
    var curTail = 0L
    var state   = STATE_LOOP

    while (state == STATE_LOOP) {
      curIdx = posToIdx(curHead, idxMask)
      curSeq = seq(curIdx)

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

        curTail = tail
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
        if (head == curHead) {
          // Successfully reserved the spot and can proceed to dequeueing.
          head = curHead + 1
          state = STATE_RESERVED
        } else {
          // Another concurrent dequeue won. Let's try again at the next location.
          curHead += 1
          state = STATE_LOOP
        }
      } else { // curSeq > curHead + 1
        // Either some other thread beat us or this thread got
        // delayed. We need to resyncronize with `head` and try again.
        curHead = head
        state = STATE_LOOP
      }
    }

    if (state == STATE_RESERVED) {
      // See the comment in offer method about volatile writes and
      // visibility guarantees.
      val deqElement = buf(curIdx)
      buf(curIdx) = null
      seq(curIdx) = curHead + capacity

      deqElement.asInstanceOf[A]
    } else {
      default
    }
  }

  override final def isEmpty(): Boolean = tail == head

  override final def isFull(): Boolean = tail == head + capacity

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
