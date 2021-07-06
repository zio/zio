package zio.test

import zio.ZIO

trait TestConstructor[-Environment, In] {
  type Out <: ZSpec[Environment, Any]
  def apply(label: String)(assertion: => In)(implicit location: SourceLocation): Out
}

object TestConstructor extends TestConstructorLowPriority1 {
  type WithOut[Environment, In, Out0] = TestConstructor[Environment, In] { type Out = Out0 }

  implicit def TestResultConstructor[A <: TestResult]: TestConstructor.WithOut[Any, A, ZSpec[Any, Nothing]] =
    new TestConstructor[Any, A] {
      type Out = ZSpec[Any, Nothing]
      def apply(label: String)(assertion: => A)(implicit location: SourceLocation): ZSpec[Any, Nothing] =
        test(label)(ZIO.succeed(assertion))
    }
}

trait TestConstructorLowPriority1 extends TestConstructorLowPriority2 {

  implicit def TestResultZIOConstructor[R, E, A <: TestResult]: TestConstructor.WithOut[R, ZIO[R, E, A], ZSpec[R, E]] =
    new TestConstructor[R, ZIO[R, E, A]] {
      type Out = ZSpec[R, E]
      def apply(label: String)(assertion: => ZIO[R, E, A])(implicit location: SourceLocation): ZSpec[R, E] =
        Spec
          .test(label, ZTest(assertion), TestAnnotationMap.empty)
          .annotate(TestAnnotation.location, location :: Nil)
    }

}

trait TestConstructorLowPriority2 extends TestConstructorLowPriority3 {

  implicit def AssertConstructor[A <: Assert]: TestConstructor.WithOut[Any, A, ZSpec[Any, Nothing]] =
    new TestConstructor[Any, A] {
      type Out = ZSpec[Any, Nothing]
      def apply(label: String)(assertion: => A)(implicit location: SourceLocation): ZSpec[Any, Nothing] =
        test(label)(ZIO.succeed(assertion))
    }
}

trait TestConstructorLowPriority3 {
  implicit def AssertZIOConstructor[R, E, A <: Assert]: TestConstructor.WithOut[R, ZIO[R, E, A], ZSpec[R, E]] =
    new TestConstructor[R, ZIO[R, E, A]] {
      type Out = ZSpec[R, E]
      def apply(label: String)(assertion: => ZIO[R, E, A])(implicit location: SourceLocation): ZSpec[R, E] =
        Spec
          .test(label, ZTest(assertion), TestAnnotationMap.empty)
          .annotate(TestAnnotation.location, location :: Nil)
    }
}
