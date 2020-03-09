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
import zio.test.mock.internal.MockRuntime
import zio.{ Chunk, UIO, ZLayer }

object MockRandom {

  sealed trait Tag[I, A] extends Method[Random, I, A] {
    val mock = MockRandom.mock
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

  private lazy val mock: ZLayer[MockRuntime, Nothing, Random] =
    ZLayer.fromService(mock =>
      new Random.Service {
        val nextBoolean: UIO[Boolean]                = mock(NextBoolean)
        def nextBytes(length: Int): UIO[Chunk[Byte]] = mock(NextBytes, length)
        val nextDouble: UIO[Double]                  = mock(NextDouble)
        val nextFloat: UIO[Float]                    = mock(NextFloat)
        val nextGaussian: UIO[Double]                = mock(NextGaussian)
        def nextInt(n: Int): UIO[Int]                = mock(NextInt._0, n)
        val nextInt: UIO[Int]                        = mock(NextInt._1)
        val nextLong: UIO[Long]                      = mock(NextLong._0)
        def nextLong(n: Long): UIO[Long]             = mock(NextLong._1, n)
        val nextPrintableChar: UIO[Char]             = mock(NextPrintableChar)
        def nextString(length: Int)                  = mock(NextString, length)
        def shuffle[A](list: List[A]): UIO[List[A]]  = mock(Shuffle, list).asInstanceOf[UIO[List[A]]]
      }
    )
}
