/*
 * Copyright 2019-2020 John A. De Goes and the ZIO Contributors
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

package zio.test.experimental

import scala.annotation.unchecked.uncheckedVariance

import zio._
import zio.duration._
import zio.test.Spec.{ SuiteCase, TestCase }
import zio.test.environment._
import zio.test.{ TestAspectAtLeastR => _, TestAspectPoly => _, _ }

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
   * Constructs an aspect that runs the specified effect after every test.
   */
  def after[R0, E0](effect: ZIO[R0, E0, Any]): TestAspect.Aux[
    Nothing,
    E0,
    Nothing,
    E0,
    ({ type Apply[+R] = R with R0 })#Apply,
    ({ type Apply[+E <: E0] = E0 })#Apply
  ] =
    new TestAspect[Nothing, E0, Nothing, E0] {
      type ModifyEnv[+R]       = R with R0
      type ModifyErr[+E <: E0] = E0

      def after[R, E <: E0, A](
        test: ZIO[R, TestFailure[E], TestSuccess]
      ): ZIO[R with R0, TestFailure[E0], TestSuccess] =
        test.run
          .zipWith(effect.catchAllCause(cause => ZIO.fail(TestFailure.Runtime(cause))).run)(_ <* _)
          .flatMap(ZIO.done(_))

      def some[R, E <: E0](predicate: String => Boolean, spec: ZSpec[R, E]): ZSpec[R with R0, E0] =
        spec.transform[R with R0, TestFailure[E0], TestSuccess] {
          case c @ Spec.SuiteCase(_, _, _) => c
          case Spec.TestCase(label, test, annotations) =>
            Spec.TestCase(label, if (predicate(label)) after(test) else test, annotations)
        }
    }

  /**
   * Annotates tests with the specified test annotation.
   */
  def annotate[V](key: TestAnnotation[V], value: V): TestAspectPoly =
    new TestAspect[Nothing, Any, Nothing, Any] {
      type ModifyEnv[+R] = R
      type ModifyErr[+E] = E

      def some[R, E](predicate: String => Boolean, spec: ZSpec[R, E]): ZSpec[R, E] =
        spec.annotate(key, value)
    }

  /**
   * Constructs an aspect that evaluates every test between two effects,
   * `before` and `after`,  where the result of `before` can be used in
   * `after`.
   */
  def around[R0, E0, A0](before: ZIO[R0, E0, A0])(after: A0 => ZIO[R0, Nothing, Any]): TestAspect.Aux[
    Nothing,
    E0,
    Nothing,
    E0,
    ({ type Apply[+R] = R with R0 })#Apply,
    ({ type Apply[+E <: E0] = E0 })#Apply
  ] =
    new TestAspect[Nothing, E0, Nothing, E0] {
      type ModifyEnv[+R]       = R with R0
      type ModifyErr[+E <: E0] = E0

      def around[R, E <: E0](test: ZIO[R, TestFailure[E], TestSuccess]): ZIO[R with R0, TestFailure[E0], TestSuccess] =
        before.catchAllCause(c => ZIO.fail(TestFailure.Runtime(c))).bracket(after)(_ => test)

      def some[R, E <: E0](predicate: String => Boolean, spec: ZSpec[R, E]): ZSpec[R with R0, E0] =
        spec.transform[R with R0, TestFailure[E0], TestSuccess] {
          case c @ Spec.SuiteCase(_, _, _) => c
          case Spec.TestCase(label, test, annotations) =>
            Spec.TestCase(label, if (predicate(label)) around(test) else test, annotations)
        }
    }

  /**
   * A less powerful variant of `around` where the result of `before` is not
   * required by after.
   */
  def around_[R0, E0](before: ZIO[R0, E0, Any], after: ZIO[R0, Nothing, Any]): TestAspect.Aux[
    Nothing,
    E0,
    Nothing,
    E0,
    ({ type Apply[+R] = R with R0 })#Apply,
    ({ type Apply[+E <: E0] = E0 })#Apply
  ] =
    around(before)(_ => after)

  /**
   * Constructs an aspect that evaluates every test inside the context of the
   * managed function.
   */
  def aroundTest[R0, E0](
    managed: ZManaged[R0, TestFailure[E0], TestSuccess => ZIO[R0, TestFailure[E0], TestSuccess]]
  ): TestAspect.Aux[
    Nothing,
    E0,
    Nothing,
    E0,
    ({ type Apply[+R] = R with R0 })#Apply,
    ({ type Apply[+E <: E0] = E0 })#Apply
  ] =
    new TestAspect[Nothing, E0, Nothing, E0] {
      type ModifyEnv[+R]       = R with R0
      type ModifyErr[+E <: E0] = E0

      def aroundTest[R, E <: E0](
        test: ZIO[R, TestFailure[E], TestSuccess]
      ): ZIO[R with R0, TestFailure[E0], TestSuccess] =
        managed.use(f => test.flatMap(f))

      def some[R, E <: E0](predicate: String => Boolean, spec: ZSpec[R, E]): ZSpec[R with R0, E0] =
        spec.transform[R with R0, TestFailure[E0], TestSuccess] {
          case c @ Spec.SuiteCase(_, _, _) => c
          case Spec.TestCase(label, test, annotations) =>
            Spec.TestCase(label, if (predicate(label)) aroundTest(test) else test, annotations)
        }
    }

  /**
   * Constructs a simple monomorphic aspect that only works with the specified
   * environment and error type.
   */
  def aspect[R0, E0](
    f: ZIO[R0, TestFailure[E0], TestSuccess] => ZIO[R0, TestFailure[E0], TestSuccess]
  ): TestAspect.Aux[
    R0,
    E0,
    R0,
    E0,
    ({ type Apply[+R >: R0] = R0 })#Apply,
    ({ type Apply[+E <: E0] = E0 })#Apply
  ] =
    new TestAspect[R0, E0, R0, E0] {
      type ModifyEnv[+R >: R0] = R0
      type ModifyErr[+E <: E0] = E0

      def some[R >: R0, E <: E0](predicate: String => Boolean, spec: ZSpec[R, E]): ZSpec[R0, E0] =
        spec.transform[R0, TestFailure[E0], TestSuccess] {
          case c @ Spec.SuiteCase(_, _, _) => c
          case Spec.TestCase(label, test, annotations) =>
            Spec.TestCase(label, if (predicate(label)) f(test) else test, annotations)
        }
    }

  /**
   * Constructs an aspect that runs the specified effect before every test.
   */
  def before[R0, E0](effect: ZIO[R0, E0, Any]): TestAspect.Aux[
    Nothing,
    E0,
    Nothing,
    E0,
    ({ type Apply[+R] = R with R0 })#Apply,
    ({ type Apply[+E <: E0] = E0 })#Apply
  ] =
    around(effect)(_ => ZIO.unit)

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
    new TestAspect.PerTestAtLeastR[TestConsole] {
      def perTest[R, E](
        test: ZIO[R, TestFailure[E], TestSuccess]
      ): ZIO[R with TestConsole, TestFailure[E], TestSuccess] =
        TestConsole.debug(test)
    }

  /**
   * An aspect that applies the specified aspect on Dotty.
   */
  def dotty[LowerR, UpperR, LowerE, UpperE](
    that: TestAspect[LowerR, UpperR, LowerE, UpperE]
  ): TestAspect[LowerR, UpperR, LowerE, UpperE] =
    if (TestVersion.isDotty) that else ???

  /**
   * An aspect that only runs tests on Dotty.
   */
  val dottyOnly: TestAspectAtLeastR[Annotations] =
    if (TestVersion.isDotty) ??? else ignore

  /**
   * An aspect that retries a test until success, without limit.
   */
  val eventually: TestAspectAtLeastR[ZTestEnv] = {
    val eventually: TestAspectPoly =
      new TestAspect.PerTestPoly {
        def perTest[R, E](test: ZIO[R, TestFailure[E], TestSuccess]): ZIO[R, TestFailure[E], TestSuccess] =
          test.eventually
      }
    restoreTestEnvironment >>> [Nothing, Any] eventually
  }

  /**
   * An aspect that restores a given
   * [[zio.test.environment.Restorable Restorable]]'s state to its starting
   * state after the test is run. Note that this is only useful when repeating
   * tests.
   */
  def restore[R](service: R => Restorable): TestAspectAtLeastR[R] =
    new TestAspect.PerTestAtLeastR[R] {
      def perTest[R0, E](test: ZIO[R0, TestFailure[E], TestSuccess]): ZIO[R0 with R, TestFailure[E], TestSuccess] =
        ZIO
          .accessM[R](r => service(r).save)
          .catchAllCause(c => ZIO.fail(TestFailure.Runtime(c)))
          .bracket(restore => restore)(_ => test)
    }

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
   * [[zio.test.environment.TestConsole TestConsole]]'s state to its starting
   * state after the test is run. Note that this is only useful when repeating
   * tests.
   */
  def restoreTestConsole: TestAspectAtLeastR[TestConsole] =
    restore[TestConsole](_.get)

  /**
   * An aspect that restores the
   * [[zio.test.environment.TestRandom TestRandom]]'s state to its starting
   * state after the test is run. Note that this is only useful when repeating
   * tests.
   */
  def restoreTestRandom: TestAspectAtLeastR[TestRandom] =
    restore[TestRandom](_.get)

  /**
   * An aspect that restores the
   * [[zio.test.environment.TestSystem TestSystem]]'s state to its starting
   * state after the test is run. Note that this is only useful when repeating
   * tests.
   */
  def restoreTestSystem: TestAspectAtLeastR[TestSystem] =
    restore[TestSystem](_.get)

  /**
   * An aspect that restores all state in the standard provided test
   * environments ([[zio.test.environment.TestClock TestClock]],
   * [[zio.test.environment.TestConsole TestConsole]],
   * [[zio.test.environment.TestRandom TestRandom]] and
   * [[zio.test.environment.TestSystem TestSystem]]) to their starting state
   * after the test is run. Note that this is only useful when repeating tests.
   */
  def restoreTestEnvironment: TestAspectAtLeastR[ZTestEnv] =
    restoreTestClock >>> [Nothing, Any] restoreTestConsole >>> [Nothing, Any] restoreTestRandom >>> [Nothing, Any] restoreTestSystem

  /**
   * Annotates tests with string tags.
   */
  def tag(tag: String, tags: String*): TestAspectPoly =
    annotate(TestAnnotation.tagged, Set(tag) union tags.toSet)

  /**
   * Removes tracing from tests.
   */
  val untraced: TestAspectPoly =
    new TestAspect.PerTestPoly {
      def perTest[R, E](test: ZIO[R, TestFailure[E], TestSuccess]): ZIO[R, TestFailure[E], TestSuccess] =
        test.untraced
    }

  trait PerTestPoly extends TestAspect[Nothing, Any, Nothing, Any] {
    type ModifyEnv[+R] = R
    type ModifyErr[+E] = E

    def perTest[R, E](test: ZIO[R, TestFailure[E], TestSuccess]): ZIO[R, TestFailure[E], TestSuccess]

    final def some[R, E](
      predicate: String => Boolean,
      spec: Spec[R, TestFailure[E], TestSuccess]
    ): Spec[R, TestFailure[E], TestSuccess] =
      spec.transform[R, TestFailure[E], TestSuccess] {
        case c @ SuiteCase(_, _, _) => c
        case TestCase(label, test, annotations) =>
          Spec.TestCase(label, if (predicate(label)) perTest(test) else test, annotations)
      }
  }

  trait PerTestAtLeastR[R] extends TestAspect[Nothing, Any, Nothing, Any] {
    type ModifyEnv[+R0] = R0 with R
    type ModifyErr[+E]  = E

    def perTest[R0, E](test: ZIO[R0, TestFailure[E], TestSuccess]): ZIO[R0 with R, TestFailure[E], TestSuccess]

    final def some[R0, E](
      predicate: String => Boolean,
      spec: Spec[R0, TestFailure[E], TestSuccess]
    ): Spec[R0 with R, TestFailure[E], TestSuccess] =
      spec.transform[R0 with R, TestFailure[E], TestSuccess] {
        case c @ SuiteCase(_, _, _) => c
        case TestCase(label, test, annotations) =>
          Spec.TestCase(label, if (predicate(label)) perTest(test) else test, annotations)
      }
  }
}
