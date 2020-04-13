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

import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.Builder

/**
 * `ChunkCanBuildFrom` provides implicit evidence that a collection of type
 * `Chunk[A]` can be built from elements of type `A`. Since a `Chunk[A]` can
 * be built from elements of type `A` for any type `A`, this implicit
 * evidence always exists. It is used primarily to provide proof that the
 * target type of a collection operation is a `Chunk` to support high
 * performance implementations of transformation operations for chunks.
 */
sealed trait ChunkCanBuildFrom[A] extends CanBuildFrom[Chunk[Any], A, Chunk[A]] {
  override def apply(from: Chunk[Any]): Builder[A, Chunk[A]] = ChunkBuilder.make()
  override def apply(): Builder[A, Chunk[A]]                 = ChunkBuilder.make()
}

object ChunkCanBuildFrom {

  /**
   * Construct a new instance of `ChunkCanBuildFrom` for the specified type.
   */
  implicit def apply[A]: ChunkCanBuildFrom[A] =
    new ChunkCanBuildFrom[A] {}

  /**
   * The instance of `ChunkCanBuildFrom` for `Boolean`.
   */
  implicit val chunkCanBuildFromBoolean: ChunkCanBuildFrom[Boolean] =
    ChunkCanBuildFrom[Boolean]

  /**
   * The instance of `ChunkCanBuildFrom` for `Byte`.
   */
  implicit val chunkCanBuildFromByte: ChunkCanBuildFrom[Byte] =
    ChunkCanBuildFrom[Byte]

  /**
   * The instance of `ChunkCanBuildFrom` for `Char`.
   */
  implicit val chunkCanBuildFromChar: ChunkCanBuildFrom[Char] =
    ChunkCanBuildFrom[Char]

  /**
   * The instance of `ChunkCanBuildFrom` for `Double`.
   */
  implicit val chunkCanBuildFromDouble: ChunkCanBuildFrom[Double] =
    ChunkCanBuildFrom[Double]

  /**
   * The instance of `ChunkCanBuildFrom` for `Float`.
   */
  implicit val chunkCanBuildFromFloat: ChunkCanBuildFrom[Float] =
    ChunkCanBuildFrom[Float]

  /**
   * The instance of `ChunkCanBuildFrom` for `Int`.
   */
  implicit val chunkCanBuildFromInt: ChunkCanBuildFrom[Int] =
    ChunkCanBuildFrom[Int]

  /**
   * The instance of `ChunkCanBuildFrom` for `Long`.
   */
  implicit val chunkCanBuildFromLong: ChunkCanBuildFrom[Long] =
    ChunkCanBuildFrom[Long]

  /**
   * The instance of `ChunkCanBuildFrom` for `Short`.
   */
  implicit val chunkCanBuildFromShort: ChunkCanBuildFrom[Short] =
    ChunkCanBuildFrom[Short]
}
