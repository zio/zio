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

import java.util.concurrent.ConcurrentLinkedQueue
import scala.annotation.nowarn

private final class FastLinkedQueue[A] extends MutableConcurrentQueue[A] with Serializable {
  override final val capacity = Int.MaxValue

  private[this] val jucConcurrentQueue = new ConcurrentLinkedQueue[A]()

  override def size(): Int = jucConcurrentQueue.size()

  override def enqueuedCount(): Long = 0L

  override def dequeuedCount(): Long = 0L

  override def offer(a: A): Boolean =
    jucConcurrentQueue.offer(a)

  override def offerAll[A1 <: A](as: Iterable[A1]): Chunk[A1] = {
    import collection.JavaConverters._
    jucConcurrentQueue.addAll(as.asJavaCollection): @nowarn("msg=JavaConverters")
    Chunk.empty
  }

  override def poll(default: A): A = {
    val polled = jucConcurrentQueue.poll()
    if (polled != null) polled else default
  }

  override def isEmpty(): Boolean = jucConcurrentQueue.isEmpty

  override def isFull(): Boolean = false
}
