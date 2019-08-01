/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
 * Copyright 2014-2019 EPFL
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

package zio.test.mock

import scala.collection.immutable.Queue
import scala.math.{ log, sqrt }

import zio.{ Chunk, Ref, UIO, ZIO }
import zio.random.Random

trait MockRandom extends Random {
  val random: MockRandom.Service[Any]
}

object MockRandom {

  trait Service[R] extends Random.Service[R] {
    def setSeed(seed: Long): UIO[Unit]
  }

  /**
   * Adapted from @gzmo work in Scala.js (https://github.com/scala-js/scala-js/pull/780)
   */
  case class Mock(randomState: Ref[Data]) extends MockRandom.Service[Any] {

    val nextBoolean: UIO[Boolean] =
      next(1).map(_ != 0)

    val nextDouble: UIO[Double] =
      for {
        i1 <- next(26)
        i2 <- next(27)
      } yield ((i1.toDouble * (1L << 27).toDouble) + i2.toDouble) / (1L << 53).toDouble

    def nextBytes(length: Int): UIO[Chunk[Byte]] = {
      //  Our RNG generates 32 bit integers so to maximize efficieny we want to
      //  pull 8 bit bytes from the current integer until it is exhausted
      //  before generating another random integer
      def loop(i: Int, rnd: UIO[Int], n: Int, acc: UIO[List[Byte]]): UIO[List[Byte]] =
        if (i == length)
          acc.map(_.reverse)
        else if (n > 0)
          rnd.flatMap(rnd => loop(i + 1, UIO.succeed(rnd >> 8), n - 1, acc.map(rnd.toByte :: _)))
        else
          loop(i, nextInt, (length - i) min 4, acc)

      loop(0, nextInt, length min 4, UIO.succeed(List.empty[Byte])).map(Chunk.fromIterable)
    }

    val nextFloat: UIO[Float] =
      next(24).map(i => (i.toDouble / (1 << 24).toDouble).toFloat)

    val nextGaussian: UIO[Double] =
      //  The Box-Muller transform generates two normally distributed random
      //  doubles, so we store the second double in a queue and check the
      //  queue before computing a new pair of values to avoid wasted work.
      randomState.modify {
        case Data(seed1, seed2, queue) =>
          queue.dequeueOption.fold { (Option.empty[Double], Data(seed1, seed2, queue)) } {
            case (d, queue) => (Some(d), Data(seed1, seed2, queue))
          }
      }.flatMap {
        case Some(nextNextGaussian) => UIO.succeed(nextNextGaussian)
        case None =>
          def loop: UIO[(Double, Double, Double)] =
            nextDouble.zip(nextDouble).flatMap {
              case (d1, d2) =>
                val x      = 2 * d1 - 1
                val y      = 2 * d2 - 1
                val radius = x * x + y * y
                if (radius >= 1 || radius == 0) loop else UIO.succeed((x, y, radius))
            }
          loop.flatMap {
            case (x, y, radius) =>
              val c = sqrt(-2 * log(radius) / radius)
              randomState.modify {
                case Data(seed1, seed2, queue) =>
                  (x * c, Data(seed1, seed2, queue.enqueue(y * c)))
              }
          }
      }

    val nextInt: UIO[Int] =
      next(32)

    def nextInt(n: Int): UIO[Int] =
      if (n <= 0)
        UIO.die(new IllegalArgumentException("n must be positive"))
      else if ((n & -n) == n)
        next(31).map(_ >> Integer.numberOfLeadingZeros(n))
      else {
        def loop: UIO[Int] =
          next(31).flatMap { i =>
            val value = i % n
            if (i - value + (n - 1) < 0) loop
            else UIO.succeed(value)
          }
        loop
      }

    val nextLong: UIO[Long] =
      for {
        i1 <- next(32)
        i2 <- next(32)
      } yield ((i1.toLong << 32) + i2)

    def nextLong(n: Long): UIO[Long] =
      Random.nextLongWith(nextLong, n)

    val nextPrintableChar: UIO[Char] =
      nextInt(127 - 33).map(i => (i + 33).toChar)

    def nextString(length: Int): UIO[String] = {
      val safeChar = nextInt(0xD800 - 1).map(i => (i + 1).toChar)
      UIO.collectAll(List.fill(length)(safeChar)).map(_.mkString)
    }

    def shuffle[A](list: List[A]): UIO[List[A]] =
      Random.shuffleWith(nextInt, list)

    def setSeed(seed: Long): UIO[Unit] =
      randomState.set {
        val newSeed = (seed ^ 0X5DEECE66DL) & ((1L << 48) - 1)
        val seed1   = (newSeed >>> 24).toInt
        val seed2   = newSeed.toInt & ((1 << 24) - 1)
        Data(seed1, seed2, Queue.empty)
      }

    private def next(bits: Int): UIO[Int] =
      randomState.modify { data =>
        val multiplier  = 0X5DEECE66DL
        val multiplier1 = (multiplier >>> 24).toInt
        val multiplier2 = multiplier.toInt & ((1 << 24) - 1)
        val product1    = data.seed2.toDouble * multiplier1.toDouble + data.seed1.toDouble * multiplier2.toDouble
        val product2    = data.seed2.toDouble * multiplier2.toDouble + 0xB
        val newSeed1    = (mostSignificantBits(product2) + leastSignificantBits(product1)) & ((1 << 24) - 1)
        val newSeed2    = leastSignificantBits(product2)
        val result      = (newSeed1 << 8) | (newSeed2 >> 16)
        (result >>> (32 - bits), Data(newSeed1, newSeed2, data.nextNextGaussians))
      }

    @inline
    private def mostSignificantBits(x: Double): Int =
      toInt((x / (1 << 24).toDouble))

    @inline
    private def leastSignificantBits(x: Double): Int =
      toInt(x) & ((1 << 24) - 1)

    @inline
    private def toInt(x: Double): Int =
      (x.asInstanceOf[Long] | 0.asInstanceOf[Long]).asInstanceOf[Int]
  }

  def make(data: Data): UIO[MockRandom] =
    makeMock(data).map { mock =>
      new MockRandom {
        val random = mock
      }
    }

  def makeMock(data: Data): UIO[Mock] =
    Ref.make(data).map(Mock(_))

  def setSeed(seed: Long): ZIO[MockRandom, Nothing, Unit] =
    ZIO.accessM(_.random.setSeed(seed))

  def nextLong(n: Long): ZIO[MockRandom, Nothing, Long] =
    ZIO.accessM(_.random.nextLong(n))

  val DefaultData: Data = Data(1071905196, 1911589680)

  final case class Data(
    seed1: Int,
    seed2: Int,
    private[MockRandom] val nextNextGaussians: Queue[Double] = Queue.empty
  )
}
