/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
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

trait Random extends Serializable {
  def nextBoolean(implicit trace: ZTraceElement): UIO[Boolean]
  def nextBytes(length: => Int)(implicit trace: ZTraceElement): UIO[Chunk[Byte]]
  def nextDouble(implicit trace: ZTraceElement): UIO[Double]
  def nextDoubleBetween(minInclusive: => Double, maxExclusive: => Double)(implicit trace: ZTraceElement): UIO[Double]
  def nextFloat(implicit trace: ZTraceElement): UIO[Float]
  def nextFloatBetween(minInclusive: => Float, maxExclusive: => Float)(implicit trace: ZTraceElement): UIO[Float]
  def nextGaussian(implicit trace: ZTraceElement): UIO[Double]
  def nextInt(implicit trace: ZTraceElement): UIO[Int]
  def nextIntBetween(minInclusive: => Int, maxExclusive: => Int)(implicit trace: ZTraceElement): UIO[Int]
  def nextIntBounded(n: => Int)(implicit trace: ZTraceElement): UIO[Int]
  def nextLong(implicit trace: ZTraceElement): UIO[Long]
  def nextLongBetween(minInclusive: => Long, maxExclusive: => Long)(implicit trace: ZTraceElement): UIO[Long]
  def nextLongBounded(n: => Long)(implicit trace: ZTraceElement): UIO[Long]
  def nextPrintableChar(implicit trace: ZTraceElement): UIO[Char]
  def nextString(length: => Int)(implicit trace: ZTraceElement): UIO[String]
  def nextUUID(implicit trace: ZTraceElement): UIO[UUID]
  def setSeed(seed: => Long)(implicit trace: ZTraceElement): UIO[Unit]
  def shuffle[A, Collection[+Element] <: Iterable[Element]](collection: => Collection[A])(implicit
    bf: BuildFrom[Collection[A], A, Collection[A]],
    trace: ZTraceElement
  ): UIO[Collection[A]]
}

object Random extends Serializable {

  object RandomLive extends Random {

    def nextBoolean(implicit trace: ZTraceElement): UIO[Boolean] =
      ZIO.succeed(scala.util.Random.nextBoolean())
    def nextBytes(length: => Int)(implicit trace: ZTraceElement): UIO[Chunk[Byte]] =
      ZIO.succeed {
        val array = Array.ofDim[Byte](length)
        scala.util.Random.nextBytes(array)
        Chunk.fromArray(array)
      }
    def nextDouble(implicit trace: ZTraceElement): UIO[Double] =
      ZIO.succeed(scala.util.Random.nextDouble())
    def nextDoubleBetween(minInclusive: => Double, maxExclusive: => Double)(implicit
      trace: ZTraceElement
    ): UIO[Double] =
      nextDoubleBetweenWith(minInclusive, maxExclusive)(nextDouble)
    def nextFloat(implicit trace: ZTraceElement): UIO[Float] =
      ZIO.succeed(scala.util.Random.nextFloat())
    def nextFloatBetween(minInclusive: => Float, maxExclusive: => Float)(implicit trace: ZTraceElement): UIO[Float] =
      nextFloatBetweenWith(minInclusive, maxExclusive)(nextFloat)
    def nextGaussian(implicit trace: ZTraceElement): UIO[Double] =
      ZIO.succeed(scala.util.Random.nextGaussian())
    def nextInt(implicit trace: ZTraceElement): UIO[Int] =
      ZIO.succeed(scala.util.Random.nextInt())
    def nextIntBetween(minInclusive: => Int, maxExclusive: => Int)(implicit trace: ZTraceElement): UIO[Int] =
      nextIntBetweenWith(minInclusive, maxExclusive)(nextInt, nextIntBounded(_))
    def nextIntBounded(n: => Int)(implicit trace: ZTraceElement): UIO[Int] =
      ZIO.succeed(scala.util.Random.nextInt(n))
    def nextLong(implicit trace: ZTraceElement): UIO[Long] =
      ZIO.succeed(scala.util.Random.nextLong())
    def nextLongBetween(minInclusive: => Long, maxExclusive: => Long)(implicit trace: ZTraceElement): UIO[Long] =
      nextLongBetweenWith(minInclusive, maxExclusive)(nextLong, nextLongBounded(_))
    def nextLongBounded(n: => Long)(implicit trace: ZTraceElement): UIO[Long] =
      Random.nextLongBoundedWith(n)(nextLong)
    def nextPrintableChar(implicit trace: ZTraceElement): UIO[Char] =
      ZIO.succeed(scala.util.Random.nextPrintableChar())
    def nextString(length: => Int)(implicit trace: ZTraceElement): UIO[String] =
      ZIO.succeed(scala.util.Random.nextString(length))
    def nextUUID(implicit trace: ZTraceElement): UIO[UUID] =
      Random.nextUUIDWith(nextLong)
    def setSeed(seed: => Long)(implicit trace: ZTraceElement): UIO[Unit] =
      ZIO.succeed(scala.util.Random.setSeed(seed))
    def shuffle[A, Collection[+Element] <: Iterable[Element]](
      collection: => Collection[A]
    )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: ZTraceElement): UIO[Collection[A]] =
      Random.shuffleWith(nextIntBounded(_), collection)
  }

  val any: ZLayer[Random, Nothing, Random] = {
    ZLayer.service[Random](Tag[Random], IsNotIntersection[Random], Tracer.newTrace)
  }

  val live: Layer[Nothing, Random] = {
    ZLayer.succeed[Random](RandomLive)(Tag[Random], IsNotIntersection[Random], Tracer.newTrace)
  }

  /**
   * Constructs a `Random` service from a `scala.util.Random`.
   */
  val scalaRandom: ZLayer[scala.util.Random, Nothing, Random] = {
    implicit val trace = Tracer.newTrace
    ZLayer {
      for {
        random <- ZIO.service[scala.util.Random]
      } yield new Random {
        def nextBoolean(implicit trace: ZTraceElement): UIO[Boolean] =
          ZIO.succeed(random.nextBoolean())
        def nextBytes(length: => Int)(implicit trace: ZTraceElement): UIO[Chunk[Byte]] =
          ZIO.succeed {
            val array = Array.ofDim[Byte](length)
            random.nextBytes(array)
            Chunk.fromArray(array)
          }
        def nextDouble(implicit trace: ZTraceElement): UIO[Double] =
          ZIO.succeed(random.nextDouble())
        def nextDoubleBetween(minInclusive: => Double, maxExclusive: => Double)(implicit
          trace: ZTraceElement
        ): UIO[Double] =
          nextDoubleBetweenWith(minInclusive, maxExclusive)(nextDouble)
        def nextFloat(implicit trace: ZTraceElement): UIO[Float] =
          ZIO.succeed(random.nextFloat())
        def nextFloatBetween(minInclusive: => Float, maxExclusive: => Float)(implicit
          trace: ZTraceElement
        ): UIO[Float] =
          nextFloatBetweenWith(minInclusive, maxExclusive)(nextFloat)
        def nextGaussian(implicit trace: ZTraceElement): UIO[Double] =
          ZIO.succeed(random.nextGaussian())
        def nextInt(implicit trace: ZTraceElement): UIO[Int] =
          ZIO.succeed(random.nextInt())
        def nextIntBetween(minInclusive: => Int, maxExclusive: => Int)(implicit trace: ZTraceElement): UIO[Int] =
          nextIntBetweenWith(minInclusive, maxExclusive)(nextInt, nextIntBounded(_))
        def nextIntBounded(n: => Int)(implicit trace: ZTraceElement): UIO[Int] =
          ZIO.succeed(random.nextInt(n))
        def nextLong(implicit trace: ZTraceElement): UIO[Long] =
          ZIO.succeed(random.nextLong())
        def nextLongBetween(minInclusive: => Long, maxExclusive: => Long)(implicit trace: ZTraceElement): UIO[Long] =
          nextLongBetweenWith(minInclusive, maxExclusive)(nextLong, nextLongBounded(_))
        def nextLongBounded(n: => Long)(implicit trace: ZTraceElement): UIO[Long] =
          Random.nextLongBoundedWith(n)(nextLong)
        def nextPrintableChar(implicit trace: ZTraceElement): UIO[Char] =
          ZIO.succeed(random.nextPrintableChar())
        def nextString(length: => Int)(implicit trace: ZTraceElement): UIO[String] =
          ZIO.succeed(random.nextString(length))
        def nextUUID(implicit trace: ZTraceElement): UIO[UUID] =
          Random.nextUUIDWith(nextLong)
        def setSeed(seed: => Long)(implicit trace: ZTraceElement): UIO[Unit] =
          ZIO.succeed(random.setSeed(seed))
        def shuffle[A, Collection[+Element] <: Iterable[Element]](
          collection: => Collection[A]
        )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: ZTraceElement): UIO[Collection[A]] =
          Random.shuffleWith(nextIntBounded(_), collection)
      }
    }
  }

  private[zio] def nextDoubleBetweenWith(minInclusive0: => Double, maxExclusive0: => Double)(
    nextDouble: UIO[Double]
  )(implicit trace: ZTraceElement): UIO[Double] =
    ZIO.suspendSucceed {
      val minInclusive = minInclusive0
      val maxExclusive = maxExclusive0

      if (minInclusive >= maxExclusive)
        UIO.die(new IllegalArgumentException("invalid bounds"))
      else
        nextDouble.map { n =>
          val result = n * (maxExclusive - minInclusive) + minInclusive
          if (result < maxExclusive) result
          else Math.nextAfter(maxExclusive, Float.NegativeInfinity)
        }
    }

  private[zio] def nextFloatBetweenWith(minInclusive0: => Float, maxExclusive0: => Float)(
    nextFloat: UIO[Float]
  )(implicit trace: ZTraceElement): UIO[Float] =
    ZIO.suspendSucceed {
      val minInclusive = minInclusive0
      val maxExclusive = maxExclusive0

      if (minInclusive >= maxExclusive)
        UIO.die(new IllegalArgumentException("invalid bounds"))
      else
        nextFloat.map { n =>
          val result = n * (maxExclusive - minInclusive) + minInclusive
          if (result < maxExclusive) result
          else Math.nextAfter(maxExclusive, Float.NegativeInfinity)
        }
    }

  private[zio] def nextIntBetweenWith(
    minInclusive0: => Int,
    maxExclusive0: => Int
  )(nextInt: UIO[Int], nextIntBounded: Int => UIO[Int])(implicit trace: ZTraceElement): UIO[Int] =
    ZIO.suspendSucceed {
      val minInclusive = minInclusive0
      val maxExclusive = maxExclusive0

      if (minInclusive >= maxExclusive) {
        UIO.die(new IllegalArgumentException("invalid bounds"))
      } else {
        val difference = maxExclusive - minInclusive
        if (difference > 0) nextIntBounded(difference).map(_ + minInclusive)
        else nextInt.repeatUntil(n => minInclusive <= n && n < maxExclusive)
      }
    }

  private[zio] def nextLongBetweenWith(
    minInclusive0: => Long,
    maxExclusive0: => Long
  )(nextLong: UIO[Long], nextLongBounded: Long => UIO[Long])(implicit trace: ZTraceElement): UIO[Long] =
    ZIO.suspendSucceed {
      val minInclusive = minInclusive0
      val maxExclusive = maxExclusive0

      if (minInclusive >= maxExclusive)
        UIO.die(new IllegalArgumentException("invalid bounds"))
      else {
        val difference = maxExclusive - minInclusive
        if (difference > 0) nextLongBounded(difference).map(_ + minInclusive)
        else nextLong.repeatUntil(n => minInclusive <= n && n < maxExclusive)
      }
    }

  private[zio] def nextLongBoundedWith(n0: => Long)(nextLong: => UIO[Long])(implicit trace: ZTraceElement): UIO[Long] =
    ZIO.suspendSucceed {
      val n = n0

      if (n <= 0)
        UIO.die(new IllegalArgumentException("n must be positive"))
      else {
        nextLong.flatMap { r =>
          val m = n - 1
          if ((n & m) == 0L)
            UIO.succeedNow(r & m)
          else {
            def loop(u: Long): UIO[Long] =
              if (u + m - u % m < 0L) nextLong.flatMap(r => loop(r >>> 1))
              else UIO.succeedNow(u % n)
            loop(r >>> 1)
          }
        }
      }
    }

  private[zio] def nextUUIDWith(nextLong: UIO[Long])(implicit trace: ZTraceElement): UIO[UUID] =
    for {
      mostSigBits  <- nextLong
      leastSigBits <- nextLong
    } yield new UUID(
      (mostSigBits & ~0x0000f000) | 0x00004000,
      (leastSigBits & ~(0xc0000000L << 32)) | (0x80000000L << 32)
    )

  private[zio] def shuffleWith[A, Collection[+Element] <: Iterable[Element]](
    nextIntBounded: Int => UIO[Int],
    collection0: => Collection[A]
  )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: ZTraceElement): UIO[Collection[A]] =
    ZIO.suspendSucceed {
      val collection = collection0

      for {
        buffer <- ZIO.succeed {
                    val buffer = new scala.collection.mutable.ArrayBuffer[A]
                    buffer ++= collection
                  }
        swap = (i1: Int, i2: Int) =>
                 ZIO.succeed {
                   val tmp = buffer(i1)
                   buffer(i1) = buffer(i2)
                   buffer(i2) = tmp
                   buffer
                 }
        _ <-
          ZIO.foreachDiscard((collection.size to 2 by -1).toList)((n: Int) =>
            nextIntBounded(n).flatMap(k => swap(n - 1, k))
          )
      } yield bf.fromSpecific(collection)(buffer)
    }

  // Accessor Methods

  /**
   * generates a pseudo-random boolean.
   */
  def nextBoolean(implicit trace: ZTraceElement): URIO[Random, Boolean] =
    ZIO.serviceWithZIO(_.nextBoolean)

  /**
   * Generates a pseudo-random chunk of bytes of the specified length.
   */
  def nextBytes(length: => Int)(implicit trace: ZTraceElement): ZIO[Random, Nothing, Chunk[Byte]] =
    ZIO.serviceWithZIO(_.nextBytes(length))

  /**
   * Generates a pseudo-random, uniformly distributed double between 0.0 and
   * 1.0.
   */
  def nextDouble(implicit trace: ZTraceElement): URIO[Random, Double] =
    ZIO.serviceWithZIO(_.nextDouble)

  /**
   * Generates a pseudo-random double in the specified range.
   */
  def nextDoubleBetween(minInclusive: => Double, maxExclusive: => Double)(implicit
    trace: ZTraceElement
  ): URIO[Random, Double] =
    ZIO.serviceWithZIO(_.nextDoubleBetween(minInclusive, maxExclusive))

  /**
   * Generates a pseudo-random, uniformly distributed float between 0.0 and
   * 1.0.
   */
  def nextFloat(implicit trace: ZTraceElement): URIO[Random, Float] =
    ZIO.serviceWithZIO(_.nextFloat)

  /**
   * Generates a pseudo-random float in the specified range.
   */
  def nextFloatBetween(minInclusive: => Float, maxExclusive: => Float)(implicit
    trace: ZTraceElement
  ): URIO[Random, Float] =
    ZIO.serviceWithZIO(_.nextFloatBetween(minInclusive, maxExclusive))

  /**
   * Generates a pseudo-random double from a normal distribution with mean 0.0
   * and standard deviation 1.0.
   */
  def nextGaussian(implicit trace: ZTraceElement): URIO[Random, Double] =
    ZIO.serviceWithZIO(_.nextGaussian)

  /**
   * Generates a pseudo-random integer.
   */
  def nextInt(implicit trace: ZTraceElement): URIO[Random, Int] =
    ZIO.serviceWithZIO(_.nextInt)

  /**
   * Generates a pseudo-random integer in the specified range.
   */
  def nextIntBetween(minInclusive: => Int, maxExclusive: => Int)(implicit
    trace: ZTraceElement
  ): URIO[Random, Int] =
    ZIO.serviceWithZIO(_.nextIntBetween(minInclusive, maxExclusive))

  /**
   * Generates a pseudo-random integer between 0 (inclusive) and the specified
   * value (exclusive).
   */
  def nextIntBounded(n: => Int)(implicit trace: ZTraceElement): URIO[Random, Int] =
    ZIO.serviceWithZIO(_.nextIntBounded(n))

  /**
   * Generates a pseudo-random long.
   */
  def nextLong(implicit trace: ZTraceElement): URIO[Random, Long] =
    ZIO.serviceWithZIO(_.nextLong)

  /**
   * Generates a pseudo-random long in the specified range.
   */
  def nextLongBetween(minInclusive: => Long, maxExclusive: => Long)(implicit
    trace: ZTraceElement
  ): URIO[Random, Long] =
    ZIO.serviceWithZIO(_.nextLongBetween(minInclusive, maxExclusive))

  /**
   * Generates a pseudo-random long between 0 (inclusive) and the specified
   * value (exclusive).
   */
  def nextLongBounded(n: => Long)(implicit trace: ZTraceElement): URIO[Random, Long] =
    ZIO.serviceWithZIO(_.nextLongBounded(n))

  /**
   * Generates psuedo-random universally unique identifiers.
   */
  def nextUUID(implicit trace: ZTraceElement): URIO[Random, UUID] =
    ZIO.serviceWithZIO(_.nextUUID)

  /**
   * Generates a pseudo-random character from the ASCII range 33-126.
   */
  def nextPrintableChar(implicit trace: ZTraceElement): URIO[Random, Char] =
    ZIO.serviceWithZIO(_.nextPrintableChar)

  /**
   * Generates a pseudo-random string of the specified length.
   */
  def nextString(length: => Int)(implicit trace: ZTraceElement): URIO[Random, String] =
    ZIO.serviceWithZIO(_.nextString(length))

  /**
   * Sets the seed of this random number generator.
   */
  def setSeed(seed: => Long)(implicit trace: ZTraceElement): URIO[Random, Unit] =
    ZIO.serviceWithZIO(_.setSeed(seed))

  /**
   * Randomly shuffles the specified list.
   */
  def shuffle[A](list: => List[A])(implicit trace: ZTraceElement): ZIO[Random, Nothing, List[A]] =
    ZIO.serviceWithZIO(_.shuffle(list))
}
