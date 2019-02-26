/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

package scalaz.zio.internal.impls

import java.util.concurrent.ConcurrentLinkedQueue
import scalaz.zio.internal.MutableConcurrentQueue
import java.util.concurrent.atomic.AtomicLong

class LinkedQueue[A] extends MutableConcurrentQueue[A] with Serializable {
  override final val capacity: Int = Int.MaxValue

  private[this] val jucConcurrentQueue = new ConcurrentLinkedQueue[A]()

  /*
   * Using increment on AtomicLongs to provide metrics '''will''' have
   * performance implications. Having a better solution would be
   * desirable.
   */
  private[this] val enqueuedCounter = new AtomicLong(0)
  private[this] val dequeuedCounter = new AtomicLong(0)

  override final def size(): Int = jucConcurrentQueue.size()

  override final def enqueuedCount(): Long = enqueuedCounter.get()

  override final def dequeuedCount(): Long = dequeuedCounter.get()

  override final def offer(a: A): Boolean = {
    val success = jucConcurrentQueue.offer(a)
    if (success) enqueuedCounter.incrementAndGet()
    success
  }

  override final def poll(default: A): A = {
    val polled = jucConcurrentQueue.poll()
    if (polled != null) {
      dequeuedCounter.incrementAndGet()
      polled
    } else default
  }

  override final def isEmpty(): Boolean = jucConcurrentQueue.isEmpty

  override final def isFull(): Boolean = false
}
