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

package zio

import zio.Chunk.BitChunkByte
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.collection.mutable.{ArrayBuilder, Builder}
import scala.{
  Boolean => SBoolean,
  Byte => SByte,
  Char => SChar,
  Double => SDouble,
  Float => SFloat,
  Int => SInt,
  Long => SLong,
  Short => SShort
}

/**
 * A `ChunkBuilder[A]` can build a `Chunk[A]` given elements of type `A`.
 * `ChunkBuilder` is a mutable data structure that is implemented to efficiently
 * build chunks of unboxed primitives and for compatibility with the Scala
 * collection library. `result()` returns the elements added so far, and
 * elements added later do not change the returned chunk.
 *
 * On Scala 2.12, a builder stays usable after `result()`. On Scala 2.13 and
 * Scala 3, call `clear()` before using it again: when its internal array is
 * exactly full, `result()` hands that array to the returned chunk, and a later
 * `result()` or addition throws a `java.lang.NullPointerException` or, in a
 * builder of references, gives `null` in place of the earlier elements.
 */
sealed abstract class ChunkBuilder[A] extends Builder[A, Chunk[A]] { self =>
  /**
   * Adds the elements of `as` to this builder and returns the builder. The
   * same as `++=`.
   */
  def addAll(as: TraversableOnce[A]): this.type =
    self ++= as
  /**
   * Adds the element to this builder and returns the builder. The same as
   * `+=`.
   */
  def addOne(a: A): this.type =
    self += a
}

/**
 * Constructors for [[ChunkBuilder]], and the builders specialized for each
 * primitive type.
 */
object ChunkBuilder {

  /**
   * Constructs a generic `ChunkBuilder`, for any element type. `result()`
   * returns an empty chunk until an element is added.
   *
   * On the JVM and Scala Native, the builder picks its array type from the
   * first element added: a primitive value gives an array of that primitive,
   * and any other value an array of references. It switches to an array of
   * references when a later element has another type. On the JVM, a `null`
   * added after a primitive first element is stored as that primitive's zero
   * value (`0`, `0.0` or `false`). On Scala.js, it uses an array of
   * references.
   */
  def make[A](): ChunkBuilder[A] =
    new ChunkBuilder[A] {
      var arrayBuilder: ArrayBuilder[A] = null
      var size: SInt                    = -1
      def +=(a: A): this.type = {
        if (arrayBuilder eq null) {
          implicit val tag = Chunk.Tags.fromValue(a)
          arrayBuilder = ArrayBuilder.make()
          if (size != -1) {
            arrayBuilder.sizeHint(size)
          }
        }
        try {
          arrayBuilder += a
        } catch {
          case _: ClassCastException =>
            val as = arrayBuilder.result()
            arrayBuilder = ArrayBuilder.make[AnyRef].asInstanceOf[ArrayBuilder[A]]
            if (size != -1) {
              arrayBuilder.sizeHint(size)
            }
            arrayBuilder ++= as
            arrayBuilder += a
        }
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
   * Constructs a generic `ChunkBuilder` with size hint. The hint sets the
   * initial capacity and does not limit how many elements the builder
   * accepts.
   */
  def make[A](sizeHint: SInt): ChunkBuilder[A] = {
    val builder = make[A]()
    builder.sizeHint(sizeHint)
    builder
  }

  /**
   * A `ChunkBuilder` specialized for building chunks of unboxed `Boolean`
   * values. It packs eight values into each byte.
   */
  final class Boolean extends ChunkBuilder[SBoolean] { self =>

    private val arrayBuilder: ArrayBuilder.ofByte =
      new ArrayBuilder.ofByte
    private var lastByte: SByte   = 0.toByte
    private var maxBitIndex: SInt = 0

    /**
     * Adds the elements of `as` to this builder and returns the builder.
     */
    override def ++=(as: TraversableOnce[SBoolean]): this.type = {
      as.foreach(+= _)
      this
    }
    /**
     * Adds the element to this builder and returns the builder.
     */
    def +=(b: SBoolean): this.type = {
      if (b) {
        if (maxBitIndex == 8) {
          arrayBuilder += lastByte
          lastByte = (1 << 7).toByte
          maxBitIndex = 1
        } else {
          val bitIndex = 7 - maxBitIndex
          lastByte = (lastByte | (1 << bitIndex)).toByte
          maxBitIndex = maxBitIndex + 1
        }
      } else {
        if (maxBitIndex == 8) {
          arrayBuilder += lastByte
          lastByte = 0.toByte
          maxBitIndex = 1
        } else {
          maxBitIndex = maxBitIndex + 1
        }
      }
      this
    }
    /**
     * Adds the elements of `as` to this builder and returns the builder.
     */
    override def addAll(as: TraversableOnce[SBoolean]): this.type =
      self ++= as
    /**
     * Adds the element to this builder and returns the builder.
     */
    override def addOne(a: SBoolean): this.type =
      self += a
    /**
     * Removes the elements added so far, so `result()` returns an empty chunk
     * until more elements are added.
     */
    def clear(): Unit = {
      arrayBuilder.clear()
      maxBitIndex = 0
      lastByte = 0.toByte
    }
    /**
     * Returns `true` when `that` is a `ChunkBuilder.Boolean` with the same
     * internal byte buffer and the same bits in its last, partly filled byte.
     * Two separate builders that hold at most eight values each are equal when
     * their values are; with more than eight values they are not equal, even
     * when the values are the same. A builder that has held more than eight
     * values is not equal to a new one after `clear()`.
     */
    override def equals(that: Any): SBoolean =
      that match {
        case that: Boolean =>
          self.arrayBuilder.equals(that.arrayBuilder) &&
          self.maxBitIndex == that.maxBitIndex &&
          self.lastByte == that.lastByte
        case _ => false
      }
    /**
     * Returns a chunk of the values added so far, packed eight per byte. Values
     * added later do not change the returned chunk.
     *
     * On Scala 2.12, the builder stays usable after this call. On Scala 2.13
     * and Scala 3, call `clear()` before using it again: when its internal
     * array is exactly full, this call hands that array to the returned chunk,
     * and a later `result()` or addition throws a
     * `java.lang.NullPointerException`.
     */
    def result(): Chunk[SBoolean] = {
      val bytes: Chunk[SByte] = Chunk.fromArray(arrayBuilder.result() :+ lastByte)
      BitChunkByte(bytes, 0, 8 * (bytes.length - 1) + maxBitIndex)
    }
    /**
     * Reserves room for `n` values, packed eight per byte. It does not limit how
     * many values the builder accepts.
     */
    override def sizeHint(n: SInt): Unit = {
      val hint = if (n == 0) 0 else n / 8 + 1
      arrayBuilder.sizeHint(hint)
    }
    /**
     * Returns the name of this builder, `ChunkBuilder.Boolean`.
     */
    override def toString: String =
      "ChunkBuilder.Boolean"
  }

  /**
   * A `ChunkBuilder` specialized for building chunks of unboxed `Byte` values.
   */
  final class Byte extends ChunkBuilder[SByte] { self =>
    private val arrayBuilder: ArrayBuilder.ofByte =
      new ArrayBuilder.ofByte
    /**
     * Adds the elements of `as` to this builder and returns the builder.
     */
    override def ++=(as: TraversableOnce[SByte]): this.type = {
      arrayBuilder ++= as
      this
    }
    /**
     * Adds the element to this builder and returns the builder.
     */
    def +=(a: SByte): this.type = {
      arrayBuilder += a
      this
    }
    /**
     * Adds the elements of `as` to this builder and returns the builder.
     */
    override def addAll(as: TraversableOnce[SByte]): this.type =
      self ++= as
    /**
     * Adds the element to this builder and returns the builder.
     */
    override def addOne(a: SByte): this.type =
      self += a
    /**
     * Removes the elements added so far, so `result()` returns an empty chunk
     * until more elements are added.
     */
    def clear(): Unit =
      arrayBuilder.clear()
    /**
     * Returns `true` when `that` is a `ChunkBuilder.Byte` with the same
     * internal array buffer. Two builders that have had no calls made on them are equal, but two separate
     * builders that have been given elements are not, even when the elements
     * are the same or `clear()` has removed them.
     */
    override def equals(that: Any): SBoolean =
      that match {
        case that: Byte => self.arrayBuilder == that.arrayBuilder
        case _          => false
      }
    /**
     * Returns a chunk of the elements added so far. Elements added later do not
     * change the returned chunk.
     *
     * On Scala 2.12, the builder stays usable after this call. On Scala 2.13
     * and Scala 3, call `clear()` before using it again: when its internal
     * array is exactly full, this call hands that array to the returned chunk,
     * and a later `result()` or addition throws a
     * `java.lang.NullPointerException`.
     */
    def result(): Chunk[SByte] =
      Chunk.fromArray(arrayBuilder.result())
    /**
     * Reserves room for `n` elements. It does not limit how many elements the
     * builder accepts.
     */
    override def sizeHint(n: SInt): Unit =
      arrayBuilder.sizeHint(n)
    /**
     * Returns the name of this builder, `ChunkBuilder.Byte`.
     */
    override def toString: String =
      "ChunkBuilder.Byte"
  }

  /**
   * A `ChunkBuilder` specialized for building chunks of unboxed `Char` values.
   */
  final class Char extends ChunkBuilder[SChar] { self =>
    private val arrayBuilder: ArrayBuilder.ofChar =
      new ArrayBuilder.ofChar
    /**
     * Adds the elements of `as` to this builder and returns the builder.
     */
    override def ++=(as: TraversableOnce[SChar]): this.type = {
      arrayBuilder ++= as
      this
    }
    /**
     * Adds the element to this builder and returns the builder.
     */
    def +=(a: SChar): this.type = {
      arrayBuilder += a
      this
    }
    /**
     * Adds the elements of `as` to this builder and returns the builder.
     */
    override def addAll(as: TraversableOnce[SChar]): this.type =
      self ++= as
    /**
     * Adds the element to this builder and returns the builder.
     */
    override def addOne(a: SChar): this.type =
      self += a
    /**
     * Removes the elements added so far, so `result()` returns an empty chunk
     * until more elements are added.
     */
    def clear(): Unit =
      arrayBuilder.clear()
    /**
     * Returns `true` when `that` is a `ChunkBuilder.Char` with the same
     * internal array buffer. Two builders that have had no calls made on them are equal, but two separate
     * builders that have been given elements are not, even when the elements
     * are the same or `clear()` has removed them.
     */
    override def equals(that: Any): SBoolean =
      that match {
        case that: Char => self.arrayBuilder == that.arrayBuilder
        case _          => false
      }
    /**
     * Returns a chunk of the elements added so far. Elements added later do not
     * change the returned chunk.
     *
     * On Scala 2.12, the builder stays usable after this call. On Scala 2.13
     * and Scala 3, call `clear()` before using it again: when its internal
     * array is exactly full, this call hands that array to the returned chunk,
     * and a later `result()` or addition throws a
     * `java.lang.NullPointerException`.
     */
    def result(): Chunk[SChar] =
      Chunk.fromArray(arrayBuilder.result())
    /**
     * Reserves room for `n` elements. It does not limit how many elements the
     * builder accepts.
     */
    override def sizeHint(n: SInt): Unit =
      arrayBuilder.sizeHint(n)
    /**
     * Returns the name of this builder, `ChunkBuilder.Char`.
     */
    override def toString: String =
      "ChunkBuilder.Char"
  }

  /**
   * A `ChunkBuilder` specialized for building chunks of unboxed `Double`
   * values.
   */
  final class Double extends ChunkBuilder[SDouble] { self =>
    private val arrayBuilder: ArrayBuilder.ofDouble =
      new ArrayBuilder.ofDouble
    /**
     * Adds the elements of `as` to this builder and returns the builder.
     */
    override def ++=(as: TraversableOnce[SDouble]): this.type = {
      arrayBuilder ++= as
      this
    }
    /**
     * Adds the element to this builder and returns the builder.
     */
    def +=(a: SDouble): this.type = {
      arrayBuilder += a
      this
    }
    /**
     * Adds the elements of `as` to this builder and returns the builder.
     */
    override def addAll(as: TraversableOnce[SDouble]): this.type =
      self ++= as
    /**
     * Adds the element to this builder and returns the builder.
     */
    override def addOne(a: SDouble): this.type =
      self += a
    /**
     * Removes the elements added so far, so `result()` returns an empty chunk
     * until more elements are added.
     */
    def clear(): Unit =
      arrayBuilder.clear()
    /**
     * Returns a chunk of the elements added so far. Elements added later do not
     * change the returned chunk.
     *
     * On Scala 2.12, the builder stays usable after this call. On Scala 2.13
     * and Scala 3, call `clear()` before using it again: when its internal
     * array is exactly full, this call hands that array to the returned chunk,
     * and a later `result()` or addition throws a
     * `java.lang.NullPointerException`.
     */
    def result(): Chunk[SDouble] =
      Chunk.fromArray(arrayBuilder.result())
    /**
     * Returns `true` when `that` is a `ChunkBuilder.Double` with the same
     * internal array buffer. Two builders that have had no calls made on them are equal, but two separate
     * builders that have been given elements are not, even when the elements
     * are the same or `clear()` has removed them.
     */
    override def equals(that: Any): SBoolean =
      that match {
        case that: Double => self.arrayBuilder == that.arrayBuilder
        case _            => false
      }
    /**
     * Reserves room for `n` elements. It does not limit how many elements the
     * builder accepts.
     */
    override def sizeHint(n: SInt): Unit =
      arrayBuilder.sizeHint(n)
    /**
     * Returns the name of this builder, `ChunkBuilder.Double`.
     */
    override def toString: String =
      "ChunkBuilder.Double"
  }

  /**
   * A `ChunkBuilder` specialized for building chunks of unboxed `Float` values.
   */
  final class Float extends ChunkBuilder[SFloat] { self =>
    private val arrayBuilder: ArrayBuilder.ofFloat =
      new ArrayBuilder.ofFloat
    /**
     * Adds the elements of `as` to this builder and returns the builder.
     */
    override def ++=(as: TraversableOnce[SFloat]): this.type = {
      arrayBuilder ++= as
      this
    }
    /**
     * Adds the element to this builder and returns the builder.
     */
    def +=(a: SFloat): this.type = {
      arrayBuilder += a
      this
    }
    /**
     * Adds the elements of `as` to this builder and returns the builder.
     */
    override def addAll(as: TraversableOnce[SFloat]): this.type =
      self ++= as
    /**
     * Adds the element to this builder and returns the builder.
     */
    override def addOne(a: SFloat): this.type =
      self += a
    /**
     * Removes the elements added so far, so `result()` returns an empty chunk
     * until more elements are added.
     */
    def clear(): Unit =
      arrayBuilder.clear()
    /**
     * Returns `true` when `that` is a `ChunkBuilder.Float` with the same
     * internal array buffer. Two builders that have had no calls made on them are equal, but two separate
     * builders that have been given elements are not, even when the elements
     * are the same or `clear()` has removed them.
     */
    override def equals(that: Any): SBoolean =
      that match {
        case that: Float => self.arrayBuilder == that.arrayBuilder
        case _           => false
      }
    /**
     * Returns a chunk of the elements added so far. Elements added later do not
     * change the returned chunk.
     *
     * On Scala 2.12, the builder stays usable after this call. On Scala 2.13
     * and Scala 3, call `clear()` before using it again: when its internal
     * array is exactly full, this call hands that array to the returned chunk,
     * and a later `result()` or addition throws a
     * `java.lang.NullPointerException`.
     */
    def result(): Chunk[SFloat] =
      Chunk.fromArray(arrayBuilder.result())
    /**
     * Reserves room for `n` elements. It does not limit how many elements the
     * builder accepts.
     */
    override def sizeHint(n: SInt): Unit =
      arrayBuilder.sizeHint(n)
    /**
     * Returns the name of this builder, `ChunkBuilder.Float`.
     */
    override def toString: String =
      "ChunkBuilder.Float"
  }

  /**
   * A `ChunkBuilder` specialized for building chunks of unboxed `Int` values.
   */
  final class Int extends ChunkBuilder[SInt] { self =>
    private val arrayBuilder: ArrayBuilder.ofInt =
      new ArrayBuilder.ofInt
    /**
     * Adds the elements of `as` to this builder and returns the builder.
     */
    override def ++=(as: TraversableOnce[SInt]): this.type = {
      arrayBuilder ++= as
      this
    }
    /**
     * Adds the element to this builder and returns the builder.
     */
    def +=(a: SInt): this.type = {
      arrayBuilder += a
      this
    }
    /**
     * Adds the elements of `as` to this builder and returns the builder.
     */
    override def addAll(as: TraversableOnce[SInt]): this.type =
      self ++= as
    /**
     * Adds the element to this builder and returns the builder.
     */
    override def addOne(a: SInt): this.type =
      self += a
    /**
     * Removes the elements added so far, so `result()` returns an empty chunk
     * until more elements are added.
     */
    def clear(): Unit =
      arrayBuilder.clear()
    /**
     * Returns `true` when `that` is a `ChunkBuilder.Int` with the same
     * internal array buffer. Two builders that have had no calls made on them are equal, but two separate
     * builders that have been given elements are not, even when the elements
     * are the same or `clear()` has removed them.
     */
    override def equals(that: Any): SBoolean =
      that match {
        case that: Int => self.arrayBuilder == that.arrayBuilder
        case _         => false
      }
    /**
     * Returns a chunk of the elements added so far. Elements added later do not
     * change the returned chunk.
     *
     * On Scala 2.12, the builder stays usable after this call. On Scala 2.13
     * and Scala 3, call `clear()` before using it again: when its internal
     * array is exactly full, this call hands that array to the returned chunk,
     * and a later `result()` or addition throws a
     * `java.lang.NullPointerException`.
     */
    def result(): Chunk[SInt] =
      Chunk.fromArray(arrayBuilder.result())
    /**
     * Reserves room for `n` elements. It does not limit how many elements the
     * builder accepts.
     */
    override def sizeHint(n: SInt): Unit =
      arrayBuilder.sizeHint(n)
    /**
     * Returns the name of this builder, `ChunkBuilder.Int`.
     */
    override def toString: String =
      "ChunkBuilder.Int"
  }

  /**
   * A `ChunkBuilder` specialized for building chunks of unboxed `Long` values.
   */
  final class Long extends ChunkBuilder[SLong] { self =>
    private val arrayBuilder: ArrayBuilder.ofLong =
      new ArrayBuilder.ofLong
    /**
     * Adds the elements of `as` to this builder and returns the builder.
     */
    override def ++=(as: TraversableOnce[SLong]): this.type = {
      arrayBuilder ++= as
      this
    }
    /**
     * Adds the element to this builder and returns the builder.
     */
    def +=(a: SLong): this.type = {
      arrayBuilder += a
      this
    }
    /**
     * Adds the elements of `as` to this builder and returns the builder.
     */
    override def addAll(as: TraversableOnce[SLong]): this.type =
      self ++= as
    /**
     * Adds the element to this builder and returns the builder.
     */
    override def addOne(a: SLong): this.type =
      self += a
    /**
     * Removes the elements added so far, so `result()` returns an empty chunk
     * until more elements are added.
     */
    def clear(): Unit =
      arrayBuilder.clear()
    /**
     * Returns `true` when `that` is a `ChunkBuilder.Long` with the same
     * internal array buffer. Two builders that have had no calls made on them are equal, but two separate
     * builders that have been given elements are not, even when the elements
     * are the same or `clear()` has removed them.
     */
    override def equals(that: Any): SBoolean =
      that match {
        case that: Long => self.arrayBuilder == that.arrayBuilder
        case _          => false
      }
    /**
     * Returns a chunk of the elements added so far. Elements added later do not
     * change the returned chunk.
     *
     * On Scala 2.12, the builder stays usable after this call. On Scala 2.13
     * and Scala 3, call `clear()` before using it again: when its internal
     * array is exactly full, this call hands that array to the returned chunk,
     * and a later `result()` or addition throws a
     * `java.lang.NullPointerException`.
     */
    def result(): Chunk[SLong] =
      Chunk.fromArray(arrayBuilder.result())
    /**
     * Reserves room for `n` elements. It does not limit how many elements the
     * builder accepts.
     */
    override def sizeHint(n: SInt): Unit =
      arrayBuilder.sizeHint(n)
    /**
     * Returns the name of this builder, `ChunkBuilder.Long`.
     */
    override def toString: String =
      "ChunkBuilder.Long"
  }

  /**
   * A `ChunkBuilder` specialized for building chunks of unboxed `Short` values.
   */
  final class Short extends ChunkBuilder[SShort] { self =>
    private val arrayBuilder: ArrayBuilder.ofShort =
      new ArrayBuilder.ofShort
    /**
     * Adds the elements of `as` to this builder and returns the builder.
     */
    override def ++=(as: TraversableOnce[SShort]): this.type = {
      arrayBuilder ++= as
      this
    }
    /**
     * Adds the element to this builder and returns the builder.
     */
    def +=(a: SShort): this.type = {
      arrayBuilder += a
      this
    }
    /**
     * Adds the elements of `as` to this builder and returns the builder.
     */
    override def addAll(as: TraversableOnce[SShort]): this.type =
      self ++= as
    /**
     * Adds the element to this builder and returns the builder.
     */
    override def addOne(a: SShort): this.type =
      self += a
    /**
     * Removes the elements added so far, so `result()` returns an empty chunk
     * until more elements are added.
     */
    def clear(): Unit =
      arrayBuilder.clear()
    /**
     * Returns `true` when `that` is a `ChunkBuilder.Short` with the same
     * internal array buffer. Two builders that have had no calls made on them are equal, but two separate
     * builders that have been given elements are not, even when the elements
     * are the same or `clear()` has removed them.
     */
    override def equals(that: Any): SBoolean =
      that match {
        case that: Short => self.arrayBuilder == that.arrayBuilder
        case _           => false
      }
    /**
     * Returns a chunk of the elements added so far. Elements added later do not
     * change the returned chunk.
     *
     * On Scala 2.12, the builder stays usable after this call. On Scala 2.13
     * and Scala 3, call `clear()` before using it again: when its internal
     * array is exactly full, this call hands that array to the returned chunk,
     * and a later `result()` or addition throws a
     * `java.lang.NullPointerException`.
     */
    def result(): Chunk[SShort] =
      Chunk.fromArray(arrayBuilder.result())
    /**
     * Reserves room for `n` elements. It does not limit how many elements the
     * builder accepts.
     */
    override def sizeHint(n: SInt): Unit =
      arrayBuilder.sizeHint(n)
    /**
     * Returns the name of this builder, `ChunkBuilder.Short`.
     */
    override def toString: String =
      "ChunkBuilder.Short"
  }
}
