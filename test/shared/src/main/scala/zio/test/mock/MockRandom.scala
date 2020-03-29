/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

import zio.random.Random
import zio.{ Chunk, Has, UIO, URLayer, ZLayer }

object MockRandom extends Mock[Random] {

  object Between {
    object _0 extends Effect[(Long, Long), Nothing, Long]
    object _1 extends Effect[(Int, Int), Nothing, Int]
    object _2 extends Effect[(Float, Float), Nothing, Float]
    object _3 extends Effect[(Double, Double), Nothing, Double]
  }
  object NextBoolean  extends Effect[Unit, Nothing, Boolean]
  object NextBytes    extends Effect[Int, Nothing, Chunk[Byte]]
  object NextDouble   extends Effect[Unit, Nothing, Double]
  object NextFloat    extends Effect[Unit, Nothing, Float]
  object NextGaussian extends Effect[Unit, Nothing, Double]
  object NextInt {
    object _0 extends Effect[Int, Nothing, Int]
    object _1 extends Effect[Unit, Nothing, Int]
  }
  object NextLong {
    object _0 extends Effect[Unit, Nothing, Long]
    object _1 extends Effect[Long, Nothing, Long]
  }
  object NextPrintableChar extends Effect[Unit, Nothing, Char]
  object NextString        extends Effect[Int, Nothing, String]
  object Shuffle           extends Effect[List[Any], Nothing, List[Any]]

  val compose: URLayer[Has[Proxy], Random] =
    ZLayer.fromService(proxy =>
      new Random.Service {
        def between(minInclusive: Long, maxExclusive: Long): UIO[Long] =
          proxy(Between._0, minInclusive, maxExclusive)
        def between(minInclusive: Int, maxExclusive: Int): UIO[Int] =
          proxy(Between._1, minInclusive, maxExclusive)
        def between(minInclusive: Float, maxExclusive: Float): UIO[Float] =
          proxy(Between._2, minInclusive, maxExclusive)
        def between(minInclusive: Double, maxExclusive: Double): UIO[Double] =
          proxy(Between._3, minInclusive, maxExclusive)
        val nextBoolean: UIO[Boolean]                = proxy(NextBoolean)
        def nextBytes(length: Int): UIO[Chunk[Byte]] = proxy(NextBytes, length)
        val nextDouble: UIO[Double]                  = proxy(NextDouble)
        val nextFloat: UIO[Float]                    = proxy(NextFloat)
        val nextGaussian: UIO[Double]                = proxy(NextGaussian)
        def nextInt(n: Int): UIO[Int]                = proxy(NextInt._0, n)
        val nextInt: UIO[Int]                        = proxy(NextInt._1)
        val nextLong: UIO[Long]                      = proxy(NextLong._0)
        def nextLong(n: Long): UIO[Long]             = proxy(NextLong._1, n)
        val nextPrintableChar: UIO[Char]             = proxy(NextPrintableChar)
        def nextString(length: Int)                  = proxy(NextString, length)
        def shuffle[A](list: List[A]): UIO[List[A]]  = proxy(Shuffle, list).asInstanceOf[UIO[List[A]]]
      }
    )
}
