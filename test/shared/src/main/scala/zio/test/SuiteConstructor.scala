package zio.test

import zio.{Chunk, Scope, ZIO, Trace}
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stm.ZSTM

/**
 * A type class that turns one argument of `suite` into a spec. `suite`
 * accepts a member of type `In` when an instance exists: a `Spec`, a
 * collection of specs, or a `ZIO` or `ZSTM` effect that produces a collection
 * of specs.
 */
trait SuiteConstructor[In] {
  /**
   * The environment that the resulting spec requires.
   */
  type OutEnvironment
  /**
   * The error type of the resulting spec.
   */
  type OutError
  /**
   * Turns `spec` into a spec.
   */
  def apply(spec: In)(implicit trace: Trace): Spec[OutEnvironment, OutError]
}

/**
 * The instances of [[SuiteConstructor]].
 */
object SuiteConstructor extends SuiteConstructorLowPriority1 {

  /**
   * A `SuiteConstructor[In]` whose output types are fixed to `OutEnvironment0`
   * and `OutError0`.
   */
  type WithOut[In, OutEnvironment0, OutError0] =
    SuiteConstructor[In] {
      type OutEnvironment = OutEnvironment0
      type OutError       = OutError0
    }

  /**
   * The instance for `Nothing`, which lets `suite(label)()` compile with no
   * members. It returns an empty spec.
   */
  implicit val NothingConstructor: SuiteConstructor.WithOut[Nothing, Any, Nothing] =
    new SuiteConstructor[Nothing] {
      type OutEnvironment = Any
      type OutError       = Nothing
      type OutSuccess     = Nothing
      def apply(spec: Nothing)(implicit trace: Trace): Spec[Any, Nothing] =
        Spec.multiple(Chunk.empty)
    }
}

/**
 * Lower-priority instances of [[SuiteConstructor]].
 */
trait SuiteConstructorLowPriority1 extends SuiteConstructorLowPriority2 {

  /**
   * The instance for a `Spec`, which is used as it is.
   */
  implicit def SpecConstructor[R, E]: SuiteConstructor.WithOut[Spec[R, E], R, E] =
    new SuiteConstructor[Spec[R, E]] {
      type OutEnvironment = R
      type OutError       = E
      def apply(spec: Spec[R, E])(implicit trace: Trace): Spec[R, E] =
        spec
    }
}

/**
 * Lower-priority instances of [[SuiteConstructor]].
 */
trait SuiteConstructorLowPriority2 extends SuiteConstructorLowPriority3 {

  /**
   * The instance for a collection of specs, such as a `List` or a `Chunk`. Its
   * specs become one group, in the collection's order.
   */
  implicit def IterableConstructor[R, E, Collection[+Element] <: Iterable[Element]]
    : SuiteConstructor.WithOut[Collection[Spec[R, E]], R, E] =
    new SuiteConstructor[Collection[Spec[R, E]]] {
      type OutEnvironment = R
      type OutError       = E
      def apply(spec: Collection[Spec[R, E]])(implicit trace: Trace): Spec[R, E] =
        Spec.multiple(Chunk.fromIterable(spec))
    }
}

/**
 * Lower-priority instances of [[SuiteConstructor]].
 */
trait SuiteConstructorLowPriority3 extends SuiteConstructorLowPriority4 {

  /**
   * The instance for a `ZIO` effect that produces a collection of specs. The
   * effect runs when the suite runs, and its specs become one group. When the
   * effect fails, the suite fails with its error and none of the specs run.
   */
  implicit def ZIOConstructor[R, R1, E <: E2, E1 <: E2, E2, Collection[+Element] <: Iterable[Element]]
    : SuiteConstructor.WithOut[ZIO[R, E, Collection[Spec[R1, E1]]], R with R1, E2] =
    new SuiteConstructor[ZIO[R, E, Collection[Spec[R1, E1]]]] {
      type OutEnvironment = R with R1
      type OutError       = E2
      def apply(specs: ZIO[R, E, Collection[Spec[R1, E1]]])(implicit trace: Trace): Spec[R with R1, E2] =
        Spec.scoped[R with R1](specs.mapBoth(TestFailure.fail, specs => Spec.multiple(Chunk.fromIterable(specs))))
    }
}

/**
 * Lower-priority instances of [[SuiteConstructor]].
 */
trait SuiteConstructorLowPriority4 {

  /**
   * The instance for a `ZSTM` transaction that produces a collection of specs.
   * The transaction is committed when the suite runs, and its specs become one
   * group.
   */
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
