/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
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

import zio.internal.stacktracer.{SourceLocation, Tracer}
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stream.{ZSink, ZStream}
import zio.test.ReporterEventRenderer.ConsoleEventRenderer
import zio.test.Spec.{ExecCase, LabeledCase}

import scala.language.implicitConversions

/**
 * _ZIO Test_ is a testing library for effectful programs.
 *
 * Every spec is an ordinary immutable value, built with `suite` and `test` and
 * composed like any other value. Because specs are ZIO effects, they can use
 * resources (including those requiring disposal), run sequentially or in
 * parallel, and use the ZIO combinators. Property-based tests use [[Gen]] with
 * `check`.
 *
 * {{{
 * import zio.test._
 * import zio.Clock.nanoTime
 *
 * object MyTest extends ZIOSpecDefault {
 *   def spec = suite("clock")(
 *     test("the live time is not negative") {
 *       for {
 *         time <- Live.live(nanoTime)
 *       } yield assertTrue(time >= 0L)
 *     }
 *   )
 * }
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/test/ ZIO Test]]
 */
package object test extends CompileVariants {

  /**
   * The services that ZIO Test provides to every test: [[Annotations]],
   * [[Live]], [[Sized]] and [[TestConfig]]. The test versions of the default
   * ZIO services ([[TestClock]], [[TestConsole]], [[TestRandom]] and
   * [[TestSystem]]) are installed as the default services of the test instead.
   */
  type TestEnvironment = Annotations with Live with Sized with TestConfig

  /** Layers that build a [[TestEnvironment]]. */
  object TestEnvironment {
    private val sizedLive  = Sized.live(100)(Trace.empty)
    private val configLive = TestConfig.live(100, 100, 200, 1000)(Trace.empty)
    private val defaultTestConfig =
      TestConfig.TestV2(100, 100, 200, 1000, ZIOAspect.identity)

    /** A layer that passes on the `TestEnvironment` it is given. */
    val any: ZLayer[TestEnvironment, Nothing, TestEnvironment] =
      ZLayer.environment[TestEnvironment](Tracer.newTrace)

    /**
     * A layer that builds a `TestEnvironment` on top of the given live `Clock`,
     * `Console`, `System` and `Random` services, which the [[Live]] service
     * gives access to. While the layer's scope is open, it installs a
     * `TestClock`, `TestConsole`, `TestRandom` and `TestSystem` as the default
     * services, and it restores the previous ones when the scope closes. Its
     * `Sized` service has size 100, and its `TestConfig` has 100 repeats, 100
     * retries, 200 samples and 1000 shrinks.
     */
    val live: ZLayer[Clock with Console with System with Random, Nothing, TestEnvironment] =
      make(installMonotonicFiberIds = false)

    private[test] val liveWithMonotonicFiberIds
      : ZLayer[Clock with Console with System with Random, Nothing, TestEnvironment] =
      make(installMonotonicFiberIds = true)

    private def make(
      installMonotonicFiberIds: Boolean
    ): ZLayer[Clock with Console with System with Random, Nothing, TestEnvironment] = {
      implicit val trace = Tracer.newTrace
      ZLayer.scopedEnvironment {
        ZIO.environmentWithZIO[Scope with Clock with Console with System with Random] { liveEnvironment =>
          val scope       = liveEnvironment.getScope
          val annotations = Annotations.unsafe.make()(Unsafe)
          val live        = Live.Test(liveEnvironment)
          val sized       = Sized.unsafe.make(100)(Unsafe)
          val testClock   = TestClock.unsafe.make(TestClock.DefaultData, live, annotations)(Unsafe)
          val testConsole =
            TestConsole.unsafe.make(TestConsole.DefaultData, true, live, annotations)(Unsafe)
          val testRandom = TestRandom.unsafe.make(TestRandom.DefaultData)(Unsafe)
          val testSystem = TestSystem.unsafe.make(TestSystem.DefaultData)(Unsafe)
          val environment =
            ZEnvironment[Annotations, Live, Sized, TestConfig](annotations, live, sized, defaultTestConfig)

          ZIO.withFiberRuntime[Any, Nothing, ZEnvironment[TestEnvironment]] { (fiber, _) =>
            val testServices     = fiber.getFiberRef(TestServices.currentServices)
            val defaultServices  = fiber.getFiberRef(DefaultServices.currentServices)
            val fiberIdGenerator = fiber.getFiberRef(FiberRef.currentFiberIdGenerator)
            val testServices1 =
              testServices
                .add[Annotations](annotations)
                .add[Live](live)
                .add[Sized](sized)
                .add[TestConfig](defaultTestConfig)
            val defaultServices1 =
              defaultServices
                .add[TestClock](testClock)
                .add[TestConsole](testConsole)
                .add[TestRandom](testRandom)
                .add[TestSystem](testSystem)

            val finalizer = ZIO.withFiberRuntime[Any, Nothing, Unit] { (fiber, _) =>
              if (installMonotonicFiberIds)
                fiber.setFiberRef(FiberRef.currentFiberIdGenerator, fiberIdGenerator)
              fiber.setFiberRef(DefaultServices.currentServices, defaultServices)
              fiber.setFiberRef(TestServices.currentServices, testServices)
              TestClock.unsafe.close(testClock)
            }

            scope.addFinalizer(finalizer).map { _ =>
              fiber.setFiberRef(TestServices.currentServices, testServices1)
              fiber.setFiberRef(DefaultServices.currentServices, defaultServices1)
              if (installMonotonicFiberIds)
                fiber.setFiberRef(FiberRef.currentFiberIdGenerator, FiberId.Gen.Monotonic)
              environment
            }
          }
        }
      }
    }
  }

  /**
   * A layer that provides the live `Clock`, `Console`, `System` and `Random`
   * services.
   */
  val liveEnvironment: Layer[Nothing, Clock with Console with System with Random] = {
    implicit val trace = Trace.empty
    ZLayer.succeedEnvironment(
      ZEnvironment[Clock, Console, System, Random](
        Clock.ClockLive,
        Console.ConsoleLive,
        System.SystemLive,
        Random.RandomLive
      )
    )
  }

  /**
   * A layer that provides the [[TestEnvironment]], built on top of
   * [[liveEnvironment]]. `ZIOSpecDefault` provides it to its spec.
   */
  val testEnvironment: ZLayer[Any, Nothing, TestEnvironment] = {
    implicit val trace = Tracer.newTrace
    liveEnvironment >>> TestEnvironment.liveWithMonotonicFiberIds
  }

  /**
   * Provides an effect with the "real" environment as opposed to the test
   * environment. This is useful for performing effects such as timing out
   * tests, accessing the real time, or printing to the real console.
   */
  def live[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    Live.live(zio)

  /**
   * Retrieves the `TestClock` service for this test.
   */
  def testClock(implicit trace: Trace): UIO[TestClock] =
    testClockWith(ZIO.successFn)

  /**
   * Retrieves the `TestClock` service for this test and uses it to run the
   * specified workflow.
   */
  def testClockWith[R, E, A](f: TestClock => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    DefaultServices.currentServices.getWith(services => f(services.asInstanceOf[ZEnvironment[TestClock]].get))

  /**
   * Retrieves the `TestConsole` service for this test.
   */
  def testConsole(implicit trace: Trace): UIO[TestConsole] =
    testConsoleWith(ZIO.successFn)

  /**
   * Retrieves the `TestConsole` service for this test and uses it to run the
   * specified workflow.
   */
  def testConsoleWith[R, E, A](f: TestConsole => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    DefaultServices.currentServices.getWith(services => f(services.asInstanceOf[ZEnvironment[TestConsole]].get))

  /**
   * Retrieves the `TestRandom` service for this test.
   */
  def testRandom(implicit trace: Trace): UIO[TestRandom] =
    testRandomWith(ZIO.successFn)

  /**
   * Retrieves the `TestRandom` service for this test and uses it to run the
   * specified workflow.
   */
  def testRandomWith[R, E, A](f: TestRandom => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    DefaultServices.currentServices.getWith(services => f(services.asInstanceOf[ZEnvironment[TestRandom]].get))

  /**
   * Retrieves the `TestSystem` service for this test.
   */
  def testSystem(implicit trace: Trace): UIO[TestSystem] =
    testSystemWith(ZIO.successFn)

  /**
   * Retrieves the `TestSystem` service for this test and uses it to run the
   * specified workflow.
   */
  def testSystemWith[R, E, A](f: TestSystem => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    DefaultServices.currentServices.getWith(services => f(services.asInstanceOf[ZEnvironment[TestSystem]].get))

  /**
   * Retrieves the `Annotations` service for this test.
   */
  def annotations(implicit trace: Trace): UIO[Annotations] =
    annotationsWith(ZIO.successFn)

  /**
   * Retrieves the `Annotations` service for this test and uses it to run the
   * specified workflow.
   */
  def annotationsWith[R, E, A](f: Annotations => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    TestServices.currentServices.getWith(services => f(services.asInstanceOf[ZEnvironment[Annotations]].get))

  /**
   * Retrieves the `Live` service for this test.
   */
  def live(implicit trace: Trace): UIO[Live] =
    liveWith(ZIO.successFn)

  /**
   * Retrieves the `Live` service for this test and uses it to run the specified
   * workflow.
   */
  def liveWith[R, E, A](f: Live => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    TestServices.currentServices.getWith(services => f(services.asInstanceOf[ZEnvironment[Live]].get))

  /**
   * Retrieves the `Sized` service for this test.
   */
  def sized(implicit trace: Trace): UIO[Sized] =
    sizedWith(ZIO.successFn)

  /**
   * Retrieves the `Sized` service for this test and uses it to run the
   * specified workflow.
   */
  def sizedWith[R, E, A](f: Sized => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    TestServices.currentServices.getWith(services => f(services.asInstanceOf[ZEnvironment[Sized]].get))

  /**
   * Retrieves the `TestConfig` service for this test.
   */
  def testConfig(implicit trace: Trace): UIO[TestConfig] =
    testConfigWith(ZIO.successFn)

  /**
   * Retrieves the `TestConfig` service for this test and uses it to run the
   * specified workflow.
   */
  def testConfigWith[R, E, A](f: TestConfig => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    TestServices.currentServices.getWith(services => f(services.asInstanceOf[ZEnvironment[TestConfig]].get))

  /**
   * Executes the specified workflow with the specified implementation of the
   * annotations service.
   */
  def withAnnotations[R, E, A <: Annotations, B](annotations: => A)(
    zio: => ZIO[R, E, B]
  )(implicit tag: Tag[A], trace: Trace): ZIO[R, E, B] =
    TestServices.currentServices.locallyWith(_.add(annotations))(zio)

  /**
   * Sets the implementation of the annotations service to the specified value
   * and restores it to its original value when the scope is closed.
   */
  def withAnnotationsScoped[A <: Annotations](
    annotations: => A
  )(implicit tag: Tag[A], trace: Trace): ZIO[Scope, Nothing, Unit] =
    TestServices.currentServices.locallyScopedWith(_.add(annotations))

  /**
   * Executes the specified workflow with the specified implementation of the
   * config service.
   */
  def withTestConfig[R, E, A <: TestConfig, B](testConfig: => A)(
    zio: => ZIO[R, E, B]
  )(implicit tag: Tag[A], trace: Trace): ZIO[R, E, B] =
    TestServices.currentServices.locallyWith(_.add(testConfig))(zio)

  /**
   * Sets the implementation of the config service to the specified value and
   * restores it to its original value when the scope is closed.
   */
  def withTestConfigScoped[A <: TestConfig](
    testConfig: => A
  )(implicit tag: Tag[A], trace: Trace): ZIO[Scope, Nothing, Unit] =
    TestServices.currentServices.locallyScopedWith(_.add(testConfig))

  /**
   * Executes the specified workflow with the specified implementation of the
   * sized service.
   */
  def withSized[R, E, A <: Sized, B](sized: => A)(
    zio: => ZIO[R, E, B]
  )(implicit tag: Tag[A], trace: Trace): ZIO[R, E, B] =
    TestServices.currentServices.locallyWith(_.add(sized))(zio)

  /**
   * Sets the implementation of the sized service to the specified value and
   * restores it to its original value when the scope is closed.
   */
  def withSizedScoped[A <: Sized](sized: => A)(implicit tag: Tag[A], trace: Trace): ZIO[Scope, Nothing, Unit] =
    TestServices.currentServices.locallyScopedWith(_.add(sized))

  /**
   * Executes the specified workflow with the specified implementation of the
   * live service.
   */
  def withLive[R, E, A <: Live, B](live: => A)(
    zio: => ZIO[R, E, B]
  )(implicit tag: Tag[A], trace: Trace): ZIO[R, E, B] =
    TestServices.currentServices.locallyWith(_.add(live))(zio)

  /**
   * Sets the implementation of the live service to the specified value and
   * restores it to its original value when the scope is closed.
   */
  def withLiveScoped[A <: Live](live: => A)(implicit tag: Tag[A], trace: Trace): ZIO[Scope, Nothing, Unit] =
    TestServices.currentServices.locallyScopedWith(_.add(live))

  /**
   * Transforms this effect with the specified function. The test environment
   * will be provided to this effect, but the live environment will be provided
   * to the transformation function. This can be useful for applying
   * transformations to an effect that require access to the "real" environment
   * while ensuring that the effect itself uses the test environment.
   *
   * {{{
   * import zio.test._
   *
   * // The timeout uses the live clock, while the effect still uses the
   * // TestClock.
   * val timed = withLive(Clock.nanoTime)(_.timeout(1.second))
   * }}}
   */
  def withLive[R, E, E1, A, B](
    zio: ZIO[R, E, A]
  )(f: ZIO[R, E, A] => ZIO[R, E1, B])(implicit trace: Trace): ZIO[R, E1, B] =
    Live.withLive(zio)(f)

  /**
   * A `TestAspectAtLeastR[R]` is a `TestAspect` that requires at least an `R`
   * in its environment.
   */
  type TestAspectAtLeastR[-R] = TestAspect[Nothing, R, Nothing, Any]

  /**
   * A `TestAspectPoly` is a `TestAspect` that is completely polymorphic, having
   * no requirements on error or environment.
   */
  type TestAspectPoly = TestAspect[Nothing, Any, Nothing, Any]

  /**
   * A `ZTest[R, E]` is an effectfully produced test that requires an `R`. It
   * succeeds with a [[TestSuccess]], or fails with a [[TestFailure]] that holds
   * either a failed assertion or an error of type `E`.
   */
  type ZTest[-R, +E] = ZIO[R, TestFailure[E], TestSuccess]

  /** Constructors of [[ZTest]] values. */
  object ZTest {

    /**
     * Builds a test with an effectual assertion. A failed assertion becomes a
     * `TestFailure.Assertion`, and an error or defect of the effect becomes a
     * `TestFailure.Runtime`.
     */
    def apply[R, E](label: String, assertion: => ZIO[R, E, TestResult])(implicit
      trace: Trace
    ): ZIO[R, TestFailure[E], TestSuccess] =
      for {
        promise <- Promise.make[TestFailure[E], TestSuccess]
        child <- assertion
                   .foldCauseZIO(
                     cause =>
                       cause.dieOption match {
                         case Some(TestResult.Exit(assert)) => Exit.fail(TestFailure.Assertion(assert))
                         case _                             => Exit.fail(TestFailure.Runtime(cause))
                       },
                     assert =>
                       if (assert.isFailure) Exit.fail(TestFailure.Assertion(assert))
                       else TestSuccess.succeedEmptyExit
                   )
                   .intoPromise(promise)
                   .forkDaemon
        result <- promise.await
        _ <- ZIO.whenZIODiscard(child.hasChildrenAlive) {
               val cancelWarning = Clock.globalScheduler.schedule(
                 () =>
                   logMessageWithTrace({
                     val quotedLabel = "\"" + label + "\""
                     s"Warning: ZIO Test is attempting to interrupt fiber " +
                       s"${child.id} forked in test $quotedLabel due to automatic, " +
                       "supervision, but interruption has taken more than 10 " +
                       "seconds to complete. This may indicate a resource leak. " +
                       "Make sure you are not forking a fiber in an " +
                       "uninterruptible region."
                   }),
                 10.seconds
               )(Unsafe)
               (child.interrupt *> ZIO.succeed(cancelWarning())).forkDaemon
             }
      } yield result
  }

  private[zio] def assertImpl[A](
    value: => A,
    codeString: Option[String] = None,
    assertionString: Option[String] = None
  )(assertion: Assertion[A])(implicit trace: Trace, sourceLocation: SourceLocation): TestResult =
    Assertion.smartAssert(value, codeString, assertionString)(assertion)

  /**
   * Checks the assertion holds for the given effectfully-computed value.
   */
  private[zio] def assertZIOImpl[R, E, A](
    effect: ZIO[R, E, A],
    codeString: Option[String] = None,
    assertionString: Option[String] = None
  )(
    assertion: Assertion[A]
  )(implicit trace: Trace, sourceLocation: SourceLocation): ZIO[R, E, TestResult] =
    effect.map { value =>
      assertImpl(value, codeString, assertionString)(assertion)
    }

  /**
   * Returns a test result that always succeeds. Use it as the result of a test
   * that only needs to reach this point.
   */
  def assertCompletes(implicit trace: Trace, sourceLocation: SourceLocation): TestResult =
    assertImpl(true)(Assertion.isTrue)

  /**
   * Returns an effect that succeeds with a test result that always succeeds, as
   * [[assertCompletes]] does.
   */
  def assertCompletesZIO(implicit trace: Trace, sourceLocation: SourceLocation): UIO[TestResult] =
    ZIO.succeed(assertCompletes)

  /**
   * Returns a test result that always fails, labelled with `message`. Use it in
   * a branch that the test should never reach.
   */
  def assertNever(message: String)(implicit trace: Trace, sourceLocation: SourceLocation): TestResult =
    assertImpl(true)(Assertion.equalTo(false)) ?? message

  /**
   * Checks the test passes for "sufficient" numbers of samples from the given
   * random variable: the `samples` setting of [[TestConfig]], which is 200 by
   * default and which `TestAspect.samples` changes.
   *
   * The check stops at the first failing sample and shrinks it, trying up to
   * the `shrinks` setting of `TestConfig` (1000 by default) smaller inputs, and
   * the result reports the smallest failing input it found. A test effect that
   * fails with an error counts as a failing sample, and its input is shrunk the
   * same way. A generator that produces no values makes the check run forever.
   *
   * {{{
   * import zio.test._
   *
   * object ReverseSpec extends ZIOSpecDefault {
   *   def spec = test("reversing a list twice gives the same list") {
   *     check(Gen.listOf(Gen.int)) { list =>
   *       assertTrue(list.reverse.reverse == list)
   *     }
   *   }
   * }
   * }}}
   *
   * @see
   *   [[https://zio.dev/reference/test/property-testing/ Property Testing]]
   */
  def check[R <: ZAny, A, In](rv: Gen[R, A])(test: A => In)(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    TestConfig.samples.flatMap(n => checkStream(rv.sample.forever.take(n.toLong))(a => checkConstructor(test(a))))

  /**
   * A version of `check` that accepts two random variables.
   */
  def check[R <: ZAny, A, B, In](rv1: Gen[R, A], rv2: Gen[R, B])(
    test: (A, B) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    check(rv1 <*> rv2)(test.tupled)

  /**
   * A version of `check` that accepts three random variables.
   */
  def check[R <: ZAny, A, B, C, In](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C])(
    test: (A, B, C) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    check(rv1 <*> rv2 <*> rv3)(test.tupled)

  /**
   * A version of `check` that accepts four random variables.
   */
  def check[R <: ZAny, A, B, C, D, In](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C], rv4: Gen[R, D])(
    test: (A, B, C, D) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    check(rv1 <*> rv2 <*> rv3 <*> rv4)(test.tupled)

  /**
   * A version of `check` that accepts five random variables.
   */
  def check[R <: ZAny, A, B, C, D, F, In](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    rv5: Gen[R, F]
  )(
    test: (A, B, C, D, F) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    check(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5)(test.tupled)

  /**
   * A version of `check` that accepts six random variables.
   */
  def check[R <: ZAny, A, B, C, D, F, G, In](
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
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    check(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5 <*> rv6)(test.tupled)

  /**
   * A version of `check` that accepts seven random variables.
   */
  def check[R <: ZAny, A, B, C, D, F, G, H, In](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    rv5: Gen[R, F],
    rv6: Gen[R, G],
    rv7: Gen[R, H]
  )(
    test: (A, B, C, D, F, G, H) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    check(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5 <*> rv6 <*> rv7)(test.tupled)

  /**
   * A version of `check` that accepts eight random variables.
   */
  def check[R <: ZAny, A, B, C, D, F, G, H, I, In](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    rv5: Gen[R, F],
    rv6: Gen[R, G],
    rv7: Gen[R, H],
    rv8: Gen[R, I]
  )(
    test: (A, B, C, D, F, G, H, I) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    check(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5 <*> rv6 <*> rv7 <*> rv8)(test.tupled)

  /**
   * Checks the test passes for all values from the given finite, deterministic
   * generator. For non-deterministic or infinite generators use `check` or
   * `checkN`: a random generator such as `Gen.int` produces one value each time
   * it runs, so `checkAll` tests one sample of it. The check stops at the first
   * failing value and shrinks it as `check` does. A generator that produces no
   * values passes.
   *
   * {{{
   * import zio.test._
   *
   * object PortSpec extends ZIOSpecDefault {
   *   def spec = test("well-known ports are below 1024") {
   *     checkAll(Gen.fromIterable(List(22, 80, 443))) { port =>
   *       assertTrue(port < 1024)
   *     }
   *   }
   * }
   * }}}
   */
  def checkAll[R <: ZAny, A, In](rv: Gen[R, A])(test: A => In)(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkStream(rv.sample)(a => checkConstructor(test(a)))

  /**
   * A version of `checkAll` that accepts two random variables.
   */
  def checkAll[R <: ZAny, A, B, In](rv1: Gen[R, A], rv2: Gen[R, B])(
    test: (A, B) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkAll(rv1 <*> rv2)(test.tupled)

  /**
   * A version of `checkAll` that accepts three random variables.
   */
  def checkAll[R <: ZAny, A, B, C, In](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C])(
    test: (A, B, C) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkAll(rv1 <*> rv2 <*> rv3)(test.tupled)

  /**
   * A version of `checkAll` that accepts four random variables.
   */
  def checkAll[R <: ZAny, A, B, C, D, In](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C], rv4: Gen[R, D])(
    test: (A, B, C, D) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkAll(rv1 <*> rv2 <*> rv3 <*> rv4)(test.tupled)

  /**
   * A version of `checkAll` that accepts five random variables.
   */
  def checkAll[R <: ZAny, A, B, C, D, F, In](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    rv5: Gen[R, F]
  )(
    test: (A, B, C, D, F) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkAll(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5)(test.tupled)

  /**
   * A version of `checkAll` that accepts six random variables.
   */
  def checkAll[R <: ZAny, A, B, C, D, F, G, In](
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
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkAll(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5 <*> rv6)(test.tupled)

  /**
   * A version of `checkAll` that accepts seven random variables.
   */
  def checkAll[R <: ZAny, A, B, C, D, F, G, H, In](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    rv5: Gen[R, F],
    rv6: Gen[R, G],
    rv7: Gen[R, H]
  )(
    test: (A, B, C, D, F, G, H) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkAll(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5 <*> rv6 <*> rv7)(test.tupled)

  /**
   * A version of `checkAll` that accepts eight random variables.
   */
  def checkAll[R <: ZAny, E, A, B, C, D, F, G, H, I, In](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    rv5: Gen[R, F],
    rv6: Gen[R, G],
    rv7: Gen[R, H],
    rv8: Gen[R, I]
  )(
    test: (A, B, C, D, F, G, H, I) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkAll(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5 <*> rv6 <*> rv7 <*> rv8)(test.tupled)

  /**
   * Checks in parallel the effectual test passes for all values from the given
   * random variable, running the test on up to `parallelism` values at the same
   * time. This is useful for deterministic `Gen` that comprehensively explore
   * all possibilities in a given domain. A failing value is shrunk as in
   * `check`.
   */
  def checkAllPar[R <: ZAny, E, A, In](rv: Gen[R, A], parallelism: Int)(
    test: A => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkStreamPar(rv.sample, parallelism)(a => checkConstructor(test(a)))

  /**
   * A version of `checkAllPar` that accepts two random variables.
   */
  def checkAllPar[R <: ZAny, E, A, B, In](rv1: Gen[R, A], rv2: Gen[R, B], parallelism: Int)(
    test: (A, B) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkAllPar(rv1 <*> rv2, parallelism)(test.tupled)

  /**
   * A version of `checkAllPar` that accepts three random variables.
   */
  def checkAllPar[R <: ZAny, E, A, B, C, In](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    parallelism: Int
  )(
    test: (A, B, C) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkAllPar(rv1 <*> rv2 <*> rv3, parallelism)(test.tupled)

  /**
   * A version of `checkAllPar` that accepts four random variables.
   */
  def checkAllPar[R <: ZAny, E, A, B, C, D, In](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    parallelism: Int
  )(
    test: (A, B, C, D) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkAllPar(rv1 <*> rv2 <*> rv3 <*> rv4, parallelism)(test.tupled)

  /**
   * A version of `checkAllPar` that accepts five random variables.
   */
  def checkAllPar[R <: ZAny, E, A, B, C, D, F, In](
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
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkAllPar(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5, parallelism)(test.tupled)

  /**
   * A version of `checkAllPar` that accepts six random variables.
   */
  def checkAllPar[R <: ZAny, E, A, B, C, D, F, G, In](
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
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkAllPar(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5 <*> rv6, parallelism)(test.tupled)

  /**
   * A version of `checkAllPar` that accepts seven random variables.
   */
  def checkAllPar[R <: ZAny, E, A, B, C, D, F, G, H, In](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    rv5: Gen[R, F],
    rv6: Gen[R, G],
    rv7: Gen[R, H],
    parallelism: Int
  )(
    test: (A, B, C, D, F, G, H) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkAllPar(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5 <*> rv6 <*> rv7, parallelism)(test.tupled)

  /**
   * A version of `checkAllPar` that accepts eight random variables.
   */
  def checkAllPar[R <: ZAny, E, A, B, C, D, F, G, H, I, In](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    rv5: Gen[R, F],
    rv6: Gen[R, G],
    rv7: Gen[R, H],
    rv8: Gen[R, I],
    parallelism: Int
  )(
    test: (A, B, C, D, F, G, H, I) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkAllPar(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5 <*> rv6 <*> rv7 <*> rv8, parallelism)(test.tupled)

  /**
   * Checks the test passes for the specified number of samples from the given
   * random variable, instead of the `samples` setting of [[TestConfig]] that
   * `check` uses. A failing sample is shrunk as in `check`.
   *
   * {{{
   * import zio.test._
   *
   * object AbsSpec extends ZIOSpecDefault {
   *   def spec = test("abs is not negative") {
   *     checkN(50)(Gen.int(-1000, 1000)) { n =>
   *       assertTrue(math.abs(n) >= 0)
   *     }
   *   }
   * }
   * }}}
   */
  def checkN(n: Int): CheckVariants.CheckN =
    new CheckVariants.CheckN(n)

  /**
   * Checks in parallel the test passes for "sufficient" numbers of samples from
   * the given random variable, running the test on up to `parallelism` samples
   * at the same time. The number of samples is the `samples` setting of
   * [[TestConfig]], as in `check`, and a failing sample is shrunk as in
   * `check`.
   */
  def checkPar[R <: ZAny, A, In](rv: Gen[R, A], parallelism: Int)(test: A => In)(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    TestConfig.samples.flatMap(n =>
      checkStreamPar(rv.sample.forever.take(n.toLong), parallelism)(a => checkConstructor(test(a)))
    )

  /**
   * A version of `checkPar` that accepts two random variables.
   */
  def checkPar[R <: ZAny, A, B, In](rv1: Gen[R, A], rv2: Gen[R, B], parallelism: Int)(test: (A, B) => In)(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkPar(rv1 <*> rv2, parallelism)(test.tupled)

  /**
   * A version of `checkPar` that accepts three random variables.
   */
  def checkPar[R <: ZAny, A, B, C, In](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C], parallelism: Int)(
    test: (A, B, C) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkPar(rv1 <*> rv2 <*> rv3, parallelism)(test.tupled)

  /**
   * A version of `checkPar` that accepts four random variables.
   */
  def checkPar[R <: ZAny, A, B, C, D, In](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    parallelism: Int
  )(test: (A, B, C, D) => In)(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkPar(rv1 <*> rv2 <*> rv3 <*> rv4, parallelism)(test.tupled)

  /**
   * A version of `checkPar` that accepts five random variables.
   */
  def checkPar[R <: ZAny, A, B, C, D, F, In](
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
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkPar(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5, parallelism)(test.tupled)

  /**
   * A version of `checkPar` that accepts six random variables.
   */
  def checkPar[R <: ZAny, A, B, C, D, F, G, In](
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
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkPar(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5 <*> rv6, parallelism)(test.tupled)

  /**
   * A version of `checkPar` that accepts seven random variables.
   */
  def checkPar[R <: ZAny, A, B, C, D, F, G, H, In](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    rv5: Gen[R, F],
    rv6: Gen[R, G],
    rv7: Gen[R, H],
    parallelism: Int
  )(
    test: (A, B, C, D, F, G, H) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkPar(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5 <*> rv6 <*> rv7, parallelism)(test.tupled)

  /**
   * A version of `checkPar` that accepts eight random variables.
   */
  def checkPar[R <: ZAny, A, B, C, D, F, G, H, I, In](
    rv1: Gen[R, A],
    rv2: Gen[R, B],
    rv3: Gen[R, C],
    rv4: Gen[R, D],
    rv5: Gen[R, F],
    rv6: Gen[R, G],
    rv7: Gen[R, H],
    rv8: Gen[R, I],
    parallelism: Int
  )(
    test: (A, B, C, D, F, G, H, I) => In
  )(implicit
    checkConstructor: CheckConstructor[R, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
    checkPar(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5 <*> rv6 <*> rv7 <*> rv8, parallelism)(test.tupled)

  /**
   * A [[TestRunner]] that provides the [[testEnvironment]] to the spec and
   * writes the results to the live console.
   */
  lazy val defaultTestRunner: TestRunner[TestEnvironment, Any] =
    TestRunner(
      TestExecutor.default(
        testEnvironment,
        Scope.default <*> testEnvironment,
        ExecutionEventSink.live(Console.ConsoleLive, ConsoleEventRenderer),
        ZTestEventHandler.silent // The default test runner handles its own events, writing their output to the provided sink.
      )
    )

  /**
   * Creates a test that fails with a `TestFailure.Runtime` holding the
   * specified cause.
   */
  def failed[E](cause: Cause[E])(implicit trace: Trace): ZIO[Any, TestFailure[E], Nothing] =
    ZIO.fail(TestFailure.Runtime(cause))

  /**
   * A test that succeeds with an ignored result.
   */
  val ignored: UIO[TestSuccess] =
    ZIO.succeed(TestSuccess.ignored)(Trace.empty)

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
    sourceLocation: SourceLocation,
    trace: Trace
  ): Spec[suiteConstructor.OutEnvironment, suiteConstructor.OutError] =
    Spec.labeled(
      label,
      if (specs.isEmpty) Spec.empty
      else if (specs.length == 1) wrapIfLabelledCase(specs.head)
      else Spec.multiple(Chunk.fromIterable(specs.map(spec => suiteConstructor(spec))))
    )

  // Ensures we render suite label when we have an individual Labeled / Exec test case
  private def wrapIfLabelledCase[In](spec: In)(implicit suiteConstructor: SuiteConstructor[In], trace: Trace) = {
    val suite = suiteConstructor(spec)
    suite.caseValue match {
      case _: LabeledCase[?] | _: ExecCase[?] => Spec.multiple(Chunk.single(suite))
      case _                                  => suite
    }
  }

  /**
   * Builds a spec with a single test.
   */
  def test[In](label: String)(assertion: => In)(implicit
    testConstructor: TestConstructor[Nothing, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): testConstructor.Out =
    testConstructor(label)(assertion)

  /**
   * Passes version specific information to the specified function, which will
   * use that information to create a test. If the version is neither Scala 3
   * nor Scala 2, an ignored test result will be returned.
   */
  def versionSpecific[R, E, A](scala3: => A, scala2: => A)(f: A => ZTest[R, E]): ZTest[R, E] =
    if (TestVersion.isScala3) f(scala3)
    else if (TestVersion.isScala2) f(scala2)
    else ignored

  /** Helpers for the `checkN` syntax. */
  object CheckVariants {

    /**
     * The result of `checkN(n)`: apply it to one to six generators and a
     * property to check the property for `n` samples.
     */
    final class CheckN(private val n: Int) extends AnyVal {

      /**
       * Checks the test passes for `n` samples from the given random variable.
       * On the first failure it shrinks the failing input, as `check` does.
       */
      def apply[R <: ZAny, A, In](rv: Gen[R, A])(test: A => In)(implicit
        checkConstructor: CheckConstructor[R, In],
        trace: Trace
      ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
        checkStream(rv.sample.forever.take(n.toLong))(a => checkConstructor(test(a)))

      /** A version of `apply` that accepts two random variables. */
      def apply[R <: ZAny, A, B, In](rv1: Gen[R, A], rv2: Gen[R, B])(
        test: (A, B) => In
      )(implicit
        checkConstructor: CheckConstructor[R, In],
        trace: Trace
      ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
        checkN(n)(rv1 <*> rv2)(test.tupled)

      /** A version of `apply` that accepts three random variables. */
      def apply[R <: ZAny, A, B, C, In](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C])(
        test: (A, B, C) => In
      )(implicit
        checkConstructor: CheckConstructor[R, In],
        trace: Trace
      ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
        checkN(n)(rv1 <*> rv2 <*> rv3)(test.tupled)

      /** A version of `apply` that accepts four random variables. */
      def apply[R <: ZAny, A, B, C, D, In](rv1: Gen[R, A], rv2: Gen[R, B], rv3: Gen[R, C], rv4: Gen[R, D])(
        test: (A, B, C, D) => In
      )(implicit
        checkConstructor: CheckConstructor[R, In],
        trace: Trace
      ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
        checkN(n)(rv1 <*> rv2 <*> rv3 <*> rv4)(test.tupled)

      /** A version of `apply` that accepts five random variables. */
      def apply[R <: ZAny, A, B, C, D, F, In](
        rv1: Gen[R, A],
        rv2: Gen[R, B],
        rv3: Gen[R, C],
        rv4: Gen[R, D],
        rv5: Gen[R, F]
      )(
        test: (A, B, C, D, F) => In
      )(implicit
        checkConstructor: CheckConstructor[R, In],
        trace: Trace
      ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
        checkN(n)(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5)(test.tupled)

      /** A version of `apply` that accepts six random variables. */
      def apply[R <: ZAny, A, B, C, D, F, G, In](
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
        trace: Trace
      ): ZIO[checkConstructor.OutEnvironment, checkConstructor.OutError, TestResult] =
        checkN(n)(rv1 <*> rv2 <*> rv3 <*> rv4 <*> rv5 <*> rv6)(test.tupled)
    }
  }

  private def checkStream[R, R1 <: R, E, A](stream: ZStream[R, Nothing, Sample[R, A]])(
    test: A => ZIO[R1, E, TestResult]
  )(implicit trace: Trace): ZIO[R1, E, TestResult] =
    testConfigWith { testConfig =>
      warningEmptyGen { cancelWarning =>
        shrinkStream {
          stream.zipWithIndex.mapZIO { case (initial, index) =>
            cancelWarning()
            initial.foreach(input =>
              (test(input) @@ testConfig.checkAspect)
                .map(_.setGenFailureDetails(GenFailureDetails(initial.value, input, index)))
                .either
            )
          }
        }(testConfig.shrinks)
      }
    }

  private def shrinkStream[R, R1 <: R, E, A](
    stream: ZStream[R1, Nothing, Sample[R1, Either[E, TestResult]]]
  )(maxShrinks: Int)(implicit trace: Trace): ZIO[R1, E, TestResult] =
    stream
      .dropWhile(!_.value.fold(_ => true, _.isFailure)) // Drop until we get to a failure
      .take(1)                                          // Get the first failure
      .flatMap(_.shrinkSearch(_.fold(_ => true, _.isFailure)).take(maxShrinks.toLong + 1))
      .run(ZSink.collectAll[Either[E, TestResult]]) // Collect all the shrunken values
      .flatMap { shrinks =>
        // Get the "last" failure, the smallest according to the shrinker:
        shrinks
          .filter(_.fold(_ => true, _.isFailure))
          .lastOption
          .fold[ZIO[R, E, TestResult]](
            ZIO.succeed(assertCompletes)
          )(ZIO.fromEither(_))
      }

  private def checkStreamPar[R, R1 <: R, E, A](stream: ZStream[R, Nothing, Sample[R, A]], parallelism: Int)(
    test: A => ZIO[R1, E, TestResult]
  )(implicit trace: Trace): ZIO[R1, E, TestResult] =
    testConfigWith { testConfig =>
      warningEmptyGen { cancelWarning =>
        shrinkStream {
          stream.zipWithIndex
            .mapZIOPar(parallelism) { case (initial, index) =>
              cancelWarning()
              initial.foreach { input =>
                (test(input) @@ testConfig.checkAspect)
                  .map(_.setGenFailureDetails(GenFailureDetails(initial.value, input, index)))
                  .either
                // convert test failures to failures to terminate parallel tests on first failure
              }.flatMap(sample => sample.value.fold(_ => ZIO.fail(sample), _ => ZIO.succeed(sample)))
            // move failures back into success channel for shrinking logic
            }
            .catchAll(ZStream.succeed(_))
        }(testConfig.shrinks)
      }
    }

  private def warningEmptyGen[R, E, A](
    f: (() => Boolean) => ZIO[R, E, A]
  )(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.suspendSucceed {
      val cancel = Clock.globalScheduler.schedule(() => logMessageWithTrace(warning), 5.seconds)(Unsafe)
      f(cancel).onInterrupt(ZIO.succeed(cancel()))
    }

  private val warning =
    "Warning: A check / checkAll / checkN generator did not produce any test cases, " +
      "which may result in the test hanging. Ensure that the provided generator is producing values."

  /** Smart-assertion syntax for an `Option` inside `assertTrue`. */
  implicit final class TestLensOptionOps[A](private val self: TestLens[Option[A]]) extends AnyVal {

    /**
     * Transforms an [[scala.Option]] to its `Some` value `A`, otherwise fails
     * if it is a `None`.
     */
    def some: TestLens[A] = throw SmartAssertionExtensionError()
  }

  /** Smart-assertion syntax for a `Try` inside `assertTrue`. */
  implicit final class TestLensTryOps[A](private val self: TestLens[scala.util.Try[A]]) extends AnyVal {

    /**
     * Transforms an [[scala.util.Try]] to its [[scala.util.Success]] value `A`,
     * otherwise fails if it is a [[scala.util.Failure]].
     */
    def success: TestLens[A] = throw SmartAssertionExtensionError()

    /**
     * Transforms an [[scala.util.Try]] to a [[scala.Throwable]] if it is a
     * [[scala.util.Failure]], otherwise fails.
     */
    def failure: TestLens[Throwable] = throw SmartAssertionExtensionError()
  }

  /** Smart-assertion syntax for an `Either` inside `assertTrue`. */
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

  /** Smart-assertion syntax for an `Exit` inside `assertTrue`. */
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

  /** Smart-assertion syntax for a `Cause` inside `assertTrue`. */
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

  /** Smart-assertion syntax for a value of any type inside `assertTrue`. */
  implicit final class TestLensAnyOps[A](private val self: TestLens[A]) extends AnyVal {

    /**
     * Returns `true` as long as the chain of preceding transformations has
     * succeeded.
     *
     * {{{
     * import zio.test._
     *
     * val value: Either[Int, Option[String]] = Right(Some("Cool"))
     *
     * // succeeds
     * val passes: TestResult = assertTrue(value.is(_.right.some.anything))
     * // fails because of `.left`
     * val fails: TestResult = assertTrue(value.is(_.left.anything))
     * }}}
     */
    def anything: TestLens[Boolean] = throw SmartAssertionExtensionError()

    /**
     * Transforms a value of some type into the given `Subtype` if possible,
     * otherwise fails.
     *
     * {{{
     * import zio.test._
     *
     * sealed trait CustomError
     * case class Explosion(blastRadius: Int) extends CustomError
     * case class Melting(degrees: Double)    extends CustomError
     * case class Fulminating(wow: Boolean)   extends CustomError
     *
     * val error: CustomError = Melting(100)
     *
     * // succeeds
     * val passes: TestResult = assertTrue(error.is(_.subtype[Melting]).degrees > 10)
     * // fails
     * val fails: TestResult = assertTrue(error.is(_.subtype[Explosion]).blastRadius == 12)
     * }}}
     */
    def subtype[Subtype <: A]: TestLens[Subtype] = throw SmartAssertionExtensionError()

    /**
     * Transforms a value with the given [[CustomAssertion]], failing when the
     * custom assertion returns a `Left`.
     */
    def custom[B](customAssertion: CustomAssertion[A, B]): TestLens[B] = {
      val _ = customAssertion
      throw SmartAssertionExtensionError()
    }
  }

  /** Adds `is`, which opens the smart-assertion syntax of [[TestLens]]. */
  implicit final class SmartAssertionOps[A](private val self: A) extends AnyVal {

    /**
     * This extension method can only be called inside of the `assertTrue`
     * method; anywhere else it throws a `SmartAssertionExtensionError`. It will
     * transform the value using the given [[TestLens]].
     *
     * {{{
     * import zio.test._
     *
     * val value: Either[Int, Option[String]] = Right(Some("Cool"))
     *
     * // succeeds
     * val passes: TestResult = assertTrue(value.is(_.right.some) == "Cool")
     * // fails because of `.left`
     * val fails: TestResult = assertTrue(value.is(_.left) < 100)
     * }}}
     */
    def is[B](f: TestLens[A] => TestLens[B]): B = {
      val _ = f
      throw SmartAssertionExtensionError()
    }
  }

  /** Combinators for effects that produce a [[TestResult]]. */
  implicit final class TestResultZIOOps[R, E](private val self: ZIO[R, E, TestResult]) extends AnyVal {

    /**
     * Runs this effect and then `that` effect, and combines their results with
     * logical and (`&&`).
     */
    def &&[R1 <: R, E1 >: E](that: => ZIO[R1, E1, TestResult])(implicit trace: Trace): ZIO[R1, E1, TestResult] =
      self.zipWith(that)(_ && _)

    /**
     * Runs this effect and then `that` effect, and combines their results with
     * logical or (`||`).
     */
    def ||[R1 <: R, E1 >: E](that: => ZIO[R1, E1, TestResult])(implicit trace: Trace): ZIO[R1, E1, TestResult] =
      self.zipWith(that)(_ || _)
  }

  private[test] def logMessageWithTrace(message: String)(implicit trace: Trace): Unit = {
    val sb = new StringBuilder(message.length + 128)

    def appendQuoted(label: String): Unit =
      if (label.indexOf(' ') < 0) sb.append(label)
      else sb.append('"').append(label).append('"')

    sb
      .append("message=\"")
      .append(message)
      .append('"')

    val parsedTrace = Trace.parseOrNull(trace)
    if (parsedTrace ne null) {
      sb.append(" location=")
      appendQuoted(parsedTrace.location)
      sb.append(" file=")
      appendQuoted(parsedTrace.file)
      sb.append(" line=").append(parsedTrace.line)
    }

    println(sb.toString())
  }
}
