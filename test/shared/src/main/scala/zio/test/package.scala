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
 *  import zio.clock.nanoTime
 *  import Predicate.gt
 *
 *  object MyTest extends DefaultRunnableSpec {
 *    suite("clock") {
 *      testM("time is non-zero") {
 *        assertM(nanoTime, gt(0))
 *      }
 *    }
 *  }
 * }}}
 */
package object test {
  type PredicateResult = AssertResult[PredicateValue]
  type TestResult      = AssertResult[FailureDetails]

  type ExecutedSpec[-R, +E, +L] = ZSpec[R, E, (L, TestResult)]

  type TaskSpec[+L] = ZSpec[Any, Throwable, L]
  val TaskSpec = ZSpec

  type RIOSpec[-R, +L] = ZSpec[R, Throwable, L]
  val RIOSpec = ZSpec

  type IOSpec[+E, +L] = ZSpec[Any, E, L]
  val IOSpec = ZSpec

  type TestAspectPoly = TestAspect[Nothing, Any, Nothing, Any]

  /**
   * Asserts the given value satisfies the given predicate.
   */
  final def assert[A](value: A, predicate: Predicate[A]): TestResult =
    predicate.run(value).map(FailureDetails.Predicate(_, PredicateValue(predicate, value)))

  /**
   * Asserts the given effectfully-computed value satisfies the given predicate.
   */
  final def assertM[R, A](value: ZIO[R, Nothing, A], predicate: Predicate[A]): ZIO[R, Nothing, TestResult] =
    value.map(assert(_, predicate))

  /**
   * Creates a failed test result with the specified runtime cause.
   */
  def fail[E](cause: Cause[E]): TestResult = AssertResult.failure(FailureDetails.Runtime(cause))

  /**
   * Builds a suite containing a number of other specs.
   */
  final def suite[R, E, L](label: L)(specs: ZSpec[R, E, L]*): ZSpec[R, E, L] = ZSpec.Suite(label, specs.toVector)

  /**
   * Builds a spec with a single pure test.
   */
  final def test[L](label: L)(assertion: => TestResult): ZSpec[Any, Nothing, L] =
    testM(label)(ZIO.succeedLazy(assertion))

  /**
   * Builds a spec with a single effectful test.
   */
  final def testM[R, E, L](label: L)(assertion: ZIO[R, E, TestResult]): ZSpec[R, E, L] = ZSpec.Test(label, assertion)
}
