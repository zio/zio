package zio.test

import zio.{Scope, ZIO, Trace}
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stm.ZSTM

trait CheckConstructor[Environment, In] {
  type OutEnvironment <: Environment
  type OutError
  def apply(input: => In)(implicit trace: Trace): ZIO[OutEnvironment, OutError, TestResult]
}

object CheckConstructor extends CheckConstructorLowPriority1 {

  type WithOut[Environment, In, OutEnvironment0, OutError0] =
    CheckConstructor[Environment, In] {
      type OutEnvironment = OutEnvironment0
      type OutError       = OutError0
    }

  implicit def AssertConstructor[R, A <: TestResult]: CheckConstructor.WithOut[R, A, R, Nothing] =
    new CheckConstructor[R, A] {
      type OutEnvironment = R
      type OutError       = Nothing
      def apply(input: => A)(implicit trace: Trace): ZIO[OutEnvironment, OutError, TestResult] =
        ZIO.succeedNow(input)
    }
}

trait CheckConstructorLowPriority1 extends CheckConstructorLowPriority2 {

  implicit def AssertZIOConstructor[R, R1, E, A <: TestResult]
    : CheckConstructor.WithOut[R, ZIO[R1, E, A], R with R1, E] =
    new CheckConstructor[R, ZIO[R1, E, A]] {
      type OutEnvironment = R with R1
      type OutError       = E
      def apply(input: => ZIO[R1, E, A])(implicit trace: Trace): ZIO[OutEnvironment, OutError, TestResult] =
        input
    }
}

trait CheckConstructorLowPriority2 {

  implicit def AssertZSTMConstructor[R, R1, E, A <: TestResult]
    : CheckConstructor.WithOut[R, ZSTM[R1, E, A], R with R1, E] =
    new CheckConstructor[R, ZSTM[R1, E, A]] {
      type OutEnvironment = R with R1
      type OutError       = E
      def apply(input: => ZSTM[R1, E, A])(implicit trace: Trace): ZIO[OutEnvironment, OutError, TestResult] =
        input.commit
    }
}
