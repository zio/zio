/*
 * Copyright 2019 John A. De Goes and the ZIO Contributors
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

/**
 * _ZIO Test_ is a featherweight testing library for effectful programs.
 *
 * The library imagines every spec as an ordinary immutable value, providing
 * tremendous potential for composition. Thanks to tight integration with ZIO,
 * specs can use resources (including those requiring disposal), have well-
 * defined linear and parallel semantics, and can benefit from a host of ZIO
 * combinators.
 *
 * {{{
 *  import zio.test._
 *  import zio.clock._
 *
 *  class MyTest extends ZIOTestDefault[Throwable] {
 *    val tests = suite("clock") {
 *      test("time is non-zero") {
 *        nanoTime.map(time => assert(time > 0, Predicate.isTrue))
 *      }
 *    }
 *  }
 * }}}
 */
package object test {

  /**
   * Asserts the given value satisfies the given predicate.
   */
  final def assert[A](value: => A, predicate: Predicate[A]): AssertResult = predicate.run(value)

  /**
   * Asserts the boolean value is false.
   */
  final def assertFalse(value: => Boolean): AssertResult = assert(value, Predicate.isFalse)

  /**
   * Asserts the boolean value is true.
   */
  final def assertTrue(value: => Boolean): AssertResult = assert(value, Predicate.isTrue)

  /**
   * Builds a suite containing a number of other specs.
   */
  final def suite[R, E](label: String)(specs: Spec[R, E]*): Spec[R, E] = Spec.Suite(label, specs.toVector)

  /**
   * Builds a spec with a single effectful test.
   */
  final def test[R, E](label: String)(assertion: ZIO[R, E, AssertResult]): Spec[R, E] = Spec.Test(label, assertion)

  /**
   * Builds a spec with a single pure test.
   */
  final def testPure(label: String)(assertion: => AssertResult): Spec[Any, Nothing] =
    test(label)(ZIO.succeedLazy(assertion))
}
