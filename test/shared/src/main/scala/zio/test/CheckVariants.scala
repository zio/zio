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

package zio.test

import zio.ZIO
import zio.stream.{ ZSink, ZStream }

trait CheckVariants {

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
}
