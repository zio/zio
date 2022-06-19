/*
 * Copyright 2022 John A. De Goes and the ZIO Contributors
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

final class GrowableArray[A <: AnyRef](hint: Int) { self =>
  import java.lang.System

  private var array = new Array[AnyRef](hint)
  private var size  = 0

  def +=(a: A): Unit = {
    ensureCapacity(1)

    array(size) = a
    size += 1
  }

  def ++=(as: Array[A]): Unit = {
    ensureCapacity(as.length)

    System.arraycopy(as, 0, array, size, as.length)

    size += as.length
  }

  def ++=(as: Iterable[A]): Unit = {
    ensureCapacity(as.size)

    as.foreach(self += _)
  }

  def apply(index: Int): A = array(index).asInstanceOf[A] // No error checking for performance

  def build(): Chunk[A] = {
    ensureCapacity(0)

    val chunk = Chunk.fromArray(array.asInstanceOf[Array[A]]).take(size)

    array = null

    chunk
  }

  def ensureCapacity(elements: Int): Unit = {
    val newSize = size + elements

    if (array eq null) {
      array = new Array[AnyRef](newSize)
    } else if (newSize > array.length) {
      val newStack = new Array[AnyRef](newSize)
      System.arraycopy(array, 0, newStack, 0, size)
      array = newStack
    }
  }

  def length: Int = size

  def toChunk: Chunk[A] = {
    val a = array
    if (a == null) Chunk.empty
    else Chunk.fromArray(a.asInstanceOf[Array[A]]).take(size)
  }

  override def toString(): String =
    Chunk.fromArray(array.asInstanceOf[Array[A]]).take(size).mkString("GrowableArray(", ",", ")")
}
object GrowableArray {
  def make[A <: AnyRef](hint: Int): GrowableArray[A] = new GrowableArray[A](hint)
}
