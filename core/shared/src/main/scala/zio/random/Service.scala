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

package zio.random

import zio._

trait Service extends Serializable {
  def nextBoolean: UIO[Boolean]
  def nextBytes(length: Int): UIO[Chunk[Byte]]
  def nextDouble: UIO[Double]
  def nextFloat: UIO[Float]
  def nextGaussian: UIO[Double]
  def nextInt(n: Int): UIO[Int]
  def nextInt: UIO[Int]
  def nextLong: UIO[Long]
  def nextLong(n: Long): UIO[Long]
  def nextPrintableChar: UIO[Char]
  def nextString(length: Int): UIO[String]
  def shuffle[A](list: List[A]): UIO[List[A]]
}
