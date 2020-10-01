package zio.test.experimental

import scala.annotation.unchecked.uncheckedVariance

import zio._
import zio.duration._
import zio.test._
import zio.test.environment._

trait TestAspect[+EnvIn, -ErrIn, -EnvOut, +ErrOut] { self =>
  type ModifyEnv[+_ >: EnvIn] >: EnvOut
  type ModifyErr[+_ <: ErrIn] <: ErrOut

  /**
   * Applies the aspect to some tests in the spec, chosen by the provided
   * predicate.
   */
  def some[R >: EnvIn, E <: ErrIn](predicate: String => Boolean, spec: ZSpec[R, E]): ZSpec[ModifyEnv[R], ModifyErr[E]]

  /**
   * An alias for [[all]].
   */
  def apply[R >: EnvIn, E <: ErrIn](spec: ZSpec[R, E]): ZSpec[ModifyEnv[R], ModifyErr[E]] =
    all(spec)

  /**
   * Applies the aspect to every test in the spec.
   */
  def all[R >: EnvIn, E <: ErrIn](spec: ZSpec[R, E]): ZSpec[ModifyEnv[R], ModifyErr[E]] =
    some(_ => true, spec)

  /**
   * Returns a new aspect that represents the sequential composition of this
   * aspect with the specified one.
   */
  def >>>[EnvOut1, ErrOut1](that: TestAspect[EnvOut, ErrOut, EnvOut1, ErrOut1]): TestAspect.Aux[
    EnvIn,
    ErrIn,
    EnvOut1,
    ErrOut1,
    ({ type Apply[+R >: EnvIn @uncheckedVariance] = that.ModifyEnv[self.ModifyEnv[R]] })#Apply,
    ({ type Apply[+E <: ErrIn @uncheckedVariance] = that.ModifyErr[self.ModifyErr[E]] })#Apply
  ] =
    new TestAspect[EnvIn, ErrIn, EnvOut1, ErrOut1] {
      type ModifyEnv[+R >: EnvIn] = that.ModifyEnv[self.ModifyEnv[R]]
      type ModifyErr[+E <: ErrIn] = that.ModifyErr[self.ModifyErr[E]]

      def some[R >: EnvIn, E <: ErrIn](
        predicate: String => Boolean,
        spec: ZSpec[R, E]
      ): ZSpec[ModifyEnv[R], ModifyErr[E]] = {
        val spec2: ZSpec[self.ModifyEnv[R], self.ModifyErr[E]] = self.some(predicate, spec)

        that.some[self.ModifyEnv[R], self.ModifyErr[E]](predicate, spec2)
      }
    }

  def andThen[EnvOut1, ErrOut1](that: TestAspect[EnvOut, ErrOut, EnvOut1, ErrOut1]): TestAspect.Aux[
    EnvIn,
    ErrIn,
    EnvOut1,
    ErrOut1,
    ({ type Apply[+R >: EnvIn @uncheckedVariance] = that.ModifyEnv[self.ModifyEnv[R]] })#Apply,
    ({ type Apply[+E <: ErrIn @uncheckedVariance] = that.ModifyErr[self.ModifyErr[E]] })#Apply
  ] =
    self >>> that
}

object TestAspect {
  type Aux[+EnvIn, -ErrIn, -EnvOut, +ErrOut, ModifyEnv1[+_ >: EnvIn] >: EnvOut, ModifyErr1[+_ <: ErrIn] <: ErrOut] =
    TestAspect[EnvIn, ErrIn, EnvOut, ErrOut] {
      type ModifyEnv[+R >: EnvIn] = ModifyEnv1[R]
      type ModifyErr[+E <: ErrIn] = ModifyErr1[E]
    }

  /**
   * An aspect that returns the tests unchanged
   */
  val identity: TestAspectPoly =
    new TestAspect[Nothing, Any, Nothing, Any] {
      type ModifyEnv[+R] = R
      type ModifyErr[+E] = E

      def some[R, E](predicate: String => Boolean, spec: ZSpec[R, E]): ZSpec[R, E] =
        spec
    }

  /**
   * An aspect that marks tests as ignored.
   */
  val ignore: TestAspectAtLeastR[Annotations] =
    new TestAspect[Nothing, Any, Nothing, Any] {
      type ModifyEnv[+R] = R with Annotations
      type ModifyErr[+E] = E

      def some[R, E](predicate: String => Boolean, spec: ZSpec[R, E]): ZSpec[R with Annotations, E] =
        spec.when(false)
    }

  /**
   * An aspect that marks tests as ignored.
   */
  def after[R, E](effect: ZIO[R, E, Any]): TestAspect.Aux[
    Nothing,
    E,
    Nothing,
    E,
    ({ type Apply[+R1] = R with R1 })#Apply,
    ({ type Apply[+E1 <: E] = E })#Apply
  ] =
    new TestAspect[Nothing, E, Nothing, E] {
      type ModifyEnv[+R1]      = R with R1
      type ModifyErr[+E1 <: E] = E

      def some[R1, E1 <: E](predicate: String => Boolean, spec: ZSpec[R1, E1]): ZSpec[R with R1, E] =
        spec.transform[R with R1, TestFailure[E], TestSuccess] {
          case c @ Spec.SuiteCase(_, _, _) => c
          case Spec.TestCase(label, test, annotations) =>
            val after: ZIO[R with R1, TestFailure[E], TestSuccess] =
              test.run
                .zipWith(effect.catchAllCause(cause => ZIO.fail(TestFailure.Runtime(cause))).run)(_ <* _)
                .flatMap(ZIO.done(_))
            Spec.TestCase(label, if (predicate(label)) after else test, annotations)
        }
    }

  /**
   * An aspect that marks tests as ignored.
   */
  def annotate[V](key: TestAnnotation[V], value: V): TestAspectPoly =
    new TestAspect[Nothing, Any, Nothing, Any] {
      type ModifyEnv[+R] = R
      type ModifyErr[+E] = E

      def some[R, E](predicate: String => Boolean, spec: ZSpec[R, E]): ZSpec[R, E] =
        spec.annotate(key, value)
    }

  /**
   * An aspect that marks tests as ignored.
   */
  def around[R, E, A](before: ZIO[R, E, A])(after: A => ZIO[R, Nothing, Any]): TestAspect.Aux[
    Nothing,
    E,
    Nothing,
    E,
    ({ type Apply[+R1] = R with R1 })#Apply,
    ({ type Apply[+E1 <: E] = E })#Apply
  ] =
    new TestAspect[Nothing, E, Nothing, E] {
      type ModifyEnv[+R1]      = R with R1
      type ModifyErr[+E1 <: E] = E

      def some[R1, E1 <: E](predicate: String => Boolean, spec: ZSpec[R1, E1]): ZSpec[R with R1, E] =
        spec.transform[R with R1, TestFailure[E], TestSuccess] {
          case c @ Spec.SuiteCase(_, _, _) => c
          case Spec.TestCase(label, test, annotations) =>
            val around: ZIO[R with R1, TestFailure[E], TestSuccess] =
              before.catchAllCause(c => ZIO.fail(TestFailure.Runtime(c))).bracket(after)(_ => test)
            Spec.TestCase(label, if (predicate(label)) around else test, annotations)
        }
    }

  /**
   * A less powerful variant of `around` where the result of `before` is not
   * required by after.
   */
  def around_[R, E](before: ZIO[R, E, Any], after: ZIO[R, Nothing, Any]): TestAspect.Aux[
    Nothing,
    E,
    Nothing,
    E,
    ({ type Apply[+R1] = R with R1 })#Apply,
    ({ type Apply[+E1 <: E] = E })#Apply
  ] =
    around(before)(_ => after)

  /**
   * An aspect that marks tests as ignored.
   */
  def aroundTest[R, E](
    managed: ZManaged[R, TestFailure[E], TestSuccess => ZIO[R, TestFailure[E], TestSuccess]]
  ): TestAspect.Aux[
    Nothing,
    E,
    Nothing,
    E,
    ({ type Apply[+R1] = R with R1 })#Apply,
    ({ type Apply[+E1 <: E] = E })#Apply
  ] =
    new TestAspect[Nothing, E, Nothing, E] {
      type ModifyEnv[+R1]      = R with R1
      type ModifyErr[+E1 <: E] = E

      def some[R1, E1 <: E](predicate: String => Boolean, spec: ZSpec[R1, E1]): ZSpec[R with R1, E] =
        spec.transform[R with R1, TestFailure[E], TestSuccess] {
          case c @ Spec.SuiteCase(_, _, _) => c
          case Spec.TestCase(label, test, annotations) =>
            val aroundTest: ZIO[R with R1, TestFailure[E], TestSuccess] =
              managed.use(f => test.flatMap(f))
            Spec.TestCase(label, if (predicate(label)) aroundTest else test, annotations)
        }
    }

  /**
   * Constructs a simple monomorphic aspect that only works with the specified
   * environment and error type.
   */
  def aspect[R, E](
    f: ZIO[R, TestFailure[E], TestSuccess] => ZIO[R, TestFailure[E], TestSuccess]
  ): TestAspect.Aux[
    R,
    E,
    R,
    E,
    ({ type Apply[+R1 >: R] = R with R1 })#Apply,
    ({ type Apply[+E1 <: E] = E })#Apply
  ] =
    new TestAspect[R, E, R, E] {
      type ModifyEnv[+R1 >: R] = R with R1
      type ModifyErr[+E1 <: E] = E

      def some[R1 >: R, E1 <: E](predicate: String => Boolean, spec: ZSpec[R1, E1]): ZSpec[R with R1, E] =
        spec.transform[R with R1, TestFailure[E], TestSuccess] {
          case c @ Spec.SuiteCase(_, _, _) => c
          case Spec.TestCase(label, test, annotations) =>
            val aspect: ZIO[R with R1, TestFailure[E], TestSuccess] =
              f(test)
            Spec.TestCase(label, if (predicate(label)) aspect else test, annotations)
        }
    }

  /**
   * An aspect that marks tests as ignored.
   */
  def before[R, E](before: ZIO[R, E, Any]): TestAspect.Aux[
    Nothing,
    E,
    Nothing,
    E,
    ({ type Apply[+R1] = R with R1 })#Apply,
    ({ type Apply[+E1 <: E] = E })#Apply
  ] =
    around(before)(_ => ZIO.unit)

  /**
   * An aspect that runs each test on a separate fiber and prints a fiber dump
   * if the test fails or has not terminated within the specified duration.
   */
  def diagnose(duration: Duration): TestAspectAtLeastR[Live with Annotations] =
    new TestAspect[Nothing, Any, Nothing, Any] {
      type ModifyEnv[+R] = R with Live with Annotations
      type ModifyErr[+E] = E

      def some[R, E](predicate: String => Boolean, spec: ZSpec[R, E]): ZSpec[R with Live with Annotations, E] = {
        def diagnose[R <: Live with Annotations, E](
          label: String,
          test: ZIO[R, TestFailure[E], TestSuccess]
        ): ZIO[R, TestFailure[E], TestSuccess] =
          test.fork.flatMap { fiber =>
            fiber.join.raceWith[R, TestFailure[E], TestFailure[E], Unit, TestSuccess](Live.live(ZIO.sleep(duration)))(
              (exit, sleepFiber) => dump(label).when(!exit.succeeded) *> sleepFiber.interrupt *> ZIO.done(exit),
              (_, _) => dump(label) *> fiber.join
            )
          }
        def dump[E, A](label: String): URIO[Live with Annotations, Unit] =
          Annotations.supervisedFibers.flatMap(fibers => Live.live(Fiber.putDumpStr(label, fibers.toSeq: _*)))
        spec.transform[R with Live with Annotations, TestFailure[E], TestSuccess] {
          case c @ Spec.SuiteCase(_, _, _) => c
          case Spec.TestCase(label, test, annotations) =>
            Spec.TestCase(label, if (predicate(label)) diagnose(label, test) else test, annotations)
        }
      }
    }

  /**
   * An aspect that runs each test with the `TestConsole` instance in the
   * environment set to debug mode so that console output is rendered to
   * standard output in addition to being written to the output buffer.
   */
  val debug: TestAspectAtLeastR[TestConsole] =
    new TestAspect[Nothing, Any, Nothing, Any] {
      type ModifyEnv[+R] = R with TestConsole
      type ModifyErr[+E] = E

      def some[R, E](predicate: String => Boolean, spec: ZSpec[R, E]): ZSpec[R with TestConsole, E] =
        spec.transform[R with TestConsole, TestFailure[E], TestSuccess] {
          case c @ Spec.SuiteCase(_, _, _) => c
          case Spec.TestCase(label, test, annotations) =>
            val debug: ZIO[R with TestConsole, TestFailure[E], TestSuccess] =
              TestConsole.debug(test)
            Spec.TestCase(label, if (predicate(label)) debug else test, annotations)
        }
    }

  /**
   * An aspect that applies the specified aspect on Dotty.
   */
  def dotty[LowerR, UpperR, LowerE, UpperE](
    that: TestAspect[LowerR, UpperR, LowerE, UpperE]
  ): TestAspect[LowerR, UpperR, LowerE, UpperE] =
    if (TestVersion.isDotty) that else ???

  /**
   * An aspect that marks tests as ignored.
   */
  val dottyOnly: TestAspectAtLeastR[Annotations] =
    if (TestVersion.isDotty) ??? else ignore

  /**
   * An aspect that retries a test until success, without limit.
   */
  val eventually: TestAspectAtLeastR[ZTestEnv] =
    new TestAspect[Nothing, Any, Nothing, Any] {
      type ModifyEnv[+R] = R with ZTestEnv
      type ModifyErr[+E] = E

      def some[R, E](predicate: String => Boolean, spec: ZSpec[R, E]): ZSpec[R with ZTestEnv, E] =
        spec.transform[R with ZTestEnv, TestFailure[E], TestSuccess] {
          case c @ Spec.SuiteCase(_, _, _) => c
          case Spec.TestCase(label, test, annotations) =>
            val debug: ZIO[R with ZTestEnv, TestFailure[E], TestSuccess] =
              test.eventually
            Spec.TestCase(label, if (predicate(label)) debug else test, annotations)
        }
    }

  /**
   * An aspect that retries a test until success, without limit.
   */
  def restore[R](service: R => Restorable): TestAspectAtLeastR[R] =
    ??? //around(ZIO.accessM[R](r => service(r).save))(restore => restore)

  /**
   * An aspect that restores the
   * [[zio.test.environment.TestClock TestClock]]'s state to its starting
   * state after the test is run. Note that this is only useful when repeating
   * tests.
   */
  def restoreTestClock: TestAspectAtLeastR[TestClock] =
    restore[TestClock](_.get)

  /**
   * An aspect that restores the
   * [[zio.test.environment.TestClock TestClock]]'s state to its starting
   * state after the test is run. Note that this is only useful when repeating
   * tests.
   */
  def restoreTestConsole: TestAspectAtLeastR[TestConsole] =
    restore[TestConsole](_.get)

  /**
   * An aspect that restores the
   * [[zio.test.environment.TestClock TestClock]]'s state to its starting
   * state after the test is run. Note that this is only useful when repeating
   * tests.
   */
  def restoreTestRandom: TestAspectAtLeastR[TestRandom] =
    restore[TestRandom](_.get)

  /**
   * An aspect that restores the
   * [[zio.test.environment.TestClock TestClock]]'s state to its starting
   * state after the test is run. Note that this is only useful when repeating
   * tests.
   */
  def restoreTestSystem: TestAspectAtLeastR[TestSystem] =
    restore[TestSystem](_.get)

  /**
   * An aspect that restores the
   * [[zio.test.environment.TestClock TestClock]]'s state to its starting
   * state after the test is run. Note that this is only useful when repeating
   * tests.
   */
  def restoreTestEnvironment: TestAspectAtLeastR[ZTestEnv] =
    restoreTestClock >>>[Nothing, Any] restoreTestConsole >>>[Nothing, Any] restoreTestRandom >>>[Nothing, Any] restoreTestSystem
}
