/*
 * Copyright 2017-2022 John A. De Goes and the ZIO Contributors
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

import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.UUID
import scala.annotation.tailrec

trait Random extends Serializable {
  def nextBoolean(implicit trace: Trace): UIO[Boolean]
  def nextBytes(length: => Int)(implicit trace: Trace): UIO[Chunk[Byte]]
  def nextDouble(implicit trace: Trace): UIO[Double]
  def nextDoubleBetween(minInclusive: => Double, maxExclusive: => Double)(implicit trace: Trace): UIO[Double]
  def nextFloat(implicit trace: Trace): UIO[Float]
  def nextFloatBetween(minInclusive: => Float, maxExclusive: => Float)(implicit trace: Trace): UIO[Float]
  def nextGaussian(implicit trace: Trace): UIO[Double]
  def nextInt(implicit trace: Trace): UIO[Int]
  def nextIntBetween(minInclusive: => Int, maxExclusive: => Int)(implicit trace: Trace): UIO[Int]
  def nextIntBounded(n: => Int)(implicit trace: Trace): UIO[Int]
  def nextLong(implicit trace: Trace): UIO[Long]
  def nextLongBetween(minInclusive: => Long, maxExclusive: => Long)(implicit trace: Trace): UIO[Long]
  def nextLongBounded(n: => Long)(implicit trace: Trace): UIO[Long]
  def nextPrintableChar(implicit trace: Trace): UIO[Char]
  def nextString(length: => Int)(implicit trace: Trace): UIO[String]
  def nextUUID(implicit trace: Trace): UIO[UUID]
  def setSeed(seed: => Long)(implicit trace: Trace): UIO[Unit]
  def shuffle[A, Collection[+Element] <: Iterable[Element]](collection: => Collection[A])(implicit
    bf: BuildFrom[Collection[A], A, Collection[A]],
    trace: Trace
  ): UIO[Collection[A]]
  private[zio] def unsafeNextBoolean(): Boolean =
    Runtime.default.unsafeRun(nextBoolean(Trace.empty))(Trace.empty)
  private[zio] def unsafeNextBytes(length: Int): Chunk[Byte] =
    Runtime.default.unsafeRun(nextBytes(length)(Trace.empty))(Trace.empty)
  private[zio] def unsafeNextDouble(): Double =
    Runtime.default.unsafeRun(nextDouble(Trace.empty))(Trace.empty)
  private[zio] def unsafeNextDoubleBetween(minInclusive: Double, maxExclusive: Double): Double =
    Runtime.default.unsafeRun(nextDoubleBetween(minInclusive, maxExclusive)(Trace.empty))(Trace.empty)
  private[zio] def unsafeNextFloat(): Float =
    Runtime.default.unsafeRun(nextFloat(Trace.empty))(Trace.empty)
  private[zio] def unsafeNextFloatBetween(minInclusive: Float, maxExclusive: Float): Float =
    Runtime.default.unsafeRun(nextFloatBetween(minInclusive, maxExclusive)(Trace.empty))(Trace.empty)
  private[zio] def unsafeNextGaussian(): Double =
    Runtime.default.unsafeRun(nextGaussian(Trace.empty))(Trace.empty)
  private[zio] def unsafeNextInt(): Int =
    Runtime.default.unsafeRun(nextInt(Trace.empty))(Trace.empty)
  private[zio] def unsafeNextIntBetween(minInclusive: Int, maxExclusive: Int): Int =
    Runtime.default.unsafeRun(nextIntBetween(minInclusive, maxExclusive)(Trace.empty))(Trace.empty)
  private[zio] def unsafeNextIntBounded(n: Int): Int =
    Runtime.default.unsafeRun(nextIntBounded(n)(Trace.empty))(Trace.empty)
  private[zio] def unsafeNextLong(): Long =
    Runtime.default.unsafeRun(nextLong(Trace.empty))(Trace.empty)
  private[zio] def unsafeNextLongBetween(minInclusive: Long, maxExclusive: Long): Long =
    Runtime.default.unsafeRun(nextLongBetween(minInclusive, maxExclusive)(Trace.empty))(Trace.empty)
  private[zio] def unsafeNextLongBounded(n: Long): Long =
    Runtime.default.unsafeRun(nextLongBounded(n)(Trace.empty))(Trace.empty)
  private[zio] def unsafeNextPrintableChar(): Char =
    Runtime.default.unsafeRun(nextPrintableChar(Trace.empty))(Trace.empty)
  private[zio] def unsafeNextString(length: Int): String =
    Runtime.default.unsafeRun(nextString(length)(Trace.empty))(Trace.empty)
  private[zio] def unsafeNextUUID(): UUID =
    Runtime.default.unsafeRun(nextUUID(Trace.empty))(Trace.empty)
  private[zio] def unsafeSetSeed(seed: Long): Unit =
    Runtime.default.unsafeRun(setSeed(seed)(Trace.empty))(Trace.empty)
  private[zio] def unsafeShuffle[A, Collection[+Element] <: Iterable[Element]](collection: Collection[A])(implicit
    bf: BuildFrom[Collection[A], A, Collection[A]]
  ): Collection[A] =
    Runtime.default.unsafeRun(shuffle(collection)(bf, Trace.empty))(Trace.empty)
}

object Random extends Serializable {

  implicit val tag: Tag[Random] = Tag[Random]

  object RandomLive extends Random {

    def nextBoolean(implicit trace: Trace): UIO[Boolean] =
      ZIO.succeed(unsafeNextBoolean())
    def nextBytes(length: => Int)(implicit trace: Trace): UIO[Chunk[Byte]] =
      ZIO.succeed(unsafeNextBytes(length))
    def nextDouble(implicit trace: Trace): UIO[Double] =
      ZIO.succeed(unsafeNextDouble())
    def nextDoubleBetween(minInclusive: => Double, maxExclusive: => Double)(implicit
      trace: Trace
    ): UIO[Double] =
      ZIO.succeed(unsafeNextDoubleBetween(minInclusive, maxExclusive))
    def nextFloat(implicit trace: Trace): UIO[Float] =
      ZIO.succeed(unsafeNextFloat())
    def nextFloatBetween(minInclusive: => Float, maxExclusive: => Float)(implicit trace: Trace): UIO[Float] =
      ZIO.succeed(unsafeNextFloatBetween(minInclusive, maxExclusive))
    def nextGaussian(implicit trace: Trace): UIO[Double] =
      ZIO.succeed(unsafeNextGaussian())
    def nextInt(implicit trace: Trace): UIO[Int] =
      ZIO.succeed(unsafeNextInt())
    def nextIntBetween(minInclusive: => Int, maxExclusive: => Int)(implicit trace: Trace): UIO[Int] =
      ZIO.succeed(unsafeNextIntBetween(minInclusive, maxExclusive))
    def nextIntBounded(n: => Int)(implicit trace: Trace): UIO[Int] =
      ZIO.succeed(unsafeNextIntBounded(n))
    def nextLong(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(unsafeNextLong())
    def nextLongBetween(minInclusive: => Long, maxExclusive: => Long)(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(unsafeNextLongBetween(minInclusive, maxExclusive))
    def nextLongBounded(n: => Long)(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(unsafeNextLongBounded(n))
    def nextPrintableChar(implicit trace: Trace): UIO[Char] =
      ZIO.succeed(unsafeNextPrintableChar())
    def nextString(length: => Int)(implicit trace: Trace): UIO[String] =
      ZIO.succeed(unsafeNextString(length))
    def nextUUID(implicit trace: Trace): UIO[UUID] =
      ZIO.succeed(unsafeNextUUID())
    def setSeed(seed: => Long)(implicit trace: Trace): UIO[Unit] =
      ZIO.succeed(unsafeSetSeed(seed))
    def shuffle[A, Collection[+Element] <: Iterable[Element]](
      collection: => Collection[A]
    )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: Trace): UIO[Collection[A]] =
      ZIO.succeed(unsafeShuffle(collection))
    override private[zio] def unsafeNextBoolean(): Boolean =
      scala.util.Random.nextBoolean()
    override private[zio] def unsafeNextBytes(length: Int): Chunk[Byte] = {
      val array = Array.ofDim[Byte](length)
      scala.util.Random.nextBytes(array)
      Chunk.fromArray(array)
    }
    override private[zio] def unsafeNextDouble(): Double =
      scala.util.Random.nextDouble()
    override private[zio] def unsafeNextDoubleBetween(minInclusive: Double, maxExclusive: Double): Double =
      nextDoubleBetweenWith(minInclusive, maxExclusive)(() => unsafeNextDouble())
    override private[zio] def unsafeNextFloat(): Float =
      scala.util.Random.nextFloat()
    override private[zio] def unsafeNextFloatBetween(minInclusive: Float, maxExclusive: Float): Float =
      nextFloatBetweenWith(minInclusive, maxExclusive)(() => unsafeNextFloat())
    override private[zio] def unsafeNextGaussian(): Double =
      scala.util.Random.nextGaussian()
    override private[zio] def unsafeNextInt(): Int =
      scala.util.Random.nextInt()
    override private[zio] def unsafeNextIntBetween(minInclusive: Int, maxExclusive: Int): Int =
      nextIntBetweenWith(minInclusive, maxExclusive)(() => unsafeNextInt(), unsafeNextIntBounded(_))
    override private[zio] def unsafeNextIntBounded(n: Int): Int =
      scala.util.Random.nextInt(n)
    override private[zio] def unsafeNextLong(): Long =
      scala.util.Random.nextLong()
    override private[zio] def unsafeNextLongBetween(minInclusive: Long, maxExclusive: Long): Long =
      nextLongBetweenWith(minInclusive, maxExclusive)(() => unsafeNextLong(), unsafeNextLongBounded(_))
    override private[zio] def unsafeNextLongBounded(n: Long): Long =
      Random.nextLongBoundedWith(n)(() => unsafeNextLong())
    override private[zio] def unsafeNextPrintableChar(): Char =
      scala.util.Random.nextPrintableChar()
    override private[zio] def unsafeNextString(length: Int): String =
      scala.util.Random.nextString(length)
    override private[zio] def unsafeNextUUID(): UUID =
      Random.nextUUIDWith(() => unsafeNextLong())
    override private[zio] def unsafeSetSeed(seed: Long): Unit =
      scala.util.Random.setSeed(seed)
    override private[zio] def unsafeShuffle[A, Collection[+Element] <: Iterable[Element]](collection: Collection[A])(
      implicit bf: BuildFrom[Collection[A], A, Collection[A]]
    ): Collection[A] =
      Random.shuffleWith(unsafeNextIntBounded(_), collection)
  }

  /**
   * An implementation of the `Random` service backed by a `scala.util.Random`.
   */
  final case class RandomScala(random: scala.util.Random) extends Random {
    def nextBoolean(implicit trace: Trace): UIO[Boolean] =
      ZIO.succeed(unsafeNextBoolean())
    def nextBytes(length: => Int)(implicit trace: Trace): UIO[Chunk[Byte]] =
      ZIO.succeed(unsafeNextBytes(length))
    def nextDouble(implicit trace: Trace): UIO[Double] =
      ZIO.succeed(unsafeNextDouble())
    def nextDoubleBetween(minInclusive: => Double, maxExclusive: => Double)(implicit
      trace: Trace
    ): UIO[Double] =
      ZIO.succeed(unsafeNextDoubleBetween(minInclusive, maxExclusive))
    def nextFloat(implicit trace: Trace): UIO[Float] =
      ZIO.succeed(unsafeNextFloat())
    def nextFloatBetween(minInclusive: => Float, maxExclusive: => Float)(implicit
      trace: Trace
    ): UIO[Float] =
      ZIO.succeed(unsafeNextFloatBetween(minInclusive, maxExclusive))
    def nextGaussian(implicit trace: Trace): UIO[Double] =
      ZIO.succeed(unsafeNextGaussian())
    def nextInt(implicit trace: Trace): UIO[Int] =
      ZIO.succeed(unsafeNextInt())
    def nextIntBetween(minInclusive: => Int, maxExclusive: => Int)(implicit trace: Trace): UIO[Int] =
      ZIO.succeed(unsafeNextIntBetween(minInclusive, maxExclusive))
    def nextIntBounded(n: => Int)(implicit trace: Trace): UIO[Int] =
      ZIO.succeed(unsafeNextIntBounded(n))
    def nextLong(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(unsafeNextLong())
    def nextLongBetween(minInclusive: => Long, maxExclusive: => Long)(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(unsafeNextLongBetween(minInclusive, maxExclusive))
    def nextLongBounded(n: => Long)(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(unsafeNextLongBounded(n))
    def nextPrintableChar(implicit trace: Trace): UIO[Char] =
      ZIO.succeed(unsafeNextPrintableChar())
    def nextString(length: => Int)(implicit trace: Trace): UIO[String] =
      ZIO.succeed(unsafeNextString(length))
    def nextUUID(implicit trace: Trace): UIO[UUID] =
      ZIO.succeed(unsafeNextUUID())
    def setSeed(seed: => Long)(implicit trace: Trace): UIO[Unit] =
      ZIO.succeed(unsafeSetSeed(seed))
    def shuffle[A, Collection[+Element] <: Iterable[Element]](
      collection: => Collection[A]
    )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: Trace): UIO[Collection[A]] =
      ZIO.succeed(unsafeShuffle(collection))
    override private[zio] def unsafeNextBoolean(): Boolean =
      random.nextBoolean()
    override private[zio] def unsafeNextBytes(length: Int): Chunk[Byte] = {
      val array = Array.ofDim[Byte](length)
      random.nextBytes(array)
      Chunk.fromArray(array)
    }
    override private[zio] def unsafeNextDouble(): Double =
      random.nextDouble()
    override private[zio] def unsafeNextDoubleBetween(minInclusive: Double, maxExclusive: Double): Double =
      nextDoubleBetweenWith(minInclusive, maxExclusive)(() => unsafeNextDouble())
    override private[zio] def unsafeNextFloat(): Float =
      random.nextFloat()
    override private[zio] def unsafeNextFloatBetween(minInclusive: Float, maxExclusive: Float): Float =
      nextFloatBetweenWith(minInclusive, maxExclusive)(() => unsafeNextFloat())
    override private[zio] def unsafeNextGaussian(): Double =
      random.nextGaussian()
    override private[zio] def unsafeNextInt(): Int =
      random.nextInt()
    override private[zio] def unsafeNextIntBetween(minInclusive: Int, maxExclusive: Int): Int =
      nextIntBetweenWith(minInclusive, maxExclusive)(() => unsafeNextInt(), unsafeNextIntBounded(_))
    override private[zio] def unsafeNextIntBounded(n: Int): Int =
      random.nextInt(n)
    override private[zio] def unsafeNextLong(): Long =
      random.nextLong()
    override private[zio] def unsafeNextLongBetween(minInclusive: Long, maxExclusive: Long): Long =
      nextLongBetweenWith(minInclusive, maxExclusive)(() => unsafeNextLong(), unsafeNextLongBounded(_))
    override private[zio] def unsafeNextLongBounded(n: Long): Long =
      Random.nextLongBoundedWith(n)(() => unsafeNextLong())
    override private[zio] def unsafeNextPrintableChar(): Char =
      random.nextPrintableChar()
    override private[zio] def unsafeNextString(length: Int): String =
      random.nextString(length)
    override private[zio] def unsafeNextUUID(): UUID =
      Random.nextUUIDWith(() => unsafeNextLong())
    override private[zio] def unsafeSetSeed(seed: Long): Unit =
      random.setSeed(seed)
    override private[zio] def unsafeShuffle[A, Collection[+Element] <: Iterable[Element]](
      collection: Collection[A]
    )(implicit
      bf: BuildFrom[Collection[A], A, Collection[A]]
    ): Collection[A] =
      Random.shuffleWith(unsafeNextIntBounded(_), collection)
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
   * generates a pseudo-random boolean.
   */
  def nextBoolean(implicit trace: Trace): UIO[Boolean] =
    ZIO.randomWith(_.nextBoolean)

  /**
   * Generates a pseudo-random chunk of bytes of the specified length.
   */
  def nextBytes(length: => Int)(implicit trace: Trace): UIO[Chunk[Byte]] =
    ZIO.randomWith(_.nextBytes(length))

  /**
   * Generates a pseudo-random, uniformly distributed double between 0.0 and
   * 1.0.
   */
  def nextDouble(implicit trace: Trace): UIO[Double] =
    ZIO.randomWith(_.nextDouble)

  /**
   * Generates a pseudo-random double in the specified range.
   */
  def nextDoubleBetween(minInclusive: => Double, maxExclusive: => Double)(implicit
    trace: Trace
  ): UIO[Double] =
    ZIO.randomWith(_.nextDoubleBetween(minInclusive, maxExclusive))

  /**
   * Generates a pseudo-random, uniformly distributed float between 0.0 and
   * 1.0.
   */
  def nextFloat(implicit trace: Trace): UIO[Float] =
    ZIO.randomWith(_.nextFloat)

  /**
   * Generates a pseudo-random float in the specified range.
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
   * Generates a pseudo-random integer in the specified range.
   */
  def nextIntBetween(minInclusive: => Int, maxExclusive: => Int)(implicit
    trace: Trace
  ): UIO[Int] =
    ZIO.randomWith(_.nextIntBetween(minInclusive, maxExclusive))

  /**
   * Generates a pseudo-random integer between 0 (inclusive) and the specified
   * value (exclusive).
   */
  def nextIntBounded(n: => Int)(implicit trace: Trace): UIO[Int] =
    ZIO.randomWith(_.nextIntBounded(n))

  /**
   * Generates a pseudo-random long.
   */
  def nextLong(implicit trace: Trace): UIO[Long] =
    ZIO.randomWith(_.nextLong)

  /**
   * Generates a pseudo-random long in the specified range.
   */
  def nextLongBetween(minInclusive: => Long, maxExclusive: => Long)(implicit
    trace: Trace
  ): UIO[Long] =
    ZIO.randomWith(_.nextLongBetween(minInclusive, maxExclusive))

  /**
   * Generates a pseudo-random long between 0 (inclusive) and the specified
   * value (exclusive).
   */
  def nextLongBounded(n: => Long)(implicit trace: Trace): UIO[Long] =
    ZIO.randomWith(_.nextLongBounded(n))

  /**
   * Generates psuedo-random universally unique identifiers.
   */
  def nextUUID(implicit trace: Trace): UIO[UUID] =
    ZIO.randomWith(_.nextUUID)

  /**
   * Generates a pseudo-random character from the ASCII range 33-126.
   */
  def nextPrintableChar(implicit trace: Trace): UIO[Char] =
    ZIO.randomWith(_.nextPrintableChar)

  /**
   * Generates a pseudo-random string of the specified length.
   */
  def nextString(length: => Int)(implicit trace: Trace): UIO[String] =
    ZIO.randomWith(_.nextString(length))

  /**
   * Sets the seed of this random number generator.
   */
  def setSeed(seed: => Long)(implicit trace: Trace): UIO[Unit] =
    ZIO.randomWith(_.setSeed(seed))

  /**
   * Randomly shuffles the specified list.
   */
  def shuffle[A](list: => List[A])(implicit trace: Trace): UIO[List[A]] =
    ZIO.randomWith(_.shuffle(list))
}
