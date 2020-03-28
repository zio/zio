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

object MockRandom {

  object Between {
    object _0 extends Method[Random, (Long, Long), Nothing, Long](compose)
    object _1 extends Method[Random, (Int, Int), Nothing, Int](compose)
    object _2 extends Method[Random, (Float, Float), Nothing, Float](compose)
    object _3 extends Method[Random, (Double, Double), Nothing, Double](compose)
  }
  object NextBoolean  extends Method[Random, Unit, Nothing, Boolean](compose)
  object NextBytes    extends Method[Random, Int, Nothing, Chunk[Byte]](compose)
  object NextDouble   extends Method[Random, Unit, Nothing, Double](compose)
  object NextFloat    extends Method[Random, Unit, Nothing, Float](compose)
  object NextGaussian extends Method[Random, Unit, Nothing, Double](compose)
  object NextInt {
    object _0 extends Method[Random, Int, Nothing, Int](compose)
    object _1 extends Method[Random, Unit, Nothing, Int](compose)
  }
  object NextLong {
    object _0 extends Method[Random, Unit, Nothing, Long](compose)
    object _1 extends Method[Random, Long, Nothing, Long](compose)
  }
  object NextPrintableChar extends Method[Random, Unit, Nothing, Char](compose)
  object NextString        extends Method[Random, Int, Nothing, String](compose)
  object Shuffle           extends Method[Random, List[Any], Nothing, List[Any]](compose)

  private lazy val compose: URLayer[Has[Proxy], Random] =
    ZLayer.fromService(invoke =>
      new Random.Service {
        def between(minInclusive: Long, maxExclusive: Long): UIO[Long] =
          invoke(Between._0, minInclusive, maxExclusive)
        def between(minInclusive: Int, maxExclusive: Int): UIO[Int] =
          invoke(Between._1, minInclusive, maxExclusive)
        def between(minInclusive: Float, maxExclusive: Float): UIO[Float] =
          invoke(Between._2, minInclusive, maxExclusive)
        def between(minInclusive: Double, maxExclusive: Double): UIO[Double] =
          invoke(Between._3, minInclusive, maxExclusive)
        val nextBoolean: UIO[Boolean]                = invoke(NextBoolean)
        def nextBytes(length: Int): UIO[Chunk[Byte]] = invoke(NextBytes, length)
        val nextDouble: UIO[Double]                  = invoke(NextDouble)
        val nextFloat: UIO[Float]                    = invoke(NextFloat)
        val nextGaussian: UIO[Double]                = invoke(NextGaussian)
        def nextInt(n: Int): UIO[Int]                = invoke(NextInt._0, n)
        val nextInt: UIO[Int]                        = invoke(NextInt._1)
        val nextLong: UIO[Long]                      = invoke(NextLong._0)
        def nextLong(n: Long): UIO[Long]             = invoke(NextLong._1, n)
        val nextPrintableChar: UIO[Char]             = invoke(NextPrintableChar)
        def nextString(length: Int)                  = invoke(NextString, length)
        def shuffle[A](list: List[A]): UIO[List[A]]  = invoke(Shuffle, list).asInstanceOf[UIO[List[A]]]
      }
    )
}
