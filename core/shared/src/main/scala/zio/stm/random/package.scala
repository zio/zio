/*
 * Copyright 2020-2021 John A. De Goes and the ZIO Contributors
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

package zio.stm

import zio._

package object random {

  /**
   * Generates a pseudo-random boolean inside a transaction.
   */
  val nextBoolean: URSTM[Has[TRandom], Boolean] =
    ZSTM.accessM(_.get.nextBoolean)

  /**
   * Generates a pseudo-random chunk of bytes of the specified length inside a transaction.
   */
  def nextBytes(length: => Int): URSTM[Has[TRandom], Chunk[Byte]] =
    ZSTM.accessM(_.get.nextBytes(length))

  /**
   * Generates a pseudo-random, uniformly distributed double between 0.0 and
   * 1.0 inside a transaction.
   */
  val nextDouble: URSTM[Has[TRandom], Double] = ZSTM.accessM(_.get.nextDouble)

  /**
   * Generates a pseudo-random double in the specified range inside a transaction.
   */
  def nextDoubleBetween(minInclusive: Double, maxExclusive: Double): URSTM[Has[TRandom], Double] =
    ZSTM.accessM(_.get.nextDoubleBetween(minInclusive, maxExclusive))

  /**
   * Generates a pseudo-random, uniformly distributed float between 0.0 and
   * 1.0 inside a transaction.
   */
  val nextFloat: URSTM[Has[TRandom], Float] =
    ZSTM.accessM(_.get.nextFloat)

  /**
   * Generates a pseudo-random float in the specified range inside a transaction.
   */
  def nextFloatBetween(minInclusive: Float, maxExclusive: Float): URSTM[Has[TRandom], Float] =
    ZSTM.accessM(_.get.nextFloatBetween(minInclusive, maxExclusive))

  /**
   * Generates a pseudo-random double from a normal distribution with mean 0.0
   * and standard deviation 1.0 inside a transaction.
   */
  val nextGaussian: URSTM[Has[TRandom], Double] =
    ZSTM.accessM(_.get.nextGaussian)

  /**
   * Generates a pseudo-random integer inside a transaction.
   */
  val nextInt: URSTM[Has[TRandom], Int] =
    ZSTM.accessM(_.get.nextInt)

  /**
   * Generates a pseudo-random integer in the specified range inside a transaction.
   */
  def nextIntBetween(minInclusive: Int, maxExclusive: Int): URSTM[Has[TRandom], Int] =
    ZSTM.accessM(_.get.nextIntBetween(minInclusive, maxExclusive))

  /**
   * Generates a pseudo-random integer between 0 (inclusive) and the specified
   * value (exclusive) inside a transaction.
   */
  def nextIntBounded(n: => Int): URSTM[Has[TRandom], Int] =
    ZSTM.accessM(_.get.nextIntBounded(n))

  /**
   * Generates a pseudo-random long inside a transaction.
   */
  val nextLong: URSTM[Has[TRandom], Long] =
    ZSTM.accessM(_.get.nextLong)

  /**
   * Generates a pseudo-random long in the specified range inside a transaction.
   */
  def nextLongBetween(minInclusive: Long, maxExclusive: Long): URSTM[Has[TRandom], Long] =
    ZSTM.accessM(_.get.nextLongBetween(minInclusive, maxExclusive))

  /**
   * Generates a pseudo-random long between 0 (inclusive) and the specified
   * value (exclusive) inside a transaction.
   */
  def nextLongBounded(n: => Long): URSTM[Has[TRandom], Long] =
    ZSTM.accessM(_.get.nextLongBounded(n))

  /**
   * Generates a pseudo-random character from the ASCII range 33-126 inside a transaction.
   */
  val nextPrintableChar: URSTM[Has[TRandom], Char] =
    ZSTM.accessM(_.get.nextPrintableChar)

  /**
   * Generates a pseudo-random string of the specified length inside a transaction.
   */
  def nextString(length: => Int): URSTM[Has[TRandom], String] =
    ZSTM.accessM(_.get.nextString(length))

  /**
   * Sets the seed of this random number generator inside a transaction.
   */
  def setSeed(seed: Long): URSTM[Has[TRandom], Unit] =
    ZSTM.accessM(_.get.setSeed(seed))

  /**
   * Randomly shuffles the specified list.
   */
  def shuffle[A](list: => List[A]): URSTM[Has[TRandom], List[A]] =
    ZSTM.accessM(_.get.shuffle(list))
}
