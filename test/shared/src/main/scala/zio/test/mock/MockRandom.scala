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

import zio.{ Chunk, UIO }
import zio.random.Random

object MockRandom {

  trait Service extends Random.Service

  object nextBoolean  extends Method[MockRandom.Service, Unit, Boolean]
  object nextBytes    extends Method[MockRandom.Service, Int, Chunk[Byte]]
  object nextDouble   extends Method[MockRandom.Service, Unit, Double]
  object nextFloat    extends Method[MockRandom.Service, Unit, Float]
  object nextGaussian extends Method[MockRandom.Service, Unit, Double]
  object nextInt {
    object _0 extends Method[MockRandom.Service, Int, Int]
    object _1 extends Method[MockRandom.Service, Unit, Int]
  }
  object nextLong {
    object _0 extends Method[MockRandom.Service, Unit, Long]
    object _1 extends Method[MockRandom.Service, Long, Long]
  }
  object nextPrintableChar extends Method[MockRandom.Service, Unit, Char]
  object nextString        extends Method[MockRandom.Service, Int, String]
  object shuffle           extends Method[MockRandom.Service, List[Any], List[Any]]

  implicit val mockable: Mockable[MockRandom.Service] = (mock: Mock) =>
    new Service {
      val nextBoolean: UIO[Boolean]                = mock(MockRandom.nextBoolean)
      def nextBytes(length: Int): UIO[Chunk[Byte]] = mock(MockRandom.nextBytes, length)
      val nextDouble: UIO[Double]                  = mock(MockRandom.nextDouble)
      val nextFloat: UIO[Float]                    = mock(MockRandom.nextFloat)
      val nextGaussian: UIO[Double]                = mock(MockRandom.nextGaussian)
      def nextInt(n: Int): UIO[Int]                = mock(MockRandom.nextInt._0, n)
      val nextInt: UIO[Int]                        = mock(MockRandom.nextInt._1)
      val nextLong: UIO[Long]                      = mock(MockRandom.nextLong._0)
      def nextLong(n: Long): UIO[Long]             = mock(MockRandom.nextLong._1, n)
      val nextPrintableChar: UIO[Char]             = mock(MockRandom.nextPrintableChar)
      def nextString(length: Int)                  = mock(MockRandom.nextString, length)
      def shuffle[A](list: List[A]): UIO[List[A]]  = mock(MockRandom.shuffle, list).asInstanceOf[UIO[List[A]]]
    }
}
