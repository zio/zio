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
 * `GenFractionalPoly` provides evidence that instances of `Gen[T]` and
 * `Fractional[T]` exist for some concrete but unknown type `T`. Use it for
 * properties that divide generated values of a type that changes from sample to
 * sample.
 *
 * {{{
 * import zio.test._
 *
 * val divideByOne =
 *   test("dividing by one keeps the value") {
 *     check(GenFractionalPoly.genFractionalPoly) { poly =>
 *       val num = poly.numT
 *       check(poly.genT) { a =>
 *         val divided = num.div(a, num.one)
 *         assertTrue(divided == a)
 *       }
 *     }
 *   }
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/test/property-testing/ Property Testing]]
 */
trait GenFractionalPoly extends GenNumericPoly {
  override val numT: Fractional[T]
}

/**
 * Constructors of [[GenFractionalPoly]] instances for `Double` and `Float`, and
 * `genFractionalPoly`, a generator that picks between them.
 *
 * @see
 *   [[https://zio.dev/reference/test/property-testing/ Property Testing]]
 */
object GenFractionalPoly {

  /**
   * Constructs an instance of `GenFractionalPoly` using the specified `Gen` and
   * `Fractional` instances, existentially hiding the underlying type.
   */
  def apply[A](gen: Gen[Any, A], num: Fractional[A]): GenFractionalPoly =
    new GenFractionalPoly {
      type T = A
      val genT = gen
      val numT = num
    }

  /**
   * Provides evidence that instances of `Gen` and `Fractional` exist for
   * doubles, using `Gen.double` and `Numeric.DoubleIsFractional`.
   */
  def double(implicit trace: Trace): GenFractionalPoly =
    GenFractionalPoly(Gen.double, Numeric.DoubleIsFractional)

  /**
   * Provides evidence that instances of `Gen` and `Fractional` exist for
   * floats, using `Gen.float` and `Numeric.FloatIsFractional`.
   */
  def float(implicit trace: Trace): GenFractionalPoly =
    GenFractionalPoly(Gen.float, Numeric.FloatIsFractional)

  /**
   * A generator of polymorphic values constrained to have a `Fractional`
   * instance. It picks `double` or `float` with equal weight.
   */
  def genFractionalPoly(implicit trace: Trace): Gen[Any, GenFractionalPoly] =
    Gen.elements(double, float)
}
