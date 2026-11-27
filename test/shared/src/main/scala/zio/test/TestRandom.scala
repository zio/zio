/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
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

package zio.test

import zio.Clock.ClockLive
import zio._
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.UUID
import scala.annotation.tailrec
import scala.collection.immutable.Queue
import scala.math.{log, sqrt}

/**
 * `TestRandom` lets a test check effects that involve randomness, with values
 * it can repeat or feed.
 *
 * `TestRandom` operates in two modes. In the first mode, `TestRandom` is a
 * seeded pseudo-random number generator that keeps its seed in a `Ref`, which
 * each call such as `nextInt` advances. For `nextInt`, `nextIntBounded`,
 * `nextIntBetween`, `nextLong`, `nextDouble`, `nextDoubleBetween`, `nextFloat`,
 * `nextFloatBetween`, `nextBoolean`, `nextBytes`, `nextPrintableChar`,
 * `nextString` and `shuffle` it returns the same values as a
 * `scala.util.Random` created with the same seed; `nextLongBounded` and
 * `nextLongBetween` return different values, and `nextGaussian` can differ in
 * the last digit. The random seed can be set using `setSeed`, and `TestRandom`
 * returns the same sequence of values each time the same seed is set. This is
 * useful for generating a repeatable sequence of pseudo-random values, and
 * `Gen` draws its samples from it in property based tests.
 *
 * In the second mode, `TestRandom` maintains an internal buffer of values that
 * can be "fed" with methods such as `feedInts` and then when random values of
 * that type are generated they will first be taken from the buffer. This is
 * useful for verifying that functions produce the expected output for a given
 * sequence of "random" inputs.
 *
 * {{{
 * import zio._
 * import zio.test.{test, _}
 *
 * test("fed values come back first") {
 *   for {
 *     _ <- TestRandom.feedInts(4, 5, 2)
 *     x <- Random.nextIntBounded(6)
 *     y <- Random.nextIntBounded(6)
 *     z <- Random.nextIntBounded(6)
 *   } yield assertTrue(x + y + z == 11)
 * }
 *
 * test("the same seed gives the same values") {
 *   for {
 *     _ <- TestRandom.setSeed(42L)
 *     a <- Random.nextIntBounded(100)
 *     _ <- TestRandom.setSeed(42L)
 *     b <- Random.nextIntBounded(100)
 *   } yield assertTrue(a == b)
 * }
 * }}}
 *
 * `TestRandom` will automatically take values from the buffer if a value of the
 * appropriate type is available and otherwise generate a pseudo-random value,
 * so there is nothing you need to do to switch between the two modes. Generate
 * random values as you normally would to get pseudo-random values, or feed in
 * values of your own to get those values back. You can also use methods like
 * `clearInts` to clear the buffer of values of a given type so you can fill the
 * buffer with new values or go back to pseudo-random number generation.
 * Buffered values are returned unchanged, even when they lie outside the range
 * that a method such as `nextIntBounded` asks for, and taking a buffered value
 * does not advance the seed.
 *
 * ZIO Test gives each test its own `TestRandom`, created from
 * [[TestRandom.DefaultData]], so every test starts from the same seed and with
 * empty buffers. The accessors on the companion object use the `TestRandom` of
 * the current fiber; outside a test, where `Random` is the live service, they
 * die with a `java.lang.Error` saying that `TestRandom` could not be found.
 *
 * @see
 *   [[https://zio.dev/reference/test/services/random/ TestRandom]]
 * @see
 *   [[https://zio.dev/reference/test/aspects/restoring-state-of-test-services/ Restoring State of Test Services]]
 */
trait TestRandom extends Random with Restorable {

  /**
   * Clears the buffer of booleans, so that `nextBoolean` generates
   * pseudo-random booleans again.
   */
  def clearBooleans(implicit trace: Trace): UIO[Unit]

  /**
   * Clears the buffer of bytes, so that `nextBytes` generates pseudo-random
   * bytes again.
   */
  def clearBytes(implicit trace: Trace): UIO[Unit]

  /**
   * Clears the buffer of characters, so that `nextPrintableChar` generates
   * pseudo-random characters again.
   */
  def clearChars(implicit trace: Trace): UIO[Unit]

  /**
   * Clears the buffer of doubles, so that `nextDouble`, `nextDoubleBetween` and
   * `nextGaussian` generate pseudo-random doubles again.
   */
  def clearDoubles(implicit trace: Trace): UIO[Unit]

  /**
   * Clears the buffer of floats, so that `nextFloat` and `nextFloatBetween`
   * generate pseudo-random floats again.
   */
  def clearFloats(implicit trace: Trace): UIO[Unit]

  /**
   * Clears the buffer of integers, so that `nextInt`, `nextIntBounded` and
   * `nextIntBetween` generate pseudo-random integers again.
   */
  def clearInts(implicit trace: Trace): UIO[Unit]

  /**
   * Clears the buffer of longs, so that `nextLong`, `nextLongBounded`,
   * `nextLongBetween` and `nextUUID` generate pseudo-random values again.
   */
  def clearLongs(implicit trace: Trace): UIO[Unit]

  /**
   * Clears the buffer of strings, so that `nextString` generates pseudo-random
   * strings again.
   */
  def clearStrings(implicit trace: Trace): UIO[Unit]

  /**
   * Clears the buffer of UUIDs, so that `nextUUID` builds its UUIDs from longs
   * again.
   */
  def clearUUIDs(implicit trace: Trace): UIO[Unit]

  /**
   * Feeds the buffer with the specified booleans, which `nextBoolean` returns
   * in order before any values that were previously in the buffer.
   */
  def feedBooleans(booleans: Boolean*)(implicit trace: Trace): UIO[Unit]

  /**
   * Feeds the buffer with the bytes of the specified chunks, in order, before
   * any bytes that were previously in the buffer. `nextBytes(n)` takes up to
   * `n` bytes from the buffer and generates the rest.
   */
  def feedBytes(bytes: Chunk[Byte]*)(implicit trace: Trace): UIO[Unit]

  /**
   * Feeds the buffer with the specified characters, which `nextPrintableChar`
   * returns in order, and unchanged, before any values that were previously in
   * the buffer.
   */
  def feedChars(chars: Char*)(implicit trace: Trace): UIO[Unit]

  /**
   * Feeds the buffer with the specified doubles, which `nextDouble`,
   * `nextDoubleBetween` and `nextGaussian` return in order, and unchanged,
   * before any values that were previously in the buffer.
   */
  def feedDoubles(doubles: Double*)(implicit trace: Trace): UIO[Unit]

  /**
   * Feeds the buffer with the specified floats, which `nextFloat` and
   * `nextFloatBetween` return in order, and unchanged, before any values that
   * were previously in the buffer.
   */
  def feedFloats(floats: Float*)(implicit trace: Trace): UIO[Unit]

  /**
   * Feeds the buffer with the specified integers, which `nextInt`,
   * `nextIntBounded` and `nextIntBetween` return in order, and unchanged,
   * before any values that were previously in the buffer. `shuffle` does not
   * read this buffer.
   */
  def feedInts(ints: Int*)(implicit trace: Trace): UIO[Unit]

  /**
   * Feeds the buffer with the specified longs, which `nextLong`,
   * `nextLongBounded` and `nextLongBetween` return in order, and unchanged,
   * before any values that were previously in the buffer. When the buffer of
   * UUIDs is empty, `nextUUID` also takes two longs from this buffer.
   */
  def feedLongs(longs: Long*)(implicit trace: Trace): UIO[Unit]

  /**
   * Feeds the buffer with the specified strings, which `nextString` returns in
   * order, whatever length it asks for, before any values that were previously
   * in the buffer.
   */
  def feedStrings(strings: String*)(implicit trace: Trace): UIO[Unit]

  /**
   * Feeds the buffer with the specified UUIDs, which `nextUUID` returns in
   * order, and unchanged, before any values that were previously in the buffer.
   */
  def feedUUIDs(UUIDs: UUID*)(implicit trace: Trace): UIO[Unit]

  /**
   * Returns the current seed of the generator, which changes each time a
   * pseudo-random value is generated. Passing it to `setSeed` continues the
   * same sequence of values once `setSeed` has been called or a value has been
   * generated, except for `nextGaussian`: `setSeed` drops the second value that
   * `nextGaussian` keeps for its next call.
   */
  def getSeed(implicit trace: Trace): UIO[Long]

  /**
   * Sets the seed of the generator to the lower 48 bits of the specified value.
   * The buffers are left unchanged. It also drops the value that `nextGaussian`
   * keeps for its next call.
   */
  def setSeed(seed: => Long)(implicit trace: Trace): UIO[Unit]
}

/**
 * Constructors, layers and accessors for [[TestRandom]]. The accessors such as
 * `feedInts` and `setSeed` use the `TestRandom` of the current fiber, which ZIO
 * Test installs for each test.
 */
object TestRandom extends Serializable {

  /**
   * The `Tag` that identifies the `TestRandom` service in a `ZEnvironment`.
   */
  implicit val tag: Tag[TestRandom] = Tag(EnvironmentTag.tagFromTagMacro[TestRandom])

  private[test] object unsafe {
    def make(data: Data)(implicit unsafe: Unsafe): Test =
      Test(Ref.unsafe.make(data), Ref.unsafe.make(Buffer()))
  }

  /**
   * The implementation of [[TestRandom]], which keeps the seed in `randomState`
   * and the fed values in `bufferState`.
   *
   * Adapted from @gzmo work in Scala.js
   * (https://github.com/scala-js/scala-js/pull/780)
   */
  final case class Test(randomState: Ref.Atomic[Data], bufferState: Ref.Atomic[Buffer]) extends TestRandom {

    /**
     * Clears the buffer of booleans.
     */
    def clearBooleans(implicit trace: Trace): UIO[Unit] =
      bufferState.update(_.copy(booleans = List.empty))

    /**
     * Clears the buffer of bytes.
     */
    def clearBytes(implicit trace: Trace): UIO[Unit] =
      bufferState.update(_.copy(bytes = List.empty))

    /**
     * Clears the buffer of characters.
     */
    def clearChars(implicit trace: Trace): UIO[Unit] =
      bufferState.update(_.copy(chars = List.empty))

    /**
     * Clears the buffer of doubles.
     */
    def clearDoubles(implicit trace: Trace): UIO[Unit] =
      bufferState.update(_.copy(doubles = List.empty))

    /**
     * Clears the buffer of floats.
     */
    def clearFloats(implicit trace: Trace): UIO[Unit] =
      bufferState.update(_.copy(floats = List.empty))

    /**
     * Clears the buffer of integers.
     */
    def clearInts(implicit trace: Trace): UIO[Unit] =
      bufferState.update(_.copy(integers = List.empty))

    /**
     * Clears the buffer of longs.
     */
    def clearLongs(implicit trace: Trace): UIO[Unit] =
      bufferState.update(_.copy(longs = List.empty))

    /**
     * Clears the buffer of strings.
     */
    def clearStrings(implicit trace: Trace): UIO[Unit] =
      bufferState.update(_.copy(strings = List.empty))

    /**
     * Clears the buffer of UUIDs.
     */
    def clearUUIDs(implicit trace: Trace): UIO[Unit] =
      bufferState.update(_.copy(uuids = List.empty))

    /**
     * Feeds the buffer with specified sequence of booleans. The first value in
     * the sequence will be the first to be taken. These values will be taken
     * before any values that were previously in the buffer.
     */
    def feedBooleans(booleans: Boolean*)(implicit trace: Trace): UIO[Unit] =
      bufferState.update(data => data.copy(booleans = booleans.toList ::: data.booleans))

    /**
     * Feeds the buffer with specified sequence of chunks of bytes. The first
     * value in the sequence will be the first to be taken. These values will be
     * taken before any values that were previously in the buffer.
     */
    def feedBytes(bytes: Chunk[Byte]*)(implicit trace: Trace): UIO[Unit] =
      bufferState.update(data => data.copy(bytes = bytes.toList.flatten ::: data.bytes))

    /**
     * Feeds the buffer with specified sequence of characters. The first value
     * in the sequence will be the first to be taken. These values will be taken
     * before any values that were previously in the buffer.
     */
    def feedChars(chars: Char*)(implicit trace: Trace): UIO[Unit] =
      bufferState.update(data => data.copy(chars = chars.toList ::: data.chars))

    /**
     * Feeds the buffer with specified sequence of doubles. The first value in
     * the sequence will be the first to be taken. These values will be taken
     * before any values that were previously in the buffer.
     */
    def feedDoubles(doubles: Double*)(implicit trace: Trace): UIO[Unit] =
      bufferState.update(data => data.copy(doubles = doubles.toList ::: data.doubles))

    /**
     * Feeds the buffer with specified sequence of floats. The first value in
     * the sequence will be the first to be taken. These values will be taken
     * before any values that were previously in the buffer.
     */
    def feedFloats(floats: Float*)(implicit trace: Trace): UIO[Unit] =
      bufferState.update(data => data.copy(floats = floats.toList ::: data.floats))

    /**
     * Feeds the buffer with specified sequence of integers. The first value in
     * the sequence will be the first to be taken. These values will be taken
     * before any values that were previously in the buffer.
     */
    def feedInts(ints: Int*)(implicit trace: Trace): UIO[Unit] =
      bufferState.update(data => data.copy(integers = ints.toList ::: data.integers))

    /**
     * Feeds the buffer with specified sequence of longs. The first value in the
     * sequence will be the first to be taken. These values will be taken before
     * any values that were previously in the buffer.
     */
    def feedLongs(longs: Long*)(implicit trace: Trace): UIO[Unit] =
      bufferState.update(data => data.copy(longs = longs.toList ::: data.longs))

    /**
     * Feeds the buffer with specified sequence of strings. The first value in
     * the sequence will be the first to be taken. These values will be taken
     * before any values that were previously in the buffer.
     */
    def feedStrings(strings: String*)(implicit trace: Trace): UIO[Unit] =
      bufferState.update(data => data.copy(strings = strings.toList ::: data.strings))

    /**
     * Feeds the buffer with specified sequence of UUIDs. The first value in the
     * sequence will be the first to be taken. These values will be taken before
     * any values that were previously in the buffer.
     */
    def feedUUIDs(uuids: UUID*)(implicit trace: Trace): UIO[Unit] =
      bufferState.update(data => data.copy(uuids = uuids.toList ::: data.uuids))

    /**
     * Gets the current seed of this `TestRandom`, which changes each time a
     * pseudo-random value is generated.
     */
    def getSeed(implicit trace: Trace): UIO[Long] =
      randomState.get.map { case Data(seed1, seed2, _) =>
        ((seed1.toLong << 24) | seed2) ^ 0x5deece66dL
      }

    /**
     * Takes a boolean from the buffer if one exists or else generates a
     * pseudo-random boolean.
     */
    def nextBoolean(implicit trace: Trace): UIO[Boolean] =
      ZIO.succeed(unsafe.nextBoolean()(Unsafe.unsafe))

    /**
     * Takes up to `length` bytes from the buffer and generates pseudo-random
     * bytes for the rest, returning a chunk of the specified length.
     */
    def nextBytes(length: => Int)(implicit trace: Trace): UIO[Chunk[Byte]] =
      ZIO.succeed(unsafe.nextBytes(length)(Unsafe.unsafe))

    /**
     * Takes a double from the buffer if one exists or else generates a
     * pseudo-random, uniformly distributed double between 0.0 and 1.0.
     */
    def nextDouble(implicit trace: Trace): UIO[Double] =
      ZIO.succeed(unsafe.nextDouble()(Unsafe.unsafe))

    /**
     * Takes a double from the buffer if one exists or else generates a
     * pseudo-random double in the specified range. A buffered double is
     * returned unchanged, even when it lies outside the range.
     */
    def nextDoubleBetween(minInclusive: => Double, maxExclusive: => Double)(implicit
      trace: Trace
    ): UIO[Double] =
      ZIO.succeed(unsafe.nextDoubleBetween(minInclusive, maxExclusive)(Unsafe.unsafe))

    /**
     * Takes a float from the buffer if one exists or else generates a
     * pseudo-random, uniformly distributed float between 0.0 and 1.0.
     */
    def nextFloat(implicit trace: Trace): UIO[Float] =
      ZIO.succeed(unsafe.nextFloat()(Unsafe.unsafe))

    /**
     * Takes a float from the buffer if one exists or else generates a
     * pseudo-random float in the specified range. A buffered float is returned
     * unchanged, even when it lies outside the range.
     */
    def nextFloatBetween(minInclusive: => Float, maxExclusive: => Float)(implicit trace: Trace): UIO[Float] =
      ZIO.succeed(unsafe.nextFloatBetween(minInclusive, maxExclusive)(Unsafe.unsafe))

    /**
     * Takes a double from the buffer if one exists or else generates a
     * pseudo-random double from a normal distribution with mean 0.0 and
     * standard deviation 1.0.
     */
    def nextGaussian(implicit trace: Trace): UIO[Double] =
      ZIO.succeed(unsafe.nextGaussian()(Unsafe.unsafe))

    /**
     * Takes an integer from the buffer if one exists or else generates a
     * pseudo-random integer.
     */
    def nextInt(implicit trace: Trace): UIO[Int] =
      ZIO.succeed(unsafe.nextInt()(Unsafe.unsafe))

    /**
     * Takes an integer from the buffer if one exists or else generates a
     * pseudo-random integer in the specified range. A buffered integer is
     * returned unchanged, even when it lies outside the range. With an empty
     * buffer, dies with an `IllegalArgumentException` when `minInclusive` is
     * not less than `maxExclusive`.
     */
    def nextIntBetween(minInclusive: => Int, maxExclusive: => Int)(implicit trace: Trace): UIO[Int] =
      ZIO.succeed(unsafe.nextIntBetween(minInclusive, maxExclusive)(Unsafe.unsafe))

    /**
     * Takes an integer from the buffer if one exists or else generates a
     * pseudo-random integer between 0 (inclusive) and the specified value
     * (exclusive). A buffered integer is returned unchanged, even when it lies
     * outside the range. With an empty buffer, dies with an
     * `IllegalArgumentException` when `n` is not positive.
     */
    def nextIntBounded(n: => Int)(implicit trace: Trace): UIO[Int] =
      ZIO.succeed(unsafe.nextIntBounded(n)(Unsafe.unsafe))

    /**
     * Takes a long from the buffer if one exists or else generates a
     * pseudo-random long.
     */
    def nextLong(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(unsafe.nextLong()(Unsafe.unsafe))

    /**
     * Takes a long from the buffer if one exists or else generates a
     * pseudo-random long in the specified range. A buffered long is returned
     * unchanged, even when it lies outside the range.
     */
    def nextLongBetween(minInclusive: => Long, maxExclusive: => Long)(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(unsafe.nextLongBetween(minInclusive, maxExclusive)(Unsafe.unsafe))

    /**
     * Takes a long from the buffer if one exists or else generates a
     * pseudo-random long between 0 (inclusive) and the specified value
     * (exclusive). A buffered long is returned unchanged, even when it lies
     * outside the range.
     */
    def nextLongBounded(n: => Long)(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(unsafe.nextLongBounded(n)(Unsafe.unsafe))

    /**
     * Takes a character from the buffer if one exists or else generates a
     * pseudo-random character from the ASCII range 33-126. A buffered character
     * is returned unchanged, even when it is not printable.
     */
    def nextPrintableChar(implicit trace: Trace): UIO[Char] =
      ZIO.succeed(unsafe.nextPrintableChar()(Unsafe.unsafe))

    /**
     * Takes a string from the buffer if one exists or else generates a
     * pseudo-random string of the specified length. A buffered string is
     * returned whatever its length.
     */
    def nextString(length: => Int)(implicit trace: Trace): UIO[String] =
      ZIO.succeed(unsafe.nextString(length)(Unsafe.unsafe))

    /**
     * Takes a UUID from the buffer if one exists or else generates a
     * pseudo-random UUID from two values of `nextLong`, which takes them from
     * the buffer of longs first.
     */
    def nextUUID(implicit trace: Trace): UIO[UUID] =
      ZIO.succeed(unsafe.nextUUID()(Unsafe.unsafe))

    /**
     * Saves the `TestRandom`'s current state in an effect which, when run, will
     * restore the `TestRandom` state to the saved state. The saved state holds
     * both the seed and the buffers.
     */
    def save(implicit trace: Trace): UIO[UIO[Unit]] =
      for {
        randomData <- randomState.get
        bufferData <- bufferState.get
      } yield randomState.set(randomData) *> bufferState.set(bufferData)

    /**
     * Sets the seed of this `TestRandom` to the lower 48 bits of the specified
     * value. The buffers are left unchanged. It also drops the value that
     * `nextGaussian` keeps for its next call.
     */
    def setSeed(seed: => Long)(implicit trace: Trace): UIO[Unit] =
      ZIO.succeed(unsafe.setSeed(seed)(Unsafe.unsafe))

    /**
     * Returns the elements of the specified collection in a pseudo-random order
     * drawn from the seed. It does not read the buffer of integers.
     */
    def shuffle[A, Collection[+Element] <: Iterable[Element]](
      list: => Collection[A]
    )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: Trace): UIO[Collection[A]] =
      ZIO.succeed(unsafe.shuffle(list)(bf, Unsafe.unsafe))

    override val unsafe: UnsafeAPI =
      new UnsafeAPI {
        override def nextBoolean()(implicit unsafe: Unsafe): Boolean =
          getOrElse(bufferedBoolean)(randomBoolean)

        override def nextBytes(length: RuntimeFlags)(implicit unsafe: Unsafe): Chunk[Byte] =
          getOrElseChunk(length)(bufferedBytes)(randomBytes)

        override def nextDouble()(implicit unsafe: Unsafe): Double =
          getOrElse(bufferedDouble)(randomDouble)

        override def nextDoubleBetween(minInclusive: Double, maxExclusive: Double)(implicit unsafe: Unsafe): Double =
          getOrElse(bufferedDouble)(randomDoubleBetween(minInclusive, maxExclusive))

        override def nextFloat()(implicit unsafe: Unsafe): Float =
          getOrElse(bufferedFloat)(randomFloat)

        override def nextFloatBetween(minInclusive: Float, maxExclusive: Float)(implicit unsafe: Unsafe): Float =
          getOrElse(bufferedFloat)(randomFloatBetween(minInclusive, maxExclusive))

        override def nextGaussian()(implicit unsafe: Unsafe): Double =
          getOrElse(bufferedDouble)(randomGaussian)

        override def nextInt()(implicit unsafe: Unsafe): RuntimeFlags =
          getOrElse(bufferedInt)(randomInt)

        override def nextIntBetween(minInclusive: RuntimeFlags, maxExclusive: RuntimeFlags)(implicit
          unsafe: Unsafe
        ): RuntimeFlags =
          getOrElse(bufferedInt)(randomIntBetween(minInclusive, maxExclusive))

        override def nextIntBounded(n: RuntimeFlags)(implicit unsafe: Unsafe): RuntimeFlags =
          getOrElse(bufferedInt)(randomIntBounded(n))

        override def nextLong()(implicit unsafe: Unsafe): Long =
          getOrElse(bufferedLong)(randomLong)

        override def nextLongBetween(minInclusive: Long, maxExclusive: Long)(implicit unsafe: Unsafe): Long =
          getOrElse(bufferedLong)(randomLongBetween(minInclusive, maxExclusive))

        override def nextLongBounded(n: Long)(implicit unsafe: Unsafe): Long =
          getOrElse(bufferedLong)(randomLongBounded(n))

        override def nextPrintableChar()(implicit unsafe: Unsafe): Char =
          getOrElse(bufferedChar)(randomPrintableChar)

        override def nextString(length: RuntimeFlags)(implicit unsafe: Unsafe): String =
          getOrElse(bufferedString)(randomString(length))

        override def nextUUID()(implicit unsafe: Unsafe): UUID =
          getOrElse(bufferedUUID)(Random.nextUUIDWith(() => nextLong()))

        override def setSeed(seed: Long)(implicit unsafe: Unsafe): Unit =
          randomState.unsafe.set {
            val newSeed = (seed ^ 0x5deece66dL) & ((1L << 48) - 1)
            val seed1   = (newSeed >>> 24).toInt
            val seed2   = newSeed.toInt & ((1 << 24) - 1)
            Data(seed1, seed2, Queue.empty)
          }

        override def shuffle[A, Collection[+Element] <: Iterable[Element]](
          collection: Collection[A]
        )(implicit bf: zio.BuildFrom[Collection[A], A, Collection[A]], unsafe: Unsafe): Collection[A] =
          Random.shuffleWith(randomIntBounded, collection)

        private def getOrElse[A](buffer: Buffer => (Option[A], Buffer))(random: => A)(implicit unsafe: Unsafe): A =
          bufferState.unsafe.modify(buffer).getOrElse(random)

        private def getOrElseChunk[A](
          length: Int
        )(buffer: Int => Buffer => (Chunk[A], Buffer))(random: Int => Chunk[A])(implicit unsafe: Unsafe): Chunk[A] = {
          val buffered = bufferState.unsafe.modify(buffer(length))
          if (buffered.length == length) buffered
          else buffered ++ random(length - buffered.length)
        }

        private def randomBits(bits: Int)(implicit unsafe: Unsafe): Int =
          randomState.unsafe.modify { data =>
            val multiplier  = 0x5deece66dL
            val multiplier1 = (multiplier >>> 24).toInt
            val multiplier2 = multiplier.toInt & ((1 << 24) - 1)
            val product1    = data.seed2.toDouble * multiplier1.toDouble + data.seed1.toDouble * multiplier2.toDouble
            val product2    = data.seed2.toDouble * multiplier2.toDouble + 0xb
            val newSeed1    = (mostSignificantBits(product2) + leastSignificantBits(product1)) & ((1 << 24) - 1)
            val newSeed2    = leastSignificantBits(product2)
            val result      = (newSeed1 << 8) | (newSeed2 >> 16)
            (result >>> (32 - bits), Data(newSeed1, newSeed2, data.nextNextGaussians))
          }

        private def randomBoolean(implicit unsafe: Unsafe): Boolean =
          randomBits(1) != 0

        private def randomBytes(length: Int)(implicit unsafe: Unsafe): Chunk[Byte] = {
          //  Our RNG generates 32 bit integers so to maximize efficiency we want to
          //  pull 8 bit bytes from the current integer until it is exhausted
          //  before generating another random integer
          @tailrec
          def loop(i: Int, rnd: () => Int, n: Int, acc: List[Byte]): List[Byte] =
            if (i == length)
              acc.reverse
            else if (n > 0) {
              val r = rnd()
              loop(i + 1, () => r >> 8, n - 1, r.toByte :: acc)
            } else
              loop(i, () => randomInt, (length - i) min 4, acc)

          Chunk.fromIterable(loop(0, () => randomInt, length min 4, List.empty))
        }

        private def randomDouble(implicit unsafe: Unsafe): Double = {
          val i1 = randomBits(26)
          val i2 = randomBits(27)
          ((i1.toDouble * (1L << 27).toDouble) + i2.toDouble) / (1L << 53).toDouble
        }

        private def randomDoubleBetween(minInclusive: Double, maxExclusive: Double)(implicit
          unsafe: Unsafe
        ): Double =
          Random.nextDoubleBetweenWith(minInclusive, maxExclusive)(() => randomDouble)

        private def randomFloat(implicit unsafe: Unsafe): Float =
          (randomBits(24).toDouble / (1 << 24).toDouble).toFloat

        private def randomFloatBetween(minInclusive: Float, maxExclusive: Float)(implicit unsafe: Unsafe): Float =
          Random.nextFloatBetweenWith(minInclusive, maxExclusive)(() => randomFloat)

        private def randomGaussian(implicit unsafe: Unsafe): Double =
          //  The Box-Muller transform generates two normally distributed random
          //  doubles, so we store the second double in a queue and check the
          //  queue before computing a new pair of values to avoid wasted work.
          randomState.unsafe.modify { case Data(seed1, seed2, queue) =>
            queue.dequeueOption.fold((Option.empty[Double], Data(seed1, seed2, queue))) { case (d, queue) =>
              (Some(d), Data(seed1, seed2, queue))
            }
          } match {
            case Some(nextNextGaussian) => nextNextGaussian
            case None =>
              @tailrec
              def loop: (Double, Double, Double) = {
                val d1     = randomDouble
                val d2     = randomDouble
                val x      = 2 * d1 - 1
                val y      = 2 * d2 - 1
                val radius = x * x + y * y
                if (radius >= 1 || radius == 0) loop else (x, y, radius)
              }
              loop match {
                case (x, y, radius) =>
                  val c = sqrt(-2 * log(radius) / radius)
                  randomState.unsafe.modify { case Data(seed1, seed2, queue) =>
                    (x * c, Data(seed1, seed2, queue.enqueue(y * c)))
                  }
              }
          }

        private def randomInt(implicit unsafe: Unsafe): Int =
          randomBits(32)

        private def randomIntBounded(n: Int)(implicit unsafe: Unsafe): Int =
          if (n <= 0)
            throw new IllegalArgumentException("n must be positive")
          else if ((n & -n) == n)
            randomBits(31) >> Integer.numberOfLeadingZeros(n)
          else {
            @tailrec
            def loop: Int = {
              val i     = randomBits(31)
              val value = i % n
              if (i - value + (n - 1) < 0) loop
              else value
            }
            loop
          }

        private def randomIntBetween(minInclusive: Int, maxExclusive: Int)(implicit unsafe: Unsafe): Int =
          Random.nextIntBetweenWith(minInclusive, maxExclusive)(() => randomInt, randomIntBounded)

        private def randomLong(implicit unsafe: Unsafe): Long = {
          val i1 = randomBits(32)
          val i2 = randomBits(32)
          (i1.toLong << 32) + i2
        }

        private def randomLongBounded(n: Long)(implicit unsafe: Unsafe): Long =
          Random.nextLongBoundedWith(n)(() => randomLong)

        private def randomLongBetween(minInclusive: Long, maxExclusive: Long)(implicit unsafe: Unsafe): Long =
          Random.nextLongBetweenWith(minInclusive, maxExclusive)(() => randomLong, randomLongBounded)

        private def randomPrintableChar(implicit unsafe: Unsafe): Char =
          (randomIntBounded(127 - 33) + 33).toChar

        private def randomString(length: Int)(implicit unsafe: Unsafe): String =
          List.fill(length)((randomIntBounded(0xd800 - 1) + 1).toChar).mkString
      }

    private def bufferedBoolean(buffer: Buffer): (Option[Boolean], Buffer) =
      (
        buffer.booleans.headOption,
        buffer.copy(booleans = buffer.booleans.drop(1))
      )

    private def bufferedBytes(length: Int)(buffer: Buffer): (Chunk[Byte], Buffer) = {
      val (bufferedBytes, bytes) = buffer.bytes.splitAt(length)
      (Chunk.fromIterable(bufferedBytes), buffer.copy(bytes = bytes))
    }

    private def bufferedChar(buffer: Buffer): (Option[Char], Buffer) =
      (
        buffer.chars.headOption,
        buffer.copy(chars = buffer.chars.drop(1))
      )

    private def bufferedDouble(buffer: Buffer): (Option[Double], Buffer) =
      (
        buffer.doubles.headOption,
        buffer.copy(doubles = buffer.doubles.drop(1))
      )

    private def bufferedFloat(buffer: Buffer): (Option[Float], Buffer) =
      (
        buffer.floats.headOption,
        buffer.copy(floats = buffer.floats.drop(1))
      )

    private def bufferedInt(buffer: Buffer): (Option[Int], Buffer) =
      (
        buffer.integers.headOption,
        buffer.copy(integers = buffer.integers.drop(1))
      )

    private def bufferedLong(buffer: Buffer): (Option[Long], Buffer) =
      (
        buffer.longs.headOption,
        buffer.copy(longs = buffer.longs.drop(1))
      )

    private def bufferedString(buffer: Buffer): (Option[String], Buffer) =
      (
        buffer.strings.headOption,
        buffer.copy(strings = buffer.strings.drop(1))
      )

    private def bufferedUUID(buffer: Buffer): (Option[UUID], Buffer) =
      (
        buffer.uuids.headOption,
        buffer.copy(uuids = buffer.uuids.drop(1))
      )

    @inline
    private def leastSignificantBits(x: Double): Int =
      toInt(x) & ((1 << 24) - 1)

    @inline
    private def mostSignificantBits(x: Double): Int =
      toInt(x / (1 << 24).toDouble)

    @inline
    private def toInt(x: Double): Int =
      (x.asInstanceOf[Long] | 0.asInstanceOf[Long]).asInstanceOf[Int]
  }

  /**
   * An arbitrary initial seed for the `TestRandom`. ZIO Test creates the
   * `TestRandom` of each test from it.
   */
  val DefaultData: Data = Data(1071905196, 1911589680)

  /**
   * The seed of the `TestRandom`, split into its upper half (`seed1`) and its
   * lower half (`seed2`).
   */
  final case class Data(
    seed1: Int,
    seed2: Int,
    private[TestRandom] val nextNextGaussians: Queue[Double] = Queue.empty
  )

  /**
   * Accesses the current `TestRandom` and clears the buffer of booleans.
   */
  def clearBooleans(implicit trace: Trace): UIO[Unit] =
    testRandomWith(_.clearBooleans)

  /**
   * Accesses the current `TestRandom` and clears the buffer of bytes.
   */
  def clearBytes(implicit trace: Trace): UIO[Unit] =
    testRandomWith(_.clearBytes)

  /**
   * Accesses the current `TestRandom` and clears the buffer of characters.
   */
  def clearChars(implicit trace: Trace): UIO[Unit] =
    testRandomWith(_.clearChars)

  /**
   * Accesses the current `TestRandom` and clears the buffer of doubles.
   */
  def clearDoubles(implicit trace: Trace): UIO[Unit] =
    testRandomWith(_.clearDoubles)

  /**
   * Accesses the current `TestRandom` and clears the buffer of floats.
   */
  def clearFloats(implicit trace: Trace): UIO[Unit] =
    testRandomWith(_.clearFloats)

  /**
   * Accesses the current `TestRandom` and clears the buffer of integers.
   */
  def clearInts(implicit trace: Trace): UIO[Unit] =
    testRandomWith(_.clearInts)

  /**
   * Accesses the current `TestRandom` and clears the buffer of longs.
   */
  def clearLongs(implicit trace: Trace): UIO[Unit] =
    testRandomWith(_.clearLongs)

  /**
   * Accesses the current `TestRandom` and clears the buffer of strings.
   */
  def clearStrings(implicit trace: Trace): UIO[Unit] =
    testRandomWith(_.clearStrings)

  /**
   * Accesses the current `TestRandom` and clears the buffer of UUIDs.
   */
  def clearUUIDs(implicit trace: Trace): UIO[Unit] =
    testRandomWith(_.clearUUIDs)

  /**
   * Accesses the current `TestRandom` and feeds the buffer with the specified
   * sequence of booleans.
   */
  def feedBooleans(booleans: Boolean*)(implicit trace: Trace): UIO[Unit] =
    testRandomWith(_.feedBooleans(booleans: _*))

  /**
   * Accesses the current `TestRandom` and feeds the buffer with the specified
   * sequence of chunks of bytes.
   */
  def feedBytes(bytes: Chunk[Byte]*)(implicit trace: Trace): UIO[Unit] =
    testRandomWith(_.feedBytes(bytes: _*))

  /**
   * Accesses the current `TestRandom` and feeds the buffer with the specified
   * sequence of characters.
   */
  def feedChars(chars: Char*)(implicit trace: Trace): UIO[Unit] =
    testRandomWith(_.feedChars(chars: _*))

  /**
   * Accesses the current `TestRandom` and feeds the buffer with the specified
   * sequence of doubles.
   */
  def feedDoubles(doubles: Double*)(implicit trace: Trace): UIO[Unit] =
    testRandomWith(_.feedDoubles(doubles: _*))

  /**
   * Accesses the current `TestRandom` and feeds the buffer with the specified
   * sequence of floats.
   */
  def feedFloats(floats: Float*)(implicit trace: Trace): UIO[Unit] =
    testRandomWith(_.feedFloats(floats: _*))

  /**
   * Accesses the current `TestRandom` and feeds the buffer with the specified
   * sequence of integers.
   */
  def feedInts(ints: Int*)(implicit trace: Trace): UIO[Unit] =
    testRandomWith(_.feedInts(ints: _*))

  /**
   * Accesses the current `TestRandom` and feeds the buffer with the specified
   * sequence of longs.
   */
  def feedLongs(longs: Long*)(implicit trace: Trace): UIO[Unit] =
    testRandomWith(_.feedLongs(longs: _*))

  /**
   * Accesses the current `TestRandom` and feeds the buffer with the specified
   * sequence of strings.
   */
  def feedStrings(strings: String*)(implicit trace: Trace): UIO[Unit] =
    testRandomWith(_.feedStrings(strings: _*))

  /**
   * Accesses the current `TestRandom` and feeds the buffer with the specified
   * sequence of UUIDs.
   */
  def feedUUIDs(uuids: UUID*)(implicit trace: Trace): UIO[Unit] =
    testRandomWith(_.feedUUIDs(uuids: _*))

  /**
   * Accesses the current `TestRandom` and gets its current seed, which changes
   * each time a pseudo-random value is generated.
   */
  def getSeed(implicit trace: Trace): UIO[Long] =
    testRandomWith(_.getSeed)

  /**
   * Constructs a new `TestRandom` with the specified initial state. This can be
   * useful for providing the required environment to an effect that requires a
   * `Random`, such as with `ZIO#provideLayer`. While the layer is in scope,
   * `Random` methods and the `TestRandom` accessors use the new service.
   */
  def make(data: Data): Layer[Nothing, TestRandom] = {
    implicit val trace = Tracer.newTrace
    ZLayer.scoped {
      val test = unsafe.make(data)(Unsafe)
      ZIO.withRandomScoped(test).as(test)
    }
  }

  /**
   * A layer that passes on the `TestRandom` already in its input environment.
   */
  val any: ZLayer[TestRandom, Nothing, TestRandom] =
    ZLayer.environment[TestRandom](Tracer.newTrace)

  /**
   * A layer that constructs a `TestRandom` from [[TestRandom.DefaultData]], so
   * it generates the same values on every run.
   */
  val deterministic: Layer[Nothing, TestRandom] =
    make(DefaultData)

  /**
   * A layer that constructs a `TestRandom` and sets its seed to the `nanoTime`
   * of the `Clock` in its input, so each run generates different values.
   */
  val random: ZLayer[Clock, Nothing, TestRandom] = {
    implicit val trace = Tracer.newTrace
    val layer = ZLayer {
      ZIO.fromFunctionZIO { (random: TestRandom, clock: Clock) =>
        clock.nanoTime
          .flatMap(TestRandom.setSeed(_))
          .as(random)
      }
    }
    deterministic >>> layer
  }

  /**
   * Constructs a new `Test` object that implements the `TestRandom` interface,
   * with the specified initial state. It is not installed as the `Random`
   * service; run an effect with it through `ZIO.withRandom`.
   */
  def makeTest(data: Data)(implicit trace: Trace): UIO[Test] = ZIO.succeedUnsafe { implicit unsafe =>
    TestRandom.unsafe.make(data)
  }

  /**
   * Accesses the current `TestRandom` and saves the random state in an effect
   * which, when run, will restore the `TestRandom` to the saved state.
   */
  def save(implicit trace: Trace): UIO[UIO[Unit]] =
    testRandomWith(_.save)

  /**
   * Accesses the current `TestRandom` and sets the seed to the specified value.
   */
  def setSeed(seed: => Long)(implicit trace: Trace): UIO[Unit] =
    testRandomWith(_.setSeed(seed))

  /**
   * The buffer of the `TestRandom`: one list of fed values per type, taken from
   * the head.
   */
  final case class Buffer(
    booleans: List[Boolean] = List.empty,
    bytes: List[Byte] = List.empty,
    chars: List[Char] = List.empty,
    doubles: List[Double] = List.empty,
    floats: List[Float] = List.empty,
    integers: List[Int] = List.empty,
    longs: List[Long] = List.empty,
    strings: List[String] = List.empty,
    uuids: List[UUID] = List.empty
  )
}
