package zio.test

import zio.internal.stacktracer.SourceLocation
import zio.{Scope, Trace, ZIO}
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stm.ZSTM

/**
 * A type class that turns the body of `test` into a spec. `test` accepts a body
 * of type `In` when an instance exists: a `TestResult`, a `ZIO` or `ZSTM`
 * effect that produces one, or an `Either` whose `Right` holds one.
 */
trait TestConstructor[-Environment, In] {

  /**
   * The type of the spec that the test becomes.
   */
  type Out <: Spec[Environment, Any]

  /**
   * Builds a test labeled `label` whose body is `assertion`.
   */
  def apply(label: String)(assertion: => In)(implicit sourceLocation: SourceLocation, trace: Trace): Out
}

/**
 * The instances of [[TestConstructor]].
 */
object TestConstructor extends TestConstructorLowPriority1 {

  /**
   * A `TestConstructor` whose output type is fixed to `Out0`.
   */
  type WithOut[Environment, In, Out0] = TestConstructor[Environment, In] { type Out = Out0 }

  /**
   * The instance for a `TestResult`, such as the result of `assertTrue`. The
   * body is evaluated when the test runs.
   */
  implicit def AssertConstructor[A <: TestResult]: TestConstructor.WithOut[Any, A, Spec[Any, Nothing]] =
    new TestConstructor[Any, A] {
      type Out = Spec[Any, Nothing]
      def apply(label: String)(
        assertion: => A
      )(implicit sourceLocation: SourceLocation, trace: Trace): Spec[Any, Nothing] =
        test(label)(ZIO.succeed(assertion))
    }
}

/**
 * Lower-priority instances of [[TestConstructor]].
 */
trait TestConstructorLowPriority1 extends TestConstructorLowPriority2 {

  /**
   * The instance for a `ZIO` effect that produces a `TestResult`. A failure of
   * the effect fails the test with that error.
   */
  implicit def AssertZIOConstructor[R, E, A <: TestResult]: TestConstructor.WithOut[R, ZIO[R, E, A], Spec[R, E]] =
    new TestConstructor[R, ZIO[R, E, A]] {
      type Out = Spec[R, E]
      def apply(
        label: String
      )(assertion: => ZIO[R, E, A])(implicit sourceLocation: SourceLocation, trace: Trace): Spec[R, E] = {
        val assertion0 = ZIO.suspendSucceed(assertion)
        Spec.labeled(
          label,
          Spec
            .test(ZTest(label, assertion0), TestAnnotationMap.empty)
            .annotate(TestAnnotation.trace, sourceLocation :: Nil)
        )
      }
    }
}

/**
 * Lower-priority instances of [[TestConstructor]].
 */
trait TestConstructorLowPriority2 extends TestConstructorLowPriority3 {

  /**
   * The instance for a `ZSTM` transaction that produces a `TestResult`. The
   * transaction is committed when the test runs.
   */
  implicit def AssertZSTMConstructor[R, E, A <: TestResult]: TestConstructor.WithOut[R, ZSTM[R, E, A], Spec[R, E]] =
    new TestConstructor[R, ZSTM[R, E, A]] {
      type Out = Spec[R, E]
      def apply(label: String)(
        assertion: => ZSTM[R, E, A]
      )(implicit sourceLocation: SourceLocation, trace: Trace): Spec[R, E] =
        test(label)(assertion.commit)
    }
}

/**
 * Lower-priority instances of [[TestConstructor]].
 */
trait TestConstructorLowPriority3 {

  /**
   * The instance for an `Either`: a `Right` holds the `TestResult`, and a
   * `Left` fails the test with its value as the error.
   */
  implicit def AssertEitherConstructor[E, A <: TestResult]: TestConstructor.WithOut[Any, Either[E, A], Spec[Any, E]] =
    new TestConstructor[Any, Either[E, A]] {
      type Out = Spec[Any, E]
      def apply(label: String)(
        assertion: => Either[E, A]
      )(implicit sourceLocation: SourceLocation, trace: Trace): Spec[Any, E] =
        test(label)(ZIO.fromEither(assertion))
    }
}
