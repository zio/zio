/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

import scala.collection.IndexedSeqLike
import scala.collection.generic.CanBuildFrom
import scala.collection.immutable.IndexedSeq

/**
 * `ChunkLike` represents the capability for a `Chunk` to extend Scala's
 * collection library. Because of changes to Scala's collection library in
 * 2.13, separate versions of this trait are implemented for 2.11 / 2.12 and
 * 2.13 / Dotty. This allows code in `Chunk` to be written without concern for
 * the implementation details of Scala's collection library to the maximum
 * extent possible.
 *
 * Note that `IndexedSeq` is not a referentially transparent interface in that
 * it exposes methods that are partial (e.g. `apply`), allocate mutable state
 * (e.g. `iterator`), or are purely side effecting (e.g. `foreach`). `Chunk`
 * extends `IndexedSeq` to provide interoperability with Scala's collection
 * library but users should avoid these methods whenever possible.
 */
private[zio] trait ChunkLike[+A] extends IndexedSeq[A] with IndexedSeqLike[A, Chunk[A]] { self: Chunk[A] =>

  /**
   * Returns a filtered, mapped subset of the elements of this chunk.
   */
  def collect[B](pf: PartialFunction[A, B]): Chunk[B] =
    self.materialize.collect(pf)

  /**
   * Returns the first index for which the given predicate is satisfied.
   */
  override def indexWhere(f: A => Boolean): Int =
    indexWhere(f, 0)

  /**
   * Generates a readable string representation of this chunk using the
   * specified start, separator, and end strings.
   */
  override final def mkString(start: String, sep: String, end: String): String = {
    val builder = new scala.collection.mutable.StringBuilder()

    builder.append(start)

    var i   = 0
    val len = self.length

    while (i < len) {
      if (i != 0) builder.append(sep)
      builder.append(self(i).toString)
      i += 1
    }

    builder.append(end)

    builder.toString
  }

  /**
   * Generates a readable string representation of this chunk using the
   * specified separator string.
   */
  override final def mkString(sep: String): String =
    mkString("", sep, "")

  /**
   * Generates a readable string representation of this chunk.
   */
  override final def mkString: String =
    mkString("")

  /**
   * Determines if the chunk is not empty.
   */
  override final def nonEmpty: Boolean =
    length > 0

  /**
   * The number of elements in the chunk.
   */
  override final def size: Int =
    length

  override final def toSeq: Chunk[A] =
    self

  /**
   * Constructs a new `ChunkBuilder`. This operation allocates mutable state
   * and is not referentially transparent. It is provided for compatibility
   * with Scala's collection library and should not be used for other purposes.
   */
  override protected[this] def newBuilder: ChunkBuilder[A] =
    ChunkLike.newBuilder
}

private object ChunkLike {

  /**
   * Constructs a new `ChunkBuilder`.
   */
  def newBuilder[A]: ChunkBuilder[A] =
    ChunkBuilder.make()

  /**
   * Provides implicit evidence that that a collection of type `Chunk[A]` can
   * be build from elements of type `A`.
   */
  implicit def canBuildFrom[A]: CanBuildFrom[Chunk[A], A, Chunk[A]] =
    new CanBuildFrom[Chunk[A], A, Chunk[A]] {
      def apply(chunk: Chunk[A]): ChunkBuilder[A] =
        ChunkBuilder.make()
      def apply(): ChunkBuilder[A] =
        ChunkBuilder.make()
    }
}
