package zio.test

import zio.{Chunk, Scope, ZIO, ZTraceElement}
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stm.ZSTM

trait SuiteConstructor[In] {
  type OutEnvironment
  type OutError
  type OutSuccess
  def apply(spec: In)(implicit trace: ZTraceElement): Spec[OutEnvironment, OutError, OutSuccess]
}

object SuiteConstructor extends SuiteConstructorLowPriority1 {

  type WithOut[In, OutEnvironment0, OutError0, OutSuccess0] =
    SuiteConstructor[In] {
      type OutEnvironment = OutEnvironment0
      type OutError       = OutError0
      type OutSuccess     = OutSuccess0
    }

  implicit val NothingConstructor: SuiteConstructor.WithOut[Nothing, Any, Nothing, Nothing] =
    new SuiteConstructor[Nothing] {
      type OutEnvironment = Any
      type OutError       = Nothing
      type OutSuccess     = Nothing
      def apply(spec: Nothing)(implicit trace: ZTraceElement): Spec[Any, Nothing, Nothing] =
        Spec.multiple(Chunk.empty)
    }
}

trait SuiteConstructorLowPriority1 extends SuiteConstructorLowPriority2 {

  implicit def SpecConstructor[R, E, T]: SuiteConstructor.WithOut[Spec[R, E, T], R, E, T] =
    new SuiteConstructor[Spec[R, E, T]] {
      type OutEnvironment = R
      type OutError       = E
      type OutSuccess     = T
      def apply(spec: Spec[R, E, T])(implicit trace: ZTraceElement): Spec[R, E, T] =
        spec
    }
}

trait SuiteConstructorLowPriority2 extends SuiteConstructorLowPriority3 {

  implicit def IterableConstructor[R, E, T, Collection[+Element] <: Iterable[Element]]
    : SuiteConstructor.WithOut[Collection[Spec[R, E, T]], R, E, T] =
    new SuiteConstructor[Collection[Spec[R, E, T]]] {
      type OutEnvironment = R
      type OutError       = E
      type OutSuccess     = T
      def apply(spec: Collection[Spec[R, E, T]])(implicit trace: ZTraceElement): Spec[R, E, T] =
        Spec.multiple(Chunk.fromIterable(spec))
    }
}

trait SuiteConstructorLowPriority3 extends SuiteConstructorLowPriority4 {

  implicit def ZIOConstructor[R, R1, E <: E2, E1 <: E2, E2, T, Collection[+Element] <: Iterable[Element]]
    : SuiteConstructor.WithOut[ZIO[R, E, Collection[Spec[R1, E1, T]]], R with R1, E2, T] =
    new SuiteConstructor[ZIO[R, E, Collection[Spec[R1, E1, T]]]] {
      type OutEnvironment = R with R1
      type OutError       = E2
      type OutSuccess     = T
      def apply(specs: ZIO[R, E, Collection[Spec[R1, E1, T]]])(implicit trace: ZTraceElement): Spec[R with R1, E2, T] =
        Spec.scoped[R with R1](specs.mapBoth(TestFailure.fail, specs => Spec.multiple(Chunk.fromIterable(specs))))
    }
}

trait SuiteConstructorLowPriority4 {

  implicit def ZSTMConstructor[R, R1, E <: E2, E1 <: E2, E2, T, Collection[+Element] <: Iterable[Element]]
    : SuiteConstructor.WithOut[ZSTM[R, E, Collection[Spec[R1, E1, T]]], R with R1, E2, T] =
    new SuiteConstructor[ZSTM[R, E, Collection[Spec[R1, E1, T]]]] {
      type OutEnvironment = R with R1
      type OutError       = E2
      type OutSuccess     = T
      def apply(specs: ZSTM[R, E, Collection[Spec[R1, E1, T]]])(implicit trace: ZTraceElement): Spec[R with R1, E2, T] =
        Spec.scoped[R with R1](
          specs.mapBoth(TestFailure.fail, specs => Spec.multiple(Chunk.fromIterable(specs))).commit
        )
    }
}
