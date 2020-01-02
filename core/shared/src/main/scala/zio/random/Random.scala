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

package zio.random

import zio.{ Chunk, Ref, UIO, ZIO }

trait Random extends Serializable {
  def random: Random.Service[Any]
}
object Random extends Serializable {
  trait Service[R] extends Serializable {
    val nextBoolean: ZIO[R, Nothing, Boolean]
    def nextBytes(length: Int): ZIO[R, Nothing, Chunk[Byte]]
    val nextDouble: ZIO[R, Nothing, Double]
    val nextFloat: ZIO[R, Nothing, Float]
    val nextGaussian: ZIO[R, Nothing, Double]
    def nextInt(n: Int): ZIO[R, Nothing, Int]
    val nextInt: ZIO[R, Nothing, Int]
    val nextLong: ZIO[R, Nothing, Long]
    def nextLong(n: Long): ZIO[R, Nothing, Long]
    val nextPrintableChar: ZIO[R, Nothing, Char]
    def nextString(length: Int): ZIO[R, Nothing, String]
    def shuffle[A](list: List[A]): ZIO[R, Nothing, List[A]]
  }
  trait Live extends Random {
    val random: Service[Any] = new Service[Any] {
      import scala.util.{ Random => SRandom }

      val nextBoolean: UIO[Boolean] = ZIO.effectTotal(SRandom.nextBoolean())
      def nextBytes(length: Int): UIO[Chunk[Byte]] =
        ZIO.effectTotal {
          val array = Array.ofDim[Byte](length)

          SRandom.nextBytes(array)

          Chunk.fromArray(array)
        }
      val nextDouble: UIO[Double]                 = ZIO.effectTotal(SRandom.nextDouble())
      val nextFloat: UIO[Float]                   = ZIO.effectTotal(SRandom.nextFloat())
      val nextGaussian: UIO[Double]               = ZIO.effectTotal(SRandom.nextGaussian())
      def nextInt(n: Int): UIO[Int]               = ZIO.effectTotal(SRandom.nextInt(n))
      val nextInt: UIO[Int]                       = ZIO.effectTotal(SRandom.nextInt())
      val nextLong: UIO[Long]                     = ZIO.effectTotal(SRandom.nextLong())
      def nextLong(n: Long): UIO[Long]            = Random.nextLongWith(nextLong, n)
      val nextPrintableChar: UIO[Char]            = ZIO.effectTotal(SRandom.nextPrintableChar())
      def nextString(length: Int): UIO[String]    = ZIO.effectTotal(SRandom.nextString(length))
      def shuffle[A](list: List[A]): UIO[List[A]] = Random.shuffleWith(nextInt, list)
    }
  }
  object Live extends Live

  protected[zio] def shuffleWith[A](nextInt: Int => UIO[Int], list: List[A]): UIO[List[A]] =
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
      _ <- ZIO.traverse(list.length to 2 by -1) { (n: Int) =>
            nextInt(n).flatMap { k =>
              swap(n - 1, k)
            }
          }
      buffer <- bufferRef.get
    } yield buffer.toList

  protected[zio] def nextLongWith(nextLong: UIO[Long], n: Long): UIO[Long] =
    if (n <= 0)
      UIO.die(new IllegalArgumentException("n must be positive"))
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
}
