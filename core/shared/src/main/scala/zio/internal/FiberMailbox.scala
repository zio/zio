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

/**
 * A specialized MPSC mailbox for fiber messages. Takes advantage of the fact
 * that fibers almost always have at most one pending message at a time (the
 * resume after fork/async). A dedicated head slot backed by AtomicReference
 * handles this common case with a single CAS and zero allocation (no Node
 * wrapper). A lazily-initialized ConcurrentLinkedQueue absorbs the rare
 * overflow when multiple messages are pending concurrently.
 *
 * Once the queue is created (first collision), all subsequent `add` calls go
 * directly to the queue, preventing later messages from jumping ahead of
 * messages already waiting in the queue via the head slot.
 */
private[zio] final class FiberMailbox {

  private[this] val head = new AtomicReference[FiberMessage]()

  @volatile private[this] var tail: ConcurrentLinkedQueue[FiberMessage] = null

  def add(message: FiberMessage): Unit = {
    val t = tail
    if (t ne null) t.add(message)
    else if (!head.compareAndSet(null, message))
      ensureTail().add(message)
  }

  def poll(): FiberMessage = {
    val t = tail
    if (t ne null) {
      val h = head.get()
      if (h ne null) {
        head.lazySet(null)
        return h
      }
      return t.poll()
    }
    head.getAndSet(null)
  }

  def isEmpty: Boolean = {
    val t = tail
    if (t ne null) {
      (head.get() eq null) && t.isEmpty
    } else {
      head.get() eq null
    }
  }

  private[this] def ensureTail(): ConcurrentLinkedQueue[FiberMessage] = {
    var t = tail
    if (t eq null) {
      synchronized {
        t = tail
        if (t eq null) {
          t = new ConcurrentLinkedQueue[FiberMessage]()
          tail = t
        }
      }
    }
    t
  }
}
