/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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

import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.UUID
import scala.annotation.tailrec

/**
 * A service that generates pseudo-random values: numbers, booleans, bytes,
 * strings, UUIDs, and shuffled collections. Each operation returns an effect
 * that draws from the generator when it runs, so the generator can be swapped:
 * `ZIO.withRandom` runs a workflow with another implementation, and
 * `TestRandom` from ZIO Test replaces it in tests with a seeded generator or
 * with fed values.
 *
 * The default implementation is [[Random.RandomLive]], which uses the global
 * `scala.util.Random`. [[Random.RandomScala]] wraps a `scala.util.Random`
 * instance of your own, for example one created with a fixed seed.
 *
 * {{{
 * val roll: UIO[Int] = Random.nextIntBetween(1, 7)
 *
 * val seededRolls: UIO[List[Int]] =
 *   ZIO.withRandom(Random.RandomScala(new scala.util.Random(42L))) {
 *     ZIO.foreach(List(1, 2, 3, 4, 5))(_ => Random.nextIntBetween(1, 7))
 *   }
 * // List(3, 4, 1, 3, 1)
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/services/random/ Random]]
 */
trait Random extends Serializable { self =>

  /**
   * Generates a pseudo-random `Boolean`.
   */
  def nextBoolean(implicit trace: Trace): UIO[Boolean]

  /**
   * Generates a `Chunk` of `length` pseudo-random bytes.
   *
   * With [[Random.RandomLive]] and [[Random.RandomScala]], a `length` of 0
   * gives an empty `Chunk`. On the JVM, a negative `length` makes the effect
   * die with a `java.lang.NegativeArraySizeException`.
   */
  def nextBytes(length: => Int)(implicit trace: Trace): UIO[Chunk[Byte]]

  /**
   * Generates a pseudo-random `Double`, uniformly distributed from 0.0
   * (inclusive) to 1.0 (exclusive).
   */
  def nextDouble(implicit trace: Trace): UIO[Double]

  /**
   * Generates a pseudo-random `Double` from `minInclusive` (inclusive) to
   * `maxExclusive` (exclusive).
   *
   * With [[Random.RandomLive]] and [[Random.RandomScala]], the effect dies with
   * a `java.lang.IllegalArgumentException` (message `invalid bounds`) when
   * `minInclusive >= maxExclusive`. When `maxExclusive - minInclusive`
   * overflows to infinity, as for `-Double.MaxValue` to `Double.MaxValue`,
   * every result is the largest `Double` below `maxExclusive`.
   */
  def nextDoubleBetween(minInclusive: => Double, maxExclusive: => Double)(implicit trace: Trace): UIO[Double]

  /**
   * Generates a pseudo-random `Float`, uniformly distributed from 0.0
   * (inclusive) to 1.0 (exclusive).
   */
  def nextFloat(implicit trace: Trace): UIO[Float]

  /**
   * Generates a pseudo-random `Float` from `minInclusive` (inclusive) to
   * `maxExclusive` (exclusive).
   *
   * With [[Random.RandomLive]] and [[Random.RandomScala]], the effect dies with
   * a `java.lang.IllegalArgumentException` (message `invalid bounds`) when
   * `minInclusive >= maxExclusive`. When `maxExclusive - minInclusive`
   * overflows to infinity, as for `Float.MinValue` to `Float.MaxValue`, every
   * result is the largest `Float` below `maxExclusive`.
   */
  def nextFloatBetween(minInclusive: => Float, maxExclusive: => Float)(implicit trace: Trace): UIO[Float]

  /**
   * Generates a pseudo-random `Double` from a normal distribution with mean 0.0
   * and standard deviation 1.0.
   */
  def nextGaussian(implicit trace: Trace): UIO[Double]

  /**
   * Generates a pseudo-random `Int` from the whole `Int` range.
   */
  def nextInt(implicit trace: Trace): UIO[Int]

  /**
   * Generates a pseudo-random `Int` from `minInclusive` (inclusive) to
   * `maxExclusive` (exclusive). The range may be as wide as `Int.MinValue` to
   * `Int.MaxValue`.
   *
   * With [[Random.RandomLive]] and [[Random.RandomScala]], the effect dies with
   * a `java.lang.IllegalArgumentException` (message `invalid bounds`) when
   * `minInclusive >= maxExclusive`.
   */
  def nextIntBetween(minInclusive: => Int, maxExclusive: => Int)(implicit trace: Trace): UIO[Int]

  /**
   * Generates a pseudo-random `Int` from 0 (inclusive) to `n` (exclusive).
   *
   * With [[Random.RandomLive]] and [[Random.RandomScala]], the effect dies with
   * a `java.lang.IllegalArgumentException` when `n` is 0 or negative.
   */
  def nextIntBounded(n: => Int)(implicit trace: Trace): UIO[Int]

  /**
   * Generates a pseudo-random `Long` from the whole `Long` range.
   */
  def nextLong(implicit trace: Trace): UIO[Long]

  /**
   * Generates a pseudo-random `Long` from `minInclusive` (inclusive) to
   * `maxExclusive` (exclusive). The range may be as wide as `Long.MinValue` to
   * `Long.MaxValue`.
   *
   * With [[Random.RandomLive]] and [[Random.RandomScala]], the effect dies with
   * a `java.lang.IllegalArgumentException` (message `invalid bounds`) when
   * `minInclusive >= maxExclusive`.
   */
  def nextLongBetween(minInclusive: => Long, maxExclusive: => Long)(implicit trace: Trace): UIO[Long]

  /**
   * Generates a pseudo-random `Long` from 0 (inclusive) to `n` (exclusive).
   *
   * With [[Random.RandomLive]] and [[Random.RandomScala]], the effect dies with
   * a `java.lang.IllegalArgumentException` (message `n must be positive`) when
   * `n` is 0 or negative.
   */
  def nextLongBounded(n: => Long)(implicit trace: Trace): UIO[Long]

  /**
   * Generates a pseudo-random printable ASCII character, from code 33 (`!`) to
   * code 126 (`~`).
   */
  def nextPrintableChar(implicit trace: Trace): UIO[Char]

  /**
   * Generates a pseudo-random `String` of `length` characters.
   *
   * With [[Random.RandomLive]] and [[Random.RandomScala]], each character is
   * between U+0001 and U+D7FF, so the string holds no surrogate characters, and
   * a `length` of 0 or less gives an empty string.
   */
  def nextString(length: => Int)(implicit trace: Trace): UIO[String]

  /**
   * Generates a pseudo-random version 4 `java.util.UUID` (IETF variant).
   *
   * [[Random.RandomLive]] and [[Random.RandomScala]] build it from two
   * `nextLong` values of the same generator, so a seeded generator gives a
   * repeatable sequence of UUIDs.
   */
  def nextUUID(implicit trace: Trace): UIO[UUID]

  /**
   * Sets the seed of the generator. The values generated after it follow a
   * repeatable sequence, as long as nothing else draws from the same generator
   * in between.
   *
   * For [[Random.RandomLive]] this reseeds the global `scala.util.Random`,
   * which is shared with all code in the process that calls the
   * `scala.util.Random` object directly. For [[Random.RandomScala]] it reseeds
   * the wrapped instance.
   */
  def setSeed(seed: => Long)(implicit trace: Trace): UIO[Unit]

  /**
   * Returns the elements of `collection` in a pseudo-random order, in a
   * collection of the same type, for example a `List` or a `Vector`. An empty
   * collection gives an empty collection.
   *
   * [[Random.RandomLive]] and [[Random.RandomScala]] use a Fisher-Yates shuffle
   * that draws each swap position from `nextIntBounded`.
   */
  def shuffle[A, Collection[+Element] <: Iterable[Element]](collection: => Collection[A])(implicit
    bf: BuildFrom[Collection[A], A, Collection[A]],
    trace: Trace
  ): UIO[Collection[A]]

  /**
   * Synchronous versions of the `Random` operations, for code that runs outside
   * ZIO effects. Each method generates its value as a side effect when it is
   * called, and needs an implicit `Unsafe` value, which `Unsafe.unsafe {
   * implicit unsafe => ... }` provides. Errors are thrown as exceptions instead
   * of being returned in an effect.
   */
  trait UnsafeAPI extends Serializable {

    /**
     * Returns a pseudo-random `Boolean`.
     */
    def nextBoolean()(implicit unsafe: Unsafe): Boolean

    /**
     * Returns a `Chunk` of `length` pseudo-random bytes. [[Random.RandomLive]]
     * and [[Random.RandomScala]] throw an exception when `length` is negative.
     */
    def nextBytes(length: Int)(implicit unsafe: Unsafe): Chunk[Byte]

    /**
     * Returns a pseudo-random `Double` from 0.0 (inclusive) to 1.0 (exclusive).
     */
    def nextDouble()(implicit unsafe: Unsafe): Double

    /**
     * Returns a pseudo-random `Double` from `minInclusive` (inclusive) to
     * `maxExclusive` (exclusive). [[Random.RandomLive]] and
     * [[Random.RandomScala]] throw an exception when `minInclusive >=
     * maxExclusive`.
     */
    def nextDoubleBetween(minInclusive: Double, maxExclusive: Double)(implicit unsafe: Unsafe): Double

    /**
     * Returns a pseudo-random `Float` from 0.0 (inclusive) to 1.0 (exclusive).
     */
    def nextFloat()(implicit unsafe: Unsafe): Float

    /**
     * Returns a pseudo-random `Float` from `minInclusive` (inclusive) to
     * `maxExclusive` (exclusive). [[Random.RandomLive]] and
     * [[Random.RandomScala]] throw an exception when `minInclusive >=
     * maxExclusive`.
     */
    def nextFloatBetween(minInclusive: Float, maxExclusive: Float)(implicit unsafe: Unsafe): Float

    /**
     * Returns a pseudo-random `Double` from a normal distribution with mean 0.0
     * and standard deviation 1.0.
     */
    def nextGaussian()(implicit unsafe: Unsafe): Double

    /**
     * Returns a pseudo-random `Int` from the whole `Int` range.
     */
    def nextInt()(implicit unsafe: Unsafe): Int

    /**
     * Returns a pseudo-random `Int` from `minInclusive` (inclusive) to
     * `maxExclusive` (exclusive). [[Random.RandomLive]] and
     * [[Random.RandomScala]] throw an exception when `minInclusive >=
     * maxExclusive`.
     */
    def nextIntBetween(minInclusive: Int, maxExclusive: Int)(implicit unsafe: Unsafe): Int

    /**
     * Returns a pseudo-random `Int` from 0 (inclusive) to `n` (exclusive).
     * [[Random.RandomLive]] and [[Random.RandomScala]] throw an exception when
     * `n` is 0 or negative.
     */
    def nextIntBounded(n: Int)(implicit unsafe: Unsafe): Int

    /**
     * Returns a pseudo-random `Long` from the whole `Long` range.
     */
    def nextLong()(implicit unsafe: Unsafe): Long

    /**
     * Returns a pseudo-random `Long` from `minInclusive` (inclusive) to
     * `maxExclusive` (exclusive). [[Random.RandomLive]] and
     * [[Random.RandomScala]] throw an exception when `minInclusive >=
     * maxExclusive`.
     */
    def nextLongBetween(minInclusive: Long, maxExclusive: Long)(implicit unsafe: Unsafe): Long

    /**
     * Returns a pseudo-random `Long` from 0 (inclusive) to `n` (exclusive).
     * [[Random.RandomLive]] and [[Random.RandomScala]] throw an exception when
     * `n` is 0 or negative.
     */
    def nextLongBounded(n: Long)(implicit unsafe: Unsafe): Long

    /**
     * Returns a pseudo-random printable ASCII character, from code 33 (`!`) to
     * code 126 (`~`).
     */
    def nextPrintableChar()(implicit unsafe: Unsafe): Char

    /**
     * Returns a pseudo-random `String` of `length` characters.
     */
    def nextString(length: Int)(implicit unsafe: Unsafe): String

    /**
     * Returns a pseudo-random version 4 `java.util.UUID`.
     */
    def nextUUID()(implicit unsafe: Unsafe): UUID

    /**
     * Sets the seed of the generator.
     */
    def setSeed(seed: Long)(implicit unsafe: Unsafe): Unit

    /**
     * Returns the elements of `collection` in a pseudo-random order, in a
     * collection of the same type.
     */
    def shuffle[A, Collection[+Element] <: Iterable[Element]](
      collection: Collection[A]
    )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], unsafe: Unsafe): Collection[A]
  }

  /**
   * Returns the synchronous `UnsafeAPI` of this service.
   *
   * The default implementation runs each operation of this service as an effect
   * on `Runtime.default` and throws a `zio.FiberFailure` when the effect fails
   * or dies. [[Random.RandomLive]] and [[Random.RandomScala]] override it to
   * call their `scala.util.Random` directly, so they throw the original
   * exception, such as a `java.lang.IllegalArgumentException`.
   */
  def unsafe: UnsafeAPI =
    new UnsafeAPI {
      def nextBoolean()(implicit unsafe: Unsafe): Boolean =
        Runtime.default.unsafe.run(self.nextBoolean(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def nextBytes(length: Int)(implicit unsafe: Unsafe): Chunk[Byte] =
        Runtime.default.unsafe.run(self.nextBytes(length)(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def nextDouble()(implicit unsafe: Unsafe): Double =
        Runtime.default.unsafe.run(self.nextDouble(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def nextDoubleBetween(minInclusive: Double, maxExclusive: Double)(implicit
        unsafe: Unsafe
      ): Double =
        Runtime.default.unsafe
          .run(self.nextDoubleBetween(minInclusive, maxExclusive)(Trace.empty))(Trace.empty, unsafe)
          .getOrThrowFiberFailure()

      def nextFloat()(implicit unsafe: Unsafe): Float =
        Runtime.default.unsafe.run(self.nextFloat(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def nextFloatBetween(minInclusive: Float, maxExclusive: Float)(implicit
        unsafe: Unsafe
      ): Float =
        Runtime.default.unsafe
          .run(self.nextFloatBetween(minInclusive, maxExclusive)(Trace.empty))(Trace.empty, unsafe)
          .getOrThrowFiberFailure()

      def nextGaussian()(implicit unsafe: Unsafe): Double =
        Runtime.default.unsafe.run(self.nextGaussian(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def nextInt()(implicit unsafe: Unsafe): Int =
        Runtime.default.unsafe.run(self.nextInt(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def nextIntBetween(minInclusive: Int, maxExclusive: Int)(implicit unsafe: Unsafe): Int =
        Runtime.default.unsafe
          .run(self.nextIntBetween(minInclusive, maxExclusive)(Trace.empty))(Trace.empty, unsafe)
          .getOrThrowFiberFailure()

      def nextIntBounded(n: Int)(implicit unsafe: Unsafe): Int =
        Runtime.default.unsafe.run(self.nextIntBounded(n)(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def nextLong()(implicit unsafe: Unsafe): Long =
        Runtime.default.unsafe.run(self.nextLong(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def nextLongBetween(minInclusive: Long, maxExclusive: Long)(implicit unsafe: Unsafe): Long =
        Runtime.default.unsafe
          .run(self.nextLongBetween(minInclusive, maxExclusive)(Trace.empty))(Trace.empty, unsafe)
          .getOrThrowFiberFailure()

      def nextLongBounded(n: Long)(implicit unsafe: Unsafe): Long =
        Runtime.default.unsafe.run(self.nextLongBounded(n)(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def nextPrintableChar()(implicit unsafe: Unsafe): Char =
        Runtime.default.unsafe.run(self.nextPrintableChar(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def nextString(length: Int)(implicit unsafe: Unsafe): String =
        Runtime.default.unsafe.run(self.nextString(length)(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def nextUUID()(implicit unsafe: Unsafe): UUID =
        Runtime.default.unsafe.run(self.nextUUID(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def setSeed(seed: Long)(implicit unsafe: Unsafe): Unit =
        Runtime.default.unsafe.run(self.setSeed(seed)(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def shuffle[A, Collection[+Element] <: Iterable[Element]](collection: Collection[A])(implicit
        bf: BuildFrom[Collection[A], A, Collection[A]],
        unsafe: Unsafe
      ): Collection[A] =
        Runtime.default.unsafe
          .run(self.shuffle(collection)(bf, Trace.empty))(Trace.empty, unsafe)
          .getOrThrowFiberFailure()
    }
}

/**
 * Accessors that run each `Random` operation with the `Random` service of the
 * current fiber, and the built-in implementations [[Random.RandomLive]] and
 * [[Random.RandomScala]].
 *
 * @see
 *   [[https://zio.dev/reference/services/random/ Random]]
 */
object Random extends Serializable {

  /**
   * The `Tag` of the `Random` service, used to look it up in a `ZEnvironment`.
   */
  implicit val tag: Tag[Random] = Tag(EnvironmentTag.tagFromTagMacro[Random])

  /**
   * The default `Random` service, which every ZIO runtime provides unless a
   * workflow installs another one. It uses the global `scala.util.Random`
   * object, so its state is shared with all code in the process that uses
   * `scala.util.Random` directly, and `setSeed` reseeds that shared generator.
   */
  object RandomLive extends Random {

    /**
     * Generates a pseudo-random `Boolean` with the global `scala.util.Random`.
     */
    def nextBoolean(implicit trace: Trace): UIO[Boolean] =
      ZIO.succeed(unsafe.nextBoolean()(Unsafe.unsafe))

    /**
     * Generates a `Chunk` of `length` pseudo-random bytes with the global
     * `scala.util.Random`. On the JVM, a negative `length` makes the effect die
     * with a `java.lang.NegativeArraySizeException`.
     */
    def nextBytes(length: => Int)(implicit trace: Trace): UIO[Chunk[Byte]] =
      ZIO.succeed(unsafe.nextBytes(length)(Unsafe.unsafe))

    /**
     * Generates a pseudo-random `Double` from 0.0 (inclusive) to 1.0
     * (exclusive) with the global `scala.util.Random`.
     */
    def nextDouble(implicit trace: Trace): UIO[Double] =
      ZIO.succeed(unsafe.nextDouble()(Unsafe.unsafe))

    /**
     * Generates a pseudo-random `Double` from `minInclusive` (inclusive) to
     * `maxExclusive` (exclusive) with the global `scala.util.Random`. Dies with
     * a `java.lang.IllegalArgumentException` (message `invalid bounds`) when
     * `minInclusive >= maxExclusive`.
     */
    def nextDoubleBetween(minInclusive: => Double, maxExclusive: => Double)(implicit
      trace: Trace
    ): UIO[Double] =
      ZIO.succeed(unsafe.nextDoubleBetween(minInclusive, maxExclusive)(Unsafe.unsafe))

    /**
     * Generates a pseudo-random `Float` from 0.0 (inclusive) to 1.0 (exclusive)
     * with the global `scala.util.Random`.
     */
    def nextFloat(implicit trace: Trace): UIO[Float] =
      ZIO.succeed(unsafe.nextFloat()(Unsafe.unsafe))

    /**
     * Generates a pseudo-random `Float` from `minInclusive` (inclusive) to
     * `maxExclusive` (exclusive) with the global `scala.util.Random`. Dies with
     * a `java.lang.IllegalArgumentException` (message `invalid bounds`) when
     * `minInclusive >= maxExclusive`.
     */
    def nextFloatBetween(minInclusive: => Float, maxExclusive: => Float)(implicit trace: Trace): UIO[Float] =
      ZIO.succeed(unsafe.nextFloatBetween(minInclusive, maxExclusive)(Unsafe.unsafe))

    /**
     * Generates a pseudo-random `Double` from a normal distribution with mean
     * 0.0 and standard deviation 1.0, with the global `scala.util.Random`.
     */
    def nextGaussian(implicit trace: Trace): UIO[Double] =
      ZIO.succeed(unsafe.nextGaussian()(Unsafe.unsafe))

    /**
     * Generates a pseudo-random `Int` from the whole `Int` range with the
     * global `scala.util.Random`.
     */
    def nextInt(implicit trace: Trace): UIO[Int] =
      ZIO.succeed(unsafe.nextInt()(Unsafe.unsafe))

    /**
     * Generates a pseudo-random `Int` from `minInclusive` (inclusive) to
     * `maxExclusive` (exclusive) with the global `scala.util.Random`. Dies with
     * a `java.lang.IllegalArgumentException` (message `invalid bounds`) when
     * `minInclusive >= maxExclusive`.
     */
    def nextIntBetween(minInclusive: => Int, maxExclusive: => Int)(implicit trace: Trace): UIO[Int] =
      ZIO.succeed(unsafe.nextIntBetween(minInclusive, maxExclusive)(Unsafe.unsafe))

    /**
     * Generates a pseudo-random `Int` from 0 (inclusive) to `n` (exclusive)
     * with the global `scala.util.Random`. Dies with a
     * `java.lang.IllegalArgumentException` when `n` is 0 or negative.
     */
    def nextIntBounded(n: => Int)(implicit trace: Trace): UIO[Int] =
      ZIO.succeed(unsafe.nextIntBounded(n)(Unsafe.unsafe))

    /**
     * Generates a pseudo-random `Long` from the whole `Long` range with the
     * global `scala.util.Random`.
     */
    def nextLong(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(unsafe.nextLong()(Unsafe.unsafe))

    /**
     * Generates a pseudo-random `Long` from `minInclusive` (inclusive) to
     * `maxExclusive` (exclusive) with the global `scala.util.Random`. Dies with
     * a `java.lang.IllegalArgumentException` (message `invalid bounds`) when
     * `minInclusive >= maxExclusive`.
     */
    def nextLongBetween(minInclusive: => Long, maxExclusive: => Long)(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(unsafe.nextLongBetween(minInclusive, maxExclusive)(Unsafe.unsafe))

    /**
     * Generates a pseudo-random `Long` from 0 (inclusive) to `n` (exclusive)
     * with the global `scala.util.Random`. Dies with a
     * `java.lang.IllegalArgumentException` (message `n must be positive`) when
     * `n` is 0 or negative.
     */
    def nextLongBounded(n: => Long)(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(unsafe.nextLongBounded(n)(Unsafe.unsafe))

    /**
     * Generates a pseudo-random printable ASCII character, from code 33 (`!`)
     * to code 126 (`~`), with the global `scala.util.Random`.
     */
    def nextPrintableChar(implicit trace: Trace): UIO[Char] =
      ZIO.succeed(unsafe.nextPrintableChar()(Unsafe.unsafe))

    /**
     * Generates a pseudo-random `String` of `length` characters between U+0001
     * and U+D7FF with the global `scala.util.Random`. A `length` of 0 or less
     * gives an empty string.
     */
    def nextString(length: => Int)(implicit trace: Trace): UIO[String] =
      ZIO.succeed(unsafe.nextString(length)(Unsafe.unsafe))

    /**
     * Generates a pseudo-random version 4 `java.util.UUID` from two `nextLong`
     * values of the global `scala.util.Random`.
     */
    def nextUUID(implicit trace: Trace): UIO[UUID] =
      ZIO.succeed(unsafe.nextUUID()(Unsafe.unsafe))

    /**
     * Sets the seed of the global `scala.util.Random`.
     */
    def setSeed(seed: => Long)(implicit trace: Trace): UIO[Unit] =
      ZIO.succeed(unsafe.setSeed(seed)(Unsafe.unsafe))

    /**
     * Returns the elements of `collection` in a pseudo-random order, in a
     * collection of the same type, using the global `scala.util.Random`.
     */
    def shuffle[A, Collection[+Element] <: Iterable[Element]](
      collection: => Collection[A]
    )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: Trace): UIO[Collection[A]] =
      ZIO.succeed(unsafe.shuffle(collection)(bf, Unsafe.unsafe))

    /**
     * Calls the global `scala.util.Random` directly, without running an effect.
     */
    override val unsafe: UnsafeAPI =
      new UnsafeAPI {
        override def nextBoolean()(implicit unsafe: Unsafe): Boolean =
          scala.util.Random.nextBoolean()

        override def nextBytes(length: Int)(implicit unsafe: Unsafe): Chunk[Byte] = {
          val array = Array.ofDim[Byte](length)
          scala.util.Random.nextBytes(array)
          Chunk.fromArray(array)
        }

        override def nextDouble()(implicit unsafe: Unsafe): Double =
          scala.util.Random.nextDouble()

        override def nextDoubleBetween(minInclusive: Double, maxExclusive: Double)(implicit unsafe: Unsafe): Double =
          nextDoubleBetweenWith(minInclusive, maxExclusive)(() => nextDouble())

        override def nextFloat()(implicit unsafe: Unsafe): Float =
          scala.util.Random.nextFloat()

        override def nextFloatBetween(minInclusive: Float, maxExclusive: Float)(implicit unsafe: Unsafe): Float =
          nextFloatBetweenWith(minInclusive, maxExclusive)(() => nextFloat())

        override def nextGaussian()(implicit unsafe: Unsafe): Double =
          scala.util.Random.nextGaussian()

        override def nextInt()(implicit unsafe: Unsafe): Int =
          scala.util.Random.nextInt()

        override def nextIntBetween(minInclusive: Int, maxExclusive: Int)(implicit
          unsafe: Unsafe
        ): Int =
          nextIntBetweenWith(minInclusive, maxExclusive)(() => nextInt(), nextIntBounded(_))

        override def nextIntBounded(n: Int)(implicit unsafe: Unsafe): Int =
          scala.util.Random.nextInt(n)

        override def nextLong()(implicit unsafe: Unsafe): Long =
          scala.util.Random.nextLong()

        override def nextLongBetween(minInclusive: Long, maxExclusive: Long)(implicit unsafe: Unsafe): Long =
          nextLongBetweenWith(minInclusive, maxExclusive)(() => nextLong(), nextLongBounded(_))

        override def nextLongBounded(n: Long)(implicit unsafe: Unsafe): Long =
          Random.nextLongBoundedWith(n)(() => nextLong())

        override def nextPrintableChar()(implicit unsafe: Unsafe): Char =
          scala.util.Random.nextPrintableChar()

        override def nextString(length: Int)(implicit unsafe: Unsafe): String =
          scala.util.Random.nextString(length)

        override def nextUUID()(implicit unsafe: Unsafe): UUID =
          Random.nextUUIDWith(() => nextLong())

        override def setSeed(seed: Long)(implicit unsafe: Unsafe): Unit =
          scala.util.Random.setSeed(seed)

        override def shuffle[A, Collection[+Element] <: Iterable[Element]](
          collection: Collection[A]
        )(implicit bf: zio.BuildFrom[Collection[A], A, Collection[A]], unsafe: Unsafe): Collection[A] =
          Random.shuffleWith(nextIntBounded(_), collection)
      }
  }

  /**
   * A `Random` service that uses the given `scala.util.Random` instance. A
   * `scala.util.Random` created with a fixed seed gives a repeatable sequence
   * of values, and `setSeed` reseeds that instance. `ZIO.withRandom` runs a
   * workflow with it.
   *
   * @param random
   *   the generator that every operation draws from
   */
  final case class RandomScala(random: scala.util.Random) extends Random {

    /**
     * Generates a pseudo-random `Boolean` with the wrapped `random`.
     */
    def nextBoolean(implicit trace: Trace): UIO[Boolean] =
      ZIO.succeed(unsafe.nextBoolean()(Unsafe.unsafe))

    /**
     * Generates a `Chunk` of `length` pseudo-random bytes with the wrapped
     * `random`. On the JVM, a negative `length` makes the effect die with a
     * `java.lang.NegativeArraySizeException`.
     */
    def nextBytes(length: => Int)(implicit trace: Trace): UIO[Chunk[Byte]] =
      ZIO.succeed(unsafe.nextBytes(length)(Unsafe.unsafe))

    /**
     * Generates a pseudo-random `Double` from 0.0 (inclusive) to 1.0
     * (exclusive) with the wrapped `random`.
     */
    def nextDouble(implicit trace: Trace): UIO[Double] =
      ZIO.succeed(unsafe.nextDouble()(Unsafe.unsafe))

    /**
     * Generates a pseudo-random `Double` from `minInclusive` (inclusive) to
     * `maxExclusive` (exclusive) with the wrapped `random`. Dies with a
     * `java.lang.IllegalArgumentException` (message `invalid bounds`) when
     * `minInclusive >= maxExclusive`.
     */
    def nextDoubleBetween(minInclusive: => Double, maxExclusive: => Double)(implicit
      trace: Trace
    ): UIO[Double] =
      ZIO.succeed(unsafe.nextDoubleBetween(minInclusive, maxExclusive)(Unsafe.unsafe))

    /**
     * Generates a pseudo-random `Float` from 0.0 (inclusive) to 1.0 (exclusive)
     * with the wrapped `random`.
     */
    def nextFloat(implicit trace: Trace): UIO[Float] =
      ZIO.succeed(unsafe.nextFloat()(Unsafe.unsafe))

    /**
     * Generates a pseudo-random `Float` from `minInclusive` (inclusive) to
     * `maxExclusive` (exclusive) with the wrapped `random`. Dies with a
     * `java.lang.IllegalArgumentException` (message `invalid bounds`) when
     * `minInclusive >= maxExclusive`.
     */
    def nextFloatBetween(minInclusive: => Float, maxExclusive: => Float)(implicit trace: Trace): UIO[Float] =
      ZIO.succeed(unsafe.nextFloatBetween(minInclusive, maxExclusive)(Unsafe.unsafe))

    /**
     * Generates a pseudo-random `Double` from a normal distribution with mean
     * 0.0 and standard deviation 1.0, with the wrapped `random`.
     */
    def nextGaussian(implicit trace: Trace): UIO[Double] =
      ZIO.succeed(unsafe.nextGaussian()(Unsafe.unsafe))

    /**
     * Generates a pseudo-random `Int` from the whole `Int` range with the
     * wrapped `random`.
     */
    def nextInt(implicit trace: Trace): UIO[Int] =
      ZIO.succeed(unsafe.nextInt()(Unsafe.unsafe))

    /**
     * Generates a pseudo-random `Int` from `minInclusive` (inclusive) to
     * `maxExclusive` (exclusive) with the wrapped `random`. Dies with a
     * `java.lang.IllegalArgumentException` (message `invalid bounds`) when
     * `minInclusive >= maxExclusive`.
     */
    def nextIntBetween(minInclusive: => Int, maxExclusive: => Int)(implicit trace: Trace): UIO[Int] =
      ZIO.succeed(unsafe.nextIntBetween(minInclusive, maxExclusive)(Unsafe.unsafe))

    /**
     * Generates a pseudo-random `Int` from 0 (inclusive) to `n` (exclusive)
     * with the wrapped `random`. Dies with a
     * `java.lang.IllegalArgumentException` when `n` is 0 or negative.
     */
    def nextIntBounded(n: => Int)(implicit trace: Trace): UIO[Int] =
      ZIO.succeed(unsafe.nextIntBounded(n)(Unsafe.unsafe))

    /**
     * Generates a pseudo-random `Long` from the whole `Long` range with the
     * wrapped `random`.
     */
    def nextLong(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(unsafe.nextLong()(Unsafe.unsafe))

    /**
     * Generates a pseudo-random `Long` from `minInclusive` (inclusive) to
     * `maxExclusive` (exclusive) with the wrapped `random`. Dies with a
     * `java.lang.IllegalArgumentException` (message `invalid bounds`) when
     * `minInclusive >= maxExclusive`.
     */
    def nextLongBetween(minInclusive: => Long, maxExclusive: => Long)(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(unsafe.nextLongBetween(minInclusive, maxExclusive)(Unsafe.unsafe))

    /**
     * Generates a pseudo-random `Long` from 0 (inclusive) to `n` (exclusive)
     * with the wrapped `random`. Dies with a
     * `java.lang.IllegalArgumentException` (message `n must be positive`) when
     * `n` is 0 or negative.
     */
    def nextLongBounded(n: => Long)(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(unsafe.nextLongBounded(n)(Unsafe.unsafe))

    /**
     * Generates a pseudo-random printable ASCII character, from code 33 (`!`)
     * to code 126 (`~`), with the wrapped `random`.
     */
    def nextPrintableChar(implicit trace: Trace): UIO[Char] =
      ZIO.succeed(unsafe.nextPrintableChar()(Unsafe.unsafe))

    /**
     * Generates a pseudo-random `String` of `length` characters between U+0001
     * and U+D7FF with the wrapped `random`. A `length` of 0 or less gives an
     * empty string.
     */
    def nextString(length: => Int)(implicit trace: Trace): UIO[String] =
      ZIO.succeed(unsafe.nextString(length)(Unsafe.unsafe))

    /**
     * Generates a pseudo-random version 4 `java.util.UUID` from two `nextLong`
     * values of the wrapped `random`.
     */
    def nextUUID(implicit trace: Trace): UIO[UUID] =
      ZIO.succeed(unsafe.nextUUID()(Unsafe.unsafe))

    /**
     * Sets the seed of the wrapped `random`.
     */
    def setSeed(seed: => Long)(implicit trace: Trace): UIO[Unit] =
      ZIO.succeed(unsafe.setSeed(seed)(Unsafe.unsafe))

    /**
     * Returns the elements of `collection` in a pseudo-random order, in a
     * collection of the same type, using the wrapped `random`.
     */
    def shuffle[A, Collection[+Element] <: Iterable[Element]](
      collection: => Collection[A]
    )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: Trace): UIO[Collection[A]] =
      ZIO.succeed(unsafe.shuffle(collection)(bf, Unsafe.unsafe))

    /**
     * Calls the wrapped `random` directly, without running an effect.
     */
    override val unsafe: UnsafeAPI =
      new UnsafeAPI {
        override def nextBoolean()(implicit unsafe: Unsafe): Boolean =
          random.nextBoolean()

        override def nextBytes(length: Int)(implicit unsafe: Unsafe): Chunk[Byte] = {
          val array = Array.ofDim[Byte](length)
          random.nextBytes(array)
          Chunk.fromArray(array)
        }

        override def nextDouble()(implicit unsafe: Unsafe): Double =
          random.nextDouble()

        override def nextDoubleBetween(minInclusive: Double, maxExclusive: Double)(implicit unsafe: Unsafe): Double =
          nextDoubleBetweenWith(minInclusive, maxExclusive)(() => nextDouble())

        override def nextFloat()(implicit unsafe: Unsafe): Float =
          random.nextFloat()

        override def nextFloatBetween(minInclusive: Float, maxExclusive: Float)(implicit unsafe: Unsafe): Float =
          nextFloatBetweenWith(minInclusive, maxExclusive)(() => nextFloat())

        override def nextGaussian()(implicit unsafe: Unsafe): Double =
          random.nextGaussian()

        override def nextInt()(implicit unsafe: Unsafe): Int =
          random.nextInt()

        override def nextIntBetween(minInclusive: Int, maxExclusive: Int)(implicit
          unsafe: Unsafe
        ): Int =
          nextIntBetweenWith(minInclusive, maxExclusive)(() => nextInt(), nextIntBounded(_))

        override def nextIntBounded(n: Int)(implicit unsafe: Unsafe): Int =
          random.nextInt(n)

        override def nextLong()(implicit unsafe: Unsafe): Long =
          random.nextLong()

        override def nextLongBetween(minInclusive: Long, maxExclusive: Long)(implicit unsafe: Unsafe): Long =
          nextLongBetweenWith(minInclusive, maxExclusive)(() => nextLong(), nextLongBounded(_))

        override def nextLongBounded(n: Long)(implicit unsafe: Unsafe): Long =
          Random.nextLongBoundedWith(n)(() => nextLong())

        override def nextPrintableChar()(implicit unsafe: Unsafe): Char =
          random.nextPrintableChar()

        override def nextString(length: Int)(implicit unsafe: Unsafe): String =
          random.nextString(length)

        override def nextUUID()(implicit unsafe: Unsafe): UUID =
          Random.nextUUIDWith(() => nextLong())

        override def setSeed(seed: Long)(implicit unsafe: Unsafe): Unit =
          random.setSeed(seed)

        override def shuffle[A, Collection[+Element] <: Iterable[Element]](
          collection: Collection[A]
        )(implicit bf: zio.BuildFrom[Collection[A], A, Collection[A]], unsafe: Unsafe): Collection[A] =
          Random.shuffleWith(nextIntBounded(_), collection)
      }
  }

  private[zio] def nextDoubleBetweenWith(minInclusive: Double, maxExclusive: Double)(nextDouble: () => Double): Double =
    if (minInclusive >= maxExclusive)
      throw new IllegalArgumentException("invalid bounds")
    else {
      val n      = nextDouble()
      val result = n * (maxExclusive - minInclusive) + minInclusive
      if (result < maxExclusive) result
      else Math.nextAfter(maxExclusive, Float.NegativeInfinity)
    }

  private[zio] def nextFloatBetweenWith(minInclusive: Float, maxExclusive: Float)(
    nextFloat: () => Float
  ): Float =
    if (minInclusive >= maxExclusive)
      throw new IllegalArgumentException("invalid bounds")
    else {
      val n      = nextFloat()
      val result = n * (maxExclusive - minInclusive) + minInclusive
      if (result < maxExclusive) result
      else Math.nextAfter(maxExclusive, Float.NegativeInfinity)
    }

  private[zio] def nextIntBetweenWith(
    minInclusive: Int,
    maxExclusive: Int
  )(nextInt: () => Int, nextIntBounded: Int => Int): Int =
    if (minInclusive >= maxExclusive) {
      throw new IllegalArgumentException("invalid bounds")
    } else {
      val difference = maxExclusive - minInclusive
      if (difference > 0) nextIntBounded(difference) + minInclusive
      else {
        @tailrec
        def loop: Int = {
          val n = nextInt()
          if (minInclusive <= n && n < maxExclusive) n
          else loop
        }
        loop
      }
    }

  private[zio] def nextLongBetweenWith(
    minInclusive: Long,
    maxExclusive: Long
  )(nextLong: () => Long, nextLongBounded: Long => Long): Long =
    if (minInclusive >= maxExclusive)
      throw new IllegalArgumentException("invalid bounds")
    else {
      val difference = maxExclusive - minInclusive
      if (difference > 0) nextLongBounded(difference) + minInclusive
      else {
        @tailrec
        def loop: Long = {
          val n = nextLong()
          if (minInclusive <= n && n < maxExclusive) n
          else loop
        }
        loop
      }
    }

  private[zio] def nextLongBoundedWith(n: Long)(nextLong: => () => Long): Long =
    if (n <= 0)
      throw new IllegalArgumentException("n must be positive")
    else {
      val r = nextLong()
      val m = n - 1
      if ((n & m) == 0L)
        r & m
      else {
        @tailrec
        def loop(u: Long): Long =
          if (u + m - u % m < 0L) {
            val r = nextLong()
            loop(r >>> 1)
          } else u % n
        loop(r >>> 1)
      }
    }

  private[zio] def nextUUIDWith(nextLong: () => Long): UUID = {
    val mostSigBits  = nextLong()
    val leastSigBits = nextLong()
    new UUID(
      (mostSigBits & ~0x0000f000) | 0x00004000,
      (leastSigBits & ~(0xc0000000L << 32)) | (0x80000000L << 32)
    )
  }

  private[zio] def shuffleWith[A, Collection[+Element] <: Iterable[Element]](
    nextIntBounded: Int => Int,
    collection: Collection[A]
  )(implicit bf: BuildFrom[Collection[A], A, Collection[A]]): Collection[A] = {
    val buffer = new scala.collection.mutable.ArrayBuffer[A]
    buffer ++= collection
    def swap(i1: Int, i2: Int): Unit = {
      val tmp = buffer(i1)
      buffer(i1) = buffer(i2)
      buffer(i2) = tmp
    }
    (collection.size to 2 by -1).foreach { n =>
      val k = nextIntBounded(n)
      swap(n - 1, k)
    }
    bf.fromSpecific(collection)(buffer)
  }

  /**
   * Generates a pseudo-random `Boolean` with the `Random` service of the
   * current fiber.
   */
  def nextBoolean(implicit trace: Trace): UIO[Boolean] =
    ZIO.randomWith(_.nextBoolean)

  /**
   * Generates a pseudo-random chunk of bytes of the specified length, with the
   * `Random` service of the current fiber. With the default [[RandomLive]] on
   * the JVM, a negative length makes the effect die with a
   * `java.lang.NegativeArraySizeException`.
   */
  def nextBytes(length: => Int)(implicit trace: Trace): UIO[Chunk[Byte]] =
    ZIO.randomWith(_.nextBytes(length))

  /**
   * Generates a pseudo-random, uniformly distributed `Double` from 0.0
   * (inclusive) to 1.0 (exclusive), with the `Random` service of the current
   * fiber.
   */
  def nextDouble(implicit trace: Trace): UIO[Double] =
    ZIO.randomWith(_.nextDouble)

  /**
   * Generates a pseudo-random `Double` from `minInclusive` (inclusive) to
   * `maxExclusive` (exclusive), with the `Random` service of the current fiber.
   *
   * With the default [[RandomLive]], the effect dies with a
   * `java.lang.IllegalArgumentException` (message `invalid bounds`) when
   * `minInclusive >= maxExclusive`. When `maxExclusive - minInclusive`
   * overflows to infinity, every result is the largest `Double` below
   * `maxExclusive`.
   */
  def nextDoubleBetween(minInclusive: => Double, maxExclusive: => Double)(implicit
    trace: Trace
  ): UIO[Double] =
    ZIO.randomWith(_.nextDoubleBetween(minInclusive, maxExclusive))

  /**
   * Generates a pseudo-random, uniformly distributed `Float` from 0.0
   * (inclusive) to 1.0 (exclusive), with the `Random` service of the current
   * fiber.
   */
  def nextFloat(implicit trace: Trace): UIO[Float] =
    ZIO.randomWith(_.nextFloat)

  /**
   * Generates a pseudo-random `Float` from `minInclusive` (inclusive) to
   * `maxExclusive` (exclusive), with the `Random` service of the current fiber.
   *
   * With the default [[RandomLive]], the effect dies with a
   * `java.lang.IllegalArgumentException` (message `invalid bounds`) when
   * `minInclusive >= maxExclusive`. When `maxExclusive - minInclusive`
   * overflows to infinity, every result is the largest `Float` below
   * `maxExclusive`.
   */
  def nextFloatBetween(minInclusive: => Float, maxExclusive: => Float)(implicit
    trace: Trace
  ): UIO[Float] =
    ZIO.randomWith(_.nextFloatBetween(minInclusive, maxExclusive))

  /**
   * Generates a pseudo-random double from a normal distribution with mean 0.0
   * and standard deviation 1.0.
   */
  def nextGaussian(implicit trace: Trace): UIO[Double] =
    ZIO.randomWith(_.nextGaussian)

  /**
   * Generates a pseudo-random integer.
   */
  def nextInt(implicit trace: Trace): UIO[Int] =
    ZIO.randomWith(_.nextInt)

  /**
   * Generates a pseudo-random `Int` from `minInclusive` (inclusive) to
   * `maxExclusive` (exclusive), with the `Random` service of the current fiber.
   * With the default [[RandomLive]], the effect dies with a
   * `java.lang.IllegalArgumentException` (message `invalid bounds`) when
   * `minInclusive >= maxExclusive`.
   *
   * {{{
   * val dieRoll: UIO[Int] = Random.nextIntBetween(1, 7) // 1 to 6
   * }}}
   */
  def nextIntBetween(minInclusive: => Int, maxExclusive: => Int)(implicit
    trace: Trace
  ): UIO[Int] =
    ZIO.randomWith(_.nextIntBetween(minInclusive, maxExclusive))

  /**
   * Generates a pseudo-random integer between 0 (inclusive) and the specified
   * value (exclusive). With the default [[RandomLive]], the effect dies with a
   * `java.lang.IllegalArgumentException` when `n` is 0 or negative.
   */
  def nextIntBounded(n: => Int)(implicit trace: Trace): UIO[Int] =
    ZIO.randomWith(_.nextIntBounded(n))

  /**
   * Generates a pseudo-random long.
   */
  def nextLong(implicit trace: Trace): UIO[Long] =
    ZIO.randomWith(_.nextLong)

  /**
   * Generates a pseudo-random `Long` from `minInclusive` (inclusive) to
   * `maxExclusive` (exclusive), with the `Random` service of the current fiber.
   * With the default [[RandomLive]], the effect dies with a
   * `java.lang.IllegalArgumentException` (message `invalid bounds`) when
   * `minInclusive >= maxExclusive`.
   */
  def nextLongBetween(minInclusive: => Long, maxExclusive: => Long)(implicit
    trace: Trace
  ): UIO[Long] =
    ZIO.randomWith(_.nextLongBetween(minInclusive, maxExclusive))

  /**
   * Generates a pseudo-random long between 0 (inclusive) and the specified
   * value (exclusive). With the default [[RandomLive]], the effect dies with a
   * `java.lang.IllegalArgumentException` (message `n must be positive`) when
   * `n` is 0 or negative.
   */
  def nextLongBounded(n: => Long)(implicit trace: Trace): UIO[Long] =
    ZIO.randomWith(_.nextLongBounded(n))

  /**
   * Generates a pseudo-random version 4 `java.util.UUID` (IETF variant), with
   * the `Random` service of the current fiber.
   */
  def nextUUID(implicit trace: Trace): UIO[UUID] =
    ZIO.randomWith(_.nextUUID)

  /**
   * Generates a pseudo-random character from the ASCII range 33-126.
   */
  def nextPrintableChar(implicit trace: Trace): UIO[Char] =
    ZIO.randomWith(_.nextPrintableChar)

  /**
   * Generates a pseudo-random string of the specified length. With the default
   * [[RandomLive]], each character is between U+0001 and U+D7FF, and a length
   * of 0 or less gives an empty string.
   */
  def nextString(length: => Int)(implicit trace: Trace): UIO[String] =
    ZIO.randomWith(_.nextString(length))

  /**
   * Sets the seed of the `Random` service of the current fiber, so that the
   * values generated after it follow a repeatable sequence as long as nothing
   * else draws from the same generator in between.
   *
   * With the default [[RandomLive]], this reseeds the global
   * `scala.util.Random`, which is shared with all code in the process that
   * calls the `scala.util.Random` object directly.
   *
   * {{{
   * val firstTwo: UIO[(Int, Int)] =
   *   Random.setSeed(0L) *> Random.nextInt.zip(Random.nextInt)
   * // (-1155484576,-723955400)
   * }}}
   */
  def setSeed(seed: => Long)(implicit trace: Trace): UIO[Unit] =
    ZIO.randomWith(_.setSeed(seed))

  /**
   * Returns the elements of `list` in a pseudo-random order, with the `Random`
   * service of the current fiber. For other collection types, such as `Vector`,
   * call `shuffle` on the service: `ZIO.randomWith(_.shuffle(vector))`.
   */
  def shuffle[A](list: => List[A])(implicit trace: Trace): UIO[List[A]] =
    ZIO.randomWith(_.shuffle(list))
}
