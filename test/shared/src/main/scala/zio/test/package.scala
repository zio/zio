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

import zio.duration.Duration
import zio.test.environment.TestEnvironment

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
 *  import Assertion.isGreaterThan
 *
 *  object MyTest extends DefaultRunnableSpec {
 *    suite("clock") {
 *      testM("time is non-zero") {
 *        assertM(nanoTime, isGreaterThan(0))
 *      }
 *    }
 *  }
 * }}}
 */
package object test extends CheckVariants {
  type AssertResult = BoolAlgebra[AssertionValue]
  type TestResult   = BoolAlgebra[FailureDetails]

  /**
   * A `TestReporter[L, E, S]` is capable of reporting test results annotated
   * with labels `L`, error type `E`, and success type `S`.
   */
  type TestReporter[-L, -E, -S] = (Duration, ExecutedSpec[L, E, S]) => URIO[TestLogger, Unit]

  object TestReporter {

    /**
     * TestReporter that does nothing
     */
    final val silent: TestReporter[Any, Any, Any] = (_, _) => ZIO.unit
  }

  /**
   * A `TestExecutor[R, L, T, E, S]` is capable of executing specs containing
   * tests of type `T`, annotated with labels of type `L`, that require an
   * environment `R` and may fail with an `E` or succeed with a `S`.
   */
  type TestExecutor[+R, L, -T, E, +S] = (Spec[R, E, L, T], ExecutionStrategy) => UIO[ExecutedSpec[L, E, S]]

  /**
   * A `TestAspectPoly` is a `TestAspect` that is completely polymorphic,
   * having no requirements on error or environment.
   */
  type TestAspectPoly = TestAspect[Nothing, Any, Nothing, Any, Nothing, Any]

  /**
   * A `ZTest[R, E, S]` is an effectfully produced test that requires an `R`
   * and may fail with an `E` or succeed with a `S`.
   */
  type ZTest[-R, +E, +S] = ZIO[R, E, Either[TestFailure[Nothing], TestSuccess[S]]]

  /**
   * A `ZSpec[R, E, L, S]` is the canonical spec for testing ZIO programs. The
   * spec's test type is a ZIO effect that requires an `R`, might fail with an
   * `E`, might succeed with an `S`, and whose nodes are annotated with labels
   * `L`.
   */
  type ZSpec[-R, +E, +L, +S] = Spec[R, E, L, Either[TestFailure[Nothing], TestSuccess[S]]]

  /**
   * An `ExecutedSpec` is a spec that has been run to produce test results.
   */
  type ExecutedSpec[+L, +E, +S] = Spec[Any, Nothing, L, Either[TestFailure[E], TestSuccess[S]]]

  /**
   * Checks the assertion holds for the given value.
   */
  final def assert[A](value: => A, assertion: Assertion[A]): TestResult =
    assertion.run(value).map { fragment =>
      FailureDetails(fragment, AssertionValue(assertion, value))
    }

  /**
   * Checks the assertion holds for the given effectfully-computed value.
   */
  final def assertM[R, E, A](value: ZIO[R, E, A], assertion: Assertion[A]): ZIO[R, E, TestResult] =
    value.map(assert(_, assertion))

  /**
   * Creates a failed test result with the specified runtime cause.
   */
  final def fail[E](cause: Cause[E]): ZTest[Any, E, Nothing] =
    ZIO.halt(cause)

  /**
   * Creates an ignored test result.
   */
  final val ignore: ZTest[Any, Nothing, Nothing] =
    ZIO.succeed(Right(TestSuccess.Ignored))

  /**
   * Passes platform specific information to the specified function, which will
   * use that information to create a test. If the platform is neither ScalaJS
   * nor the JVM, an ignored test result will be returned.
   */
  final def platformSpecific[R, E, A, S](js: => A, jvm: => A)(f: A => ZTest[R, E, S]): ZTest[R, E, S] =
    if (TestPlatform.isJS) f(js)
    else if (TestPlatform.isJVM) f(jvm)
    else ignore

  /**
   * Builds a suite containing a number of other specs.
   */
  final def suite[R, E, L, T](label: L)(specs: Spec[R, E, L, T]*): Spec[R, E, L, T] =
    Spec.suite(label, ZIO.succeed(specs.toVector), None)

  /**
   * Builds a spec with a single pure test.
   */
  final def test[L](label: L)(assertion: => TestResult): ZSpec[Any, Nothing, L, Unit] =
    testM(label)(ZIO.effectTotal(assertion))

  /**
   * Builds a spec with a single effectful test.
   */
  final def testM[R, E, L](label: L)(assertion: ZIO[R, E, TestResult]): ZSpec[R, E, L, Unit] =
    Spec.test(
      label,
      assertion.map { result =>
        result.failures match {
          case None           => Right(TestSuccess.Succeeded(BoolAlgebra.unit))
          case Some(failures) => Left(TestFailure.Assertion(failures))
        }
      }
    )

  /**
   * Adds syntax for adding aspects.
   * {{{
   * test("foo") { assert(42, equalTo(42)) } @@ ignore
   * }}}
   */
  implicit class ZSpecSyntax[R, E, L, S](spec: ZSpec[R, E, L, S]) {
    def @@[LowerR <: R, UpperR >: R, LowerE <: E, UpperE >: E, LowerS <: S, UpperS >: S](
      aspect: TestAspect[LowerR, UpperR, LowerE, UpperE, LowerS, UpperS]
    ): ZSpec[R, E, L, S] =
      aspect(spec)
  }

  val defaultTestRunner: TestRunner[TestEnvironment, String, Either[TestFailure[Nothing], TestSuccess[Any]], Any, Any] =
    TestRunner(TestExecutor.managed(zio.test.environment.testEnvironmentManaged))
}
