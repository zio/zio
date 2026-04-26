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
 * Fiber mailboxes are:
 *   - '''Single-consumer''': only the fiber's own run loop ever calls
 *     [[poll]].
 *   - '''Multi-producer''': any fiber can call [[offer]] concurrently.
 *   - '''Typically very small''': almost always 0 or 1 message is in flight.
 *
 * == Design ==
 *
 * A single ''hot slot'' ([[AtomicReference]]) covers the common case where at
 * most one message is outstanding at a time. Placing a message in the hot slot
 * is a single CAS with '''no heap allocation''', whereas a
 * [[ConcurrentLinkedQueue]] always allocates a linked-list node per element.
 *
 * When the hot slot is already occupied a standard [[ConcurrentLinkedQueue]]
 * absorbs the overflow; this path is practically never taken.
 *
 * == Correctness ==
 *
 * [[isEmpty]] is ''eventually consistent'': it may transiently return `true`
 * while a concurrent [[offer]] is in flight. This is safe because
 * [[FiberRuntime]] re-checks emptiness after releasing the drain-lock (see the
 * retry pattern in `drainQueueOnCurrentThread`).
 */
private[zio] final class FiberMailbox {

  /** Hot slot for the common single-message-in-flight case. */
  private[this] val hotSlot  = new AtomicReference[FiberMessage](null)
  /** Overflow for the rare case where the hot slot is already occupied. */
  private[this] val overflow = new ConcurrentLinkedQueue[FiberMessage]()

  /**
   * Offers a message to the mailbox. Thread-safe for concurrent callers.
   *
   * Fast path: CAS the hot slot — zero allocations. Slow path (hot slot
   * occupied): enqueue into the overflow CLQ.
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
