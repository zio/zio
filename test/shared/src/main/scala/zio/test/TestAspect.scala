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

import java.util.concurrent.TimeoutException

import zio.{ clock, Cause, ZIO, ZManaged, ZSchedule }
import zio.duration.Duration
import zio.clock.Clock
import zio.test.mock.Live

/**
 * A `TestAspect` is an aspect that can be weaved into specs. You can think of
 * an aspect as a polymorphic function, capable of transforming one test into
 * another, possibly enlarging the environment or error type.
 */
trait TestAspect[+LowerR, -UpperR, +LowerE, -UpperE] { self =>

  /**
   * Applies the aspect to some tests in the spec, chosen by the provided
   * predicate.
   */
  def some[R >: LowerR <: UpperR, E >: LowerE <: UpperE, L](
    predicate: L => Boolean,
    spec: ZSpec[R, E, L]
  ): ZSpec[R, E, L]

  /**
   * An alias for [[all]].
   */
  final def apply[R >: LowerR <: UpperR, E >: LowerE <: UpperE, L](spec: ZSpec[R, E, L]): ZSpec[R, E, L] = all(spec)

  /**
   * Applies the aspect to every test in the spec.
   */
  final def all[R >: LowerR <: UpperR, E >: LowerE <: UpperE, L](spec: ZSpec[R, E, L]): ZSpec[R, E, L] =
    some[R, E, L](_ => true, spec)

  /**
   * Returns a new aspect that represents the sequential composition of this
   * aspect with the specified one.
   */
  final def >>>[LowerR1 >: LowerR, UpperR1 <: UpperR, LowerE1 >: LowerE, UpperE1 <: UpperE](
    that: TestAspect[LowerR1, UpperR1, LowerE1, UpperE1]
  ): TestAspect[LowerR1, UpperR1, LowerE1, UpperE1] =
    new TestAspect[LowerR1, UpperR1, LowerE1, UpperE1] {
      def some[R >: LowerR1 <: UpperR1, E >: LowerE1 <: UpperE1, L](
        predicate: L => Boolean,
        spec: ZSpec[R, E, L]
      ): ZSpec[R, E, L] =
        that.some(predicate, self.some(predicate, spec))
    }

  final def andThen[LowerR1 >: LowerR, UpperR1 <: UpperR, LowerE1 >: LowerE, UpperE1 <: UpperE](
    that: TestAspect[LowerR1, UpperR1, LowerE1, UpperE1]
  ): TestAspect[LowerR1, UpperR1, LowerE1, UpperE1] = self >>> that
}
object TestAspect {

  /**
   * Constructs an aspect that runs the specified effect after every test.
   */
  def after[R0, E0](effect: ZIO[R0, E0, Any]): TestAspect[Nothing, R0, E0, Any] =
    new TestAspect.PerTest[Nothing, R0, E0, Any] {
      def perTest[R >: Nothing <: R0, E >: E0 <: Any](test: ZIO[R, E, TestResult]): ZIO[R, E, TestResult] =
        test <* effect
    }

  /**
   * Constructs an aspect that evaluates every test inside the context of the managed function.
   */
  def around[R0, E0](managed: ZManaged[R0, E0, TestResult => ZIO[R0, E0, TestResult]]) =
    new TestAspect.PerTest[Nothing, R0, E0, Any] {
      def perTest[R >: Nothing <: R0, E >: E0 <: Any](test: ZIO[R, E, TestResult]): ZIO[R, E, TestResult] =
        managed.use(f => test.flatMap(f))
    }

  /**
   * Constucts a simple monomorphic aspect that only works with the specified
   * environment and error type.
   */
  def aspect[R0, E0](f: ZIO[R0, E0, TestResult] => ZIO[R0, E0, TestResult]): TestAspect[R0, R0, E0, E0] =
    new TestAspect.PerTest[R0, R0, E0, E0] {
      def perTest[R >: R0 <: R0, E >: E0 <: E0](test: ZIO[R, E, TestResult]): ZIO[R, E, TestResult] = f(test)
    }

  /**
   * Constructs an aspect that runs the specified effect before every test.
   */
  def before[R0, E0](effect: ZIO[R0, E0, Any]): TestAspect[Nothing, R0, E0, Any] =
    new TestAspect.PerTest[Nothing, R0, E0, Any] {
      def perTest[R >: Nothing <: R0, E >: E0 <: Any](test: ZIO[R, E, TestResult]): ZIO[R, E, TestResult] =
        effect *> test
    }

  /**
   * An aspect that retries a test until success, without limit.
   */
  val eventually: TestAspectPoly =
    new TestAspect.PerTest[Nothing, Any, Nothing, Any] {
      def perTest[R >: Nothing <: Any, E >: Nothing <: Any](
        test: ZIO[R, E, TestResult]
      ): ZIO[R, Nothing, TestResult] = {
        lazy val untilSuccess: ZIO[R, Nothing, TestResult] =
          test.foldM(_ => untilSuccess, ZIO.succeed(_))

        untilSuccess
      }
    }

  /**
   * An aspect that sets suites to the specified execution strategy, but only
   * if their current strategy is inherited (undefined).
   */
  def executionStrategy(exec: ExecutionStrategy): TestAspectPoly =
    new TestAspect[Nothing, Any, Nothing, Any] {
      def some[R >: Nothing <: Any, E >: Nothing <: Any, L](
        predicate: L => Boolean,
        spec: ZSpec[R, E, L]
      ): ZSpec[R, E, L] = spec.transform[L, ZIO[R, E, TestResult]] {
        case Spec.SuiteCase(label, specs, None) if (predicate(label)) => Spec.SuiteCase(label, specs, Some(exec))
        case c                                                        => c
      }
    }

  /**
   * An aspect that retries a test until success, without limit, for use with
   * flaky tests.
   */
  val flaky: TestAspectPoly = eventually

  /**
   * An aspect that repeats the test a specified number of times, ensuring it
   * is stable ("non-flaky"). Stops at the first failure.
   */
  def nonFlaky(n0: Int): TestAspectPoly =
    new TestAspect.PerTest[Nothing, Any, Nothing, Any] {
      def perTest[R >: Nothing <: Any, E >: Nothing <: Any](test: ZIO[R, E, TestResult]): ZIO[R, E, TestResult] = {
        def repeat(n: Int): ZIO[R, E, TestResult] =
          if (n <= 1) test
          else
            test.flatMap { result =>
              if (result.success) repeat(n - 1)
              else ZIO.succeed(result)
            }

        repeat(n0)
      }
    }

  /**
   * An aspect that marks tests as ignored.
   */
  val ignore: TestAspectPoly =
    new TestAspect.PerTest[Nothing, Any, Nothing, Any] {
      def perTest[R >: Nothing <: Any, E >: Nothing <: Any](test: ZIO[R, E, TestResult]): ZIO[R, E, TestResult] =
        ZIO.succeed(AssertResult.Ignore)
    }

  /**
   * An aspect that executes the members of a suite in parallel.
   */
  val parallel: TestAspectPoly = executionStrategy(ExecutionStrategy.Parallel)

  /**
   * An aspect that executes the members of a suite in parallel, up to the
   * specified number of concurent fibers.
   */
  def parallelN(n: Int): TestAspectPoly = executionStrategy(ExecutionStrategy.ParallelN(n))

  /**
   * An aspect that retries failed tests according to a schedule.
   */
  def retry[R0, E0](schedule: ZSchedule[R0, E0, Any]): TestAspect[Nothing, R0 with Live[Clock], Nothing, E0] =
    new TestAspect.PerTest[Nothing, R0 with Live[Clock], Nothing, E0] {
      def perTest[R >: Nothing <: R0 with Live[Clock], E >: Nothing <: E0](
        test: ZIO[R, E, TestResult]
      ): ZIO[R, E, TestResult] = {
        def loop(state: schedule.State): ZIO[R with Live[Clock], E, TestResult] =
          test.foldM(
            err =>
              schedule
                .update(err, state)
                .flatMap(
                  decision =>
                    if (decision.cont) Live.live(clock.sleep(decision.delay)) *> loop(decision.state)
                    else ZIO.fail(err)
                ),
            succ => ZIO.succeed(succ)
          )

        schedule.initial.flatMap(loop)
      }
    }

  /**
   * An aspect that executes the members of a suite sequentially.
   */
  val sequential: TestAspectPoly = executionStrategy(ExecutionStrategy.Sequential)

  /**
   * An aspect that times out tests using the specified duration.
   */
  def timeout(duration: Duration): TestAspect[Nothing, Live[Clock], Nothing, Any] =
    new TestAspect.PerTest[Nothing, Live[Clock], Nothing, Any] {
      def perTest[R >: Nothing <: Live[Clock], E >: Nothing <: Any](
        test: ZIO[R, E, TestResult]
      ): ZIO[R, E, TestResult] =
        Live.withLive(test)(_.timeout(duration)).map {
          case None =>
            AssertResult
              .failure(FailureDetails.Runtime(Cause.fail(new TimeoutException(s"Timeout of ${duration} exceeded"))))
          case Some(v) => v
        }
    }

  trait PerTest[+LowerR, -UpperR, +LowerE, -UpperE] extends TestAspect[LowerR, UpperR, LowerE, UpperE] {
    def perTest[R >: LowerR <: UpperR, E >: LowerE <: UpperE](test: ZIO[R, E, TestResult]): ZIO[R, E, TestResult]

    final def some[R >: LowerR <: UpperR, E >: LowerE <: UpperE, L](
      predicate: L => Boolean,
      spec: ZSpec[R, E, L]
    ): ZSpec[R, E, L] =
      spec.transform[L, ZIO[R, E, TestResult]] {
        case c @ Spec.SuiteCase(_, _, _) => c
        case Spec.TestCase(label, test)  => Spec.TestCase(label, if (predicate(label)) perTest(test) else test)
      }
  }
}
