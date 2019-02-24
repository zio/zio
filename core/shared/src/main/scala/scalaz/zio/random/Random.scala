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

package scalaz.zio.random

import scalaz.zio.{ Chunk, UIO, ZIO }

trait Random extends Serializable {
  val random: Random.Service[Any]
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
    val nextPrintableChar: ZIO[R, Nothing, Char]
    def nextString(length: Int): ZIO[R, Nothing, String]
  }
  trait Live extends Random {
    val random: Service[Any] = new Service[Any] {
      import scala.util.{ Random => SRandom }

      val nextBoolean: UIO[Boolean] = ZIO.defer(SRandom.nextBoolean())
      def nextBytes(length: Int): UIO[Chunk[Byte]] =
        ZIO.defer {
          val array = Array.ofDim[Byte](length)

          SRandom.nextBytes(array)

          Chunk.fromArray(array)
        }
      val nextDouble: UIO[Double]              = ZIO.defer(SRandom.nextDouble())
      val nextFloat: UIO[Float]                = ZIO.defer(SRandom.nextFloat())
      val nextGaussian: UIO[Double]            = ZIO.defer(SRandom.nextGaussian())
      def nextInt(n: Int): UIO[Int]            = ZIO.defer(SRandom.nextInt(n))
      val nextInt: UIO[Int]                    = ZIO.defer(SRandom.nextInt())
      val nextLong: UIO[Long]                  = ZIO.defer(SRandom.nextLong())
      val nextPrintableChar: UIO[Char]         = ZIO.defer(SRandom.nextPrintableChar())
      def nextString(length: Int): UIO[String] = ZIO.defer(SRandom.nextString(length))
    }
  }
  object Live extends Live
}
