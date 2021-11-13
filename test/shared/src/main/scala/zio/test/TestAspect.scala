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

package zio.test

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.AtomicReference
import scala.collection.immutable.SortedSet

/**
 * A `TestAspect` is an aspect that can be weaved into specs. You can think of
 * an aspect as a polymorphic function, capable of transforming one test into
 * another, possibly enlarging the environment or error type.
 */
trait TestAspect[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr]
    extends TestAspectVersionSpecific[LowerEnv, UpperEnv, LowerErr, UpperErr] { self =>
  type OutEnv[Env]
  type OutErr[Err]

  /**
   * An alias for [[all]].
   */
  def apply[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](spec: Spec[Env, TestFailure[Err], TestSuccess])(
    implicit trace: ZTraceElement
  ): Spec[OutEnv[Env], TestFailure[OutErr[Err]], TestSuccess]
}

object TestAspect extends TestAspectCompanionVersionSpecific with TimeoutVariants {

  type WithOut[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr, OutEnv0[Err], OutErr0[Env]] =
    TestAspect[LowerEnv, UpperEnv, LowerErr, UpperErr] {
      type OutEnv[Env] = OutEnv0[Env]
      type OutErr[Err] = OutErr0[Err]
    }

  /**
   * An aspect that returns the tests unchanged
   */
  val identity: TestAspect.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new TestAspect[Nothing, Any, Nothing, Any] {
      type OutEnv[Env] = Env
      type OutErr[Err] = Err
      def apply[R, E](spec: ZSpec[R, E])(implicit trace: ZTraceElement): ZSpec[R, E] =
        spec
    }

  /**
   * An aspect that marks tests as ignored.
   */
  val ignore: TestAspect.WithOut[
    Nothing,
    Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new TestAspect[Nothing, Has[Annotations], Nothing, Any] {
      type OutEnv[Env] = Env
      type OutErr[Err] = Err
      def apply[R <: Has[Annotations], E](spec: ZSpec[R, E])(implicit trace: ZTraceElement): ZSpec[R, E] =
        spec.when(false)
    }

  /**
   * Constructs an aspect that runs the specified effect after every test.
   */
  def after[R0, E0](effect: ZIO[R0, E0, Any]): TestAspect.WithOut[
    Nothing,
    R0,
    E0,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new TestAspect.PerTest[Nothing, R0, E0, Any] {
      def perTest[R <: R0, E >: E0](
        test: ZIO[R, TestFailure[E], TestSuccess]
      )(implicit trace: ZTraceElement): ZIO[R, TestFailure[E], TestSuccess] =
        test.exit
          .zipWith(effect.catchAllCause(cause => ZIO.fail(TestFailure.Runtime(cause))).exit)(_ <* _)
          .flatMap(ZIO.done(_))
    }

  /**
   * Constructs an aspect that runs the specified effect after all tests.
   */
  def afterAll[R0](effect: ZIO[R0, Nothing, Any]): TestAspect.WithOut[
    Nothing,
    R0,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    aroundAll(ZIO.unit, effect)

  /**
   * Annotates tests with the specified test annotation.
   */
  def annotate[V](key: TestAnnotation[V], value: V): TestAspect.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new TestAspect[Nothing, Any, Nothing, Any] {
      type OutEnv[Env] = Env
      type OutErr[Err] = Err
      def apply[R, E](spec: ZSpec[R, E])(implicit trace: ZTraceElement): ZSpec[R, E] =
        spec.annotate(key, value)
    }

  /**
   * Constructs an aspect that evaluates every test between two effects,
   * `before` and `after`, where the result of `before` can be used in `after`.
   */
  def aroundWith[R0, E0, A0](
    before: ZIO[R0, E0, A0]
  )(after: A0 => ZIO[R0, Nothing, Any]): TestAspect.WithOut[
    Nothing,
    R0,
    E0,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new TestAspect.PerTest[Nothing, R0, E0, Any] {
      def perTest[R <: R0, E >: E0](test: ZIO[R, TestFailure[E], TestSuccess])(implicit
        trace: ZTraceElement
      ): ZIO[R, TestFailure[E], TestSuccess] =
        before.catchAllCause(c => ZIO.fail(TestFailure.Runtime(c))).acquireReleaseWith(after)(_ => test)
    }

  /**
   * A less powerful variant of `around` where the result of `before` is not
   * required by after.
   */
  def around[R0, E0](before: ZIO[R0, E0, Any], after: ZIO[R0, Nothing, Any]): TestAspect.WithOut[
    Nothing,
    R0,
    E0,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    aroundWith(before)(_ => after)

  /**
   * Constructs an aspect that evaluates all tests between two effects, `before`
   * and `after`, where the result of `before` can be used in `after`.
   */
  def aroundAllWith[R0, E0, A0](
    before: ZIO[R0, E0, A0]
  )(after: A0 => ZIO[R0, Nothing, Any]): TestAspect.WithOut[
    Nothing,
    R0,
    E0,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new TestAspect[Nothing, R0, E0, Any] {
      type OutEnv[Env] = Env
      type OutErr[Err] = Err
      def apply[R <: R0, E >: E0](spec: ZSpec[R, E])(implicit trace: ZTraceElement): ZSpec[R, E] =
        Spec.managed(ZManaged.acquireReleaseWith(before)(after).mapError(TestFailure.fail).as(spec))
    }

  /**
   * A less powerful variant of `aroundAll` where the result of `before` is not
   * required by `after`.
   */
  def aroundAll[R0, E0](before: ZIO[R0, E0, Any], after: ZIO[R0, Nothing, Any]): TestAspect.WithOut[
    Nothing,
    R0,
    E0,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    aroundAllWith(before)(_ => after)

  /**
   * Constructs an aspect that evaluates every test inside the context of the
   * managed function.
   */
  def aroundTest[R0, E0](
    managed: ZManaged[R0, TestFailure[E0], TestSuccess => ZIO[R0, TestFailure[E0], TestSuccess]]
  ): TestAspect.WithOut[
    Nothing,
    R0,
    E0,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new TestAspect.PerTest[Nothing, R0, E0, Any] {
      def perTest[R <: R0, E >: E0](test: ZIO[R, TestFailure[E], TestSuccess])(implicit
        trace: ZTraceElement
      ): ZIO[R, TestFailure[E], TestSuccess] =
        managed.use(f => test.flatMap(f))
    }

  /**
   * Constructs a simple monomorphic aspect that only works with the specified
   * environment and error type.
   */
  def aspect[R0, E0](
    f: ZIO[R0, TestFailure[E0], TestSuccess] => ZIO[R0, TestFailure[E0], TestSuccess]
  ): TestAspect.WithOut[R0, R0, E0, E0, ({ type OutEnv[Env] = Env })#OutEnv, ({ type OutErr[Err] = Err })#OutErr] =
    new TestAspect.PerTest[R0, R0, E0, E0] {
      def perTest[R >: R0 <: R0, E >: E0 <: E0](
        test: ZIO[R, TestFailure[E], TestSuccess]
      )(implicit trace: ZTraceElement): ZIO[R, TestFailure[E], TestSuccess] =
        f(test)
    }

  /**
   * Constructs an aspect that runs the specified effect before every test.
   */
  def before[R0, E0](effect: ZIO[R0, E0, Any]): TestAspect.WithOut[
    Nothing,
    R0,
    E0,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new TestAspect.PerTest[Nothing, R0, E0, Any] {
      def perTest[R <: R0, E >: E0](test: ZIO[R, TestFailure[E], TestSuccess])(implicit
        trace: ZTraceElement
      ): ZIO[R, TestFailure[E], TestSuccess] =
        effect.catchAllCause(cause => ZIO.fail(TestFailure.failCause(cause))) *> test
    }

  /**
   * Constructs an aspect that runs the specified effect a single time before
   * all tests.
   */
  def beforeAll[R0, E0](effect: ZIO[R0, E0, Any]): TestAspect.WithOut[
    Nothing,
    R0,
    E0,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    aroundAll(effect, ZIO.unit)

  /**
   * An aspect that runs each test on a separate fiber and prints a fiber dump
   * if the test fails or has not terminated within the specified duration.
   */
  def diagnose(duration: Duration): TestAspect.WithOut[Nothing, Has[Live] with Has[
    Annotations
  ], Nothing, Any, ({ type OutEnv[Env] = Env })#OutEnv, ({ type OutErr[Err] = Err })#OutErr] =
    new TestAspect[Nothing, Has[Live] with Has[Annotations], Nothing, Any] {
      type OutEnv[Env] = Env
      type OutErr[Err] = Err
      def apply[R <: Has[Live] with Has[Annotations], E](
        spec: ZSpec[R, E]
      )(implicit trace: ZTraceElement): ZSpec[R, E] = {
        def diagnose[R <: Has[Live] with Has[Annotations], E](
          label: String,
          test: ZIO[R, TestFailure[E], TestSuccess]
        ): ZIO[R, TestFailure[E], TestSuccess] =
          test.fork.flatMap { fiber =>
            fiber.join.raceWith[R, TestFailure[E], TestFailure[E], Unit, TestSuccess](Live.live(ZIO.sleep(duration)))(
              (exit, sleepFiber) => dump(label).when(!exit.isSuccess) *> sleepFiber.interrupt *> ZIO.done(exit),
              (_, _) => dump(label) *> fiber.join
            )
          }
        def dump[E, A](label: String): URIO[Has[Live] with Has[Annotations], Unit] =
          Annotations.supervisedFibers.flatMap(fibers => Live.live(Fiber.putDumpStr(label, fibers.toSeq: _*).orDie))
        spec.transform[R, TestFailure[E], TestSuccess] {
          case Spec.TestCase(test, annotations) => Spec.TestCase(diagnose("", test), annotations)
          case c                                => c
        }
      }
    }

  /**
   * An aspect that runs each test with the `TestConsole` instance in the
   * environment set to debug mode so that console output is rendered to
   * standard output in addition to being written to the output buffer.
   */
  val debug: TestAspect.WithOut[Nothing, Has[
    TestConsole
  ], Nothing, Any, ({ type OutEnv[Env] = Env })#OutEnv, ({ type OutErr[Err] = Err })#OutErr] =
    new TestAspect.PerTest[Nothing, Has[TestConsole], Nothing, Any] {
      def perTest[R <: Has[TestConsole], E](
        test: ZIO[R, TestFailure[E], TestSuccess]
      )(implicit trace: ZTraceElement): ZIO[R, TestFailure[E], TestSuccess] =
        TestConsole.debug(test)
    }

  /**
   * An aspect that applies the specified aspect on Dotty.
   */
  def dotty[LowerR, UpperR, LowerE, UpperE](
    that: TestAspect.WithOut[
      LowerR,
      UpperR,
      LowerE,
      UpperE,
      ({ type OutEnv[Env] = Env })#OutEnv,
      ({ type OutErr[Err] = Err })#OutErr
    ]
  ): TestAspect.WithOut[
    LowerR,
    UpperR,
    LowerE,
    UpperE,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    if (TestVersion.isDotty) that else identity

  /**
   * An aspect that only runs tests on Dotty.
   */
  val dottyOnly: TestAspect.WithOut[
    Nothing,
    Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    if (TestVersion.isDotty) identity else ignore

  /**
   * An aspect that retries a test until success, without limit.
   */
  val eventually: TestAspect.WithOut[
    Nothing,
    ZTestEnv,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] = {
    val eventually: TestAspect.WithOut[
      Nothing,
      Any,
      Nothing,
      Any,
      ({ type OutEnv[Env] = Env })#OutEnv,
      ({ type OutErr[Err] = Err })#OutErr
    ] = new PerTest[Nothing, Any, Nothing, Any] {
      def perTest[R, E](test: ZIO[R, TestFailure[E], TestSuccess])(implicit
        trace: ZTraceElement
      ): ZIO[R, TestFailure[E], TestSuccess] =
        test.eventually
    }
    val restore = restoreTestEnvironment >>> eventually
    restore
  }

  /**
   * An aspect that runs tests on all versions except Dotty.
   */
  val exceptDotty: TestAspect.WithOut[
    Nothing,
    Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    if (TestVersion.isDotty) ignore else identity

  /**
   * An aspect that runs tests on all platforms except ScalaJS.
   */
  val exceptJS: TestAspect.WithOut[
    Nothing,
    Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    if (TestPlatform.isJS) ignore else identity

  /**
   * An aspect that runs tests on all platforms except the JVM.
   */
  val exceptJVM: TestAspect.WithOut[
    Nothing,
    Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    if (TestPlatform.isJVM) ignore else identity

  /**
   * An aspect that runs tests on all platforms except ScalaNative.
   */
  val exceptNative: TestAspect.WithOut[
    Nothing,
    Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    if (TestPlatform.isNative) ignore else identity

  /**
   * An aspect that runs tests on all versions except Scala 2.
   */
  val exceptScala2: TestAspect.WithOut[
    Nothing,
    Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    if (TestVersion.isScala2) ignore else identity

  /**
   * An aspect that runs tests on all versions except Scala 2.11.
   */
  val exceptScala211: TestAspect.WithOut[
    Nothing,
    Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    if (TestVersion.isScala211) ignore else identity

  /**
   * An aspect that runs tests on all versions except Scala 2.12.
   */
  val exceptScala212: TestAspect.WithOut[
    Nothing,
    Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    if (TestVersion.isScala212) ignore else identity

  /**
   * An aspect that runs tests on all versions except Scala 2.13.
   */
  val exceptScala213: TestAspect.WithOut[
    Nothing,
    Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    if (TestVersion.isScala213) ignore else identity

  /**
   * An aspect that sets suites to the specified execution strategy, but only if
   * their current strategy is inherited (undefined).
   */
  def executionStrategy(exec: ExecutionStrategy): TestAspect.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new TestAspect[Nothing, Any, Nothing, Any] {
      type OutEnv[Env] = Env
      type OutErr[Err] = Err
      def apply[R, E](spec: ZSpec[R, E])(implicit trace: ZTraceElement): ZSpec[R, E] =
        Spec.exec(exec, spec)
    }

  /**
   * An aspect that makes a test that failed for any reason pass. Note that if
   * the test passes this aspect will make it fail.
   */
  val failing: TestAspect.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    failing(_ => true)

  /**
   * An aspect that makes a test that failed for the specified failure pass.
   * Note that the test will fail for other failures and also if it passes
   * correctly.
   */
  def failing[E0](assertion: TestFailure[E0] => Boolean): TestAspect.WithOut[
    Nothing,
    Any,
    Nothing,
    E0,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new TestAspect.PerTest[Nothing, Any, Nothing, E0] {
      def perTest[R, E <: E0](test: ZIO[R, TestFailure[E], TestSuccess])(implicit
        trace: ZTraceElement
      ): ZIO[R, TestFailure[E], TestSuccess] =
        test.foldZIO(
          failure =>
            if (assertion(failure)) ZIO.succeedNow(TestSuccess.Succeeded(BoolAlgebra.unit))
            else ZIO.fail(TestFailure.die(new RuntimeException("did not fail as expected"))),
          _ => ZIO.fail(TestFailure.die(new RuntimeException("did not fail as expected")))
        )
    }

  /**
   * An aspect that records the state of fibers spawned by the current test in
   * [[TestAnnotation.fibers]]. Applied by default in [[DefaultRunnableSpec]]
   * but not in [[RunnableSpec]]. This aspect is required for the proper
   * functioning of `TestClock.adjust`.
   */
  lazy val fibers: TestAspect.WithOut[
    Nothing,
    Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new TestAspect.PerTest[Nothing, Has[Annotations], Nothing, Any] {
      def perTest[R <: Has[Annotations], E](
        test: ZIO[R, TestFailure[E], TestSuccess]
      )(implicit trace: ZTraceElement): ZIO[R, TestFailure[E], TestSuccess] = {
        val acquire = ZIO.succeed(new AtomicReference(SortedSet.empty[Fiber.Runtime[Any, Any]])).tap { ref =>
          Annotations.annotate(TestAnnotation.fibers, Right(Chunk(ref)))
        }
        val release = Annotations.get(TestAnnotation.fibers).flatMap {
          case Right(refs) =>
            ZIO
              .foreach(refs)(ref => ZIO.succeed(ref.get))
              .map(_.foldLeft(SortedSet.empty[Fiber.Runtime[Any, Any]])(_ ++ _).size)
              .tap { n =>
                Annotations.annotate(TestAnnotation.fibers, Left(n))
              }
          case Left(_) => ZIO.unit
        }
        acquire.acquireReleaseWith(_ => release) { ref =>
          Supervisor.fibersIn(ref).flatMap(supervisor => test.supervised(supervisor))
        }
      }
    }

  /**
   * An aspect that retries a test until success, with a default limit, for use
   * with flaky tests.
   */
  val flaky: TestAspect.WithOut[
    Nothing,
    Has[Annotations] with Has[TestConfig] with ZTestEnv,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] = {
    val flaky: TestAspect.WithOut[Nothing, Has[Annotations] with Has[
      TestConfig
    ] with ZTestEnv, Nothing, Any, ({ type OutEnv[Env] = Env })#OutEnv, ({ type OutErr[Err] = Err })#OutErr] =
      new PerTest[Nothing, Has[Annotations] with Has[TestConfig] with ZTestEnv, Nothing, Any] {
        def perTest[R <: Has[Annotations] with Has[TestConfig] with ZTestEnv, E](
          test: ZIO[R, TestFailure[E], TestSuccess]
        )(implicit trace: ZTraceElement): ZIO[R, TestFailure[E], TestSuccess] =
          TestConfig.retries.flatMap { n =>
            test.catchAll(_ => test.tapError(_ => Annotations.annotate(TestAnnotation.retried, 1)).retryN(n - 1))
          }
      }
    val restore = restoreTestEnvironment >>> flaky
    restore
  }

  /**
   * An aspect that retries a test until success, with the specified limit, for
   * use with flaky tests.
   */
  def flaky(n: Int): TestAspect.WithOut[
    Nothing,
    ZTestEnv with Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] = {
    val flaky: TestAspect.WithOut[Nothing, ZTestEnv with Has[
      Annotations
    ], Nothing, Any, ({ type OutEnv[Env] = Env })#OutEnv, ({ type OutErr[Err] = Err })#OutErr] =
      new PerTest[Nothing, ZTestEnv with Has[Annotations], Nothing, Any] {
        def perTest[R <: ZTestEnv with Has[Annotations], E](
          test: ZIO[R, TestFailure[E], TestSuccess]
        )(implicit trace: ZTraceElement): ZIO[R, TestFailure[E], TestSuccess] =
          test.catchAll(_ => test.tapError(_ => Annotations.annotate(TestAnnotation.retried, 1)).retryN(n - 1))
      }
    val restore = restoreTestEnvironment >>> flaky
    restore
  }

  /**
   * An aspect that runs each test on its own separate fiber.
   */
  val forked: TestAspect.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new PerTest[Nothing, Any, Nothing, Any] {
      def perTest[R, E](test: ZIO[R, TestFailure[E], TestSuccess])(implicit
        trace: ZTraceElement
      ): ZIO[R, TestFailure[E], TestSuccess] =
        test.fork.flatMap(_.join)
    }

  /**
   * An aspect that only runs a test if the specified environment variable
   * satisfies the specified assertion.
   */
  def ifEnv(env: String)(assertion: String => Boolean): TestAspect.WithOut[
    Nothing,
    Has[Live] with Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new TestAspect[Nothing, Has[Live] with Has[Annotations], Nothing, Any] {
      type OutEnv[Env] = Env
      type OutErr[Err] = Err
      def apply[R <: Has[Live] with Has[Annotations], E](spec: ZSpec[R, E])(implicit
        trace: ZTraceElement
      ): ZSpec[R, E] =
        spec.whenZIO(Live.live(System.env(env)).orDie.map(_.fold(false)(assertion)))
    }

  /**
   * As aspect that only runs a test if the specified environment variable is
   * set.
   */
  def ifEnvSet(env: String): TestAspect.WithOut[
    Nothing,
    Has[Live] with Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    ifEnv(env)(_ => true)

  /**
   * An aspect that only runs a test if the specified Java property satisfies
   * the specified assertion.
   */
  def ifProp(prop: String)(assertion: String => Boolean): TestAspect.WithOut[
    Nothing,
    Has[Live] with Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new TestAspect[Nothing, Has[Live] with Has[Annotations], Nothing, Any] {
      type OutEnv[Env] = Env
      type OutErr[Err] = Err
      def apply[R <: Has[Live] with Has[Annotations], E](spec: ZSpec[R, E])(implicit
        trace: ZTraceElement
      ): ZSpec[R, E] =
        spec.whenZIO(Live.live(System.property(prop)).orDie.map(_.fold(false)(assertion)))
    }

  /**
   * As aspect that only runs a test if the specified Java property is set.
   */
  def ifPropSet(prop: String): TestAspect.WithOut[
    Nothing,
    Has[Live] with Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    ifProp(prop)(_ => true)

  /**
   * An aspect that applies the specified aspect on ScalaJS.
   */
  def js[LowerR, UpperR, LowerE, UpperE](
    that: TestAspect.WithOut[
      LowerR,
      UpperR,
      LowerE,
      UpperE,
      ({ type OutEnv[Env] = Env })#OutEnv,
      ({ type OutErr[Err] = Err })#OutErr
    ]
  ): TestAspect.WithOut[
    LowerR,
    UpperR,
    LowerE,
    UpperE,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    if (TestPlatform.isJS) that else identity

  /**
   * An aspect that only runs tests on ScalaJS.
   */
  val jsOnly: TestAspect.WithOut[
    Nothing,
    Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    if (TestPlatform.isJS) identity else ignore

  /**
   * An aspect that applies the specified aspect on the JVM.
   */
  def jvm[LowerR, UpperR, LowerE, UpperE](
    that: TestAspect.WithOut[
      LowerR,
      UpperR,
      LowerE,
      UpperE,
      ({ type OutEnv[Env] = Env })#OutEnv,
      ({ type OutErr[Err] = Err })#OutErr
    ]
  ): TestAspect.WithOut[
    LowerR,
    UpperR,
    LowerE,
    UpperE,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    if (TestPlatform.isJVM) that else identity

  /**
   * An aspect that only runs tests on the JVM.
   */
  val jvmOnly: TestAspect.WithOut[
    Nothing,
    Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    if (TestPlatform.isJVM) identity else ignore

  /**
   * An aspect that runs only on operating systems accepted by the specified
   * predicate.
   */
  def os(f: System.OS => Boolean): TestAspect.WithOut[
    Nothing,
    Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    if (f(System.os)) identity else ignore

  /**
   * Runs only on Mac operating systems.
   */
  val mac: TestAspect.WithOut[
    Nothing,
    Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] = os(_.isMac)

  /**
   * An aspect that applies the specified aspect on ScalaNative.
   */
  def native[LowerR, UpperR, LowerE, UpperE](
    that: TestAspect.WithOut[
      LowerR,
      UpperR,
      LowerE,
      UpperE,
      ({ type OutEnv[Env] = Env })#OutEnv,
      ({ type OutErr[Err] = Err })#OutErr
    ]
  ): TestAspect.WithOut[
    LowerR,
    UpperR,
    LowerE,
    UpperE,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    if (TestPlatform.isNative) that else identity

  /**
   * An aspect that only runs tests on ScalaNative.
   */
  val nativeOnly: TestAspect.WithOut[
    Nothing,
    Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    if (TestPlatform.isNative) identity else ignore

  /**
   * An aspect that repeats the test a default number of times, ensuring it is
   * stable ("non-flaky"). Stops at the first failure.
   */
  val nonFlaky: TestAspect.WithOut[
    Nothing,
    ZTestEnv with Has[Annotations] with Has[TestConfig],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] = {
    val nonFlaky: TestAspect.WithOut[Nothing, ZTestEnv with Has[Annotations] with Has[
      TestConfig
    ], Nothing, Any, ({ type OutEnv[Env] = Env })#OutEnv, ({ type OutErr[Err] = Err })#OutErr] =
      new PerTest[Nothing, ZTestEnv with Has[Annotations] with Has[TestConfig], Nothing, Any] {
        def perTest[R <: ZTestEnv with Has[Annotations] with Has[TestConfig], E](
          test: ZIO[R, TestFailure[E], TestSuccess]
        )(implicit trace: ZTraceElement): ZIO[R, TestFailure[E], TestSuccess] =
          TestConfig.repeats.flatMap { n =>
            test *> test.tap(_ => Annotations.annotate(TestAnnotation.repeated, 1)).repeatN(n - 1)
          }
      }
    val restore = restoreTestEnvironment >>> nonFlaky
    restore
  }

  /**
   * An aspect that repeats the test a specified number of times, ensuring it is
   * stable ("non-flaky"). Stops at the first failure.
   */
  def nonFlaky(n: Int): TestAspect.WithOut[
    Nothing,
    ZTestEnv with Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] = {
    val nonFlaky: TestAspect.WithOut[Nothing, ZTestEnv with Has[
      Annotations
    ], Nothing, Any, ({ type OutEnv[Env] = Env })#OutEnv, ({ type OutErr[Err] = Err })#OutErr] =
      new PerTest[Nothing, ZTestEnv with Has[Annotations], Nothing, Any] {
        def perTest[R <: ZTestEnv with Has[Annotations], E](
          test: ZIO[R, TestFailure[E], TestSuccess]
        )(implicit trace: ZTraceElement): ZIO[R, TestFailure[E], TestSuccess] =
          test *> test.tap(_ => Annotations.annotate(TestAnnotation.repeated, 1)).repeatN(n - 1)
      }
    val restore = restoreTestEnvironment >>> nonFlaky
    restore
  }

  /**
   * Constructs an aspect that requires a test to not terminate within the
   * specified time.
   */
  def nonTermination(duration: Duration): TestAspect.WithOut[
    Nothing,
    Has[Live],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] = {
    val nonTermination = timeout(duration) >>>
      failing[Any] {
        case TestFailure.Assertion(_) => false
        case TestFailure.Runtime(cause) =>
          cause.dieOption match {
            case Some(t) => t.getMessage == s"Timeout of ${duration.render} exceeded."
            case None    => false
          }
      }
    nonTermination
  }

  /**
   * Sets the seed of the `TestRandom` instance in the environment to a random
   * value before each test.
   */
  val nondeterministic: TestAspect.WithOut[
    Nothing,
    Has[Live] with Has[TestRandom],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    before(
      Live
        .live(Clock.nanoTime(ZTraceElement.empty))(ZTraceElement.empty)
        .flatMap(TestRandom.setSeed(_)(ZTraceElement.empty))(ZTraceElement.empty)
    )

  /**
   * An aspect that executes the members of a suite in parallel.
   */
  val parallel: TestAspect.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    executionStrategy(ExecutionStrategy.Parallel)

  /**
   * An aspect that executes the members of a suite in parallel, up to the
   * specified number of concurrent fibers.
   */
  def parallelN(n: Int): TestAspect.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    executionStrategy(ExecutionStrategy.ParallelN(n))

  /**
   * An aspect that provides each test in the spec with its required
   * environment.
   */
  final def provide[R0](r: R0): TestAspect.WithOut[
    R0,
    Any,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Any })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new TestAspect[R0, Any, Nothing, Any] {
      type OutEnv[Env] = Any
      type OutErr[Err] = Err
      def apply[R >: R0, E](spec: ZSpec[R, E])(implicit trace: ZTraceElement): ZSpec[Any, E] =
        spec.provide(r)
    }

  /**
   * Provides each test with the part of the environment that is not part of the
   * `TestEnvironment`, leaving a spec that only depends on the
   * `TestEnvironment`.
   *
   * {{{
   * val loggingServiceBuilder: ZServiceBuilder[Any, Nothing, Logging] = ???
   *
   * val spec: ZSpec[TestEnvironment with Logging, Nothing] = ???
   *
   * val spec2 = spec.provideCustomService(loggingServiceBuilder)
   * }}}
   */
  def provideCustomService[E, R](serviceBuilder: ZServiceBuilder[TestEnvironment, TestFailure[E], R])(implicit
    ev2: Has.Union[TestEnvironment, R],
    tagged: Tag[R],
    trace: ZTraceElement
  ): TestAspect.WithOut[
    TestEnvironment with R,
    Any,
    E,
    Any,
    ({ type OutEnv[Env] = TestEnvironment })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    provideSomeService[TestEnvironment][E, R](serviceBuilder)

  /**
   * Provides each test with the part of the environment that is not part of the
   * `TestEnvironment`, leaving a spec that only depends on the
   * `TestEnvironment`.
   *
   * {{{
   * val loggingServiceBuilder: ZServiceBuilder[Any, Nothing, Logging] = ???
   *
   * val spec: ZSpec[TestEnvironment with Logging, Nothing] = ???
   *
   * val spec2 = spec.provideCustomService(loggingServiceBuilder)
   * }}}
   */
  def provideCustomServiceShared[E, R](serviceBuilder: ZServiceBuilder[TestEnvironment, TestFailure[E], R])(implicit
    ev2: Has.Union[TestEnvironment, R],
    tagged: Tag[R],
    trace: ZTraceElement
  ): TestAspect.WithOut[
    TestEnvironment with R,
    Any,
    E,
    Any,
    ({ type OutEnv[Env] = TestEnvironment })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    provideSomeServiceShared[TestEnvironment][E, R](serviceBuilder)

  /**
   * An aspect that provides a service builder to the spec, translating it up a
   * level.
   */
  final def provideService[R0, E1, R1](
    serviceBuilder: ZServiceBuilder[R0, TestFailure[E1], R1]
  ): TestAspect.WithOut[R1, Any, E1, Any, ({ type OutEnv[Env] = R0 })#OutEnv, ({ type OutErr[Err] = Err })#OutErr] =
    new TestAspect[R1, Any, E1, Any] {
      type OutEnv[Env] = R0
      type OutErr[Err] = Err
      def apply[R >: R1, E >: E1](spec: ZSpec[R, E])(implicit trace: ZTraceElement): ZSpec[R0, E] =
        spec.provideService(serviceBuilder)
    }

  /**
   * An aspect that provides a service builder to the spec, translating it up a
   * level.
   */
  final def provideServiceShared[R0, E1, R1](
    serviceBuilder: ZServiceBuilder[R0, TestFailure[E1], R1]
  ): TestAspect.WithOut[R1, Any, E1, Any, ({ type OutEnv[Env] = R0 })#OutEnv, ({ type OutErr[Err] = Err })#OutErr] =
    new TestAspect[R1, Any, E1, Any] {
      type OutEnv[Env] = R0
      type OutErr[Err] = Err
      def apply[R >: R1, E >: E1](spec: ZSpec[R, E])(implicit trace: ZTraceElement): ZSpec[R0, E] =
        spec.provideServiceShared(serviceBuilder)
    }

  /**
   * Uses the specified function to provide each test in this spec with part of
   * its required environment.
   */
  final def provideSome[R0, R1](f: R0 => R1): TestAspect.WithOut[
    R1,
    Any,
    Nothing,
    Any,
    ({ type OutEnv[Env] = R0 })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new TestAspect[R1, Any, Nothing, Any] {
      type OutEnv[Env] = R0
      type OutErr[Err] = Err
      def apply[R >: R1, E](spec: ZSpec[R, E])(implicit trace: ZTraceElement): ZSpec[R0, E] =
        spec.provideSome(f)
    }

  /**
   * Splits the environment into two parts, providing each test with one part
   * using the specified service builder and leaving the remainder `R0`.
   *
   * {{{
   * val clockServiceBuilder: ZServiceBuilder[Any, Nothing, Has[Clock]] = ???
   *
   * val spec: ZSpec[Has[Clock] with Has[Random], Nothing] = ???
   *
   * val spec2 = spec @@ provideSomeService[Has[Random]](clockServiceBuilder)
   * }}}
   */
  final def provideSomeService[R0]: TestAspect.ProvideSomeServiceBuilder[R0] =
    new TestAspect.ProvideSomeServiceBuilder[R0]

  /**
   * Splits the environment into two parts, providing all tests with a shared
   * version of one part using the specified service builder and leaving the
   * remainder `R0`.
   *
   * {{{
   * val clockServiceBuilder: ZServiceBuilder[Any, Nothing, Has[Clock]] = ???
   *
   * val spec: ZSpec[Has[Clock] with Has[Random], Nothing] = ???
   *
   * val spec2 = spec.provideSomeServiceShared[Has[Random]](clockServiceBuilder)
   * }}}
   */
  final def provideSomeServiceShared[R0]: TestAspect.ProvideSomeServiceBuilderShared[R0] =
    new TestAspect.ProvideSomeServiceBuilderShared[R0]

  /**
   * An aspect that repeats successful tests according to a schedule.
   */
  def repeat[R0 <: ZTestEnv with Has[Annotations] with Has[Live]](
    schedule: Schedule[R0, TestSuccess, Any]
  ): TestAspect.WithOut[
    Nothing,
    R0,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] = {
    val repeat: TestAspect.WithOut[
      Nothing,
      R0,
      Nothing,
      Any,
      ({ type OutEnv[Env] = Env })#OutEnv,
      ({ type OutErr[Err] = Err })#OutErr
    ] = new PerTest[Nothing, R0, Nothing, Any] {
      def perTest[R <: R0, E](
        test: ZIO[R, TestFailure[E], TestSuccess]
      )(implicit trace: ZTraceElement): ZIO[R, TestFailure[E], TestSuccess] =
        ZIO.accessZIO { r =>
          val repeatSchedule: Schedule[Any, TestSuccess, TestSuccess] =
            schedule
              .zipRight(Schedule.identity[TestSuccess])
              .tapOutput(_ => Annotations.annotate(TestAnnotation.repeated, 1))
              .provide(r)
          Live.live(test.provide(r).repeat(repeatSchedule))
        }
    }
    val restore = restoreTestEnvironment >>> repeat
    restore
  }

  /**
   * An aspect that runs each test with the number of times to repeat tests to
   * ensure they are stable set to the specified value.
   */
  def repeats(n: Int): TestAspect.WithOut[
    Nothing,
    Has[TestConfig],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new PerTest[Nothing, Has[TestConfig], Nothing, Any] {
      def perTest[R <: Has[TestConfig], E](
        test: ZIO[R, TestFailure[E], TestSuccess]
      )(implicit trace: ZTraceElement): ZIO[R, TestFailure[E], TestSuccess] =
        test.updateService[TestConfig] { old =>
          new TestConfig {
            val repeats = n
            val retries = old.retries
            val samples = old.samples
            val shrinks = old.shrinks
          }
        }
    }

  /**
   * An aspect that restores a given [[zio.test.Restorable Restorable]]'s state
   * to its starting state after the test is run. Note that this is only useful
   * when repeating tests.
   */
  def restore[R0](service: R0 => Restorable): TestAspect.WithOut[
    Nothing,
    R0,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    aroundWith(ZIO.accessZIO[R0](r => service(r).save(ZTraceElement.empty))(ZTraceElement.empty))(restore => restore)

  /**
   * An aspect that restores the [[zio.test.TestClock TestClock]]'s state to its
   * starting state after the test is run. Note that this is only useful when
   * repeating tests.
   */
  def restoreTestClock: TestAspect.WithOut[
    Nothing,
    Has[TestClock],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    restore[Has[TestClock]](_.get)

  /**
   * An aspect that restores the [[zio.test.TestConsole TestConsole]]'s state to
   * its starting state after the test is run. Note that this is only useful
   * when repeating tests.
   */
  def restoreTestConsole: TestAspect.WithOut[
    Nothing,
    Has[TestConsole],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    restore[Has[TestConsole]](_.get)

  /**
   * An aspect that restores the [[zio.test.TestRandom TestRandom]]'s state to
   * its starting state after the test is run. Note that this is only useful
   * when repeating tests.
   */
  def restoreTestRandom: TestAspect.WithOut[
    Nothing,
    Has[TestRandom],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    restore[Has[TestRandom]](_.get)

  /**
   * An aspect that restores the [[zio.test.TestSystem TestSystem]]'s state to
   * its starting state after the test is run. Note that this is only useful
   * when repeating tests.
   */
  def restoreTestSystem: TestAspect.WithOut[
    Nothing,
    Has[TestSystem],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    restore[Has[TestSystem]](_.get)

  /**
   * An aspect that restores all state in the standard provided test
   * environments ([[zio.test.TestClock TestClock]],
   * [[zio.test.TestConsole TestConsole]], [[zio.test.TestRandom TestRandom]],
   * and [[zio.test.TestSystem TestSystem]]) to their starting state after the
   * test is run. Note that this is only useful when repeating tests.
   */
  def restoreTestEnvironment: TestAspect.WithOut[
    Nothing,
    ZTestEnv,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] = {
    val restoreTestEnvironment = restoreTestClock >>> restoreTestConsole >>> restoreTestRandom >>> restoreTestSystem
    restoreTestEnvironment
  }

  /**
   * An aspect that runs each test with the number of times to retry flaky tests
   * set to the specified value.
   */
  def retries(n: Int): TestAspect.WithOut[
    Nothing,
    Has[TestConfig],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new PerTest[Nothing, Has[TestConfig], Nothing, Any] {
      def perTest[R <: Has[TestConfig], E](
        test: ZIO[R, TestFailure[E], TestSuccess]
      )(implicit trace: ZTraceElement): ZIO[R, TestFailure[E], TestSuccess] =
        test.updateService[TestConfig] { old =>
          new TestConfig {
            val repeats = old.repeats
            val retries = n
            val samples = old.samples
            val shrinks = old.shrinks
          }
        }
    }

  /**
   * An aspect that retries failed tests according to a schedule.
   */
  def retry[R0 <: ZTestEnv with Has[Annotations] with Has[Live], E0](
    schedule: Schedule[R0, TestFailure[E0], Any]
  ): TestAspect.WithOut[
    Nothing,
    R0,
    Nothing,
    E0,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] = {
    val retry: TestAspect.WithOut[
      Nothing,
      R0,
      Nothing,
      E0,
      ({ type OutEnv[Env] = Env })#OutEnv,
      ({ type OutErr[Err] = Err })#OutErr
    ] =
      new TestAspect.PerTest[Nothing, R0, Nothing, E0] {
        def perTest[R <: R0, E <: E0](
          test: ZIO[R, TestFailure[E], TestSuccess]
        )(implicit trace: ZTraceElement): ZIO[R, TestFailure[E], TestSuccess] =
          ZIO.accessZIO[R] { r =>
            val retrySchedule: Schedule[Any, TestFailure[E0], Any] =
              schedule
                .tapOutput(_ => Annotations.annotate(TestAnnotation.retried, 1))
                .provide(r)
            Live.live(test.provide(r).retry(retrySchedule))
          }
      }
    val restore = restoreTestEnvironment >>> retry
    restore
  }

  /**
   * As aspect that runs each test with the specified `RuntimeConfigAspect`.
   */
  def runtimeConfig(runtimeConfigAspect: RuntimeConfigAspect): TestAspect.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new PerTest[Nothing, Any, Nothing, Any] {
      def perTest[R, E](test: ZIO[R, TestFailure[E], TestSuccess])(implicit
        trace: ZTraceElement
      ): ZIO[R, TestFailure[E], TestSuccess] =
        ZIO.runtimeConfig.flatMap(runtimeConfig => test.withRuntimeConfig(runtimeConfigAspect(runtimeConfig)))
    }

  /**
   * An aspect that runs each test with the number of sufficient samples to
   * check for a random variable set to the specified value.
   */
  def samples(n: Int): TestAspect.WithOut[
    Nothing,
    Has[TestConfig],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new PerTest[Nothing, Has[TestConfig], Nothing, Any] {
      def perTest[R <: Has[TestConfig], E](
        test: ZIO[R, TestFailure[E], TestSuccess]
      )(implicit trace: ZTraceElement): ZIO[R, TestFailure[E], TestSuccess] =
        test.updateService[TestConfig] { old =>
          new TestConfig {
            val repeats = old.repeats
            val retries = old.retries
            val samples = n
            val shrinks = old.shrinks
          }
        }
    }

  /**
   * An aspect that executes the members of a suite sequentially.
   */
  val sequential: TestAspect.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    executionStrategy(ExecutionStrategy.Sequential)

  /**
   * An aspect that applies the specified aspect on Scala 2.
   */
  def scala2[LowerR, UpperR, LowerE, UpperE](
    that: TestAspect.WithOut[
      LowerR,
      UpperR,
      LowerE,
      UpperE,
      ({ type OutEnv[Env] = Env })#OutEnv,
      ({ type OutErr[Err] = Err })#OutErr
    ]
  ): TestAspect.WithOut[
    LowerR,
    UpperR,
    LowerE,
    UpperE,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    if (TestVersion.isScala2) that else identity

  /**
   * An aspect that applies the specified aspect on Scala 2.11.
   */
  def scala211[LowerR, UpperR, LowerE, UpperE](
    that: TestAspect.WithOut[
      LowerR,
      UpperR,
      LowerE,
      UpperE,
      ({ type OutEnv[Env] = Env })#OutEnv,
      ({ type OutErr[Err] = Err })#OutErr
    ]
  ): TestAspect.WithOut[
    LowerR,
    UpperR,
    LowerE,
    UpperE,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    if (TestVersion.isScala211) that else identity

  /**
   * An aspect that applies the specified aspect on Scala 2.12.
   */
  def scala212[LowerR, UpperR, LowerE, UpperE](
    that: TestAspect.WithOut[
      LowerR,
      UpperR,
      LowerE,
      UpperE,
      ({ type OutEnv[Env] = Env })#OutEnv,
      ({ type OutErr[Err] = Err })#OutErr
    ]
  ): TestAspect.WithOut[
    LowerR,
    UpperR,
    LowerE,
    UpperE,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    if (TestVersion.isScala212) that else identity

  /**
   * An aspect that applies the specified aspect on Scala 2.13.
   */
  def scala213[LowerR, UpperR, LowerE, UpperE](
    that: TestAspect.WithOut[
      LowerR,
      UpperR,
      LowerE,
      UpperE,
      ({ type OutEnv[Env] = Env })#OutEnv,
      ({ type OutErr[Err] = Err })#OutErr
    ]
  ): TestAspect.WithOut[
    LowerR,
    UpperR,
    LowerE,
    UpperE,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    if (TestVersion.isScala213) that else identity

  /**
   * An aspect that only runs tests on Scala 2.
   */
  val scala2Only: TestAspect.WithOut[
    Nothing,
    Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    if (TestVersion.isScala2) identity else ignore

  /**
   * An aspect that only runs tests on Scala 2.11.
   */
  val scala211Only: TestAspect.WithOut[
    Nothing,
    Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    if (TestVersion.isScala211) identity else ignore

  /**
   * An aspect that only runs tests on Scala 2.12.
   */
  val scala212Only: TestAspect.WithOut[
    Nothing,
    Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    if (TestVersion.isScala212) identity else ignore

  /**
   * An aspect that only runs tests on Scala 2.13.
   */
  val scala213Only: TestAspect.WithOut[
    Nothing,
    Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    if (TestVersion.isScala213) identity else ignore

  /**
   * Sets the seed of the `TestRandom` instance in the environment to the
   * specified value before each test.
   */
  def setSeed(seed: => Long): TestAspect.WithOut[
    Nothing,
    Has[TestRandom],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    before(TestRandom.setSeed(seed)(ZTraceElement.empty))

  /**
   * An aspect that runs each test with the maximum number of shrinkings to
   * minimize large failures set to the specified value.
   */
  def shrinks(n: Int): TestAspect.WithOut[
    Nothing,
    Has[TestConfig],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new PerTest[Nothing, Has[TestConfig], Nothing, Any] {
      def perTest[R <: Has[TestConfig], E](
        test: ZIO[R, TestFailure[E], TestSuccess]
      )(implicit trace: ZTraceElement): ZIO[R, TestFailure[E], TestSuccess] =
        test.updateService[TestConfig] { old =>
          new TestConfig {
            val repeats = old.repeats
            val retries = old.retries
            val samples = old.samples
            val shrinks = n
          }
        }
    }

  /**
   * An aspect that runs each test with the [[zio.test.TestConsole TestConsole]]
   * instance in the environment set to silent mode so that console output is
   * only written to the output buffer and not rendered to standard output.
   */
  val silent: TestAspect.WithOut[
    Nothing,
    Has[TestConsole],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new PerTest[Nothing, Has[TestConsole], Nothing, Any] {
      def perTest[R <: Has[TestConsole], E](
        test: ZIO[R, TestFailure[E], TestSuccess]
      )(implicit trace: ZTraceElement): ZIO[R, TestFailure[E], TestSuccess] =
        TestConsole.silent(test)
    }

  /**
   * An aspect that runs each test with the size set to the specified value.
   */
  def sized(n: Int): TestAspect.WithOut[
    Nothing,
    Has[Sized],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new PerTest[Nothing, Has[Sized], Nothing, Any] {
      def perTest[R <: Has[Sized], E](test: ZIO[R, TestFailure[E], TestSuccess])(implicit
        trace: ZTraceElement
      ): ZIO[R, TestFailure[E], TestSuccess] =
        Sized.withSize(n)(test)
    }

  /**
   * An aspect that converts ignored tests into test failures.
   */
  val success: TestAspect.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new PerTest[Nothing, Any, Nothing, Any] {
      def perTest[R, E](
        test: ZIO[R, TestFailure[E], TestSuccess]
      )(implicit trace: ZTraceElement): ZIO[R, TestFailure[E], TestSuccess] =
        test.flatMap {
          case TestSuccess.Ignored =>
            ZIO.fail(TestFailure.Runtime(Cause.die(new RuntimeException("Test was ignored."))))
          case x => ZIO.succeedNow(x)
        }
    }

  /**
   * Annotates tests with string tags.
   */
  def tag(tag: String, tags: String*): TestAspect.WithOut[
    Nothing,
    Any,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    annotate(TestAnnotation.tagged, Set(tag) union tags.toSet)

  /**
   * Annotates tests with their execution times.
   */
  val timed: TestAspect.WithOut[
    Nothing,
    Has[Live] with Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new TestAspect.PerTest[Nothing, Has[Live] with Has[Annotations], Nothing, Any] {
      def perTest[R <: Has[Live] with Has[Annotations], E](
        test: ZIO[R, TestFailure[E], TestSuccess]
      )(implicit trace: ZTraceElement): ZIO[R, TestFailure[E], TestSuccess] =
        Live.withLive(test)(_.either.timed).flatMap { case (duration, result) =>
          ZIO.fromEither(result).ensuring(Annotations.annotate(TestAnnotation.timing, duration))
        }
    }

  /**
   * An aspect that times out tests using the specified duration.
   * @param duration
   *   maximum test duration
   */
  def timeout(
    duration: Duration
  ): TestAspect.WithOut[
    Nothing,
    Has[Live],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new PerTest[Nothing, Has[Live], Nothing, Any] {
      def perTest[R <: Has[Live], E](
        test: ZIO[R, TestFailure[E], TestSuccess]
      )(implicit trace: ZTraceElement): ZIO[R, TestFailure[E], TestSuccess] = {
        def timeoutFailure =
          TestTimeoutException(s"Timeout of ${duration.render} exceeded.")
        Live
          .withLive(test)(_.either.disconnect.timeout(duration).flatMap {
            case None         => ZIO.fail(TestFailure.Runtime(Cause.die(timeoutFailure)))
            case Some(result) => ZIO.fromEither(result)
          })
      }
    }

  /**
   * Verifies the specified post-condition after each test is run.
   */
  def verify[R0, E0](condition: => ZIO[R0, E0, TestResult]): TestAspect.WithOut[
    Nothing,
    R0,
    E0,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    new TestAspect.PerTest[Nothing, R0, E0, Any] {
      def perTest[R <: R0, E >: E0](test: ZIO[R, TestFailure[E], TestSuccess])(implicit
        trace: ZTraceElement
      ): ZIO[R, TestFailure[E], TestSuccess] =
        test <* ZTest("verify", condition)
    }

  /**
   * Runs only on Unix / Linux operating systems.
   */
  val unix: TestAspect.WithOut[
    Nothing,
    Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    os(_.isUnix)

  /**
   * Runs only on Windows operating systems.
   */
  val windows: TestAspect.WithOut[
    Nothing,
    Has[Annotations],
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ] =
    os(_.isWindows)

  abstract class PerTest[+LowerR, -UpperR, +LowerE, -UpperE] extends TestAspect[LowerR, UpperR, LowerE, UpperE] {
    type OutEnv[Env] = Env
    type OutErr[Err] = Err

    def perTest[R >: LowerR <: UpperR, E >: LowerE <: UpperE](
      test: ZIO[R, TestFailure[E], TestSuccess]
    )(implicit trace: ZTraceElement): ZIO[R, TestFailure[E], TestSuccess]

    final def apply[R >: LowerR <: UpperR, E >: LowerE <: UpperE](
      spec: Spec[R, TestFailure[E], TestSuccess]
    )(implicit trace: ZTraceElement): Spec[R, TestFailure[E], TestSuccess] =
      spec.transform[R, TestFailure[E], TestSuccess] {
        case Spec.TestCase(test, annotations) => Spec.TestCase(perTest(test), annotations)
        case c                                => c
      }
  }

  final class ProvideSomeServiceBuilder[R0](private val dummy: Boolean = true) extends AnyVal {
    def apply[E1, R1](
      serviceBuilder: ZServiceBuilder[R0, TestFailure[E1], R1]
    )(implicit
      ev: Has.Union[R0, R1],
      tagged: Tag[R1],
      trace: ZTraceElement
    ): TestAspect.WithOut[
      R0 with R1,
      Any,
      E1,
      Any,
      ({ type OutEnv[Env] = R0 })#OutEnv,
      ({ type OutErr[Err] = Err })#OutErr
    ] =
      new TestAspect[R0 with R1, Any, E1, Any] {
        type OutEnv[Env] = R0
        type OutErr[Err] = Err
        def apply[R >: R0 with R1, E >: E1](
          spec: Spec[R, TestFailure[E], TestSuccess]
        )(implicit trace: ZTraceElement): Spec[R0, TestFailure[E], TestSuccess] =
          spec.provideService[TestFailure[E], R0, R0 with R1](ZServiceBuilder.environment[R0] ++ serviceBuilder)
      }
  }

  final class ProvideSomeServiceBuilderShared[R0](private val dummy: Boolean = true) extends AnyVal {
    def apply[E1, R1](
      serviceBuilder: ZServiceBuilder[R0, TestFailure[E1], R1]
    )(implicit
      ev2: Has.Union[R0, R1],
      tagged: Tag[R1],
      trace: ZTraceElement
    ): TestAspect.WithOut[
      R0 with R1,
      Any,
      E1,
      Any,
      ({ type OutEnv[Env] = R0 })#OutEnv,
      ({ type OutErr[Err] = Err })#OutErr
    ] =
      new TestAspect[R0 with R1, Any, E1, Any] {
        type OutEnv[Env] = R0
        type OutErr[Err] = Err
        def apply[R >: R0 with R1, E >: E1](
          spec: Spec[R, TestFailure[E], TestSuccess]
        )(implicit trace: ZTraceElement): Spec[R0, TestFailure[E], TestSuccess] =
          spec.provideServiceShared[TestFailure[E], R0, R0 with R1](ZServiceBuilder.environment[R0] ++ serviceBuilder)
      }
  }
}
