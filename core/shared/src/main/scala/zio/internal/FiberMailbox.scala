/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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
import java.util.concurrent.atomic.{AtomicLong, AtomicReferenceArray}

private[zio] final class FiberMailbox[A <: AnyRef](coreCapacityHint: Int = 8) {
  private[this] final val SpinLimit = 16

  private[this] val coreCapacity = {
    val minCapacity = if (coreCapacityHint < 2) 2 else math.min(coreCapacityHint, 1 << 30)
    var value       = 1
    while (value < minCapacity) value <<= 1
    value
  }

  private[this] val mask = coreCapacity - 1
  private[this] val core = new AtomicReferenceArray[A](coreCapacity)

  @volatile private[this] var head = 0L
  private[this] var headPadding1   = 0L
  private[this] var headPadding2   = 0L
  private[this] var headPadding3   = 0L
  private[this] var headPadding4   = 0L
  private[this] var headPadding5   = 0L
  private[this] var headPadding6   = 0L
  private[this] var headPadding7   = 0L

  private[this] val tail         = new AtomicLong(0L)
  private[this] var tailPadding1 = 0L
  private[this] var tailPadding2 = 0L
  private[this] var tailPadding3 = 0L
  private[this] var tailPadding4 = 0L
  private[this] var tailPadding5 = 0L
  private[this] var tailPadding6 = 0L
  private[this] var tailPadding7 = 0L

  private[this] val overflow = new ConcurrentLinkedQueue[A]()

  def offer(message: A): Boolean = {
    var spinning = 0
    while (true) {
      val currentTail = tail.get()
      val currentHead = head

      if ((currentTail - currentHead) >= coreCapacity) {
        return overflow.offer(message)
      }

      if (tail.compareAndSet(currentTail, currentTail + 1)) {
        val slot = (currentTail & mask).toInt
        core.lazySet(slot, message)
        return true
      }

      if (spinning < SpinLimit) {
        spinning += 1
        Thread.onSpinWait()
      }
    }

    false
  }

  def poll(): A = {
    val currentHead = head

    if (currentHead < tail.get()) {
      val slot  = (currentHead & mask).toInt
      var value = core.get(slot)
      var spins = 0

      while ((value eq null) && (spins < SpinLimit)) {
        Thread.onSpinWait()
        spins += 1
        value = core.get(slot)
      }

      while (value eq null) {
        Thread.`yield`()
        value = core.get(slot)
      }

      core.lazySet(slot, null.asInstanceOf[A])
      head = currentHead + 1
      value
    } else {
      overflow.poll()
    }
  }

  def isEmpty: Boolean =
    (head >= tail.get()) && overflow.isEmpty
}
