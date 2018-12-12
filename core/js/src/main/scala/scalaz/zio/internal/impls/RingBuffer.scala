package scalaz.zio.internal.impls

import scalaz.zio.internal.MutableConcurrentQueue

object RingBuffer {
  def build[A](requiredCapacity: Int): MutableConcurrentQueue[A] = new RingBuffer(requiredCapacity)
}

/**
 * See [[scalaz.zio.internal.impls.RingBuffer]] for details
 * on design, tradeoffs, etc.
 */
class RingBuffer[A](override final val capacity: Int) extends MutableConcurrentQueue[A] {
  private[this] val buf: Array[AnyRef] = new Array[AnyRef](capacity)
  private[this] val seq: Array[Long]   = 0.until(capacity).map(_.toLong).toArray

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
      curIdx = posToIdx(curTail, capacity)
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
          // Try again at the next location.
          curTail += 1
          state = STATE_LOOP
        }
      } else { // curSeq > curTail
        // Resynchronize with `tail` and try again.
        curTail = tail
        state = STATE_LOOP
      }
    }

    if (state == STATE_RESERVED) {
      // To add an element into the queue we do
      // 1. plain store into `buf`,
      // 2. plain store into `seq`.
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
      curIdx = posToIdx(curHead, capacity)
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
          // Try again at the next location.
          curHead += 1
          state = STATE_LOOP
        }
      } else { // curSeq > curHead + 1
        // Resynchronize with `head` and try again.
        curHead = head
        state = STATE_LOOP
      }
    }

    if (state == STATE_RESERVED) {
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

  private def posToIdx(pos: Long, capacity: Int): Int = (pos % capacity.toLong).toInt
}
