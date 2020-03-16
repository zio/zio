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

  sealed trait Tag[I, A] extends Method[Random, I, A] {
    def envBuilder = MockRandom.envBuilder
  }

  object Between {
    object _0 extends Tag[(Long, Long), Long]
    object _1 extends Tag[(Int, Int), Int]
    object _2 extends Tag[(Float, Float), Float]
    object _3 extends Tag[(Double, Double), Double]
  }
  object NextBoolean  extends Tag[Unit, Boolean]
  object NextBytes    extends Tag[Int, Chunk[Byte]]
  object NextDouble   extends Tag[Unit, Double]
  object NextFloat    extends Tag[Unit, Float]
  object NextGaussian extends Tag[Unit, Double]
  object NextInt {
    object _0 extends Tag[Int, Int]
    object _1 extends Tag[Unit, Int]
  }
  object NextLong {
    object _0 extends Tag[Unit, Long]
    object _1 extends Tag[Long, Long]
  }
  object NextPrintableChar extends Tag[Unit, Char]
  object NextString        extends Tag[Int, String]
  object Shuffle           extends Tag[List[Any], List[Any]]

  private lazy val envBuilder: URLayer[Has[Proxy], Random] =
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
