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
import zio.stream.{ ZSink, ZStream }

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
package object test {
  type AssertionResult = AssertResult[Either[AssertionValue, Unit]]
  type TestResult      = AssertResult[Either[FailureDetails, Unit]]

  /**
   * A `TestReporter[L]` is capable of reporting test results annotated with
   * labels `L`.
   */
  type TestReporter[-L, -E, -S] = (Duration, ExecutedSpec[L, E, S]) => URIO[TestLogger, Unit]

  object TestReporter {

    /**
     * TestReporter that does nothing
     */
    def silent[L, E, S]: TestReporter[L, E, S] = (_, _) => ZIO.unit
  }

  /**
   * A `TestExecutor[L, T]` is capable of executing specs containing tests of
   * type `T`, annotated with labels of type `L`.
   */
  type TestExecutor[L, -T, +E, +S] = (Spec[L, T], ExecutionStrategy) => UIO[ExecutedSpec[L, E, S]]

  /**
   * A `TestAspectPoly` is a `TestAspect` that is completely polymorphic,
   * having no requirements on error or environment.
   */
  type TestAspectPoly = TestAspect[Nothing, Any, Nothing, Any, Nothing, Any]

  /**
   * A `ZTest[R, E, S]` is an effectfully produced test that requires an `R`
   * and may fail with a `TestFailure[E]` or succeed with an `S`.
   */
  type ZTest[-R, +E, +S] = ZIO[R, TestFailure[E], S]

  /**
   * A `ZSpec[R, E, L, S]` is the canonical spec for testing ZIO programs. The
   * spec's test type is a ZIO effect that requires an `R`, might fail with
   * a `TestFailure[E]`, might succeed with an `S`, and whose nodes are
   * annotated with labels `L`.
   */
  type ZSpec[-R, +E, +L, +S] = Spec[L, ZTest[R, E, S]]

  /**
   * An `ExecutedSpec` is a spec that has been run to produce test results.
   */
  type ExecutedSpec[+L, +E, +S] = Spec[L, Either[TestFailure[E], AssertResult[S]]]

  /**
   * Checks the assertion holds for the given value.
   */
  final def assert[A](value: => A, assertion: Assertion[A]): TestResult =
    assertion.run(value).map {
      case Left(fragment) =>
        Left(FailureDetails(fragment, AssertionValue(assertion, value)))
      case _ =>
        Right(())
    }

  /**
   * Checks the assertion holds for the given effectfully-computed value.
   */
  final def assertM[R, A](value: ZIO[R, Nothing, A], assertion: Assertion[A]): ZTest[R, Nothing, Unit] =
    value.flatMap { a =>
      val result = assert(a, assertion)
      if (result.isSuccess) ZIO.succeed(()) else ZIO.fail(???)
    }

  /**
   * Checks the assertion holds for "sufficient" numbers of samples from the
   * given random variable.
   */
  final def check[R, A](rv: Gen[R, A])(assertion: Assertion[A]): ZTest[R, Nothing, Unit] =
    checkSome(200)(rv)(assertion)

  /**
   * Checks the assertion holds for all values from the given random variable.
   * This is useful for deterministic `Gen` that comprehensively explore all
   * possibilities in a given domain.
   */
  final def checkAll[R, A](rv: Gen[R, A])(assertion: Assertion[A]): ZTest[R, Nothing, Unit] =
    checkStream(rv.sample)(assertion)

  /**
   * Checks the assertion holds for the specified number of samples from the
   * given random variable.
   */
  final def checkSome[R, A](n: Int)(rv: Gen[R, A])(assertion: Assertion[A]): ZTest[R, Nothing, Unit] =
    checkStream(rv.sample.forever.take(n))(assertion)

  /**
   * Creates a failed test result with the specified runtime cause.
   */
  final def fail[E](cause: Cause[E]): ZTest[Any, E, Nothing] =
    ZIO.fail(TestFailure.Runtime(cause))

  /**
   * Builds a suite containing a number of other specs.
   */
  final def suite[L, T](label: L)(specs: Spec[L, T]*): Spec[L, T] =
    Spec.suite(label, specs.toVector, None)

  /**
   * Builds a spec with a single pure test.
   */
  final def test[L](label: L)(assertion: => TestResult): ZSpec[Any, Nothing, L, Unit] =
    testM(label)(ZIO.succeed(assertion))

  /**
   * Builds a spec with a single effectful test.
   */
  final def testM[R, L, T](label: L)(assertion: ZIO[R, Nothing, TestResult]): ZSpec[R, Nothing, L, Unit] =
    Spec.test(label, assertion.flatMap { result =>
      if (result.isSuccess) ZIO.succeed(())
      else ZIO.fail(TestFailure.Assertion(result.collect { case Left(e) => e }.get))
    })

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

  private final def checkStream[R, A](stream: ZStream[R, Nothing, Sample[R, A]], maxShrinks: Int = 1000)(
    assertion: Assertion[A]
  ): ZIO[R, TestFailure[Nothing], Unit] = {
    def checkValue(value: A): TestResult =
      assertion.run(value).map {
        case Left(e)  => Left(FailureDetails(e, AssertionValue(assertion, value)))
        case Right(s) => Right(s)
      }

    stream
      .map(_.map(checkValue))
      .dropWhile(!_.value.isFailure) // Drop until we get to a failure
      .take(1)                       // Get the first failure
      .flatMap(_.shrinkSearch(_.isFailure).take(maxShrinks))
      .run(ZSink.collectAll[TestResult]) // Collect all the shrunken failures
      .flatMap { failures =>
        // Get the "last" failure, the smallest according to the shrinker:
        failures.reverse.headOption.fold[ZIO[R, TestFailure[Nothing], Unit]] { ZIO.succeed(()) } {
          case AssertResult.Value(Left(failureDetails)) =>
            ZIO.fail(TestFailure.Assertion(AssertResult.value(failureDetails)))
          case _ => ???
        }
      }
  }

  val defaultTestRunner: TestRunner[String, ZTest[mock.MockEnvironment, Any, Unit], Any, Unit] =
    TestRunner(TestExecutor.managed(zio.test.mock.mockEnvironmentManaged))
}
