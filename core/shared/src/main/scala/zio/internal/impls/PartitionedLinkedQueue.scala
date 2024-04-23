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

import zio.Chunk
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.ThreadLocalRandom

private[zio] final class PartitionedLinkedQueue[A <: AnyRef](
  preferredPartitions: Int,
  addMetrics: Boolean
) extends MutableConcurrentQueue[A]
    with Serializable {

  override final val capacity = Int.MaxValue

  private[this] val mask    = MutableConcurrentQueue.roundToPow2MinusOne(preferredPartitions)
  private[this] val nQueues = mask + 1
  private[this] val queues  = Array.fill(nQueues)(if (addMetrics) new LinkedQueue[A] else new FastLinkedQueue[A])

  def nPartitions(): Int = nQueues

  override def size(): Int = {
    val from = ThreadLocalRandom.current().nextInt(nQueues)
    var i    = 0
    var size = 0
    while (i < nQueues) {
      val idx = (from + i) & mask
      size += queues(idx).size()
      i += 1
    }
    size
  }

  override def enqueuedCount(): Long =
    if (addMetrics) {
      val from = ThreadLocalRandom.current().nextInt(nQueues)
      var i    = 0
      var size = 0L
      while (i < nQueues) {
        val idx = (from + i) & mask
        size += queues(idx).enqueuedCount()
        i += 1
      }
      size
    } else 0L

  override def dequeuedCount(): Long =
    if (addMetrics) {
      val random = ThreadLocalRandom.current()
      val from   = random.nextInt(nQueues)
      var i      = 0
      var size   = 0L
      while (i < nQueues) {
        val idx = (from + i) & mask
        size += queues(idx).dequeuedCount()
        i += 1
      }
      size
    } else 0L

  def offer(a: A, random: ThreadLocalRandom): Unit = {
    val idx = random.nextInt(nQueues)
    queues(idx).offer(a)
  }

  override def offer(a: A): Boolean = {
    offer(a, ThreadLocalRandom.current())
    true
  }

  def offerAll[A1 <: A](as: Iterable[A1], random: ThreadLocalRandom): Unit = {
    val from = random.nextInt(nQueues)
    var i    = 0
    val iter = as.iterator
    while (iter.hasNext) {
      val value = iter.next()
      val idx   = (from + i) & mask
      queues(idx).offer(value)
      i += 1
    }
  }

  override def offerAll[A1 <: A](as: Iterable[A1]): Chunk[A1] = {
    offerAll(as, ThreadLocalRandom.current())
    Chunk.empty
  }

  def poll(default: A, random: ThreadLocalRandom): A = {
    val from   = random.nextInt(nQueues)
    var i      = 0
    var result = null.asInstanceOf[A]
    while ((result eq null) && i < nQueues) {
      val idx = (from + i) & mask
      result = queues(idx).poll(default)
      i += 1
    }
    result
  }

  override def poll(default: A): A =
    poll(default, ThreadLocalRandom.current())

  override def isEmpty(): Boolean = {
    val random = ThreadLocalRandom.current()
    val from   = random.nextInt(nQueues)
    var i      = 0
    var result = true
    while (result && i < nQueues) {
      val idx = (from + i) & mask
      result = queues(idx).isEmpty()
      i += 1
    }
    result
  }

  override def isFull(): Boolean = false
}
