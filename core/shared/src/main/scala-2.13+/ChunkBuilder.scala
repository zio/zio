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
import scala.{
  Boolean => SBoolean,
  Short => SShort,
  Int => SInt,
  Double => SDouble,
  Float => SFloat,
  Long => SLong,
  Char => SChar,
  Byte => SByte
}

/**
 * A `ChunkBuilder[A]` can build a `Chunk[A]` given elements of type `A`.
 * `ChunkBuilder` is a mutable data structure that is implemented to
 * efficiently build chunks of unboxed primitives and for compatibility with
 * the Scala collection library.
 */
private[zio] sealed trait ChunkBuilder[A] extends Builder[A, Chunk[A]]

private[zio] object ChunkBuilder {

  /**
   * Constructs a generic `ChunkBuilder`.
   */
  def make[A]: ChunkBuilder[A] =
    new ChunkBuilder[A] {
      var arrayBuilder: ArrayBuilder[A] = null
      var size: SInt                    = -1
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
      override def sizeHint(n: SInt): Unit =
        if (arrayBuilder eq null) {
          size = n
        } else {
          arrayBuilder.sizeHint(n)
        }
    }

  /**
   * A `ChunkBuilder` specialized for building chunks of unboxed `Boolean`
   * values.
   */
  final class Boolean extends ChunkBuilder[SBoolean] { self =>
    private val arrayBuilder: ArrayBuilder[SBoolean] = {
      new ArrayBuilder.ofBoolean
    }
    def addOne(a: SBoolean): this.type = {
      arrayBuilder += a
      this
    }
    def clear(): Unit =
      arrayBuilder.clear()
    def result(): Chunk[SBoolean] =
      Chunk.fromArray(arrayBuilder.result())
    override def addAll(as: IterableOnce[SBoolean]): this.type = {
      arrayBuilder ++= as
      this
    }
    override def equals(that: Any): SBoolean =
      that match {
        case that: Boolean => self.arrayBuilder == that.arrayBuilder
        case _             => false
      }
    override def sizeHint(n: SInt): Unit =
      arrayBuilder.sizeHint(n)
    override def toString: String =
      "ChunkBuilder.Boolean"
  }

  /**
   * A `ChunkBuilder` specialized for building chunks of unboxed `Byte` values.
   */
  final class Byte extends ChunkBuilder[SByte] { self =>
    private val arrayBuilder: ArrayBuilder[SByte] = {
      new ArrayBuilder.ofByte
    }
    def addOne(a: SByte): this.type = {
      arrayBuilder += a
      this
    }
    def clear(): Unit =
      arrayBuilder.clear()
    def result(): Chunk[SByte] =
      Chunk.fromArray(arrayBuilder.result())
    override def addAll(as: IterableOnce[SByte]): this.type = {
      arrayBuilder ++= as
      this
    }
    override def equals(that: Any): SBoolean =
      that match {
        case that: Byte => self.arrayBuilder == that.arrayBuilder
        case _          => false
      }
    override def sizeHint(n: SInt): Unit =
      arrayBuilder.sizeHint(n)
    override def toString: String =
      "ChunkBuilder.Byte"
  }

  /**
   * A `ChunkBuilder` specialized for building chunks of unboxed `Char` values.
   */
  final class Char extends ChunkBuilder[SChar] { self =>
    private val arrayBuilder: ArrayBuilder[SChar] = {
      new ArrayBuilder.ofChar
    }
    def addOne(a: SChar): this.type = {
      arrayBuilder += a
      this
    }
    def clear(): Unit =
      arrayBuilder.clear()
    def result(): Chunk[SChar] =
      Chunk.fromArray(arrayBuilder.result())
    override def addAll(as: IterableOnce[SChar]): this.type = {
      arrayBuilder ++= as
      this
    }
    override def equals(that: Any): SBoolean =
      that match {
        case that: Char => self.arrayBuilder == that.arrayBuilder
        case _          => false
      }
    override def sizeHint(n: SInt): Unit =
      arrayBuilder.sizeHint(n)
    override def toString: String =
      "ChunkBuilder.Char"
  }

  /**
   * A `ChunkBuilder` specialized for building chunks of unboxed `Double`
   * values.
   */
  final class Double extends ChunkBuilder[SDouble] { self =>
    private val arrayBuilder: ArrayBuilder[SDouble] = {
      new ArrayBuilder.ofDouble
    }
    def addOne(a: SDouble): this.type = {
      arrayBuilder += a
      this
    }
    def clear(): Unit =
      arrayBuilder.clear()
    def result(): Chunk[SDouble] =
      Chunk.fromArray(arrayBuilder.result())
    override def addAll(as: IterableOnce[SDouble]): this.type = {
      arrayBuilder ++= as
      this
    }
    override def equals(that: Any): SBoolean =
      that match {
        case that: Double => self.arrayBuilder == that.arrayBuilder
        case _            => false
      }
    override def sizeHint(n: SInt): Unit =
      arrayBuilder.sizeHint(n)
    override def toString: String =
      "ChunkBuilder.Double"
  }

  /**
   * A `ChunkBuilder` specialized for building chunks of unboxed `Float`
   * values.
   */
  final class Float extends ChunkBuilder[SFloat] { self =>
    private val arrayBuilder: ArrayBuilder[SFloat] = {
      new ArrayBuilder.ofFloat
    }
    def addOne(a: SFloat): this.type = {
      arrayBuilder += a
      this
    }
    def clear(): Unit =
      arrayBuilder.clear()
    def result(): Chunk[SFloat] =
      Chunk.fromArray(arrayBuilder.result())
    override def addAll(as: IterableOnce[SFloat]): this.type = {
      arrayBuilder ++= as
      this
    }
    override def equals(that: Any): SBoolean =
      that match {
        case that: Float => self.arrayBuilder == that.arrayBuilder
        case _           => false
      }
    override def sizeHint(n: SInt): Unit =
      arrayBuilder.sizeHint(n)
    override def toString: String =
      "ChunkBuilder.Float"
  }

  /**
   * A `ChunkBuilder` specialized for building chunks of unboxed `Int` values.
   */
  final class Int extends ChunkBuilder[SInt] { self =>
    private val arrayBuilder: ArrayBuilder[SInt] = {
      new ArrayBuilder.ofInt
    }
    def addOne(a: SInt): this.type = {
      arrayBuilder += a
      this
    }
    def clear(): Unit =
      arrayBuilder.clear()
    def result(): Chunk[SInt] =
      Chunk.fromArray(arrayBuilder.result())
    override def addAll(as: IterableOnce[SInt]): this.type = {
      arrayBuilder ++= as
      this
    }
    override def equals(that: Any): SBoolean =
      that match {
        case that: Int => self.arrayBuilder == that.arrayBuilder
        case _         => false
      }
    override def sizeHint(n: SInt): Unit =
      arrayBuilder.sizeHint(n)
    override def toString: String =
      "ChunkBuilder.Int"
  }

  /**
   * A `ChunkBuilder` specialized for building chunks of unboxed `Long` values.
   */
  final class Long extends ChunkBuilder[SLong] { self =>
    private val arrayBuilder: ArrayBuilder[SLong] = {
      new ArrayBuilder.ofLong
    }
    def addOne(a: SLong): this.type = {
      arrayBuilder += a
      this
    }
    def clear(): Unit =
      arrayBuilder.clear()
    def result(): Chunk[SLong] =
      Chunk.fromArray(arrayBuilder.result())
    override def addAll(as: IterableOnce[SLong]): this.type = {
      arrayBuilder ++= as
      this
    }
    override def equals(that: Any): SBoolean =
      that match {
        case that: Long => self.arrayBuilder == that.arrayBuilder
        case _          => false
      }
    override def sizeHint(n: SInt): Unit =
      arrayBuilder.sizeHint(n)
    override def toString: String =
      "ChunkBuilder.Long"
  }

  /**
   * A `ChunkBuilder` specialized for building chunks of unboxed `Short`
   * values.
   */
  final class Short extends ChunkBuilder[SShort] { self =>
    private val arrayBuilder: ArrayBuilder[SShort] = {
      new ArrayBuilder.ofShort
    }
    def addOne(a: SShort): this.type = {
      arrayBuilder += a
      this
    }
    def clear(): Unit =
      arrayBuilder.clear()
    def result(): Chunk[SShort] =
      Chunk.fromArray(arrayBuilder.result())
    override def addAll(as: IterableOnce[SShort]): this.type = {
      arrayBuilder ++= as
      this
    }
    override def equals(that: Any): SBoolean =
      that match {
        case that: Short => self.arrayBuilder == that.arrayBuilder
        case _           => false
      }
    override def sizeHint(n: SInt): Unit =
      arrayBuilder.sizeHint(n)
    override def toString: String =
      "ChunkBuilder.Short"
  }
}
