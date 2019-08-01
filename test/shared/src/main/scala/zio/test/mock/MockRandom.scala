/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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
    def nextLong(n: Long): UIO[Long]
  }

  case class Mock(randomState: Ref[Data]) extends MockRandom.Service[Any] {
    import Mock._

    val nextBoolean: UIO[Boolean] =
      next(1).map(_ != 0)

    val nextDouble: UIO[Double] =
      for {
        i1 <- next(26)
        i2 <- next(27)
      } yield ((i1.toLong << 27) + i2) / (1L << 53).toDouble

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
      next(24).map(_ / (1 << 24).toFloat)

    val nextGaussian: UIO[Double] =
      //  The Box-Muller transform generates two normally distributed random
      //  doubles, so we store the second double in a queue and check the
      //  queue before computing a new pair of values to avoid wasted work.
      randomState.modify {
        case Data(seed, queue) =>
          queue.dequeueOption.fold { (Option.empty[Double], Data(seed, queue)) } {
            case (d, queue) => (Some(d), Data(seed, queue))
          }
      }.flatMap {
        case Some(nextNextGaussian) => UIO.succeed(nextNextGaussian)
        case None =>
          def loop: UIO[(Double, Double, Double)] =
            nextDouble.zip(nextDouble).flatMap {
              case (d1, d2) =>
                val v1 = 2 * d1 - 1
                val v2 = 2 * d2 - 1
                val s  = v1 * v1 + v2 * v2
                if (s >= 1 || s == 0) loop else UIO.succeed((v1, v2, s))
            }
          loop.flatMap {
            case (v1, v2, s) =>
              val multiplier = sqrt(-2 * log(s) / s)
              randomState.modify {
                case Data(seed, queue) =>
                  (v1 * multiplier, Data(seed, queue.enqueue(v2 * multiplier)))
              }
          }
      }

    val nextInt: UIO[Int] =
      next(32)

    def nextInt(n: Int): UIO[Int] =
      if (n <= 0)
        UIO.die(new IllegalArgumentException("bound must be positive"))
      else if ((n & -n) == n)
        next(31).map(x => (n * x.toLong >> 31).toInt)
      else {
        def loop: UIO[Int] =
          next(31).flatMap { i =>
            val value = i % n
            if (i - value + n - 1 < 0) loop
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
      if (n <= 0)
        UIO.die(new IllegalArgumentException("bound must be positive"))
      else {
        nextLong.flatMap { r =>
          val m = n - 1
          if ((n & m) == 0L)
            UIO.succeed(r & m)
          else {
            def loop(u: Long): UIO[Long] =
              if (u + m - u % m < 0L) nextLong.flatMap(r => loop(r >>> 1))
              else UIO.succeed(u % n)
            loop(r >>> 1)
          }
        }
      }

    val nextPrintableChar: UIO[Char] =
      nextInt(127 - 33).map(i => (i + 33).toChar)

    def nextString(length: Int): UIO[String] = {
      val safeChar = nextInt(0xD800 - 1).map(i => (i + 1).toChar)
      UIO.collectAll(List.fill(length)(safeChar)).map(_.mkString)
    }

    def shuffle[A](list: List[A]): UIO[List[A]] =
      for {
        bufferRef <- Ref.make(new scala.collection.mutable.ArrayBuffer[A])
        _         <- bufferRef.update(_ ++= list)
        swap = (i1: Int, i2: Int) =>
          bufferRef.update {
            case buffer =>
              val tmp = buffer(i1)
              buffer(i1) = buffer(i2)
              buffer(i2) = tmp
              buffer
          }
        _ <- ZIO.traverse(list.length to 2 by -1) { n: Int =>
              nextInt(n).flatMap { k =>
                swap(n - 1, k)
              }
            }
        buffer <- bufferRef.get
      } yield buffer.toList

    def setSeed(seed: Long): UIO[Unit] =
      randomState.set(Data(initialScramble(seed), Queue.empty))

    private def next(bits: Int): UIO[Int] =
      randomState.modify { data =>
        val newSeed = (data.seed * multiplier + addend) & mask
        val newData = Data(newSeed)
        val n       = (newSeed >>> (48 - bits)).toInt
        (n, newData)
      }
  }

  object Mock {
    private val multiplier = 0X5DEECE66DL
    private val addend     = 0XBL
    private val mask       = (1L << 48) - 1

    private[MockRandom] def initialScramble(seed: Long): Long =
      (seed ^ multiplier) & mask
  }

  def make(data: Data): UIO[MockRandom] =
    makeMock(data).map { mock =>
      new MockRandom {
        val random = mock
      }
    }

  def makeMock(data: Data): UIO[Mock] =
    Ref.make(data.copy(Mock.initialScramble(data.seed))).map(Mock(_))

  def setSeed(seed: Long): ZIO[MockRandom, Nothing, Unit] =
    ZIO.accessM(_.random.setSeed(seed))

  def nextLong(n: Long): ZIO[MockRandom, Nothing, Long] =
    ZIO.accessM(_.random.nextLong(n))

  val DefaultData: Data = Data(7505117374955035541L)

  final case class Data(
    seed: Long,
    private[MockRandom] val nextNextGaussians: Queue[Double] = Queue.empty
  )
}
