/*
 * Copyright 2020-2024 John A. De Goes and the ZIO Contributors
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

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.Gen
import zio.Trace

/**
 * `GenIntegralPoly` provides evidence that instances of `Gen[T]` and
 * `Integral[T]` exist for some concrete but unknown type `T`. Use it for
 * properties of integer arithmetic, such as division with remainder, on a type
 * that changes from sample to sample.
 *
 * {{{
 * import zio.test._
 *
 * val division =
 *   test("quotient and remainder rebuild the dividend") {
 *     check(GenIntegralPoly.genIntegralPoly) { poly =>
 *       val num = poly.numT
 *       check(poly.genT, poly.genT.filter(_ != num.zero)) { (a, b) =>
 *         val rebuilt = num.plus(num.times(num.quot(a, b), b), num.rem(a, b))
 *         assertTrue(rebuilt == a)
 *       }
 *     }
 *   }
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/test/property-testing/ Property Testing]]
 */
trait GenIntegralPoly extends GenNumericPoly {
  override val numT: Integral[T]
}

/**
 * Constructors of [[GenIntegralPoly]] instances for `Byte`, `Char`, `Int`,
 * `Long` and `Short`, and `genIntegralPoly`, a generator that picks among them.
 *
 * @see
 *   [[https://zio.dev/reference/test/property-testing/ Property Testing]]
 */
object GenIntegralPoly {

  /**
   * Constructs an instance of `GenIntegralPoly` using the specified `Gen` and
   * `Integral` instances, existentially hiding the underlying type.
   */
  def apply[A](gen: Gen[Any, A], num: Integral[A]): GenIntegralPoly =
    new GenIntegralPoly {
      type T = A
      val genT = gen
      val numT = num
    }

  /**
   * Provides evidence that instances of `Gen` and `Integral` exist for bytes,
   * using `Gen.byte` and `Numeric.ByteIsIntegral`.
   */
  def byte(implicit trace: Trace): GenIntegralPoly =
    GenIntegralPoly(Gen.byte, Numeric.ByteIsIntegral)

  /**
   * Provides evidence that instances of `Gen` and `Integral` exist for
   * characters, using `Gen.char` and `Numeric.CharIsIntegral`.
   */
  def char(implicit trace: Trace): GenIntegralPoly =
    GenIntegralPoly(Gen.char, Numeric.CharIsIntegral)

  /**
   * A generator of polymorphic values constrained to have an `Integral`
   * instance. It picks one of `byte`, `char`, `int`, `long` and `short` with
   * equal weight.
   */
  def genIntegralPoly(implicit trace: Trace): Gen[Any, GenIntegralPoly] =
    Gen.elements(byte, char, int, long, short)

  /**
   * Provides evidence that instances of `Gen` and `Integral` exist for
   * integers, using `Gen.int` and `Numeric.IntIsIntegral`.
   */
  def int(implicit trace: Trace): GenIntegralPoly =
    GenIntegralPoly(Gen.int, Numeric.IntIsIntegral)

  /**
   * Provides evidence that instances of `Gen` and `Integral` exist for longs,
   * using `Gen.long` and `Numeric.LongIsIntegral`.
   */
  def long(implicit trace: Trace): GenIntegralPoly =
    GenIntegralPoly(Gen.long, Numeric.LongIsIntegral)

  /**
   * Provides evidence that instances of `Gen` and `Integral` exist for shorts,
   * using `Gen.short` and `Numeric.ShortIsIntegral`.
   */
  def short(implicit trace: Trace): GenIntegralPoly =
    GenIntegralPoly(Gen.short, Numeric.ShortIsIntegral)
}
