/*
 * Copyright 2021 John A. De Goes and the ZIO Contributors
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

package zio

import scala.{Iterator => ScalaIterator}

/**
 * A `ChunkIterator` is a specialized iterator that supports efficient
 * iteration over chunks. Unlike a normal iterator, the caller is responsible
 * for providing an `index` with each call to `hasNextAt` and `nextAt`. By
 * contract this should be `0` initially and incremented by `1` each time
 * `nextAt` is called. This allows the caller to maintain the current index in
 * local memory rather than the iterator having to do it on the heap for array
 * backed chunks.
 */
sealed trait ChunkIterator[+A] { self =>

  /**
   * Checks if the chunk iterator has another element.
   */
  def hasNextAt(index: Int): Boolean

  /**
   * The length of the iterator.
   */
  def length: Int

  /**
   * Gets the next element from the chunk iterator.
   */
  def nextAt(index: Int): A

  /**
   * Returns a new iterator that is a slice of this iterator.
   */
  def slice(offset: Int, length: Int): ChunkIterator[A]

  /**
   * Concatenates this chunk iterator with the specified chunk iterator.
   */
  final def ++[A1 >: A](that: ChunkIterator[A1]): ChunkIterator[A1] =
    ChunkIterator.Concat(self, that)
}

private object ChunkIterator {

  /**
   * The empty iterator.
   */
  val empty: ChunkIterator[Nothing] =
    Empty

  /**
   * Constructs an iterator from an array of `AnyRef` values.
   */
  def fromAnyRefArray[A <: AnyRef](array: Array[A]): ChunkIterator[A] =
    new AnyRefArray(array, 0, array.length)

  /**
   * Constructs an iterator from an array of arbitrary values.
   */
  def fromArray[A](array: Array[A]): ChunkIterator[A] =
    array match {
      case array: Array[AnyRef]  => fromAnyRefArray(array)
      case array: Array[Boolean] => fromBooleanArray(array)
      case array: Array[Byte]    => fromByteArray(array)
      case array: Array[Char]    => fromCharArray(array)
      case array: Array[Double]  => fromDoubleArray(array)
      case array: Array[Float]   => fromFloatArray(array)
      case array: Array[Int]     => fromIntArray(array)
      case array: Array[Long]    => fromLongArray(array)
      case array: Array[Short]   => fromShortArray(array)
    }

  /**
   * Constructs an iterator from a `BitChunk`.
   */
  def fromBitChunk(chunk: Chunk.BitChunk): ChunkIterator[Boolean] =
    if (chunk.isEmpty) Empty
    else new BitChunk(chunk, 0, chunk.length)

  /**
   * Constructs an iterator from an array of `Boolean` values.
   */
  def fromBooleanArray(array: Array[Boolean]): ChunkIterator[Boolean] =
    if (array.isEmpty) Empty
    else new BooleanArray(array, 0, array.length)

  /**
   * Constructs an iterator from an array of `Byte` values.
   */
  def fromByteArray(array: Array[Byte]): ChunkIterator[Byte] =
    if (array.isEmpty) Empty
    else new ByteArray(array, 0, array.length)

  /**
   * Constructs an iterator from an array of `Char` values.
   */
  def fromCharArray(array: Array[Char]): ChunkIterator[Char] =
    if (array.isEmpty) Empty
    else new CharArray(array, 0, array.length)

  /**
   * Constructs an iterator from an array of `Double` values.
   */
  def fromDoubleArray(array: Array[Double]): ChunkIterator[Double] =
    if (array.isEmpty) Empty
    else new DoubleArray(array, 0, array.length)

  /**
   * Constructs an iterator from an array of `Float` values.
   */
  def fromFloatArray(array: Array[Float]): ChunkIterator[Float] =
    if (array.isEmpty) Empty
    else new FloatArray(array, 0, array.length)

  /**
   * Constructs an iterator from an array of `Int` values.
   */
  def fromIntArray(array: Array[Int]): ChunkIterator[Int] =
    if (array.isEmpty) Empty
    else IntArray(array, 0, array.length)

  /**
   * Constructs an iterator from a `Vector`.
   */
  def fromVector[A](vector: Vector[A]): ChunkIterator[A] =
    if (vector.length <= 0) Empty
    else Iterator(vector.iterator, vector.length)

  /**
   * Constructs an iterator from an array of `Long` values.
   */
  def fromLongArray(array: Array[Long]): ChunkIterator[Long] =
    if (array.isEmpty) Empty
    else LongArray(array, 0, array.length)

  /**
   * Constructs an iterator from an array of `Short` values.
   */
  def fromShortArray(array: Array[Short]): ChunkIterator[Short] =
    if (array.isEmpty) Empty
    else ShortArray(array, 0, array.length)

  /**
   * Constructs an iterator from a single value..
   */
  def single[A](a: A): ChunkIterator[A] =
    Singleton(a)

  private final case class AnyRefArray[A <: AnyRef](array: Array[A], offset: Int, length: Int)
      extends ChunkIterator[A] { self =>
    def hasNextAt(index: Int): Boolean =
      index < length
    def nextAt(index: Int): A =
      array(index + offset)
    def slice(offset: Int, length: Int): ChunkIterator[A] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) Empty
      else AnyRefArray(array, self.offset + offset, self.length - offset min length)
  }

  private final case class BooleanArray(array: Array[Boolean], offset: Int, length: Int)
      extends ChunkIterator[Boolean] { self =>
    def hasNextAt(index: Int): Boolean =
      index < length
    def nextAt(index: Int): Boolean =
      array(index + offset)
    def slice(offset: Int, length: Int): ChunkIterator[Boolean] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) Empty
      else BooleanArray(array, self.offset + offset, self.length - offset min length)
  }

  private final case class BitChunk(chunk: Chunk.BitChunk, offset: Int, length: Int) extends ChunkIterator[Boolean] {
    self =>
    def hasNextAt(index: Int): Boolean =
      index < length
    def nextAt(index: Int): Boolean =
      chunk(index + offset)
    def slice(offset: Int, length: Int): ChunkIterator[Boolean] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) Empty
      else BitChunk(chunk, self.offset + offset, self.length - offset min length)
  }

  private final case class ByteArray(array: Array[Byte], offset: Int, length: Int) extends ChunkIterator[Byte] { self =>
    def hasNextAt(index: Int): Boolean =
      index < length
    def nextAt(index: Int): Byte =
      array(index + offset)
    def slice(offset: Int, length: Int): ChunkIterator[Byte] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) Empty
      else ByteArray(array, self.offset + offset, self.length - offset min length)
  }

  private final case class CharArray(array: Array[Char], offset: Int, length: Int) extends ChunkIterator[Char] { self =>
    def hasNextAt(index: Int): Boolean =
      index < length
    def nextAt(index: Int): Char =
      array(index + offset)
    def slice(offset: Int, length: Int): ChunkIterator[Char] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) Empty
      else CharArray(array, self.offset + offset, self.length - offset min length)
  }

  private final case class Concat[A](left: ChunkIterator[A], right: ChunkIterator[A]) extends ChunkIterator[A] { self =>
    def hasNextAt(index: Int): Boolean =
      index < length
    val length: Int =
      left.length + right.length
    def nextAt(index: Int): A =
      if (left.hasNextAt(index)) left.nextAt(index)
      else right.nextAt(index - left.length)
    def slice(offset: Int, length: Int): ChunkIterator[A] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) Empty
      else if (offset >= left.length) right.slice(offset - left.length, length)
      else if (length <= left.length - offset) left.slice(offset, length)
      else Concat(left.slice(offset, left.length - offset), right.slice(0, offset + length - left.length))
  }

  private final case class DoubleArray(array: Array[Double], offset: Int, length: Int) extends ChunkIterator[Double] {
    self =>
    def hasNextAt(index: Int): Boolean =
      index < length
    def nextAt(index: Int): Double =
      array(index + offset)
    def slice(offset: Int, length: Int): ChunkIterator[Double] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) Empty
      else DoubleArray(array, self.offset + offset, self.length - offset min length)
  }

  private case object Empty extends ChunkIterator[Nothing] { self =>
    def hasNextAt(index: Int): Boolean =
      false
    val length: Int =
      0
    def nextAt(index: Int): Nothing =
      throw new ArrayIndexOutOfBoundsException(s"Empty chunk access to $index")
    def slice(offset: Int, length: Int): ChunkIterator[Nothing] =
      self
  }

  private final case class FloatArray(array: Array[Float], offset: Int, length: Int) extends ChunkIterator[Float] {
    self =>
    def hasNextAt(index: Int): Boolean =
      index < length
    def nextAt(index: Int): Float =
      array(index + offset)
    def slice(offset: Int, length: Int): ChunkIterator[Float] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) Empty
      else FloatArray(array, self.offset + offset, self.length - offset min length)
  }

  private final case class IntArray(array: Array[Int], offset: Int, length: Int) extends ChunkIterator[Int] { self =>
    def hasNextAt(index: Int): Boolean =
      index < length
    def nextAt(index: Int): Int =
      array(index + offset)
    def slice(offset: Int, length: Int): ChunkIterator[Int] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) Empty
      else IntArray(array, self.offset + offset, self.length - offset min length)
  }

  private final case class Iterator[A](iterator: ScalaIterator[A], length: Int) extends ChunkIterator[A] { self =>
    def hasNextAt(index: Int): Boolean =
      iterator.hasNext
    def nextAt(index: Int): A =
      iterator.next()
    def slice(offset: Int, length: Int): ChunkIterator[A] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) Empty
      else Iterator(iterator.slice(offset, offset + length), self.length - offset min length)
  }

  private final case class LongArray(array: Array[Long], offset: Int, length: Int) extends ChunkIterator[Long] { self =>
    def hasNextAt(index: Int): Boolean =
      index < length
    def nextAt(index: Int): Long =
      array(index + offset)
    def slice(offset: Int, length: Int): ChunkIterator[Long] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) Empty
      else LongArray(array, self.offset + offset, self.length - offset min length)
  }

  private final case class ShortArray(array: Array[Short], offset: Int, length: Int) extends ChunkIterator[Short] {
    self =>
    def hasNextAt(index: Int): Boolean =
      index < length
    def nextAt(index: Int): Short =
      array(index + offset)
    def slice(offset: Int, length: Int): ChunkIterator[Short] =
      if (offset <= 0 && length >= self.length) self
      else if (offset >= self.length || length <= 0) Empty
      else ShortArray(array, self.offset + offset, self.length - offset min length)
  }

  private final case class Singleton[A](a: A) extends ChunkIterator[A] { self =>
    def hasNextAt(index: Int): Boolean =
      index == 0
    val length: Int =
      1
    def nextAt(index: Int): A =
      if (index == 0) a
      else throw new ArrayIndexOutOfBoundsException(s"Singleton chunk access to $index")
    def slice(offset: Int, length: Int): ChunkIterator[A] =
      if (offset <= 0 && length >= 1) self
      else Empty
  }
}
