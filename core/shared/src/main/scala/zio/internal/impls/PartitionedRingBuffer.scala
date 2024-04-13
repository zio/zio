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

import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.ThreadLocalRandom

private[zio] final class PartitionedRingBuffer[A <: AnyRef](
  preferredPartitions: Int,
  preferredCapacity: Int
) extends MutableConcurrentQueue[A]
    with Serializable {

  private[this] val mask: Int          = MutableConcurrentQueue.maskFor(preferredPartitions)
  private[this] val nQueues: Int       = mask + 1
  private[this] val ringBufferCapacity = Math.ceil(preferredCapacity.toDouble / nQueues).toInt.max(2)
  private[this] val queues             = Array.fill(nQueues)(RingBuffer[A](ringBufferCapacity))

  override final val capacity = nQueues * ringBufferCapacity

  override def size(): Int = {
    val nq   = nQueues
    var i    = 0
    var size = 0
    while (i < nq) {
      size += queues(i).size()
      i += 1
    }
    size
  }

  override def enqueuedCount(): Long = {
    val nq   = nQueues
    var i    = 0
    var size = 0L
    while (i < nq) {
      size += queues(i).enqueuedCount()
      i += 1
    }
    size
  }

  override def dequeuedCount(): Long = {
    val nq   = nQueues
    var i    = 0
    var size = 0L
    while (i < nq) {
      size += queues(i).dequeuedCount()
      i += 1
    }
    size
  }

  /**
   * Offers an element to the queue, returning whether the offer was successful.
   *
   * @param maxMisses
   *   How many partitions to try before giving up
   */
  def offer(a: A, random: ThreadLocalRandom, maxMisses: Int): Boolean = {
    val maxI = nQueues.min(maxMisses + 1)
    val from = random.nextInt(nQueues)
    var i    = 0
    while (i < maxI) {
      val idx = (from + i) & mask
      if (queues(idx).offer(a)) return true
      i += 1
    }
    false
  }

  override def offer(a: A): Boolean =
    offer(a, ThreadLocalRandom.current(), nQueues)

  /**
   * Polls an element to the queue, returning the element or `default` if the
   * queue is empty.
   *
   * @param maxMisses
   *   How many partitions to try polling from before giving up
   */
  def poll(default: A, random: ThreadLocalRandom, maxMisses: Int): A = {
    val nq     = nQueues
    val from   = random.nextInt(nQueues)
    var i      = 0
    var result = null.asInstanceOf[A]
    var misses = 0
    while ((result eq null) && i < nq && misses <= maxMisses) {
      val idx   = (from + i) & mask
      val queue = queues(idx)
      result = queue.poll(default)
      if (result eq null) misses += 1
      i += 1
    }
    result
  }

  override def poll(default: A): A =
    poll(default, ThreadLocalRandom.current(), nQueues)

  override def isEmpty(): Boolean = {
    val nq = nQueues
    var i  = 0
    while (i < nq) {
      if (!queues(i).isEmpty()) return false
      i += 1
    }
    true
  }

  override def isFull(): Boolean = false
}
