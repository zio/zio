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

package zio.test.poly

import zio.Has
import zio.random.Random
import zio.test.{Gen, Sized}

/**
 * `GenNumericPoly` provides evidence that instances of `Gen[T]` and
 * `Numeric[T]` exist for some concrete but unknown type `T`.
 */
trait GenNumericPoly extends GenOrderingPoly {
  val numT: Numeric[T]
  override final val ordT: Ordering[T] = numT
}

object GenNumericPoly {

  /**
   * Constructs an instance of `GenIntegralPoly` using the specified `Gen`
   * and `Numeric` instances, existentially hiding the underlying type.
   */
  def apply[A](gen: Gen[Has[Random] with Sized, A], num: Numeric[A]): GenNumericPoly =
    new GenNumericPoly {
      type T = A
      val genT = gen
      val numT = num
    }

  /**
   * Provides evidence that instances of `Gen` and `Numeric` exist for bytes.
   */
  val byte: GenNumericPoly =
    GenIntegralPoly.byte

  /**
   * Provides evidence that instances of `Gen` and `Numeric` exist for
   * characters.
   */
  val char: GenNumericPoly =
    GenIntegralPoly.char

  /**
   * Provides evidence that instances of `Gen` and `Numeric` exist for doubles.
   */
  val double: GenNumericPoly =
    GenFractionalPoly.double

  /**
   * Provides evidence that instances of `Gen` and `Numeric` exist for floats.
   */
  val float: GenNumericPoly =
    GenFractionalPoly.float

  /**
   * A generator of polymorphic values constrainted to have a `Numeric`
   * instance.
   */
  lazy val genNumericPoly: Gen[Has[Random], GenNumericPoly] =
    Gen.elements(
      byte,
      char,
      double,
      float,
      int,
      long,
      short
    )

  /**
   * Provides evidence that instances of `Gen` and `Numeric` exist for
   * integers.
   */
  val int: GenNumericPoly =
    GenIntegralPoly.int

  /**
   * Provides evidence that instances of `Gen` and `Numeric` exist for longs.
   */
  val long: GenNumericPoly =
    GenIntegralPoly.long

  /**
   * Provides evidence that instances of `Gen` and `Numeric` exist for shorts.
   */
  val short: GenNumericPoly =
    GenIntegralPoly.long
}
