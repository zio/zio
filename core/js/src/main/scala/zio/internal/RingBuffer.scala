/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

import zio.{Chunk, ChunkBuilder}

object RingBuffer {

  /**
   * @note minimum supported capacity is 2
   */
  def apply[A](capacity: Int): MutableConcurrentQueue[A] = {
    assert(capacity >= 2)

    new RingBuffer(Math.max(capacity, 2))
  }
}

/**
 * See [[zio.internal.RingBuffer]] for details
 * on design, tradeoffs, etc.
 */
final class RingBuffer[A](override final val capacity: Int) extends MutableConcurrentQueue[A] {
  private[this] val buf: Array[AnyRef] = new Array[AnyRef](capacity)
  private[this] val seq: Array[Long]   = 0.until(capacity).map(_.toLong).toArray

  private[this] var head: Long = 0L
  private[this] var tail: Long = 0L

  private[this] final val STATE_LOOP     = 0
  private[this] final val STATE_EMPTY    = -1
  private[this] final val STATE_FULL     = -2
  private[this] final val STATE_RESERVED = 1

  override def size(): Int = (tail - head).toInt

  override def enqueuedCount(): Long = tail

  override def dequeuedCount(): Long = head

  override def offer(a: A): Boolean = {
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
          // `seq`. However, this should happen momentarily, so we can
          // just spin for a little while.
          state = STATE_LOOP
        }
      } else if (curSeq == curTail) {
        // We're at the right spot. At this point we can try to
        // reserve the place for enqueue by doing CAS on tail.
        if (tail == curTail) {
          // We successfully reserved a place to enqueue.
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

  override final def offerAll(as: Iterable[A]): Iterable[A] = {
    var curSeq  = 0L
    var curHead = 0L
    var curTail = tail
    var curIdx  = 0
    var state   = STATE_LOOP
    val offers  = as.size.toLong
    var enqHead = 0L
    var enqTail = 0L

    while (state == STATE_LOOP) {
      curHead = head
      curTail = tail
      val size      = curTail - curHead
      val available = capacity - size
      val forQueue  = math.min(offers, available)
      if (forQueue == 0) {
        // There are no elements to offer or no space in the queue, terminate
        // immediately.
        state = STATE_FULL
      } else {
        // We know that space for this many elements has been reserved in the
        // queue. However, elements in some of these spaces may be in the
        // process of being dequeued so we need to check for that.
        enqHead = curTail
        enqTail = curTail + forQueue
        var continue = true
        while (continue & enqHead < enqTail) {
          curIdx = posToIdx(enqHead, capacity)
          curSeq = seq(curIdx)
          if (curSeq != enqHead) {
            // The element at this spot has not been dequeued yet, or possibly
            // has been dequeued and another thread has already enqueued a new
            // element. We need to abort and retry.
            continue = false
          }
          enqHead += 1
        }
        // If all elements have been dequeued, we can do compare and swap to
        // try to reserve the space in the queue.
        if (continue && tail == curTail) {
          // We successfully reserved the space in the queue. We can prepare to
          // enqueue the elements and publish our changes.
          tail = enqTail
          enqHead = curTail
          state = STATE_RESERVED
        } else {
          // Another thread beat us to reserving space in the queue. We need to
          // abort and retry.
          state = STATE_LOOP
        }
      }
    }

    if (state == STATE_RESERVED) {
      // We have successfully resserved space in the queue and have exclusive
      // ownership of each space until we publish our changes. Enqueue the
      // elements sequentially and publish our changes as we go.
      val iterator = as.iterator
      while (enqHead < enqTail) {
        val a = iterator.next()
        curIdx = posToIdx(enqHead, capacity)
        buf(curIdx) = a.asInstanceOf[AnyRef]
        seq(curIdx) = enqHead + 1
        enqHead += 1
      }
      Chunk.fromIterator(iterator)
    } else {
      // There was no space in the queue or the original collection was empty.
      // Just return the original collection unchanged.
      as
    }
  }

  override def poll(default: A): A = {
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

  override final def pollUpTo(n: Int): Iterable[A] = {
    var curSeq  = 0L
    var curHead = head
    var curIdx  = 0
    var curTail = 0L
    var state   = STATE_LOOP
    val takers  = n.toLong
    var deqHead = 0L
    var deqTail = 0L

    while (state == STATE_LOOP) {
      curHead = head
      curTail = tail
      val size   = curTail - curHead
      val toTake = math.min(takers, size)
      if (toTake <= 0) {
        // There are no elements to take, terminate immediately.
        state = STATE_EMPTY
      } else {
        // We know that space for this many elements has been reserved in the
        // queue. However, some of these elements may still be in the process
        // of being enqueued, so we need to check for that.
        deqHead = curHead
        deqTail = curHead + toTake
        var continue = true
        while (continue && deqHead < deqTail) {
          curIdx = posToIdx(deqHead, capacity)
          curSeq = seq(curIdx)
          if (curSeq != deqHead + 1) {
            // The element at this spot has not been enqueued yet, or possibly
            // has been enqueued and already dequeued by another thread. We
            // need to abort and retry.
            continue = false
          }
          deqHead += 1
        }
        // If all elements have been enqueued, we can do compare and swap to
        // try to reserve the space in the queue.
        if (continue && head == curHead) {
          // We successfully reserved the space in the queue. We can prepare to
          // dequeue the elements and publish our changes.
          head = deqTail
          deqHead = curHead
          state = STATE_RESERVED
        } else {
          // Another thread beat us to reserving space in the queue. We need to
          // abort and retry.
          state = STATE_LOOP
        }
      }
    }

    if (state == STATE_RESERVED) {
      // We have successfully resserved space in the queue and have exclusive
      // ownership of each space until we publish our changes. Dequeue the
      // elements sequentially and publish our changes as we go.
      val builder = ChunkBuilder.make[A]()
      builder.sizeHint(n)
      while (deqHead < deqTail) {
        curIdx = posToIdx(deqHead, capacity)
        val a = buf(curIdx).asInstanceOf[A]
        buf(curIdx) = null
        seq(curIdx) = deqHead + capacity
        builder += a
        deqHead += 1
      }
      builder.result()
    } else {
      // There were no elements to take, just return an empty collection.
      Chunk.empty
    }
  }

  override def isEmpty(): Boolean = tail == head

  override def isFull(): Boolean = tail == head + capacity

  private def posToIdx(pos: Long, capacity: Int): Int = (pos % capacity.toLong).toInt
}
