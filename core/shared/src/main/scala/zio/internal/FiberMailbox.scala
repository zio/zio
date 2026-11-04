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
 * A specialized mailbox for fiber messages.
 *
 * Most fiber mailboxes contain at most one message, so the empty/single-message
 * states avoid the node allocation and traversal cost of a linked queue. When a
 * second message arrives before the first is drained, the mailbox promotes once
 * to a ConcurrentLinkedQueue and keeps using it for the rest of the fiber's
 * lifetime, preserving FIFO ordering and the same MPSC safety profile as the
 * previous implementation.
 */
private[zio] final class FiberMailbox extends Serializable {
  private[this] val state = new AtomicReference[AnyRef]()

  @tailrec
  final def add(message: FiberMessage): Unit = {
    assert(message ne null)

    val current = state.get()

    if (current eq null) {
      if (!state.compareAndSet(null, message.asInstanceOf[AnyRef])) add(message)
    } else if (current.isInstanceOf[ConcurrentLinkedQueue[_]]) {
      current.asInstanceOf[ConcurrentLinkedQueue[FiberMessage]].add(message)
      ()
    } else {
      val queue = new ConcurrentLinkedQueue[FiberMessage]()
      queue.add(current.asInstanceOf[FiberMessage])
      queue.add(message)

      if (!state.compareAndSet(current, queue)) add(message)
    }
  }

  @tailrec
  final def poll(): FiberMessage = {
    val current = state.get()

    if (current eq null) null
    else if (current.isInstanceOf[ConcurrentLinkedQueue[_]]) {
      current.asInstanceOf[ConcurrentLinkedQueue[FiberMessage]].poll()
    } else if (state.compareAndSet(current, null)) {
      current.asInstanceOf[FiberMessage]
    } else poll()
  }

  final def isEmpty: Boolean = {
    val current = state.get()

    (current eq null) || (
      current.isInstanceOf[ConcurrentLinkedQueue[_]] &&
        current.asInstanceOf[ConcurrentLinkedQueue[FiberMessage]].isEmpty
    )
  }
}
