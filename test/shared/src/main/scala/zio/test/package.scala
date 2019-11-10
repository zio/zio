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
package object test extends AssertionVariants {
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
  type TestExecutor[+R, L, -T, E, +S] = (ZSpec[R, E, L, T], ExecutionStrategy) => UIO[ExecutedSpec[L, E, S]]

  /**
   * A `TestAspectPoly` is a `TestAspect` that is completely polymorphic,
   * having no requirements on error or environment.
   */
  type TestAspectPoly = TestAspect[Nothing, Any, Nothing, Any, Nothing, Any]

  /**
   * A `ZTest[R, E, S]` is an effectfully produced test that requires an `R`
   * and may fail with an `E` or succeed with a `S`.
   */
  type ZTest[-R, +E, +S] = ZIO[R, TestFailure[E], TestSuccess[S]]

  /**
   * A `ZSpec[R, E, L, S]` is the canonical spec for testing ZIO programs. The
   * spec's test type is a ZIO effect that requires an `R`, might fail with an
   * `E`, might succeed with an `S`, and whose nodes are annotated with labels
   * `L`.
   */
  type ZSpec[-R, +E, +L, +S] = Spec[R, TestFailure[E], L, TestSuccess[S]]

  /**
   * An `ExecutedSpec` is a spec that has been run to produce test results.
   */
  type ExecutedSpec[+L, +E, +S] = Spec[Any, Nothing, L, Either[TestFailure[E], TestSuccess[S]]]

  /**
   * Checks the assertion holds for the given value.
   */
  final def assert[A](value: => A, assertion: Assertion[A]): TestResult =
    assertion.run(value).flatMap { fragment =>
      def loop(whole: AssertionValue, failureDetails: FailureDetails): TestResult =
        if (whole.assertion == failureDetails.assertion.head.assertion)
          BoolAlgebra.success(failureDetails)
        else {
          val satisfied = whole.assertion.test(whole.value)
          val fragment  = whole.assertion.run(whole.value)
          val result    = if (satisfied) fragment else !fragment
          result.flatMap { fragment =>
            loop(fragment, FailureDetails(::(whole, failureDetails.assertion), failureDetails.gen))
          }
        }
      loop(fragment, FailureDetails(::(AssertionValue(assertion, value), Nil)))
    }

  /**
   * Asserts that the given test was completed.
   */
  final val assertCompletes: TestResult =
    assert(true, Assertion.isTrue)

  /**
   * Checks the assertion holds for the given effectfully-computed value.
   */
  final def assertM[R, E, A](value: ZIO[R, E, A], assertion: Assertion[A]): ZIO[R, E, TestResult] =
    value.map(assert(_, assertion))

  /**
   * Checks the test passes for "sufficient" numbers of samples from the
   * given random variable.
   */
  final def check[R, A](rv: Gen[R, A])(test: A => TestResult): ZIO[R, Nothing, TestResult] =
    checkSome(rv)(200)(test)

  /**
   * A version of `check` that accepts two random variables.
   */
  final def check[R, A, B](rv1: Gen[R, A], rv2: Gen[R, B])(test: (A, B) => TestResult): ZIO[R, Nothing, TestResult] =
    check(rv1 <*> rv2)(test.tupled)

  /**
   * A version of `check` that accepts three random variables.
   */
  final def check[R, A, B, C](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C])(
    test: (A, B, C) => TestResult
  ): ZIO[R, Nothing, TestResult] =
    check(rv1 <*> rv2 <*> rv3)(reassociate(test))

  /**
   * A version of `check` that accepts four random variables.
   */
  final def check[R, A, B, C, D](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C], rv4: Gen[R, D])(
    test: (A, B, C, D) => TestResult
  ): ZIO[R, Nothing, TestResult] =
    check(rv1 <*> rv2 <*> rv3 <*> rv4)(reassociate(test))

  /**
   * Checks the effectual test passes for "sufficient" numbers of samples from
   * the given random variable.
   */
  final def checkM[R, R1 <: R, E, A](rv: Gen[R, A])(test: A => ZIO[R1, E, TestResult]): ZIO[R1, E, TestResult] =
    checkSomeM(rv)(200)(test)

  /**
   * A version of `checkM` that accepts two random variables.
   */
  final def checkM[R, R1 <: R, E, A, B](rv1: Gen[R, A], rv2: Gen[R, B])(
    test: (A, B) => ZIO[R1, E, TestResult]
  ): ZIO[R1, E, TestResult] =
    checkM(rv1 <*> rv2)(test.tupled)

  /**
   * A version of `checkM` that accepts three random variables.
   */
  final def checkM[R, R1 <: R, E, A, B, C](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C])(
    test: (A, B, C) => ZIO[R1, E, TestResult]
  ): ZIO[R1, E, TestResult] =
    checkM(rv1 <*> rv2 <*> rv3)(reassociate(test))

  /**
   * A version of `checkM` that accepts four random variables.
   */
  final def checkM[R, R1 <: R, E, A, B, C, D](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C], rv4: Gen[R, D])(
    test: (A, B, C, D) => ZIO[R1, E, TestResult]
  ): ZIO[R1, E, TestResult] =
    checkM(rv1 <*> rv2 <*> rv3 <*> rv4)(reassociate(test))

  /**
   * Checks the test passes for all values from the given random variable. This
   * is useful for deterministic `Gen` that comprehensively explore all
   * possibilities in a given domain.
   */
  final def checkAll[R, A](rv: Gen[R, A])(test: A => TestResult): ZIO[R, Nothing, TestResult] =
    checkAllM(rv)(test andThen ZIO.succeed)

  /**
   * A version of `checkAll` that accepts two random variables.
   */
  final def checkAll[R, A, B](rv1: Gen[R, A], rv2: Gen[R, B])(test: (A, B) => TestResult): ZIO[R, Nothing, TestResult] =
    checkAll(rv1 <*> rv2)(test.tupled)

  /**
   * A version of `checkAll` that accepts three random variables.
   */
  final def checkAll[R, A, B, C](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C])(
    test: (A, B, C) => TestResult
  ): ZIO[R, Nothing, TestResult] =
    checkAll(rv1 <*> rv2 <*> rv3)(reassociate(test))

  /**
   * A version of `checkAll` that accepts four random variables.
   */
  final def checkAll[R, A, B, C, D](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C], rv4: Gen[R, D])(
    test: (A, B, C, D) => TestResult
  ): ZIO[R, Nothing, TestResult] =
    checkAll(rv1 <*> rv2 <*> rv3 <*> rv4)(reassociate(test))

  /**
   * Checks the effectual test passes for all values from the given random
   * variable. This is useful for deterministic `Gen` that comprehensively
   * explore all possibilities in a given domain.
   */
  final def checkAllM[R, R1 <: R, E, A](rv: Gen[R, A])(test: A => ZIO[R1, E, TestResult]): ZIO[R1, E, TestResult] =
    checkStream(rv.sample)(test)

  /**
   * A version of `checkAllM` that accepts two random variables.
   */
  final def checkAllM[R, R1 <: R, E, A, B](rv1: Gen[R, A], rv2: Gen[R, B])(
    test: (A, B) => ZIO[R1, E, TestResult]
  ): ZIO[R1, E, TestResult] =
    checkAllM(rv1 <*> rv2)(test.tupled)

  /**
   * A version of `checkAllM` that accepts three random variables.
   */
  final def checkAllM[R, R1 <: R, E, A, B, C](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C])(
    test: (A, B, C) => ZIO[R1, E, TestResult]
  ): ZIO[R1, E, TestResult] =
    checkAllM(rv1 <*> rv2 <*> rv3)(reassociate(test))

  /**
   * A version of `checkAllM` that accepts four random variables.
   */
  final def checkAllM[R, R1 <: R, E, A, B, C, D](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C], rv4: Gen[R, D])(
    test: (A, B, C, D) => ZIO[R1, E, TestResult]
  ): ZIO[R1, E, TestResult] =
    checkAllM(rv1 <*> rv2 <*> rv3 <*> rv4)(reassociate(test))

  /**
   * Checks the test passes for the specified number of samples from the given
   * random variable.
   */
  final def checkSome[R, A](rv: Gen[R, A])(n: Int)(test: A => TestResult): ZIO[R, Nothing, TestResult] =
    checkSomeM(rv)(n)(test andThen ZIO.succeed)

  /**
   * A version of `checkSome` that accepts two random variables.
   */
  final def checkSome[R, A, B](rv1: Gen[R, A], rv2: Gen[R, B])(
    n: Int
  )(test: (A, B) => TestResult): ZIO[R, Nothing, TestResult] =
    checkSome(rv1 <*> rv2)(n)(test.tupled)

  /**
   * A version of `checkSome` that accepts three random variables.
   */
  final def checkSome[R, A, B, C](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C])(
    n: Int
  )(test: (A, B, C) => TestResult): ZIO[R, Nothing, TestResult] =
    checkSome(rv1 <*> rv2 <*> rv3)(n)(reassociate(test))

  /**
   * A version of `checkSome` that accepts four random variables.
   */
  final def checkSome[R, A, B, C, D](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C], rv4: Gen[R, D])(
    n: Int
  )(test: (A, B, C, D) => TestResult): ZIO[R, Nothing, TestResult] =
    checkSome(rv1 <*> rv2 <*> rv3 <*> rv4)(n)(reassociate(test))

  /**
   * Checks the effectual test passes for the specified number of samples from
   * the given random variable.
   */
  final def checkSomeM[R, R1 <: R, E, A](
    rv: Gen[R, A]
  )(n: Int)(test: A => ZIO[R1, E, TestResult]): ZIO[R1, E, TestResult] =
    checkStream(rv.sample.forever.take(n))(test)

  /**
   * A version of `checkSomeM` that accepts two random variables.
   */
  final def checkSomeM[R, R1 <: R, E, A, B](rv1: Gen[R, A], rv2: Gen[R, B])(
    n: Int
  )(test: (A, B) => ZIO[R1, E, TestResult]): ZIO[R1, E, TestResult] =
    checkSomeM(rv1 <*> rv2)(n)(test.tupled)

  /**
   * A version of `checkSomeM` that accepts three random variables.
   */
  final def checkSomeM[R, R1 <: R, E, A, B, C](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C])(
    n: Int
  )(test: (A, B, C) => ZIO[R1, E, TestResult]): ZIO[R1, E, TestResult] =
    checkSomeM(rv1 <*> rv2 <*> rv3)(n)(reassociate(test))

  /**
   * A version of `checkSomeM` that accepts four random variables.
   */
  final def checkSomeM[R, R1 <: R, E, A, B, C, D](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C], rv4: Gen[R, D])(
    n: Int
  )(test: (A, B, C, D) => ZIO[R1, E, TestResult]): ZIO[R1, E, TestResult] =
    checkSomeM(rv1 <*> rv2 <*> rv3 <*> rv4)(n)(reassociate(test))

  /**
   * Creates a failed test result with the specified runtime cause.
   */
  final def failed[E](cause: Cause[E]): ZTest[Any, E, Nothing] =
    ZIO.fail(TestFailure.Runtime(cause))

  /**
   * Creates an ignored test result.
   */
  final val ignored: ZTest[Any, Nothing, Nothing] =
    ZIO.succeed(TestSuccess.Ignored)

  /**
   * Passes platform specific information to the specified function, which will
   * use that information to create a test. If the platform is neither ScalaJS
   * nor the JVM, an ignored test result will be returned.
   */
  final def platformSpecific[R, E, A, S](js: => A, jvm: => A)(f: A => ZTest[R, E, S]): ZTest[R, E, S] =
    if (TestPlatform.isJS) f(js)
    else if (TestPlatform.isJVM) f(jvm)
    else ignored

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
  final def testM[R, E, L](label: L)(assertion: => ZIO[R, E, TestResult]): ZSpec[R, E, L, Unit] =
    Spec.test(
      label,
      ZIO
        .effectSuspendTotal(assertion)
        .foldCauseM(
          cause => ZIO.fail(TestFailure.Runtime(cause)),
          result =>
            result.failures match {
              case None           => ZIO.succeed(TestSuccess.Succeeded(BoolAlgebra.unit))
              case Some(failures) => ZIO.fail(TestFailure.Assertion(failures))
            }
        )
    )

  /**
   * Passes version specific information to the specified function, which will
   * use that information to create a test. If the version is neither Dotty nor
   * Scala 2, an ignored test result will be returned.
   */
  final def versionSpecific[R, E, A, S](dotty: => A, scala2: => A)(f: A => ZTest[R, E, S]): ZTest[R, E, S] =
    if (TestVersion.isDotty) f(dotty)
    else if (TestVersion.isScala2) f(scala2)
    else ignored

  private final def checkStream[R, R1 <: R, E, A](stream: ZStream[R, Nothing, Sample[R, A]], maxShrinks: Int = 1000)(
    test: A => ZIO[R1, E, TestResult]
  ): ZIO[R1, E, TestResult] =
    stream.zipWithIndex.mapM {
      case (initial, index) =>
        initial.traverse(
          input =>
            test(input).traced
              .map(_.map(_.copy(gen = Some(GenFailureDetails(initial.value, input, index)))))
              .either
        )
    }.dropWhile(!_.value.fold(_ => true, _.isFailure)) // Drop until we get to a failure
      .take(1)                                          // Get the first failure
      .flatMap(_.shrinkSearch(_.fold(_ => true, _.isFailure)).take(maxShrinks))
      .run(ZSink.collectAll[Either[E, TestResult]]) // Collect all the shrunken values
      .flatMap { shrinks =>
        // Get the "last" failure, the smallest according to the shrinker:
        shrinks
          .filter(_.fold(_ => true, _.isFailure))
          .lastOption
          .fold[ZIO[R, E, TestResult]](
            ZIO.succeed {
              BoolAlgebra.success {
                FailureDetails(
                  ::(AssertionValue(Assertion.anything, ()), Nil)
                )
              }
            }
          )(ZIO.fromEither(_))
      }
      .untraced

  private final def reassociate[A, B, C, D](f: (A, B, C) => D): (((A, B), C)) => D = {
    case ((a, b), c) => f(a, b, c)
  }

  private final def reassociate[A, B, C, D, E](f: (A, B, C, D) => E): ((((A, B), C), D)) => E = {
    case (((a, b), c), d) => f(a, b, c, d)
  }

  implicit class ZioOfTestResultOps[R, E](val res: ZIO[R, E, TestResult]) {
    def &&[R1](that: ZIO[R1, E, TestResult]): ZIO[R1 with R, E, TestResult] = res.zipWith(that)(_ && _)
  }
}
