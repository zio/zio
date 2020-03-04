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

package zio

package object random {
  type Random = Has[Service]

  val nextBoolean: ZIO[Random, Nothing, Boolean]                   = ZIO.accessM(_.get.nextBoolean)
  def nextBytes(length: => Int): ZIO[Random, Nothing, Chunk[Byte]] = ZIO.accessM(_.get.nextBytes(length))
  val nextDouble: ZIO[Random, Nothing, Double]                     = ZIO.accessM(_.get.nextDouble)
  val nextFloat: ZIO[Random, Nothing, Float]                       = ZIO.accessM(_.get.nextFloat)
  val nextGaussian: ZIO[Random, Nothing, Double]                   = ZIO.accessM(_.get.nextGaussian)
  def nextInt(n: => Int): ZIO[Random, Nothing, Int]                = ZIO.accessM(_.get.nextInt(n))
  val nextInt: ZIO[Random, Nothing, Int]                           = ZIO.accessM(_.get.nextInt)
  val nextLong: ZIO[Random, Nothing, Long]                         = ZIO.accessM(_.get.nextLong)
  def nextLong(n: => Long): ZIO[Random, Nothing, Long]             = ZIO.accessM(_.get.nextLong(n))
  val nextPrintableChar: ZIO[Random, Nothing, Char]                = ZIO.accessM(_.get.nextPrintableChar)
  def nextString(length: => Int): ZIO[Random, Nothing, String]     = ZIO.accessM(_.get.nextString(length))
  def shuffle[A](list: => List[A]): ZIO[Random, Nothing, List[A]]  = ZIO.accessM(_.get.shuffle(list))
}
