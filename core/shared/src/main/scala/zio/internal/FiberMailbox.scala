/*
 * Copyright 2022-2024 John A. De Goes and the ZIO Contributors
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

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReferenceArray

/**
 * A highly optimized mailbox for fiber communication, designed for the
 * specific characteristics of fiber run loops:
 *
 * 1. Single reader: Only the fiber itself reads from the mailbox
 * 2. Multiple writers: Any thread can add messages to the mailbox
 * 3. Typically small: Most mailboxes have 1-4 entries
 * 4. Relaxed consistency: Precise "isEmpty" is not required in all cases
 *
 * This implementation uses a small fixed-size ring buffer for the common case
 * with better cache locality than ConcurrentLinkedQueue, and falls back to
 * ConcurrentLinkedQueue for overflow scenarios.
 *
 * Key optimizations:
 * - Uses a small fixed array (4 slots) avoiding heap allocation in common case
 * - Single reader: uses plain int for read index (no atomic operations needed)
 * - Multiple writers: uses AtomicInteger for write index with CAS
 * - Relaxed isEmpty: uses slightly stale value for better performance
 */
private[zio] final class FiberMailbox[A] {
  import FiberMailbox._

  // Marker for reserved but unfilled slots
  private val PendingMarker = new AnyRef()

  // Small ring buffer for the common case (1-4 messages)
  private val buffer = new AtomicReferenceArray[AnyRef](BufferSize)

  // Write position - atomic because multiple threads can write
  private val writeIndex = new AtomicInteger(0)

  // Read position - not atomic because only the fiber reads
  // Volatile for visibility in isEmpty check
  @volatile private var readIndex: Int = 0

  // Overflow queue for when the buffer is full
  @volatile private var overflow: ConcurrentLinkedQueue[A] = _

  /**
   * Adds an element to the mailbox.
   */
  def offer(a: A): Boolean = {
    // Check overflow first for better performance after overflow starts
    val overflowQueue = overflow
    if (overflowQueue ne null) {
      overflowQueue.offer(a)
    } else {
      var done = false
      var result = false

      while (!done) {
        val windex = writeIndex.get()
        val rindex = readIndex
        val size = windex - rindex

        if (size < BufferSize) {
          // Buffer has space, try to claim slot
          val slot = windex & Mask

          // First, try to reserve the slot by CAS-ing null to PendingMarker
          if (buffer.compareAndSet(slot, null, PendingMarker)) {
            // Slot reserved, now try to atomically increment write index
            if (writeIndex.compareAndSet(windex, windex + 1)) {
              // Successfully claimed the slot, write the element
              buffer.set(slot, a.asInstanceOf[AnyRef])
              result = true
              done = true
            } else {
              // CAS failed, release the slot and retry
              buffer.set(slot, null)
            }
          }
          // If CAS failed (slot not empty), retry with new windex
        } else {
          // Buffer full, create overflow
          val newOverflow = new ConcurrentLinkedQueue[A]()
          newOverflow.offer(a)

          synchronized {
            if (overflow eq null) {
              overflow = newOverflow
            } else {
              overflow.offer(a)
            }
          }
          result = true
          done = true
        }
      }
      result
    }
  }

  /**
   * Removes and returns the first element from the mailbox.
   * Should only be called by the owning fiber (single reader).
   */
  def poll(default: A): A = {
    val rindex = readIndex
    val windex = writeIndex.get()

    if (rindex < windex) {
      // Buffer has data
      val slot = rindex & Mask
      val elem = buffer.getAndSet(slot, null)

      if (elem ne null && elem ne PendingMarker) {
        readIndex = rindex + 1
        elem.asInstanceOf[A]
      } else {
        // Buffer slot was null or pending, check overflow
        val overflowQueue = overflow
        if (overflowQueue ne null) {
          val overflowElem = overflowQueue.poll()
          if (overflowElem ne null) overflowElem else default
        } else {
          default
        }
      }
    } else {
      // Buffer empty, check overflow
      val overflowQueue = overflow
      if (overflowQueue ne null) {
        val overflowElem = overflowQueue.poll()
        if (overflowElem ne null) overflowElem else default
      } else {
        default
      }
    }
  }

  /**
   * Checks if the mailbox is empty.
   * Note: May return slightly stale result - acceptable for fiber run loop.
   */
  def isEmpty: Boolean = {
    val rindex = readIndex
    val windex = writeIndex.get()

    if (rindex < windex) {
      false
    } else {
      val overflowQueue = overflow
      if (overflowQueue ne null) !overflowQueue.isEmpty
      else true
    }
  }

  /**
   * Returns the number of elements (approximate).
   */
  def size: Int = {
    val rindex = readIndex
    val windex = writeIndex.get()
    val bufferSize = (windex - rindex).max(0)

    val overflowQueue = overflow
    if (overflowQueue ne null) bufferSize + overflowQueue.size()
    else bufferSize
  }
}

private[zio] object FiberMailbox {
  private final val BufferSize = 4
  private final val Mask = BufferSize - 1
}
