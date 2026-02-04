/*
 * Copyright 2022-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.internal

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

/**
 * A specialized mailbox for ZIO Fibers. * Strategy:
 *   - 0-1 items: Uses a single AtomicReference (Fast Lane, Zero Allocation).
 *   - >1 items: Upgrades to a ConcurrentLinkedQueue (Slow Lane). * This
 *     optimizes for the typical ZIO fiber usage pattern (ping-pong effects)
 *     where the mailbox rarely contains more than one message.
 */
final class FiberMailbox {
  // state can be:
  // null                                 -> Empty
  // FiberMessage                         -> One item
  // ConcurrentLinkedQueue[FiberMessage]  -> Multiple items (Fallback)
  private val state = new AtomicReference[AnyRef](null)

  def offer(message: FiberMessage): Unit = {
    if (message == null) throw new NullPointerException("Cannot offer null")

    var continue = true
    while (continue) {
      val current = state.get()

      if (current == null) {
        // Case 1: Empty -> Single Item (Fast Path)
        if (state.compareAndSet(null, message)) {
          continue = false
        }
      } else if (current.isInstanceOf[FiberMessage]) {
        // Case 2: Single Item -> Queue (Collision)
        // We must upgrade to a queue to hold both the existing and new message
        val queue = new ConcurrentLinkedQueue[FiberMessage]()
        queue.add(current.asInstanceOf[FiberMessage])
        queue.add(message)
        if (state.compareAndSet(current, queue)) {
          continue = false
        }
      } else {
        // Case 3: Queue -> Queue (Add)
        // The mailbox is already in "Heavy Mode", just use the queue
        val queue = current.asInstanceOf[ConcurrentLinkedQueue[FiberMessage]]
        queue.offer(message)
        continue = false
      }
    }
  }

  def poll(): FiberMessage = {
    var result: FiberMessage = null
    var continue             = true

    while (continue) {
      val current = state.get()

      if (current == null) {
        // Case 1: Empty
        continue = false
      } else if (current.isInstanceOf[FiberMessage]) {
        // Case 2: Single Item -> Empty (Fast Path)
        // Attempt to take the value and reset state to null
        if (state.compareAndSet(current, null)) {
          result = current.asInstanceOf[FiberMessage]
          continue = false
        }
      } else {
        // Case 3: Queue
        val queue = current.asInstanceOf[ConcurrentLinkedQueue[FiberMessage]]
        val msg   = queue.poll()

        if (msg != null) {
          result = msg
          continue = false
        } else {
          // The Queue is empty. We attempt to downgrade back to null to restore 0-alloc mode.
          // Note: If a producer adds to the queue *during* this check, compareAndSet fails safely.
          if (queue.isEmpty) {
            state.compareAndSet(queue, null)
            // Loop again to re-read state (it might be null now, or new data arrived)
          }
        }
      }
    }
    result
  }

  def isEmpty: Boolean = {
    val current = state.get()
    if (current == null) true
    else if (current.isInstanceOf[FiberMessage]) false
    else current.asInstanceOf[ConcurrentLinkedQueue[FiberMessage]].isEmpty
  }
}
