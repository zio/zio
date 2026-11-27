package zio.test

import zio.{Scope, ZIO, Trace}
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stm.ZSTM

/**
 * A type class that turns the body of a property (the function passed to
 * `check`, `checkAll`, `checkN`, `checkPar` or `checkAllPar`) into an effect
 * that produces a [[TestResult]]. Instances exist for a `TestResult`, and for a
 * `ZIO`, a `ZSTM` or an `Either` whose success type is a `TestResult`.
 *
 * @tparam Environment
 *   the environment of the generator
 * @tparam In
 *   the type of the property body
 */
trait CheckConstructor[Environment, In] {

  /** The environment that the resulting effect requires. */
  type OutEnvironment <: Environment

  /** The error type of the resulting effect. */
  type OutError

  /** Turns `input` into an effect that produces its `TestResult`. */
  def apply(input: => In)(implicit trace: Trace): ZIO[OutEnvironment, OutError, TestResult]
}

/**
 * The `CheckConstructor` instances, in priority order: a `TestResult`, then a
 * `ZIO`, then a `ZSTM`, then an `Either`.
 */
object CheckConstructor extends CheckConstructorLowPriority1 {

  /**
   * A `CheckConstructor` whose output environment and output error types are
   * fixed to `OutEnvironment0` and `OutError0`.
   */
  type WithOut[Environment, In, OutEnvironment0, OutError0] =
    CheckConstructor[Environment, In] {
      type OutEnvironment = OutEnvironment0
      type OutError       = OutError0
    }

  /**
   * Turns a `TestResult` into an effect that succeeds with it. The result is
   * computed when the effect runs.
   */
  implicit def AssertConstructor[R, A <: TestResult]: CheckConstructor.WithOut[R, A, R, Nothing] =
    new CheckConstructor[R, A] {
      type OutEnvironment = R
      type OutError       = Nothing
      def apply(input: => A)(implicit trace: Trace): ZIO[OutEnvironment, OutError, TestResult] =
        ZIO.succeed(input)
    }
}

/** The `CheckConstructor` instance for a `ZIO`. */
trait CheckConstructorLowPriority1 extends CheckConstructorLowPriority2 {

  /**
   * Uses an effect that produces a `TestResult` as it is. Its environment is
   * added to the generator's environment, and its error type becomes the output
   * error type.
   */
  implicit def AssertZIOConstructor[R, R1, E, A <: TestResult]
    : CheckConstructor.WithOut[R, ZIO[R1, E, A], R with R1, E] =
    new CheckConstructor[R, ZIO[R1, E, A]] {
      type OutEnvironment = R with R1
      type OutError       = E
      def apply(input: => ZIO[R1, E, A])(implicit trace: Trace): ZIO[OutEnvironment, OutError, TestResult] =
        input
    }
}

/** The `CheckConstructor` instance for a `ZSTM`. */
trait CheckConstructorLowPriority2 extends CheckConstructorLowPriority3 {

  /**
   * Turns a transaction that produces a `TestResult` into an effect that
   * commits it.
   */
  implicit def AssertZSTMConstructor[R, R1, E, A <: TestResult]
    : CheckConstructor.WithOut[R, ZSTM[R1, E, A], R with R1, E] =
    new CheckConstructor[R, ZSTM[R1, E, A]] {
      type OutEnvironment = R with R1
      type OutError       = E
      def apply(input: => ZSTM[R1, E, A])(implicit trace: Trace): ZIO[OutEnvironment, OutError, TestResult] =
        input.commit
    }
}

/** The `CheckConstructor` instance for an `Either`. */
trait CheckConstructorLowPriority3 {

  /**
   * Turns an `Either` into an effect that fails with the `Left` value or
   * succeeds with the `TestResult` in the `Right`.
   */
  implicit def AssertEitherConstructor[R, E, A <: TestResult]: CheckConstructor.WithOut[R, Either[E, A], R, E] =
    new CheckConstructor[R, Either[E, A]] {
      type OutEnvironment = R
      type OutError       = E
      def apply(input: => Either[E, A])(implicit trace: Trace): ZIO[OutEnvironment, OutError, TestResult] =
        ZIO.fromEither(input)
    }
}
