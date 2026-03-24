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
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

/**
 * A specialized MPSC (multi-producer, single-consumer) mailbox for fiber
 * messages.
 *
 * Uses a three-state AtomicReference state machine:
 *   - null => empty
 *   - FiberMessage => exactly one message (zero-allocation fast path)
 *   - CLQ => promoted to queue under contention
 *
 * The single-slot fast path avoids all CLQ allocation for the common case of a
 * single outstanding message (e.g. resume-after-async). Under contention a
 * ConcurrentLinkedQueue is created and used for subsequent messages. All
 * coordination is done via CAS — no locks.
 */
private[zio] trait FiberMailbox {

  // Holds: null | FiberMessage | ConcurrentLinkedQueue[FiberMessage]
  private[this] val state = new AtomicReference[AnyRef](null)

  @tailrec
  final def add(msg: FiberMessage): Unit = {
    val current = state.get()
    if (current eq null) {
      if (!state.compareAndSet(null, msg)) add(msg)
    } else
      current match {
        case q: ConcurrentLinkedQueue[FiberMessage] @unchecked =>
          q.add(msg)
        case existing: FiberMessage =>
          val q = new ConcurrentLinkedQueue[FiberMessage]()
          q.add(existing)
          q.add(msg)
          if (!state.compareAndSet(existing, q)) add(msg)
        case _ => add(msg) // unreachable; guards against MatchError
      }
  }

  @tailrec
  final def poll(): FiberMessage = {
    val current = state.get()
    if (current eq null) null
    else
      current match {
        case q: ConcurrentLinkedQueue[FiberMessage] @unchecked =>
          q.poll()
        case msg: FiberMessage =>
          if (state.compareAndSet(msg, null)) msg else poll()
        case _ => poll() // unreachable; guards against MatchError
      }
  }

  final def isEmpty: Boolean = {
    val current = state.get()
    if (current eq null) true
    else
      current match {
        case q: ConcurrentLinkedQueue[FiberMessage] @unchecked => q.isEmpty
        case _                                                 => false
      }
  }
}
