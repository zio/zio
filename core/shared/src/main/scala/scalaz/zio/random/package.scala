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

package scalaz.zio

package object random extends Random.Service[Random] {
  final val randomService: ZIO[Random, Nothing, Random.Service[Any]] =
    ZIO.access(_.random)

  val nextBoolean: ZIO[Random, Nothing, Boolean]                = ZIO.accessM(_.random.nextBoolean)
  def nextBytes(length: Int): ZIO[Random, Nothing, Chunk[Byte]] = ZIO.accessM(_.random.nextBytes(length))
  val nextDouble: ZIO[Random, Nothing, Double]                  = ZIO.accessM(_.random.nextDouble)
  val nextFloat: ZIO[Random, Nothing, Float]                    = ZIO.accessM(_.random.nextFloat)
  val nextGaussian: ZIO[Random, Nothing, Double]                = ZIO.accessM(_.random.nextGaussian)
  def nextInt(n: Int): ZIO[Random, Nothing, Int]                = ZIO.accessM(_.random.nextInt(n))
  val nextInt: ZIO[Random, Nothing, Int]                        = ZIO.accessM(_.random.nextInt)
  val nextLong: ZIO[Random, Nothing, Long]                      = ZIO.accessM(_.random.nextLong)
  val nextPrintableChar: ZIO[Random, Nothing, Char]             = ZIO.accessM(_.random.nextPrintableChar)
  def nextString(length: Int): ZIO[Random, Nothing, String]     = ZIO.accessM(_.random.nextString(length))
}
