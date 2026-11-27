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

package zio.test.laws

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.{FunctionVariants, Gen}
import zio.Trace

/**
 * A `GenF2` knows how to construct a generator of `F[A, B]` values given a generator of `B` values, for any `A` and `B`. For example, a `GenF2` of `Function1` values knows how to generate functions `A => B` given a generator of their results of type `B`.
 *
 * The laws in `ZLawsF2` take a `GenF2` to build the `F[A, B]` values they check.
 *
 * {{{
 * import zio.test._
 *
 * val lengths: Gen[Any, String => Int] =
 *   GenF2.function1[Any, String, Int](Gen.int(0, 100))
 * }}}
 *
 * @see [[https://zio.dev/reference/test/property-testing/ Property Testing]]
 */
trait GenF2[-R, F[_, _]] {

  /**
   * Construct a generator of `F[A, B]` values given a generator of `B` values.
   */
  def apply[R1 <: R, A, B](gen: Gen[R1, B])(implicit trace: Trace): Gen[R1, F[A, B]]
}

/**
 * `GenF2` instances. It also inherits the function generators of `FunctionVariants`, such as `function` and `functionWith`.
 */
object GenF2 extends FunctionVariants {

  /**
   * A `GenF2` of `Function1` values: it builds a generator of functions `A => B` with `function`, whose results are drawn from the given generator of `B` values. A generated function returns the same result for two inputs with the same `hashCode`.
   */
  val function1: GenF2[Any, Function1] =
    new GenF2[Any, Function1] {

      override def apply[R1, A, B](gen: Gen[R1, B])(implicit
        trace: Trace
      ): Gen[R1, Function1[A, B]] =
        function[R1, A, B](gen)
    }
}
