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

import scala.collection.mutable.{ ArrayBuilder, Builder }

/**
 * A `ChunkBuilder[A]` can build a `Chunk[A]` given elements of type `A`.
 * `ChunkBuilder` is a mutable data structure that is implemented purely for
 * compatibility with Scala's collection library and should not be used for
 * other purposes.
 *
 * Its implementation wraps an `ArrayBuilder`, delegating all operations to
 * it. Because we need a `ClassTag` to construct an `Array` but cannot require
 * one, the implementation defers the creation of the underlying
 * `ArrayBuilder` until the first element is added, using the `ClassTag` of
 * that element as the `ClassTag` of the `ArrayBuilder`. This is similar to
 * the approach in `Chunk`.
 */
private[zio] trait ChunkBuilder[A] extends Builder[A, Chunk[A]]

private[zio] object ChunkBuilder {

  /**
   * Constructs a new `ChunkBuilder`.
   */
  def make[A]: ChunkBuilder[A] =
    new ChunkBuilder[A] {
      var arrayBuilder: ArrayBuilder[A] = null
      var size: Int                     = -1
      def addOne(a: A): this.type = {
        if (arrayBuilder eq null) {
          implicit val tag = Chunk.Tags.fromValue(a)
          arrayBuilder = ArrayBuilder.make
          if (size != -1) {
            arrayBuilder.sizeHint(size)
          }
        }
        arrayBuilder.addOne(a)
        this
      }
      def clear(): Unit =
        if (arrayBuilder ne null) {
          arrayBuilder.clear()
        }
      def result(): Chunk[A] =
        if (arrayBuilder eq null) {
          Chunk.empty
        } else {
          Chunk.fromArray(arrayBuilder.result)
        }
      override def sizeHint(n: Int): Unit =
        if (arrayBuilder eq null) {
          size = n
        } else {
          arrayBuilder.sizeHint(n)
        }
    }
}
