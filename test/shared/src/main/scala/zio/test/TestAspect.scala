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

import zio.{ Cause, ZIO, ZManaged, ZSchedule }
import zio.duration._
import zio.clock.Clock
import zio.system
import zio.system.System
import zio.test.environment.Live

/**
 * A `TestAspect` is an aspect that can be weaved into specs. You can think of
 * an aspect as a polymorphic function, capable of transforming one test into
 * another, possibly enlarging the environment, error, or success type.
 */
trait TestAspect[+LowerR, -UpperR, +LowerE, -UpperE, +LowerS, -UpperS] { self =>

  /**
   * Applies the aspect to some tests in the spec, chosen by the provided
   * predicate.
   */
  def some[R >: LowerR <: UpperR, E >: LowerE <: UpperE, S >: LowerS <: UpperS, L](
    predicate: L => Boolean,
    spec: ZSpec[R, E, L, S]
  ): ZSpec[R, E, L, S]

  /**
   * An alias for [[all]].
   */
  final def apply[R >: LowerR <: UpperR, E >: LowerE <: UpperE, S >: LowerS <: UpperS, L](
    spec: ZSpec[R, E, L, S]
  ): ZSpec[R, E, L, S] =
    all(spec)

  /**
   * Applies the aspect to every test in the spec.
   */
  final def all[R >: LowerR <: UpperR, E >: LowerE <: UpperE, S >: LowerS <: UpperS, L](
    spec: ZSpec[R, E, L, S]
  ): ZSpec[R, E, L, S] =
    some[R, E, S, L](_ => true, spec)

  /**
   * Returns a new aspect that represents the sequential composition of this
   * aspect with the specified one.
   */
  final def >>>[
    LowerR1 >: LowerR,
    UpperR1 <: UpperR,
    LowerE1 >: LowerE,
    UpperE1 <: UpperE,
    LowerS1 >: LowerS,
    UpperS1 <: UpperS
  ](
    that: TestAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerS1, UpperS1]
  ): TestAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerS1, UpperS1] =
    new TestAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerS1, UpperS1] {
      def some[R >: LowerR1 <: UpperR1, E >: LowerE1 <: UpperE1, S >: LowerS1 <: UpperS1, L](
        predicate: L => Boolean,
        spec: ZSpec[R, E, L, S]
      ): ZSpec[R, E, L, S] =
        that.some(predicate, self.some(predicate, spec))
    }

  final def andThen[
    LowerR1 >: LowerR,
    UpperR1 <: UpperR,
    LowerE1 >: LowerE,
    UpperE1 <: UpperE,
    LowerS1 >: LowerS,
    UpperS1 <: UpperS
  ](
    that: TestAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerS1, UpperS1]
  ): TestAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerS1, UpperS1] =
    self >>> that
}
object TestAspect extends TimeoutVariants {

  /**
   * An aspect that returns the tests unchanged
   */
  val identity: TestAspectPoly =
    new TestAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def some[R >: Nothing <: Any, E >: Nothing <: Any, S >: Nothing <: Any, L](
        predicate: L => Boolean,
        spec: ZSpec[R, E, L, S]
      ): ZSpec[R, E, L, S] = spec
    }

  /**
   * An aspect that marks tests as ignored.
   */
  val ignore: TestAspectPoly =
    new TestAspect.PerTest[Nothing, Any, Nothing, Any, Nothing, Any] {
      def perTest[R >: Nothing <: Any, E >: Nothing <: Any, S >: Nothing <: Any](
        test: ZIO[R, TestFailure[E], TestSuccess[S]]
      ): ZIO[R, TestFailure[E], TestSuccess[S]] =
        ZIO.succeed(TestSuccess.Ignored)
    }

  /**
   * Constructs an aspect that runs the specified effect after every test.
   */
  def after[R0, E0](effect: ZIO[R0, E0, Any]): TestAspect[Nothing, R0, E0, Any, Nothing, Any] =
    new TestAspect.PerTest[Nothing, R0, E0, Any, Nothing, Any] {
      def perTest[R >: Nothing <: R0, E >: E0 <: Any, S](
        test: ZIO[R, TestFailure[E], TestSuccess[S]]
      ): ZIO[R, TestFailure[E], TestSuccess[S]] =
        test <* effect.catchAllCause(cause => ZIO.fail(TestFailure.Runtime(cause)))
    }

  /**
   * Constructs an aspect that evaluates every test inside the context of a `Managed`.
   */
  def around[R0, E0](before: ZIO[R0, E0, Any], after: ZIO[R0, Nothing, Any]) =
    new TestAspect.PerTest[Nothing, R0, E0, Any, Nothing, Any] {
      def perTest[R >: Nothing <: R0, E >: E0 <: Any, S >: Nothing <: Any](
        test: ZIO[R, TestFailure[E], TestSuccess[S]]
      ): ZIO[R, TestFailure[E], TestSuccess[S]] =
        ZManaged
          .make(before)(_ => after)
          .catchAllCause(c => ZManaged.fail(TestFailure.Runtime(c)))
          .use(_ => test)
    }

  /**
   * Constructs an aspect that evaluates every test inside the context of the managed function.
   */
  def aroundTest[R0, E0, S0](
    managed: ZManaged[R0, TestFailure[E0], TestSuccess[S0] => ZIO[R0, TestFailure[E0], TestSuccess[S0]]]
  ) =
    new TestAspect.PerTest[Nothing, R0, E0, Any, S0, S0] {
      def perTest[R >: Nothing <: R0, E >: E0 <: Any, S >: S0 <: S0](
        test: ZIO[R, TestFailure[E], TestSuccess[S]]
      ): ZIO[R, TestFailure[E], TestSuccess[S]] =
        managed.use(f => test.flatMap(f))
    }

  /**
   * Constucts a simple monomorphic aspect that only works with the specified
   * environment and error type.
   */
  def aspect[R0, E0, S0](
    f: ZIO[R0, TestFailure[E0], TestSuccess[S0]] => ZIO[R0, TestFailure[E0], TestSuccess[S0]]
  ): TestAspect[R0, R0, E0, E0, S0, S0] =
    new TestAspect.PerTest[R0, R0, E0, E0, S0, S0] {
      def perTest[R >: R0 <: R0, E >: E0 <: E0, S >: S0 <: S0](
        test: ZIO[R, TestFailure[E], TestSuccess[S]]
      ): ZIO[R, TestFailure[E], TestSuccess[S]] =
        f(test)
    }

  /**
   * Constructs an aspect that runs the specified effect before every test.
   */
  def before[R0, E0, S0](effect: ZIO[R0, Nothing, Any]): TestAspect[Nothing, R0, E0, Any, S0, Any] =
    new TestAspect.PerTest[Nothing, R0, E0, Any, S0, Any] {
      def perTest[R >: Nothing <: R0, E >: E0 <: Any, S >: S0 <: Any](
        test: ZIO[R, TestFailure[E], TestSuccess[S]]
      ): ZIO[R, TestFailure[E], TestSuccess[S]] =
        effect *> test
    }

  /**
   * An aspect that applies the specified aspect on Dotty.
   */
  def dotty[LowerR, UpperR, LowerE, UpperE, LowerS, UpperS](
    that: TestAspect[LowerR, UpperR, LowerE, UpperE, LowerS, UpperS]
  ): TestAspect[LowerR, UpperR, LowerE, UpperE, LowerS, UpperS] =
    if (TestVersion.isDotty) that else identity

  /**
   * An aspect that only runs tests on Dotty.
   */
  val dottyOnly: TestAspectPoly =
    if (TestVersion.isDotty) identity else ignore

  /**
   * An aspect that retries a test until success, without limit.
   */
  val eventually: TestAspectPoly =
    new TestAspect.PerTest[Nothing, Any, Nothing, Any, Nothing, Any] {
      def perTest[R >: Nothing <: Any, E >: Nothing <: Any, S >: Nothing <: Any](
        test: ZIO[R, TestFailure[E], TestSuccess[S]]
      ): ZIO[R, TestFailure[E], TestSuccess[S]] =
        test.eventually
    }

  /**
   * An aspect that runs tests on all versions except Dotty.
   */
  val exceptDotty: TestAspectPoly =
    if (TestVersion.isDotty) ignore else identity

  /**
   * An aspect that runs tests on all platforms except ScalaJS.
   */
  val exceptJS: TestAspectPoly =
    if (TestPlatform.isJS) ignore else identity

  /**
   * An aspect that runs tests on all platforms except the JVM.
   */
  val exceptJVM: TestAspectPoly =
    if (TestPlatform.isJVM) ignore else identity

  /**
   * An aspect that runs tests on all versions except Scala2.
   */
  val exceptScala2: TestAspectPoly =
    if (TestVersion.isScala2) ignore else identity

  /**
   * An aspect that sets suites to the specified execution strategy, but only
   * if their current strategy is inherited (undefined).
   */
  def executionStrategy(exec: ExecutionStrategy): TestAspectPoly =
    new TestAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def some[R >: Nothing <: Any, E >: Nothing <: Any, S >: Nothing <: Any, L](
        predicate: L => Boolean,
        spec: ZSpec[R, E, L, S]
      ): ZSpec[R, E, L, S] = spec.transform[R, TestFailure[E], L, TestSuccess[S]] {
        case Spec.SuiteCase(label, specs, None) if (predicate(label)) => Spec.SuiteCase(label, specs, Some(exec))
        case c                                                        => c
      }
    }

  /**
   * An aspect that makes a test that failed for any reason pass. Note that if the test
   * passes this aspect will make it fail.
   */
  val failure: PerTest[Nothing, Any, Nothing, Any, Unit, Unit] = failure(Assertion.anything)

  /**
   * An aspect that makes a test that failed for the specified failure pass.  Note that the
   * test will fail for other failures and also if it passes correctly.
   */
  def failure[E0](p: Assertion[TestFailure[E0]]): PerTest[Nothing, Any, Nothing, E0, Unit, Unit] =
    new TestAspect.PerTest[Nothing, Any, Nothing, E0, Unit, Unit] {
      def perTest[R >: Nothing <: Any, E >: Nothing <: E0, S >: Unit <: Unit](
        test: ZIO[R, TestFailure[E], TestSuccess[S]]
      ): ZIO[R, TestFailure[E], TestSuccess[S]] = {
        lazy val succeed = ZIO.succeed(TestSuccess.Succeeded(BoolAlgebra.unit))
        test.foldM(
          {
            case testFailure if p.run(testFailure).isSuccess => succeed
            case other =>
              ZIO.fail(
                TestFailure.Assertion(assert(other, p))
              )
          },
          _ => ZIO.fail(TestFailure.Runtime(zio.Cause.die(new RuntimeException("did not fail as expected"))))
        )
      }
    }

  /**
   * An aspect that retries a test until success, with a default limit, for use
   * with flaky tests.
   */
  val flaky: TestAspectPoly =
    flaky(100)

  /**
   * An aspect that retries a test until success, with the specified limit, for
   * use with flaky tests.
   */
  def flaky(n: Int): TestAspectPoly =
    retry(ZSchedule.recurs(n))

  /**
   * An aspect that only runs a test if the specified environment variable
   * satisfies the specified assertion.
   */
  def ifEnv(env: String, assertion: Assertion[String]): TestAspect[Nothing, Live[System], Nothing, Any, Nothing, Any] =
    new TestAspect.PerTest[Nothing, Live[System], Nothing, Any, Nothing, Any] {
      def perTest[R <: Live[System], E, S](
        test: ZIO[R, TestFailure[E], TestSuccess[S]]
      ): ZIO[R, TestFailure[E], TestSuccess[S]] =
        Live.live(system.env(env)).orDie.flatMap { value =>
          value
            .filter(assertion.test)
            .fold[ZIO[R, TestFailure[E], TestSuccess[S]]](ZIO.succeed(TestSuccess.Ignored))(
              _ => test
            )
        }
    }

  /**
   * As aspect that only runs a test if the specified environment variable is
   * set.
   */
  def ifEnvSet(env: String): TestAspect[Nothing, Live[System], Nothing, Any, Nothing, Any] =
    ifEnv(env, Assertion.anything)

  /**
   * An aspect that only runs a test if the specified Java property satisfies
   * the specified assertion.
   */
  def ifProp(
    prop: String,
    assertion: Assertion[String]
  ): TestAspect[Nothing, Live[System], Nothing, Any, Nothing, Any] =
    new TestAspect.PerTest[Nothing, Live[System], Nothing, Any, Nothing, Any] {
      def perTest[R <: Live[System], E, S](
        test: ZIO[R, TestFailure[E], TestSuccess[S]]
      ): ZIO[R, TestFailure[E], TestSuccess[S]] =
        Live.live(system.property(prop)).orDie.flatMap { value =>
          value
            .filter(assertion.test)
            .fold[ZIO[R, TestFailure[E], TestSuccess[S]]](ZIO.succeed(TestSuccess.Ignored))(
              _ => test
            )
        }
    }

  /**
   * As aspect that only runs a test if the specified Java property is set.
   */
  def ifPropSet(prop: String): TestAspect[Nothing, Live[System], Nothing, Any, Nothing, Any] =
    ifProp(prop, Assertion.anything)

  /**
   * An aspect that applies the specified aspect on ScalaJS.
   */
  def js[LowerR, UpperR, LowerE, UpperE, LowerS, UpperS](
    that: TestAspect[LowerR, UpperR, LowerE, UpperE, LowerS, UpperS]
  ): TestAspect[LowerR, UpperR, LowerE, UpperE, LowerS, UpperS] =
    if (TestPlatform.isJS) that else identity

  /**
   * An aspect that only runs tests on ScalaJS.
   */
  val jsOnly: TestAspectPoly =
    if (TestPlatform.isJS) identity else ignore

  /**
   * An aspect that applies the specified aspect on the JVM.
   */
  def jvm[LowerR, UpperR, LowerE, UpperE, LowerS, UpperS](
    that: TestAspect[LowerR, UpperR, LowerE, UpperE, LowerS, UpperS]
  ): TestAspect[LowerR, UpperR, LowerE, UpperE, LowerS, UpperS] =
    if (TestPlatform.isJVM) that else identity

  /**
   * An aspect that only runs tests on the JVM.
   */
  val jvmOnly: TestAspectPoly =
    if (TestPlatform.isJVM) identity else ignore

  /**
   * An aspect that repeats the test a default number of times, ensuring it is
   * stable ("non-flaky"). Stops at the first failure.
   */
  val nonFlaky: TestAspectPoly =
    nonFlaky(100)

  /**
   * An aspect that repeats the test a specified number of times, ensuring it
   * is stable ("non-flaky"). Stops at the first failure.
   */
  def nonFlaky(n0: Int): TestAspectPoly =
    new TestAspect.PerTest[Nothing, Any, Nothing, Any, Nothing, Any] {
      def perTest[R >: Nothing <: Any, E >: Nothing <: Any, S >: Nothing <: Any](
        test: ZIO[R, TestFailure[E], TestSuccess[S]]
      ): ZIO[R, TestFailure[E], TestSuccess[S]] = {
        def repeat(n: Int): ZIO[R, TestFailure[E], TestSuccess[S]] =
          if (n <= 1) test
          else test.flatMap(_ => repeat(n - 1))

        repeat(n0)
      }
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
  def retry[R0, E0, S0](
    schedule: ZSchedule[R0, TestFailure[E0], S0]
  ): TestAspect[Nothing, R0, Nothing, E0, Nothing, S0] =
    new TestAspect.PerTest[Nothing, R0, Nothing, E0, Nothing, S0] {
      def perTest[R >: Nothing <: R0, E >: Nothing <: E0, S >: Nothing <: S0](
        test: ZIO[R, TestFailure[E], TestSuccess[S]]
      ): ZIO[R, TestFailure[E], TestSuccess[S]] =
        test.retry(schedule)
    }

  /**
   * An aspect that executes the members of a suite sequentially.
   */
  val sequential: TestAspectPoly = executionStrategy(ExecutionStrategy.Sequential)

  /**
   * An aspect that applies the specified aspect on Scala 2.
   */
  def scala2[LowerR, UpperR, LowerE, UpperE, LowerS, UpperS](
    that: TestAspect[LowerR, UpperR, LowerE, UpperE, LowerS, UpperS]
  ): TestAspect[LowerR, UpperR, LowerE, UpperE, LowerS, UpperS] =
    if (TestVersion.isScala2) that else identity

  /**
   * An aspect that only runs tests on Scala 2.
   */
  val scala2Only: TestAspectPoly =
    if (TestVersion.isScala2) identity else ignore

  /**
   * An aspect that converts ignored tests into test failures.
   */
  val success: TestAspectPoly =
    new TestAspect.PerTest[Nothing, Any, Nothing, Any, Nothing, Any] {
      def perTest[R >: Nothing <: Any, E >: Nothing <: Any, S >: Nothing <: Any](
        test: ZIO[R, TestFailure[E], TestSuccess[S]]
      ): ZIO[R, TestFailure[E], TestSuccess[S]] =
        test.flatMap {
          case TestSuccess.Ignored =>
            ZIO.fail(TestFailure.Runtime(Cause.die(new RuntimeException("Test was ignored."))))
          case x => ZIO.succeed(x)
        }
    }

  /**
   * An aspect that times out tests using the specified duration.
   * @param duration maximum test duration
   * @param interruptDuration after test timeout will wait given duration for successful interruption
   */
  def timeout(
    duration: Duration,
    interruptDuration: Duration = 1.second
  ): TestAspect[Nothing, Live[Clock], Nothing, Any, Nothing, Any] =
    new TestAspect.PerTest[Nothing, Live[Clock], Nothing, Any, Nothing, Any] {
      def perTest[R >: Nothing <: Live[Clock], E >: Nothing <: Any, S >: Nothing <: Any](
        test: ZIO[R, TestFailure[E], TestSuccess[S]]
      ): ZIO[R, TestFailure[E], TestSuccess[S]] = {
        def timeoutFailure =
          TestTimeoutException(s"Timeout of ${duration.render} exceeded.")
        def interruptionTimeoutFailure = {
          val msg =
            s"Timeout of ${duration.render} exceeded. Couldn't interrupt test within ${interruptDuration.render}, possible resource leak!"
          TestTimeoutException(msg)
        }
        Live
          .withLive(test)(_.either.timeoutFork(duration).flatMap {
            case Left(fiber) =>
              fiber.join.raceWith(ZIO.sleep(interruptDuration))(
                (_, fiber) => fiber.interrupt *> ZIO.fail(TestFailure.Runtime(Cause.die(timeoutFailure))),
                (_, _) => ZIO.fail(TestFailure.Runtime(Cause.die(interruptionTimeoutFailure)))
              )
            case Right(result) => result.fold(ZIO.fail, ZIO.succeed)
          })
      }
    }

  trait PerTest[+LowerR, -UpperR, +LowerE, -UpperE, +LowerS, -UpperS]
      extends TestAspect[LowerR, UpperR, LowerE, UpperE, LowerS, UpperS] {
    def perTest[R >: LowerR <: UpperR, E >: LowerE <: UpperE, S >: LowerS <: UpperS](
      test: ZIO[R, TestFailure[E], TestSuccess[S]]
    ): ZIO[R, TestFailure[E], TestSuccess[S]]

    final def some[R >: LowerR <: UpperR, E >: LowerE <: UpperE, S >: LowerS <: UpperS, L](
      predicate: L => Boolean,
      spec: ZSpec[R, E, L, S]
    ): ZSpec[R, E, L, S] =
      spec.transform[R, TestFailure[E], L, TestSuccess[S]] {
        case c @ Spec.SuiteCase(_, _, _) => c
        case Spec.TestCase(label, test) =>
          Spec.TestCase(label, if (predicate(label)) perTest(test) else test)
      }
  }
}
