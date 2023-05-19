package zio.test

import zio.internal.stacktracer.SourceLocation
import zio.{Scope, Trace, ZIO}
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stm.ZSTM

trait TestConstructor[-Environment, In] {
  type Out <: Spec[Environment, Any]
  def apply(label: String)(assertion: => In)(implicit sourceLocation: SourceLocation, trace: Trace): Out
}

object TestConstructor extends TestConstructorLowPriority1 {
  type WithOut[Environment, In, Out0] = TestConstructor[Environment, In] { type Out = Out0 }

  implicit def AssertConstructor[A <: TestResult]: TestConstructor.WithOut[Any, A, Spec[Any, Nothing]] =
    new TestConstructor[Any, A] {
      type Out = Spec[Any, Nothing]
      def apply(label: String)(
        assertion: => A
      )(implicit sourceLocation: SourceLocation, trace: Trace): Spec[Any, Nothing] =
        test(label)(ZIO.succeed(assertion))
    }
}

trait TestConstructorLowPriority1 extends TestConstructorLowPriority2 {

  implicit def AssertZIOConstructor[R, E, A <: TestResult]: TestConstructor.WithOut[R, ZIO[R, E, A], Spec[R, E]] =
    new TestConstructor[R, ZIO[R, E, A]] {
      type Out = Spec[R, E]
      def apply(
        label: String
      )(assertion: => ZIO[R, E, A])(implicit sourceLocation: SourceLocation, trace: Trace): Spec[R, E] =
        Spec.labeled(
          label,
          Spec
            .test(ZTest(label, assertion), TestAnnotationMap.empty)
            .annotate(TestAnnotation.trace, sourceLocation :: Nil)
        )
    }
}

trait TestConstructorLowPriority2 extends TestConstructorLowPriority3 {

  implicit def AssertZSTMConstructor[R, E, A <: TestResult]: TestConstructor.WithOut[R, ZSTM[R, E, A], Spec[R, E]] =
    new TestConstructor[R, ZSTM[R, E, A]] {
      type Out = Spec[R, E]
      def apply(label: String)(
        assertion: => ZSTM[R, E, A]
      )(implicit sourceLocation: SourceLocation, trace: Trace): Spec[R, E] =
        test(label)(assertion.commit)
    }
}

trait TestConstructorLowPriority3 {

  implicit def AssertEitherConstructor[E, A <: TestResult]: TestConstructor.WithOut[Any, Either[E, A], Spec[Any, E]] =
    new TestConstructor[Any, Either[E, A]] {
      type Out = Spec[Any, E]
      def apply(label: String)(
        assertion: => Either[E, A]
      )(implicit sourceLocation: SourceLocation, trace: Trace): Spec[Any, E] =
        test(label)(ZIO.fromEither(assertion))
    }
}
