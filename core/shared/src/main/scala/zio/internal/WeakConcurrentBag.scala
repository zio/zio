/*
 * Copyright 2020-2024 John A. De Goes and the ZIO Contributors
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

import zio.internal.WeakConcurrentBag.IsAlive
import zio.{Chunk, ChunkBuilder, Duration, Unsafe}

/**
 * A [[WeakConcurrentBag]] stores a collection of values using weak references.
 *
 * This implementation is a high-performance, lock-free wrapper around
 * Platform-specific concurrent weak sets (FiberSet on JVM).
 */
private[zio] class WeakConcurrentBag[A <: AnyRef](nurserySize: Int, isAlive: IsAlive[A]) { self =>
  private[this] val storage = Platform.newConcurrentWeakSet[A]()(Unsafe.unsafe)

  /**
   * Schedules a thread (if not already running) which will wake up on the
   * specified interval and remove dead references.
   *
   * In this new implementation, this is a no-op as the underlying storage
   * handles pruning automatically during concurrent operations.
   */
  def withAutoGc(every: Duration): WeakConcurrentBag[A] = self

  /**
   * Adds a new value to the weak concurrent bag.
   */
  final def add(a: A): Unit = {
    storage.add(a)
  }

  final def size: Int =
    storage.size()

  /**
   * Performs a garbage collection.
   *
   * In this new implementation, this is a no-op as the underlying storage
   * handles pruning automatically.
   */
  final def gc(): Unit = ()

  final def gc(force: Boolean): Unit = ()

  /**
   * No-op for compatibility.
   */
  final def graduate(): Unit = ()

  /**
   * Returns an iterator over the contents of the bag.
   */
  final def iterator: Iterator[A] = {
    val it = storage.iterator()
    new Iterator[A] {
      def hasNext: Boolean = it.hasNext
      def next(): A        = it.next()
    }
  }

  /**
   * Returns a weakly consistent chunk of the bag's contents.
   */
  final def toChunk: Chunk[A] = {
    val builder = ChunkBuilder.make[A]()
    val it      = storage.iterator()
    while (it.hasNext) {
      val next = it.next()
      if (next ne null) {
        builder += next
      }
    }
    builder.result()
  }

  override def toString: String = s"WeakConcurrentBag(${toChunk.mkString(", ")})"
}

private[zio] object WeakConcurrentBag {
  type IsAlive[A] = A => Boolean

  def apply[A <: AnyRef](nurserySize: Int): WeakConcurrentBag[A] =
    new WeakConcurrentBag[A](nurserySize, _ => true)

  def apply[A <: AnyRef](nurserySize: Int, isAlive: IsAlive[A]): WeakConcurrentBag[A] =
    new WeakConcurrentBag[A](nurserySize, isAlive)
}
