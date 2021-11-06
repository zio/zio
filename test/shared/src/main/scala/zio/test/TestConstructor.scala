package zio.test

import zio.{ZIO, ZManaged, ZTraceElement}
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stm.ZSTM

trait TestConstructor[-Environment, In] {
  type Out <: ZSpec[Environment, Any]
  def apply(label: String)(assertion: => In)(implicit trace: ZTraceElement): Out
}

object TestConstructor extends TestConstructorLowPriority1 {
  type WithOut[Environment, In, Out0] = TestConstructor[Environment, In] { type Out = Out0 }

  implicit def TestResultConstructor[A <: TestResult]: TestConstructor.WithOut[Any, A, ZSpec[Any, Nothing]] =
    new TestConstructor[Any, A] {
      type Out = ZSpec[Any, Nothing]
      def apply(label: String)(
        assertion: => A
      )(implicit trace: ZTraceElement): ZSpec[Any, Nothing] =
        test(label)(ZIO.succeed(assertion))
    }
}

trait TestConstructorLowPriority1 extends TestConstructorLowPriority2 {

  implicit def TestResultZIOConstructor[R, E, A <: TestResult]: TestConstructor.WithOut[R, ZIO[R, E, A], ZSpec[R, E]] =
    new TestConstructor[R, ZIO[R, E, A]] {
      type Out = ZSpec[R, E]
      def apply(
        label: String
      )(assertion: => ZIO[R, E, A])(implicit trace: ZTraceElement): ZSpec[R, E] =
        Spec.labeled(
          label,
          Spec
            .test(ZTest(label, assertion), TestAnnotationMap.empty)
            .annotate(TestAnnotation.trace, trace :: Nil)
        )
    }
}

trait TestConstructorLowPriority2 extends TestConstructorLowPriority3 {

  implicit def TestResultZManagedConstructor[R, E, A <: TestResult]
    : TestConstructor.WithOut[R, ZManaged[R, E, A], ZSpec[R, E]] =
    new TestConstructor[R, ZManaged[R, E, A]] {
      type Out = ZSpec[R, E]
      def apply(label: String)(
        assertion: => ZManaged[R, E, A]
      )(implicit trace: ZTraceElement): ZSpec[R, E] =
        test(label)(assertion.useNow)
    }
}

trait TestConstructorLowPriority3 extends TestConstructorLowPriority4 {

  implicit def TestResultZSTMConstructor[R, E, A <: TestResult]
    : TestConstructor.WithOut[R, ZSTM[R, E, A], ZSpec[R, E]] =
    new TestConstructor[R, ZSTM[R, E, A]] {
      type Out = ZSpec[R, E]
      def apply(label: String)(
        assertion: => ZSTM[R, E, A]
      )(implicit trace: ZTraceElement): ZSpec[R, E] =
        test(label)(assertion.commit)
    }
}

trait TestConstructorLowPriority4 extends TestConstructorLowPriority5 {

  implicit def AssertConstructor[A <: Assert]: TestConstructor.WithOut[Any, A, ZSpec[Any, Nothing]] =
    new TestConstructor[Any, A] {
      type Out = ZSpec[Any, Nothing]
      def apply(label: String)(
        assertion: => A
      )(implicit trace: ZTraceElement): ZSpec[Any, Nothing] =
        test(label)(ZIO.succeed(assertion))
    }
}

trait TestConstructorLowPriority5 extends TestConstructorLowPriority6 {

  implicit def AssertZIOConstructor[R, E, A <: Assert]: TestConstructor.WithOut[R, ZIO[R, E, A], ZSpec[R, E]] =
    new TestConstructor[R, ZIO[R, E, A]] {
      type Out = ZSpec[R, E]
      def apply(
        label: String
      )(assertion: => ZIO[R, E, A])(implicit trace: ZTraceElement): ZSpec[R, E] =
        Spec.labeled(
          label,
          Spec
            .test(ZTest(label, assertion), TestAnnotationMap.empty)
            .annotate(TestAnnotation.trace, trace :: Nil)
        )
    }
}

trait TestConstructorLowPriority6 extends TestConstructorLowPriority7 {

  implicit def AssertZManagedConstructor[R, E, A <: Assert]
    : TestConstructor.WithOut[R, ZManaged[R, E, A], ZSpec[R, E]] =
    new TestConstructor[R, ZManaged[R, E, A]] {
      type Out = ZSpec[R, E]
      def apply(label: String)(
        assertion: => ZManaged[R, E, A]
      )(implicit trace: ZTraceElement): ZSpec[R, E] =
        test(label)(assertion.useNow)
    }
}

trait TestConstructorLowPriority7 {

  implicit def AssertZSTMConstructor[R, E, A <: Assert]: TestConstructor.WithOut[R, ZSTM[R, E, A], ZSpec[R, E]] =
    new TestConstructor[R, ZSTM[R, E, A]] {
      type Out = ZSpec[R, E]
      def apply(label: String)(
        assertion: => ZSTM[R, E, A]
      )(implicit trace: ZTraceElement): ZSpec[R, E] =
        test(label)(assertion.commit)
    }
}
