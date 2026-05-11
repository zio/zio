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

import java.util.concurrent.atomic.AtomicReference

/**
 * A multiple-producer, single-consumer mailbox specialized for fiber messages.
 *
 * The fiber itself is the only consumer, while async callbacks, interruption,
 * and supervision can enqueue from multiple producer threads.
 */
private[zio] final class FiberMailbox[A <: AnyRef] {
  import FiberMailbox._

  private[this] val stub = new Node[A](null.asInstanceOf[A])

  private[this] var head  = stub
  private[this] val tail  = new AtomicReference[Node[A]](stub)
  private[this] val empty = null.asInstanceOf[A]

  def offer(message: A): Unit = {
    val node = new Node[A](message)
    val prev = tail.getAndSet(node)

    prev.next = node
  }

  def poll(): A = {
    val currentHead = head
    var next        = currentHead.next

    if (next eq null) {
      if (currentHead eq tail.get()) empty
      else {
        var spins = SpinLimit
        while ((next eq null) && spins > 0) {
          spins -= 1
          next = currentHead.next
        }

        if (next eq null) empty
        else pollNext(currentHead, next)
      }
    } else {
      pollNext(currentHead, next)
    }
  }

  def isEmpty(): Boolean = {
    val currentHead = head

    (currentHead.next eq null) && (currentHead eq tail.get())
  }

  private[this] def pollNext(currentHead: Node[A], next: Node[A]): A = {
    val message = next.value

    next.value = empty
    head = next
    currentHead.next = null

    message
  }
}

private[zio] object FiberMailbox {
  private final val SpinLimit = 8

  private final class Node[A <: AnyRef](var value: A) {
    @volatile var next: Node[A] = null
  }
}
