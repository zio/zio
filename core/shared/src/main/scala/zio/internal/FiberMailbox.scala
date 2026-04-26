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

import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

/**
 * An MPSC (Multiple-Producer, Single-Consumer) mailbox for [[FiberRuntime]].
 *
 * Fiber mailboxes are single-consumer (only the fiber's own run loop calls
 * [[poll]]), multi-producer (any fiber may call [[offer]] concurrently), and
 * typically very small — almost always 0 or 1 message in flight.
 *
 * A single ''hot slot'' ([[AtomicReference]]) covers the common case where at
 * most one message is outstanding. Placing a message in the hot slot requires
 * only a single CAS with '''no heap allocation''', whereas a
 * [[ConcurrentLinkedQueue]] always allocates a linked-list node per element.
 * When the hot slot is already occupied an overflow [[ConcurrentLinkedQueue]]
 * absorbs the extra message; this overflow path is practically never taken.
 *
 * [[isEmpty]] is ''eventually consistent'': it may transiently return `true`
 * while a concurrent [[offer]] is in flight. This is safe because
 * [[FiberRuntime]] re-checks emptiness after releasing the drain-lock (see the
 * retry pattern in `drainQueueOnCurrentThread`).
 */
private[zio] final class FiberMailbox {

  // Hot slot: holds at most one message. Writers CAS; the reader uses getAndSet.
  private[this] val hotSlot = new AtomicReference[FiberMessage](null)
  // Overflow: absorbs messages that arrive while the hot slot is occupied.
  private[this] val overflow = new ConcurrentLinkedQueue[FiberMessage]()

  /**
   * Offers a message to the mailbox. Thread-safe for concurrent callers.
   *
   * Fast path: a single CAS into the hot slot — zero allocations. Slow path
   * (hot slot already occupied): enqueue into the overflow CLQ.
   */
  def offer(msg: FiberMessage): Unit =
    if (!hotSlot.compareAndSet(null, msg)) overflow.add(msg)

  /**
   * Polls a message from the mailbox, returning `null` if empty.
   *
   * Must only be called from the single consumer (the fiber's run loop).
   */
  def poll(): FiberMessage = {
    val hot = hotSlot.getAndSet(null)
    if (hot ne null) hot else overflow.poll()
  }

  /**
   * Returns `true` iff the mailbox appears empty. Eventually consistent;
   * callers must tolerate false negatives.
   */
  def isEmpty: Boolean = (hotSlot.get eq null) && overflow.isEmpty

}
