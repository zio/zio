package zio.test

import zio.{Chunk, Scope, ZIO, Trace}
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stm.ZSTM

trait SuiteConstructor[In] {
  type OutEnvironment
  type OutError
  def apply(spec: In)(implicit trace: Trace): Spec[OutEnvironment, OutError]
}

object SuiteConstructor extends SuiteConstructorLowPriority1 {

  type WithOut[In, OutEnvironment0, OutError0] =
    SuiteConstructor[In] {
      type OutEnvironment = OutEnvironment0
      type OutError       = OutError0
    }

  implicit val NothingConstructor: SuiteConstructor.WithOut[Nothing, Any, Nothing] =
    new SuiteConstructor[Nothing] {
      type OutEnvironment = Any
      type OutError       = Nothing
      type OutSuccess     = Nothing
      def apply(spec: Nothing)(implicit trace: Trace): Spec[Any, Nothing] =
        Spec.multiple(Chunk.empty)
    }
}

trait SuiteConstructorLowPriority1 extends SuiteConstructorLowPriority2 {

  implicit def SpecConstructor[R, E]: SuiteConstructor.WithOut[Spec[R, E], R, E] =
    new SuiteConstructor[Spec[R, E]] {
      type OutEnvironment = R
      type OutError       = E
      def apply(spec: Spec[R, E])(implicit trace: Trace): Spec[R, E] =
        spec
    }
}

trait SuiteConstructorLowPriority2 extends SuiteConstructorLowPriority3 {

  implicit def IterableConstructor[R, E, Collection[+Element] <: Iterable[Element]]
    : SuiteConstructor.WithOut[Collection[Spec[R, E]], R, E] =
    new SuiteConstructor[Collection[Spec[R, E]]] {
      type OutEnvironment = R
      type OutError       = E
      def apply(spec: Collection[Spec[R, E]])(implicit trace: Trace): Spec[R, E] =
        Spec.multiple(Chunk.fromIterable(spec))
    }
}

trait SuiteConstructorLowPriority3 extends SuiteConstructorLowPriority4 {

  implicit def ZIOConstructor[R, R1, E <: E2, E1 <: E2, E2, Collection[+Element] <: Iterable[Element]]
    : SuiteConstructor.WithOut[ZIO[R, E, Collection[Spec[R1, E1]]], R with R1, E2] =
    new SuiteConstructor[ZIO[R, E, Collection[Spec[R1, E1]]]] {
      type OutEnvironment = R with R1
      type OutError       = E2
      def apply(specs: ZIO[R, E, Collection[Spec[R1, E1]]])(implicit trace: Trace): Spec[R with R1, E2] =
        Spec.scoped[R with R1](specs.mapBoth(TestFailure.fail, specs => Spec.multiple(Chunk.fromIterable(specs))))
    }
}

trait SuiteConstructorLowPriority4 {

  implicit def ZSTMConstructor[R, R1, E <: E2, E1 <: E2, E2, Collection[+Element] <: Iterable[Element]]
    : SuiteConstructor.WithOut[ZSTM[R, E, Collection[Spec[R1, E1]]], R with R1, E2] =
    new SuiteConstructor[ZSTM[R, E, Collection[Spec[R1, E1]]]] {
      type OutEnvironment = R with R1
      type OutError       = E2
      def apply(specs: ZSTM[R, E, Collection[Spec[R1, E1]]])(implicit trace: Trace): Spec[R with R1, E2] =
        Spec.scoped[R with R1](
          specs.mapBoth(TestFailure.fail, specs => Spec.multiple(Chunk.fromIterable(specs))).commit
        )
    }
}
