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
  val random: Random.Service
}
object Random extends Serializable {
  trait Service extends Serializable {
    val nextBoolean: UIO[Boolean]
    def nextBytes(length: Int): UIO[Chunk[Byte]]
    val nextDouble: UIO[Double]
    val nextFloat: UIO[Float]
    val nextGaussian: UIO[Double]
    def nextInt(n: Int): UIO[Int]
    val nextInt: UIO[Int]
    val nextLong: UIO[Long]
    val nextPrintableChar: UIO[Char]
    def nextString(length: Int): UIO[String]
    def shuffle[A](list: List[A]): UIO[List[A]]
  }
  trait Live extends Random {
    val random: Service = new Service {
      import scala.util.{ Random => SRandom }

      val nextBoolean: UIO[Boolean] = UIO.effectTotal(SRandom.nextBoolean())
      def nextBytes(length: Int): UIO[Chunk[Byte]] =
        ZIO.effectTotal {
          val array = Array.ofDim[Byte](length)

          SRandom.nextBytes(array)

          Chunk.fromArray(array)
        }
      val nextDouble: UIO[Double]                 = UIO.effectTotal(SRandom.nextDouble())
      val nextFloat: UIO[Float]                   = UIO.effectTotal(SRandom.nextFloat())
      val nextGaussian: UIO[Double]               = UIO.effectTotal(SRandom.nextGaussian())
      def nextInt(n: Int): UIO[Int]               = UIO.effectTotal(SRandom.nextInt(n))
      val nextInt: UIO[Int]                       = UIO.effectTotal(SRandom.nextInt())
      val nextLong: UIO[Long]                     = UIO.effectTotal(SRandom.nextLong())
      val nextPrintableChar: UIO[Char]            = UIO.effectTotal(SRandom.nextPrintableChar())
      def nextString(length: Int): UIO[String]    = UIO.effectTotal(SRandom.nextString(length))
      def shuffle[A](list: List[A]): UIO[List[A]] = Random.shuffleWith(nextInt, list)
    }
  }
  object Live extends Live

  protected[zio] def shuffleWith[A](nextInt: Int => UIO[Int], list: List[A]): UIO[List[A]] =
    for {
      bufferRef <- Ref.make(new scala.collection.mutable.ArrayBuffer[A])
      _         <- bufferRef.update(_ ++= list)
      swap = (i1: Int, i2: Int) =>
        bufferRef.update { buffer =>
          val tmp = buffer(i1)
          buffer(i1) = buffer(i2)
          buffer(i2) = tmp
          buffer
        }
      _ <- UIO.traverse(list.length to 2 by -1) { n: Int =>
            nextInt(n).flatMap { k =>
              swap(n - 1, k)
            }
          }
      buffer <- bufferRef.get
    } yield buffer.toList
}
