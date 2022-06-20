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

import scala.reflect.ClassTag

import zio.Chunk

final class GrowableArray[A: ClassTag](hint: Int) extends Iterable[A] { self =>
  import java.lang.System

  private var array = if (hint < 0) null else new Array[A](hint)
  private var _size = 0

  def +=(a: A): Unit = {
    ensureCapacity(1)

    array(_size) = a
    _size += 1
  }

  def ++=(as: Array[A]): Unit = {
    ensureCapacity(as.length)

    System.arraycopy(as, 0, array, _size, as.length)

    _size += as.length
  }

  def ++=(as: Chunk[A]): Unit = {
    ensureCapacity(as.length)

    var i = 0
    var j = _size
    while (i < as.length) {
      array(j) = as(i)
      i = i + 1
      j = j + 1
    }

    _size = _size + as.length
  }

  def ++=(as: Iterable[A]): Unit = {
    ensureCapacity(as.size)

    as.foreach(self += _)
  }

  def apply(index: Int): A = array(index) // No error checking for performance

  def build(): Chunk[A] =
    Chunk.fromArray(buildArray())

  def buildArray(): Array[A] = {
    val copy = buildArrayOrNull()

    if (copy eq null) Array.empty[A]
    else copy
  }

  def buildArrayOrNull(): Array[A] =
    if ((array eq null) || _size == 0) null
    else {
      val copy = new Array[A](_size)

      System.arraycopy(array, 0, copy, 0, _size)

      _size = 0

      copy
    }

  def ensureCapacity(elements: Int): Unit = {
    val newSize = _size + elements

    if (array eq null) {
      array = new Array[A](newSize)
    } else if (newSize > array.length) {
      val newStack = new Array(newSize + _size / 2)
      System.arraycopy(array, 0, newStack, 0, _size)
      array = newStack
    }
  }

  def iterator: Iterator[A] =
    new Iterator[A] {
      var index = 0

      def hasNext: Boolean = index < self._size

      def next(): A = {
        if (!hasNext) throw new NoSuchElementException("There are no elements to iterate")

        val value = self.array(index)

        index = index + 1

        value
      }
    }

  def length: Int = _size

  private[zio] def asChunk(): Chunk[A] =
    if (array == null) Chunk.empty
    else Chunk.fromArray(array).take(_size)

  override def toString(): String = self.mkString("GrowableArray(", ",", ")")
}
object GrowableArray {
  def make[A: ClassTag](hint: Int): GrowableArray[A] = new GrowableArray[A](hint)
}
