/*
 * Copyright 2019-2021 John A. De Goes and the ZIO Contributors
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

import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stream.{ZSink, ZStream}
import zio.test.AssertionResult.FailureDetailsResult

import scala.collection.immutable.{Queue => ScalaQueue}
import scala.language.implicitConversions
import scala.util.Try

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
 *   import zio.test._
 *   import zio.Clock.nanoTime
 *   import Assertion.isGreaterThan
 *
 *   object MyTest extends DefaultRunnableSpec {
 *     def spec = suite("clock")(
 *       test("time is non-zero") {
 *         for {
 *           time <- Live.live(nanoTime)
 *         } yield assertTrue(time >= 0)
 *       }
 *     )
 *   }
 * }}}
 */
package object test extends CompileVariants {
  type AssertResultM = BoolAlgebraM[Any, Nothing, AssertionValue]
  type AssertResult  = BoolAlgebra[AssertionValue]

  type TestEnvironment =
    Has[Annotations]
      with Has[Live]
      with Has[Sized]
      with Has[TestClock]
      with Has[TestConfig]
      with Has[TestConsole]
      with Has[TestRandom]
      with Has[TestSystem]
      with ZEnv

  object TestEnvironment {
    val any: ZDeps[TestEnvironment, Nothing, TestEnvironment] =
      ZDeps.environment[TestEnvironment](Tracer.newTrace)
    val live: ZDeps[ZEnv, Nothing, TestEnvironment] = {
      implicit val trace = Tracer.newTrace
      Annotations.live ++
        Live.default ++
        Sized.live(100) ++
        ((Live.default ++ Annotations.live) >>> TestClock.default) ++
        TestConfig.live(100, 100, 200, 1000) ++
        (Live.default >>> TestConsole.debug) ++
        TestRandom.deterministic ++
        TestSystem.default
    }
  }

  val liveEnvironment: Deps[Nothing, ZEnv] = ZEnv.live

  val testEnvironment: Deps[Nothing, TestEnvironment] = {
    implicit val trace = Tracer.newTrace
    ZEnv.live >>> TestEnvironment.live
  }

  /**
   * Provides an effect with the "real" environment as opposed to the test
   * environment. This is useful for performing effects such as timing out
   * tests, accessing the real time, or printing to the real console.
   */
  def live[E, A](zio: ZIO[ZEnv, E, A])(implicit trace: ZTraceElement): ZIO[Has[Live], E, A] =
    Live.live(zio)

  /**
   * Transforms this effect with the specified function. The test environment
   * will be provided to this effect, but the live environment will be provided
   * to the transformation function. This can be useful for applying
   * transformations to an effect that require access to the "real" environment
   * while ensuring that the effect itself uses the test environment.
   *
   * {{{
   *   withLive(test)(_.timeout(duration))
   * }}}
   */
  def withLive[R, E, E1, A, B](
    zio: ZIO[R, E, A]
  )(f: IO[E, A] => ZIO[ZEnv, E1, B])(implicit trace: ZTraceElement): ZIO[R with Has[Live], E1, B] =
    Live.withLive(zio)(f)

  type TestResult = BoolAlgebra[AssertionResult]

  object TestResult {
    implicit def trace2TestResult(assert: Assert): TestResult = {
      val trace = TestArrow.run(assert.arrow, Right(()))
      if (trace.isSuccess) BoolAlgebra.success(AssertionResult.TraceResult(trace))
      else BoolAlgebra.failure(AssertionResult.TraceResult(trace))
    }
  }

  /**
   * A `TestReporter[E]` is capable of reporting test results with error type
   * `E`.
   */
  type TestReporter[-E] = (Duration, ExecutedSpec[E]) => URIO[Has[TestLogger], Unit]

  object TestReporter {

    /**
     * TestReporter that does nothing
     */
    val silent: TestReporter[Any] = (_, _) => ZIO.unit
  }

  /**
   * A `ZRTestEnv` is an alias for all ZIO provided
   * [[zio.test.Restorable Restorable]]
   * [[zio.test.TestEnvironment TestEnvironment]] objects
   */
  type ZTestEnv = Has[TestClock] with Has[TestConsole] with Has[TestRandom] with Has[TestSystem]

  /**
   * A `ZTest[R, E]` is an effectfully produced test that requires an `R` and
   * may fail with an `E`.
   */
  type ZTest[-R, +E] = ZIO[R, TestFailure[E], TestSuccess]

  object ZTest {

    /**
     * Builds a test with an effectual assertion.
     */
    def apply[R, E](label: String, assertion: => ZIO[R, E, TestResult])(implicit
      trace: ZTraceElement
    ): ZIO[R, TestFailure[E], TestSuccess] =
      ZIO
        .suspendSucceed(assertion)
        .overrideForkScope(ZScope.global)
        .ensuringChildren { children =>
          ZIO.foreach(children) { child =>
            val warning =
              s"Warning: ZIO Test is attempting to interrupt fiber " +
                s"${child.id} forked in test $label due to automatic, " +
                "supervision, but interruption has taken more than 10 " +
                "seconds to complete. This may indicate a resource leak. " +
                "Make sure you are not forking a fiber in an " +
                "uninterruptible region."
            for {
              fiber <- ZIO.logWarning(warning).delay(10.seconds).provide(Has(Clock.ClockLive)).interruptible.forkDaemon
              _     <- (child.interrupt *> fiber.interrupt).forkDaemon
            } yield ()
          }
        }
        .foldCauseZIO(
          cause => ZIO.fail(TestFailure.Runtime(cause)),
          _.failures match {
            case None           => ZIO.succeedNow(TestSuccess.Succeeded(BoolAlgebra.unit))
            case Some(failures) => ZIO.fail(TestFailure.Assertion(failures))
          }
        )
  }

  /**
   * A `ZSpec[R, E]` is the canonical spec for testing ZIO programs. The spec's
   * test type is a ZIO effect that requires an `R` and might fail with an `E`.
   */
  type ZSpec[-R, +E] = Spec[R, TestFailure[E], TestSuccess]

  /**
   * An `Annotated[A]` contains a value of type `A` along with zero or more test
   * annotations.
   */
  type Annotated[+A] = (A, TestAnnotationMap)

  private def traverseResult[A](
    value: => A,
    assertResult: AssertResult,
    assertion: AssertionM[A],
    expression: Option[String]
  )(implicit trace: ZTraceElement): TestResult = {
    val sourceLocation = Option(trace).collect { case ZTraceElement.SourceLocation(_, file, line, _) =>
      s"$file:$line"
    }

    assertResult.flatMap { fragment =>
      def loop(whole: AssertionValue, failureDetails: FailureDetails): TestResult =
        if (whole.sameAssertion(failureDetails.assertion.head))
          BoolAlgebra.success(FailureDetailsResult(failureDetails))
        else {
          val fragment = whole.result
          val result   = if (fragment.isSuccess) fragment else !fragment
          result.flatMap { fragment =>
            loop(fragment, FailureDetails(::(whole, failureDetails.assertion)))
          }
        }

      loop(
        fragment,
        FailureDetails(::(AssertionValue(assertion, value, assertResult, expression, sourceLocation), Nil))
      )
    }
  }

  /**
   * Checks the assertion holds for the given value.
   */
  override private[test] def assertImpl[A](
    value: => A,
    expression: Option[String] = None
  )(assertion: Assertion[A])(implicit trace: ZTraceElement): TestResult = {
    lazy val tryValue = Try(value)
    traverseResult(tryValue.get, assertion.run(tryValue.get), assertion, expression)
  }

  /**
   * Asserts that the given test was completed.
   */
  def assertCompletes(implicit trace: ZTraceElement): TestResult =
    assertImpl(true)(Assertion.isTrue)

  /**
   * Asserts that the given test was completed.
   */
  def assertCompletesM(implicit trace: ZTraceElement): UIO[TestResult] =
    assertMImpl(UIO.succeedNow(true))(Assertion.isTrue)

  /**
   * Checks the assertion holds for the given effectfully-computed value.
   */
  override private[test] def assertMImpl[R, E, A](effect: ZIO[R, E, A])(
    assertion: AssertionM[A]
  )(implicit trace: ZTraceElement): ZIO[R, E, TestResult] =
    for {
      value        <- effect
      assertResult <- assertion.runM(value).run
    } yield traverseResult(value, assertResult, assertion, None)

  /**
   * Checks the test passes for "sufficient" numbers of samples from the given
   * random variable.
   */
  def check[R <: Has[TestConfig], A, In](rv: Gen[R, A])(test: A => In)(implicit
    checkConstructor: CheckConstructor[R, In],
    trace: ZTraceElement
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    TestConfig.samples.flatMap(n =>
      checkStream(rv.sample.forever.collectSome.take(n.toLong))(a => checkConstructor(test(a)))
    )

  /**
   * A version of `check` that accepts two random variables.
   */
  def check[R <: Has[TestConfig], A, B, In](rv1: Gen[R, A], rv2: Gen[R, B])(
    test: (A, B) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    trace: ZTraceElement
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    check(rv1 <*> rv2)(test.tupled)

  /**
   * A version of `check` that accepts three random variables.
   */
  def check[R <: Has[TestConfig], A, B, C, In](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C])(
    test: (A, B, C) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    trace: ZTraceElement
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    check(rv1 <*> rv2 <*> rv3)(test.tupled)

  /**
   * A version of `check` that accepts four random variables.
   */
  def check[R <: Has[TestConfig], A, B, C, D, In](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C], rv4: Gen[R, D])(
    test: (A, B, C, D) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    trace: ZTraceElement
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    check(rv1 <*> rv2 <*> rv3 <*> rv4)(test.tupled)

  /**
   * A version of `check` that accepts five random variables.
   */
  def check[R <: Has[TestConfig], A, B, C, D, F, In](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    rv5: Gen[R, F]
  )(
    test: (A, B, C, D, F) => TestResult
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    trace: ZTraceElement
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    check(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5)(test.tupled)

  /**
   * A version of `check` that accepts six random variables.
   */
  def check[R <: Has[TestConfig], A, B, C, D, F, G, In](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    rv5: Gen[R, F],
    rv6: Gen[R, G]
  )(
    test: (A, B, C, D, F, G) => TestResult
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    trace: ZTraceElement
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    check(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5 <*> rv6)(test.tupled)

  /**
   * Checks the effectual test passes for "sufficient" numbers of samples from
   * the given random variable.
   */
  @deprecated("use check", "2.0.0")
  def checkM[R <: Has[TestConfig], R1 <: R, E, A](rv: Gen[R, A])(
    test: A => ZIO[R1, E, TestResult]
  )(implicit trace: ZTraceElement): ZIO[R1, E, TestResult] =
    TestConfig.samples.flatMap(checkNM(_)(rv)(test))

  /**
   * A version of `checkM` that accepts two random variables.
   */
  @deprecated("use check", "2.0.0")
  def checkM[R <: Has[TestConfig], R1 <: R, E, A, B](rv1: Gen[R, A], rv2: Gen[R, B])(
    test: (A, B) => ZIO[R1, E, TestResult]
  )(implicit trace: ZTraceElement): ZIO[R1, E, TestResult] =
    checkM(rv1 <*> rv2)(test.tupled)

  /**
   * A version of `checkM` that accepts three random variables.
   */
  @deprecated("use check", "2.0.0")
  def checkM[R <: Has[TestConfig], R1 <: R, E, A, B, C](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C])(
    test: (A, B, C) => ZIO[R1, E, TestResult]
  )(implicit trace: ZTraceElement): ZIO[R1, E, TestResult] =
    checkM(rv1 <*> rv2 <*> rv3)(test.tupled)

  /**
   * A version of `checkM` that accepts four random variables.
   */
  @deprecated("use check", "2.0.0")
  def checkM[R <: Has[TestConfig], R1 <: R, E, A, B, C, D](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D]
  )(
    test: (A, B, C, D) => ZIO[R1, E, TestResult]
  )(implicit trace: ZTraceElement): ZIO[R1, E, TestResult] =
    checkM(rv1 <*> rv2 <*> rv3 <*> rv4)(test.tupled)

  /**
   * A version of `checkM` that accepts five random variables.
   */
  @deprecated("use check", "2.0.0")
  def checkM[R <: Has[TestConfig], R1 <: R, E, A, B, C, D, F](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    rv5: Gen[R, F]
  )(
    test: (A, B, C, D, F) => ZIO[R1, E, TestResult]
  )(implicit trace: ZTraceElement): ZIO[R1, E, TestResult] =
    checkM(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5)(test.tupled)

  /**
   * A version of `checkM` that accepts six random variables.
   */
  @deprecated("use check", "2.0.0")
  def checkM[R <: Has[TestConfig], R1 <: R, E, A, B, C, D, F, G](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    rv5: Gen[R, F],
    rv6: Gen[R, G]
  )(
    test: (A, B, C, D, F, G) => ZIO[R1, E, TestResult]
  )(implicit trace: ZTraceElement): ZIO[R1, E, TestResult] =
    checkM(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5 <*> rv6)(test.tupled)

  /**
   * Checks the test passes for all values from the given random variable. This
   * is useful for deterministic `Gen` that comprehensively explore all
   * possibilities in a given domain.
   */
  def checkAll[R <: Has[TestConfig], A, In](rv: Gen[R, A])(test: A => In)(implicit
    checkConstructor: CheckConstructor[R, In],
    trace: ZTraceElement
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkStream(rv.sample.collectSome)(a => checkConstructor(test(a)))

  /**
   * A version of `checkAll` that accepts two random variables.
   */
  def checkAll[R <: Has[TestConfig], A, B, In](rv1: Gen[R, A], rv2: Gen[R, B])(
    test: (A, B) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    trace: ZTraceElement
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkAll(rv1 <*> rv2)(test.tupled)

  /**
   * A version of `checkAll` that accepts three random variables.
   */
  def checkAll[R <: Has[TestConfig], A, B, C, In](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C])(
    test: (A, B, C) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    trace: ZTraceElement
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkAll(rv1 <*> rv2 <*> rv3)(test.tupled)

  /**
   * A version of `checkAll` that accepts four random variables.
   */
  def checkAll[R <: Has[TestConfig], A, B, C, D, In](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C], rv4: Gen[R, D])(
    test: (A, B, C, D) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    trace: ZTraceElement
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkAll(rv1 <*> rv2 <*> rv3 <*> rv4)(test.tupled)

  /**
   * A version of `checkAll` that accepts five random variables.
   */
  def checkAll[R <: Has[TestConfig], A, B, C, D, F, In](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    rv5: Gen[R, F]
  )(
    test: (A, B, C, D, F) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    trace: ZTraceElement
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkAll(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5)(test.tupled)

  /**
   * A version of `checkAll` that accepts six random variables.
   */
  def checkAll[R <: Has[TestConfig], A, B, C, D, F, G, In](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    rv5: Gen[R, F],
    rv6: Gen[R, G]
  )(
    test: (A, B, C, D, F, G) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    trace: ZTraceElement
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkAll(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5 <*> rv6)(test.tupled)

  /**
   * Checks the effectual test passes for all values from the given random
   * variable. This is useful for deterministic `Gen` that comprehensively
   * explore all possibilities in a given domain.
   */
  @deprecated("use checkAll", "2.0.0")
  def checkAllM[R <: Has[TestConfig], R1 <: R, E, A](
    rv: Gen[R, A]
  )(test: A => ZIO[R1, E, TestResult])(implicit trace: ZTraceElement): ZIO[R1, E, TestResult] =
    checkStream(rv.sample.collectSome)(test)

  /**
   * A version of `checkAllM` that accepts two random variables.
   */
  @deprecated("use checkAll", "2.0.0")
  def checkAllM[R <: Has[TestConfig], R1 <: R, E, A, B](rv1: Gen[R, A], rv2: Gen[R, B])(
    test: (A, B) => ZIO[R1, E, TestResult]
  )(implicit trace: ZTraceElement): ZIO[R1, E, TestResult] =
    checkAllM(rv1 <*> rv2)(test.tupled)

  /**
   * A version of `checkAllM` that accepts three random variables.
   */
  @deprecated("use checkAll", "2.0.0")
  def checkAllM[R <: Has[TestConfig], R1 <: R, E, A, B, C](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C])(
    test: (A, B, C) => ZIO[R1, E, TestResult]
  )(implicit trace: ZTraceElement): ZIO[R1, E, TestResult] =
    checkAllM(rv1 <*> rv2 <*> rv3)(test.tupled)

  /**
   * A version of `checkAllM` that accepts four random variables.
   */
  @deprecated("use checkAll", "2.0.0")
  def checkAllM[R <: Has[TestConfig], R1 <: R, E, A, B, C, D](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D]
  )(
    test: (A, B, C, D) => ZIO[R1, E, TestResult]
  )(implicit trace: ZTraceElement): ZIO[R1, E, TestResult] =
    checkAllM(rv1 <*> rv2 <*> rv3 <*> rv4)(test.tupled)

  /**
   * A version of `checkAllM` that accepts five random variables.
   */
  @deprecated("use checkAll", "2.0.0")
  def checkAllM[R <: Has[TestConfig], R1 <: R, E, A, B, C, D, F](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    rv5: Gen[R, F]
  )(
    test: (A, B, C, D, F) => ZIO[R1, E, TestResult]
  )(implicit trace: ZTraceElement): ZIO[R1, E, TestResult] =
    checkAllM(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5)(test.tupled)

  /**
   * A version of `checkAllM` that accepts six random variables.
   */
  @deprecated("use checkAll", "2.0.0")
  def checkAllM[R <: Has[TestConfig], R1 <: R, E, A, B, C, D, F, G](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    rv5: Gen[R, F],
    rv6: Gen[R, G]
  )(
    test: (A, B, C, D, F, G) => ZIO[R1, E, TestResult]
  )(implicit trace: ZTraceElement): ZIO[R1, E, TestResult] =
    checkAllM(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5 <*> rv6)(test.tupled)

  /**
   * Checks in parallel the effectual test passes for all values from the given
   * random variable. This is useful for deterministic `Gen` that
   * comprehensively explore all possibilities in a given domain.
   */
  @deprecated("use checkPar", "2.0.0")
  def checkAllMPar[R <: Has[TestConfig], R1 <: R, E, A](rv: Gen[R, A], parallelism: Int)(
    test: A => ZIO[R1, E, TestResult]
  )(implicit trace: ZTraceElement): ZIO[R1, E, TestResult] =
    checkStreamPar(rv.sample.collectSome, parallelism)(test)

  /**
   * A version of `checkAllMPar` that accepts two random variables.
   */
  @deprecated("use checkPar", "2.0.0")
  def checkAllMPar[R <: Has[TestConfig], R1 <: R, E, A, B](rv1: Gen[R, A], rv2: Gen[R, B], parallelism: Int)(
    test: (A, B) => ZIO[R1, E, TestResult]
  )(implicit trace: ZTraceElement): ZIO[R1, E, TestResult] =
    checkAllMPar(rv1 <*> rv2, parallelism)(test.tupled)

  /**
   * A version of `checkAllMPar` that accepts three random variables.
   */
  @deprecated("use checkPar", "2.0.0")
  def checkAllMPar[R <: Has[TestConfig], R1 <: R, E, A, B, C](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    parallelism: Int
  )(
    test: (A, B, C) => ZIO[R1, E, TestResult]
  )(implicit trace: ZTraceElement): ZIO[R1, E, TestResult] =
    checkAllMPar(rv1 <*> rv2 <*> rv3, parallelism)(test.tupled)

  /**
   * A version of `checkAllMPar` that accepts four random variables.
   */
  @deprecated("use checkPar", "2.0.0")
  def checkAllMPar[R <: Has[TestConfig], R1 <: R, E, A, B, C, D](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    parallelism: Int
  )(
    test: (A, B, C, D) => ZIO[R1, E, TestResult]
  )(implicit trace: ZTraceElement): ZIO[R1, E, TestResult] =
    checkAllMPar(rv1 <*> rv2 <*> rv3 <*> rv4, parallelism)(test.tupled)

  /**
   * A version of `checkAllMPar` that accepts five random variables.
   */
  @deprecated("use checkPar", "2.0.0")
  def checkAllMPar[R <: Has[TestConfig], R1 <: R, E, A, B, C, D, F](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    rv5: Gen[R, F],
    parallelism: Int
  )(
    test: (A, B, C, D, F) => ZIO[R1, E, TestResult]
  )(implicit trace: ZTraceElement): ZIO[R1, E, TestResult] =
    checkAllMPar(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5, parallelism)(test.tupled)

  /**
   * A version of `checkAllMPar` that accepts six random variables.
   */
  @deprecated("use checkPar", "2.0.0")
  def checkAllMPar[R <: Has[TestConfig], R1 <: R, E, A, B, C, D, F, G](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    rv5: Gen[R, F],
    rv6: Gen[R, G],
    parallelism: Int
  )(
    test: (A, B, C, D, F, G) => ZIO[R1, E, TestResult]
  )(implicit trace: ZTraceElement): ZIO[R1, E, TestResult] =
    checkAllMPar(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5 <*> rv6, parallelism)(test.tupled)

  /**
   * Checks in parallel the effectual test passes for all values from the given
   * random variable. This is useful for deterministic `Gen` that
   * comprehensively explore all possibilities in a given domain.
   */
  def checkAllPar[R <: Has[TestConfig], R1 <: R, E, A, In](rv: Gen[R, A], parallelism: Int)(
    test: A => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    trace: ZTraceElement
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkStreamPar(rv.sample.collectSome, parallelism)(a => checkConstructor(test(a)))

  /**
   * A version of `checkAllMPar` that accepts two random variables.
   */
  def checkAllPar[R <: Has[TestConfig], R1 <: R, E, A, B, In](rv1: Gen[R, A], rv2: Gen[R, B], parallelism: Int)(
    test: (A, B) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    trace: ZTraceElement
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkAllPar(rv1 <*> rv2, parallelism)(test.tupled)

  /**
   * A version of `checkAllMPar` that accepts three random variables.
   */
  def checkAllPar[R <: Has[TestConfig], R1 <: R, E, A, B, C, In](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    parallelism: Int
  )(
    test: (A, B, C) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    trace: ZTraceElement
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkAllPar(rv1 <*> rv2 <*> rv3, parallelism)(test.tupled)

  /**
   * A version of `checkAllMPar` that accepts four random variables.
   */
  def checkAllPar[R <: Has[TestConfig], R1 <: R, E, A, B, C, D, In](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    parallelism: Int
  )(
    test: (A, B, C, D) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    trace: ZTraceElement
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkAllPar(rv1 <*> rv2 <*> rv3 <*> rv4, parallelism)(test.tupled)

  /**
   * A version of `checkAllMPar` that accepts five random variables.
   */
  def checkAllPar[R <: Has[TestConfig], R1 <: R, E, A, B, C, D, F, In](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    rv5: Gen[R, F],
    parallelism: Int
  )(
    test: (A, B, C, D, F) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    trace: ZTraceElement
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkAllPar(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5, parallelism)(test.tupled)

  /**
   * A version of `checkAllMPar` that accepts six random variables.
   */
  def checkAllPar[R <: Has[TestConfig], R1 <: R, E, A, B, C, D, F, G, In](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    rv5: Gen[R, F],
    rv6: Gen[R, G],
    parallelism: Int
  )(
    test: (A, B, C, D, F, G) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    trace: ZTraceElement
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkAllPar(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5 <*> rv6, parallelism)(test.tupled)

  /**
   * Checks the test passes for the specified number of samples from the given
   * random variable.
   */
  def checkN(n: Int): CheckVariants.CheckN =
    new CheckVariants.CheckN(n)

  /**
   * Checks the effectual test passes for the specified number of samples from
   * the given random variable.
   */
  @deprecated("use checkN", "2.0.0")
  def checkNM(n: Int): CheckVariants.CheckNM =
    new CheckVariants.CheckNM(n)

  /**
   * A `Runner` that provides a default testable environment.
   */
  val defaultTestRunner: TestRunner[TestEnvironment, Any] =
    TestRunner(TestExecutor.default(testEnvironment))

  /**
   * Creates a failed test result with the specified runtime cause.
   */
  def failed[E](cause: Cause[E])(implicit trace: ZTraceElement): ZIO[Any, TestFailure[E], Nothing] =
    ZIO.fail(TestFailure.Runtime(cause))

  /**
   * Creates an ignored test result.
   */
  val ignored: UIO[TestSuccess] =
    ZIO.succeedNow(TestSuccess.Ignored)

  /**
   * Passes platform specific information to the specified function, which will
   * use that information to create a test. If the platform is neither ScalaJS
   * nor the JVM, an ignored test result will be returned.
   */
  def platformSpecific[R, E, A](js: => A, jvm: => A)(f: A => ZTest[R, E]): ZTest[R, E] =
    if (TestPlatform.isJS) f(js)
    else if (TestPlatform.isJVM) f(jvm)
    else ignored

  /**
   * Builds a suite containing a number of other specs.
   */
  def suite[In](label: String)(specs: In*)(implicit
    suiteConstructor: SuiteConstructor[In],
    trace: ZTraceElement
  ): Spec[suiteConstructor.OutEnvironment, suiteConstructor.OutError, suiteConstructor.OutSuccess] =
    Spec.labeled(
      label,
      if (specs.isEmpty) Spec.empty
      else Spec.multiple(Chunk.fromIterable(specs).map(spec => suiteConstructor(spec)))
    )

  /**
   * Builds an effectual suite containing a number of other specs.
   */
  @deprecated("use suite", "2.0.0")
  def suiteM[R, E, T](label: String)(specs: ZIO[R, E, Iterable[Spec[R, E, T]]])(implicit
    trace: ZTraceElement
  ): Spec[R, E, T] =
    suite(label)(specs)

  /**
   * Builds a spec with a single test.
   */
  def test[In](label: String)(assertion: => In)(implicit
    testConstructor: TestConstructor[Nothing, In],
    trace: ZTraceElement
  ): testConstructor.Out =
    testConstructor(label)(assertion)

  /**
   * Builds a spec with a single effectful test.
   */
  @deprecated("use test", "2.0.0")
  def testM[R, E](label: String)(
    assertion: => ZIO[R, E, TestResult]
  )(implicit trace: ZTraceElement): ZSpec[R, E] =
    test(label)(assertion)

  /**
   * Passes version specific information to the specified function, which will
   * use that information to create a test. If the version is neither Dotty nor
   * Scala 2, an ignored test result will be returned.
   */
  def versionSpecific[R, E, A](dotty: => A, scala2: => A)(f: A => ZTest[R, E]): ZTest[R, E] =
    if (TestVersion.isDotty) f(dotty)
    else if (TestVersion.isScala2) f(scala2)
    else ignored

  object CheckVariants {

    final class CheckN(private val n: Int) extends AnyVal {
      def apply[R <: Has[TestConfig], A, In](rv: Gen[R, A])(test: A => In)(implicit
        checkConstructor: CheckConstructor[R, In],
        trace: ZTraceElement
      ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
        checkStream(rv.sample.forever.collectSome.take(n.toLong))(a => checkConstructor(test(a)))
      def apply[R <: Has[TestConfig], A, B, In](rv1: Gen[R, A], rv2: Gen[R, B])(
        test: (A, B) => In
      )(implicit
        checkConstructor: CheckConstructor[R, In],
        trace: ZTraceElement
      ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
        checkN(n)(rv1 <*> rv2)(test.tupled)
      def apply[R <: Has[TestConfig], A, B, C, In](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C])(
        test: (A, B, C) => In
      )(implicit
        checkConstructor: CheckConstructor[R, In],
        trace: ZTraceElement
      ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
        checkN(n)(rv1 <*> rv2 <*> rv3)(test.tupled)
      def apply[R <: Has[TestConfig], A, B, C, D, In](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C], rv4: Gen[R, D])(
        test: (A, B, C, D) => In
      )(implicit
        checkConstructor: CheckConstructor[R, In],
        trace: ZTraceElement
      ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
        checkN(n)(rv1 <*> rv2 <*> rv3 <*> rv4)(test.tupled)
      def apply[R <: Has[TestConfig], A, B, C, D, F, In](
        rv1: Gen[R, A],
        rv2: Gen[R, B],
        rv3: Gen[R, C],
        rv4: Gen[R, D],
        rv5: Gen[R, F]
      )(
        test: (A, B, C, D, F) => In
      )(implicit
        checkConstructor: CheckConstructor[R, In],
        trace: ZTraceElement
      ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
        checkN(n)(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5)(test.tupled)
      def apply[R <: Has[TestConfig], A, B, C, D, F, G, In](
        rv1: Gen[R, A],
        rv2: Gen[R, B],
        rv3: Gen[R, C],
        rv4: Gen[R, D],
        rv5: Gen[R, F],
        rv6: Gen[R, G]
      )(
        test: (A, B, C, D, F, G) => In
      )(implicit
        checkConstructor: CheckConstructor[R, In],
        trace: ZTraceElement
      ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
        checkN(n)(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5 <*> rv6)(test.tupled)
    }

    final class CheckNM(private val n: Int) extends AnyVal {
      @deprecated("use checkN", "2.0.0")
      def apply[R <: Has[TestConfig], R1 <: R, E, A](rv: Gen[R, A])(
        test: A => ZIO[R1, E, TestResult]
      )(implicit trace: ZTraceElement): ZIO[R1, E, TestResult] =
        checkStream(rv.sample.forever.collectSome.take(n.toLong))(test)
      @deprecated("use checkN", "2.0.0")
      def apply[R <: Has[TestConfig], R1 <: R, E, A, B](rv1: Gen[R, A], rv2: Gen[R, B])(
        test: (A, B) => ZIO[R1, E, TestResult]
      )(implicit trace: ZTraceElement): ZIO[R1, E, TestResult] =
        checkNM(n)(rv1 <*> rv2)(test.tupled)
      @deprecated("use checkN", "2.0.0")
      def apply[R <: Has[TestConfig], R1 <: R, E, A, B, C](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C])(
        test: (A, B, C) => ZIO[R1, E, TestResult]
      )(implicit trace: ZTraceElement): ZIO[R1, E, TestResult] =
        checkNM(n)(rv1 <*> rv2 <*> rv3)(test.tupled)
      @deprecated("use checkN", "2.0.0")
      def apply[R <: Has[TestConfig], R1 <: R, E, A, B, C, D](
        rv1: Gen[R, A],
        rv2: Gen[R, B],
        rv3: Gen[R, C],
        rv4: Gen[R, D]
      )(
        test: (A, B, C, D) => ZIO[R1, E, TestResult]
      )(implicit trace: ZTraceElement): ZIO[R1, E, TestResult] =
        checkNM(n)(rv1 <*> rv2 <*> rv3 <*> rv4)(test.tupled)
      @deprecated("use checkN", "2.0.0")
      def apply[R <: Has[TestConfig], R1 <: R, E, A, B, C, D, F](
        rv1: Gen[R, A],
        rv2: Gen[R, B],
        rv3: Gen[R, C],
        rv4: Gen[R, D],
        rv5: Gen[R, F]
      )(
        test: (A, B, C, D, F) => ZIO[R1, E, TestResult]
      )(implicit trace: ZTraceElement): ZIO[R1, E, TestResult] =
        checkNM(n)(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5)(test.tupled)
      @deprecated("use checkN", "2.0.0")
      def apply[R <: Has[TestConfig], R1 <: R, E, A, B, C, D, F, G](
        rv1: Gen[R, A],
        rv2: Gen[R, B],
        rv3: Gen[R, C],
        rv4: Gen[R, D],
        rv5: Gen[R, F],
        rv6: Gen[R, G]
      )(
        test: (A, B, C, D, F, G) => ZIO[R1, E, TestResult]
      )(implicit trace: ZTraceElement): ZIO[R1, E, TestResult] =
        checkNM(n)(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5 <*> rv6)(test.tupled)
    }
  }

  private def checkStream[R, R1 <: R, E, A](stream: ZStream[R, Nothing, Sample[R, A]])(
    test: A => ZIO[R1, E, TestResult]
  )(implicit trace: ZTraceElement): ZIO[R1 with Has[TestConfig], E, TestResult] =
    TestConfig.shrinks.flatMap {
      shrinkStream {
        stream.zipWithIndex.mapZIO { case (initial, index) =>
          initial.foreach(input =>
            test(input)
              .map(_.map(_.setGenFailureDetails(GenFailureDetails(initial.value, input, index))))
              .either
          )
        }
      }
    }

  private def shrinkStream[R, R1 <: R, E, A](
    stream: ZStream[R1, Nothing, Sample[R1, Either[E, TestResult]]]
  )(maxShrinks: Int)(implicit trace: ZTraceElement): ZIO[R1 with Has[TestConfig], E, TestResult] =
    stream
      .dropWhile(!_.value.fold(_ => true, _.isFailure)) // Drop until we get to a failure
      .take(1)                                          // Get the first failure
      .flatMap(_.shrinkSearch(_.fold(_ => true, _.isFailure)).take(maxShrinks.toLong + 1))
      .run(ZSink.collectAll[Nothing, Either[E, TestResult]]) // Collect all the shrunken values
      .flatMap { shrinks =>
        // Get the "last" failure, the smallest according to the shrinker:
        shrinks
          .filter(_.fold(_ => true, _.isFailure))
          .lastOption
          .fold[ZIO[R, E, TestResult]](
            ZIO.succeedNow {
              BoolAlgebra.success {
                FailureDetailsResult(
                  FailureDetails(
                    ::(AssertionValue(Assertion.anything, (), Assertion.anything.run(())), Nil)
                  )
                )
              }
            }
          )(ZIO.fromEither(_))
      }

  private def checkStreamPar[R, R1 <: R, E, A](stream: ZStream[R, Nothing, Sample[R, A]], parallelism: Int)(
    test: A => ZIO[R1, E, TestResult]
  )(implicit trace: ZTraceElement): ZIO[R1 with Has[TestConfig], E, TestResult] =
    TestConfig.shrinks.flatMap {
      shrinkStream {
        stream.zipWithIndex
          .mapZIOPar(parallelism) { case (initial, index) =>
            initial.foreach { input =>
              test(input)
                .map(_.map(_.setGenFailureDetails(GenFailureDetails(initial.value, input, index))))
                .either
            // convert test failures to failures to terminate parallel tests on first failure
            }.flatMap(sample => sample.value.fold(_ => ZIO.fail(sample), _ => ZIO.succeed(sample)))
          // move failures back into success channel for shrinking logic
          }
          .catchAll(ZStream.succeed(_))
      }
    }

  /**
   * An implementation of `ZStream#flatMap` that supports breadth first search.
   */
  private[test] def flatMapStream[R, R1 <: R, A, B](
    stream: ZStream[R, Nothing, Option[A]]
  )(f: A => ZStream[R1, Nothing, Option[B]])(implicit trace: ZTraceElement): ZStream[R1, Nothing, Option[B]] = {

    sealed trait State

    case object PullOuter extends State
    case class PullInner(
      stream: ZIO[R1, Option[Nothing], Chunk[Option[B]]],
      chunk: Chunk[Option[B]],
      index: Int,
      finalizer: ZManaged.Finalizer
    ) extends State

    def pull(
      outerDone: Ref[Boolean],
      outerStream: ZIO[R, Option[Nothing], Chunk[Option[A]]],
      currentOuterChunk: Ref[(Chunk[Option[A]], Int)],
      currentInnerStream: Ref[Option[PullInner]],
      currentStreams: Ref[ScalaQueue[State]],
      innerFinalizers: ZManaged.ReleaseMap
    ): ZIO[R1, Option[Nothing], Chunk[Option[B]]] = {

      def pullNonEmpty[R, E, A](pull: ZIO[R, Option[E], Chunk[A]]): ZIO[R, Option[E], Chunk[A]] =
        pull.flatMap(as => if (as.nonEmpty) ZIO.succeed(as) else pullNonEmpty(pull))

      def pullOuter(
        outerStream: ZIO[R, Option[Nothing], Chunk[Option[A]]],
        outerChunk: Chunk[Option[A]],
        outerChunkIndex: Int
      ): ZIO[R1, Option[Nothing], (Option[A], Chunk[Option[A]], Int)] =
        if (outerChunkIndex < outerChunk.size)
          ZIO.succeedNow((outerChunk(outerChunkIndex), outerChunk, outerChunkIndex + 1))
        else
          pullNonEmpty(outerStream).map(chunk => (chunk(0), chunk, 1))

      def openInner(a: A): ZIO[R1, Nothing, (ZIO[R1, Option[Nothing], Chunk[Option[B]]], ZManaged.Finalizer)] =
        ZIO.uninterruptibleMask { restore =>
          for {
            releaseMap <- ZManaged.ReleaseMap.make
            pull       <- restore(f(a).toPull.zio.provideSome[R1]((_, releaseMap)).map(_._2))
            finalizer  <- innerFinalizers.add(releaseMap.releaseAll(_, ExecutionStrategy.Sequential))
          } yield (pull, finalizer)
        }

      def pullInner(
        innerStream: ZIO[R1, Option[Nothing], Chunk[Option[B]]],
        innerChunk: Chunk[Option[B]],
        innerChunkIndex: Int
      ): ZIO[R1, Option[Nothing], (Option[Chunk[Option[B]]], Chunk[Option[B]], Int)] =
        if (innerChunkIndex < innerChunk.size)
          ZIO.succeedNow(takeInner(innerChunk, innerChunkIndex))
        else
          pullNonEmpty(innerStream).map(takeInner(_, 0))

      def takeInner(
        innerChunk: Chunk[Option[B]],
        innerChunkIndex: Int
      ): (Option[Chunk[Option[B]]], Chunk[Option[B]], Int) =
        if (innerChunk(innerChunkIndex).isEmpty) {
          (None, innerChunk, innerChunkIndex + 1)
        } else {
          val builder  = ChunkBuilder.make[Option[B]]()
          val length   = innerChunk.length
          var continue = true
          var i        = innerChunkIndex
          while (continue && i != length) {
            val b = innerChunk(i)
            if (b.isDefined) {
              builder += b
              i += 1
            } else {
              continue = false
            }
          }
          (Some(builder.result()), innerChunk, i)
        }

      currentInnerStream.get.flatMap {
        case None =>
          currentStreams.get.map(_.headOption).flatMap {
            case None =>
              ZIO.fail(None)
            case Some(PullInner(innerStream, chunk, index, innerFinalizer)) =>
              currentInnerStream.set(Some(PullInner(innerStream, chunk, index, innerFinalizer))) *>
                currentStreams.update(_.tail) *>
                pull(outerDone, outerStream, currentOuterChunk, currentInnerStream, currentStreams, innerFinalizers)
            case Some(PullOuter) =>
              outerDone.get.flatMap { done =>
                if (done)
                  currentStreams.get.flatMap { queue =>
                    if (queue.size == 1)
                      ZIO.fail(None)
                    else
                      currentStreams.update(_.tail.enqueue(PullOuter)) *>
                        ZIO.succeedNow(Chunk(None))
                  }
                else
                  currentOuterChunk.get.flatMap { case (outerChunk, outerChunkIndex) =>
                    pullOuter(outerStream, outerChunk, outerChunkIndex).foldZIO(
                      _ =>
                        outerDone.set(true) *>
                          pull(
                            outerDone,
                            outerStream,
                            currentOuterChunk,
                            currentInnerStream,
                            currentStreams,
                            innerFinalizers
                          ),
                      {
                        case (Some(a), outerChunk, outerChunkIndex) =>
                          openInner(a).flatMap { case (innerStream, innerFinalizer) =>
                            currentOuterChunk.set((outerChunk, outerChunkIndex)) *>
                              currentInnerStream.set(Some(PullInner(innerStream, Chunk.empty, 0, innerFinalizer))) *>
                              pull(
                                outerDone,
                                outerStream,
                                currentOuterChunk,
                                currentInnerStream,
                                currentStreams,
                                innerFinalizers
                              )
                          }
                        case (None, outerChunk, outerChunkIndex) =>
                          currentOuterChunk.set((outerChunk, outerChunkIndex)) *>
                            currentStreams.update(_.tail.enqueue(PullOuter)) *>
                            ZIO.succeedNow(Chunk(None))
                      }
                    )
                  }
              }
          }
        case Some(PullInner(innerStream, innerChunk, innerChunkIndex, innerFinalizer)) =>
          pullInner(innerStream, innerChunk, innerChunkIndex).foldZIO(
            _ =>
              innerFinalizer(Exit.unit) *>
                currentInnerStream.set(None) *>
                pull(outerDone, outerStream, currentOuterChunk, currentInnerStream, currentStreams, innerFinalizers),
            {
              case (None, innerChunk, innerChunkIndex) =>
                currentInnerStream.set(None) *>
                  currentStreams.update(
                    _.enqueue(PullInner(innerStream, innerChunk, innerChunkIndex, innerFinalizer))
                  ) *>
                  pull(outerDone, outerStream, currentOuterChunk, currentInnerStream, currentStreams, innerFinalizers)
              case (Some(bs), innerChunk, innerChunkIndex) =>
                currentInnerStream.set(Some(PullInner(innerStream, innerChunk, innerChunkIndex, innerFinalizer))) *>
                  ZIO.succeedNow(bs)
            }
          )
      }
    }

    ZStream.fromPull {
      for {
        outerDone          <- Ref.make(false).toManaged
        outerStream        <- stream.toPull
        currentOuterChunk  <- Ref.make[(Chunk[Option[A]], Int)]((Chunk.empty, 0)).toManaged
        currentInnerStream <- Ref.make[Option[PullInner]](None).toManaged
        currentStreams     <- Ref.make[ScalaQueue[State]](ScalaQueue(PullOuter)).toManaged
        innerFinalizers    <- ZManaged.ReleaseMap.makeManaged(ExecutionStrategy.Sequential)
      } yield pull(outerDone, outerStream, currentOuterChunk, currentInnerStream, currentStreams, innerFinalizers)
    }
  }

  /**
   * An implementation of `ZStream#merge` that supports breadth first search.
   */
  private[test] def mergeStream[R, A](
    left: ZStream[R, Nothing, Option[A]],
    right: ZStream[R, Nothing, Option[A]]
  )(implicit trace: ZTraceElement): ZStream[R, Nothing, Option[A]] =
    flatMapStream(ZStream(Some(left), Some(right)))(identity)

  implicit final class TestLensOptionOps[A](private val self: TestLens[Option[A]]) extends AnyVal {

    /**
     * Transforms an [[scala.Option]] to its `Some` value `A`, otherwise fails
     * if it is a `None`.
     */
    def some: TestLens[A] = throw SmartAssertionExtensionError()
  }

  implicit final class TestLensEitherOps[E, A](private val self: TestLens[Either[E, A]]) extends AnyVal {

    /**
     * Transforms an [[scala.Either]] to its [[scala.Left]] value `E`, otherwise
     * fails if it is a [[scala.Right]].
     */
    def left: TestLens[E] = throw SmartAssertionExtensionError()

    /**
     * Transforms an [[scala.Either]] to its [[scala.Right]] value `A`,
     * otherwise fails if it is a [[scala.Left]].
     */
    def right: TestLens[A] = throw SmartAssertionExtensionError()
  }

  implicit final class TestLensExitOps[E, A](private val self: TestLens[Exit[E, A]]) extends AnyVal {

    /**
     * Transforms an [[Exit]] to a [[scala.Throwable]] if it is a `die`,
     * otherwise fails.
     */
    def die: TestLens[Throwable] = throw SmartAssertionExtensionError()

    /**
     * Transforms an [[Exit]] to its failure type (`E`) if it is a `fail`,
     * otherwise fails.
     */
    def failure: TestLens[E] = throw SmartAssertionExtensionError()

    /**
     * Transforms an [[Exit]] to its success type (`A`) if it is a `succeed`,
     * otherwise fails.
     */
    def success: TestLens[A] = throw SmartAssertionExtensionError()

    /**
     * Transforms an [[Exit]] to its underlying [[Cause]] if it has one,
     * otherwise fails.
     */
    def cause: TestLens[Cause[E]] = throw SmartAssertionExtensionError()

    /**
     * Transforms an [[Exit]] to a boolean value representing whether or not it
     * was interrupted.
     */
    def interrupted: TestLens[Boolean] = throw SmartAssertionExtensionError()
  }

  implicit final class TestLensCauseOps[E](private val self: TestLens[Cause[E]]) extends AnyVal {

    /**
     * Transforms a [[Cause]] to a [[scala.Throwable]] if it is a `die`,
     * otherwise fails.
     */
    def die: TestLens[Throwable] = throw SmartAssertionExtensionError()

    /**
     * Transforms a [[Cause]] to its failure type (`E`) if it is a `fail`,
     * otherwise fails.
     */
    def failure: TestLens[E] = throw SmartAssertionExtensionError()

    /**
     * Transforms a [[Cause]] to a boolean value representing whether or not it
     * was interrupted.
     */
    def interrupted: TestLens[Boolean] = throw SmartAssertionExtensionError()
  }

  implicit final class TestLensAnyOps[A](private val self: TestLens[A]) extends AnyVal {

    /**
     * Always returns true as long the chain of preceding transformations has
     * succeeded.
     *
     * {{{
     *   val option: Either[Int, Option[String]] = Right(Some("Cool"))
     *   assertTrue(option.is(_.right.some.anything)) // returns true
     *   assertTrue(option.is(_.left.anything)) // will fail because of `.left`.
     * }}}
     */
    def anything: TestLens[Boolean] = throw SmartAssertionExtensionError()

    /**
     * Transforms a value of some type into the given `Subtype` if possible,
     * otherwise fails.
     *
     * {{{
     *   sealed trait CustomError
     *   case class Explosion(blastRadius: Int) extends CustomError
     *   case class Melting(degrees: Double) extends CustomError
     *   case class Fulminating(wow: Boolean) extends CustomError
     *
     *   val error: CustomError = Melting(100)
     *   assertTrue(option.is(_.subtype[Melting]).degrees > 10) // succeeds
     *   assertTrue(option.is(_.subtype[Explosion]).blastRadius == 12) // fails
     * }}}
     */
    def subtype[Subtype <: A]: TestLens[Subtype] = throw SmartAssertionExtensionError()

    /**
     * Transforms a value with the given [[CustomAssertion]]
     */
    def custom[B](customAssertion: CustomAssertion[A, B]): TestLens[B] = {
      val _ = customAssertion
      throw SmartAssertionExtensionError()
    }
  }

  implicit final class SmartAssertionOps[A](private val self: A) extends AnyVal {

    /**
     * This extension method can only be called inside of the `assertTrue`
     * method. It will transform the value using the given [[TestLens]].
     *
     * {{{
     *   val option: Either[Int, Option[String]] = Right(Some("Cool"))
     *   assertTrue(option.is(_.right.some) == "Cool") // returns true
     *   assertTrue(option.is(_.left) < 100) // will fail because of `.left`.
     * }}}
     */
    def is[B](f: TestLens[A] => TestLens[B]): B = {
      val _ = f
      throw SmartAssertionExtensionError()
    }
  }

}
