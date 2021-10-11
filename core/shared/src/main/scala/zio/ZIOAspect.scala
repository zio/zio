package zio

import scala.concurrent.ExecutionContext

trait ZIOAspect[+LowerR, -UpperR, +LowerE, -UpperE, +LowerA, -UpperA] { self =>

  def apply[R >: LowerR <: UpperR, E >: LowerE <: UpperE, A >: LowerA <: UpperA](zio: ZIO[R, E, A]): ZIO[R, E, A]

  def >>>[
    LowerR1 >: LowerR,
    UpperR1 <: UpperR,
    LowerE1 >: LowerE,
    UpperE1 <: UpperE,
    LowerA1 >: LowerA,
    UpperA1 <: UpperA
  ](
    that: ZIOAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerA1, UpperA1]
  ): ZIOAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerA1, UpperA1] =
    self.andThen(that)

  /**
   * Returns a new aspect that represents the sequential composition of this
   * aspect with the specified one.
   */
  def @@[
    LowerR1 >: LowerR,
    UpperR1 <: UpperR,
    LowerE1 >: LowerE,
    UpperE1 <: UpperE,
    LowerA1 >: LowerA,
    UpperA1 <: UpperA
  ](
    that: ZIOAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerA1, UpperA1]
  ): ZIOAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerA1, UpperA1] =
    self >>> that

  def andThen[
    LowerR1 >: LowerR,
    UpperR1 <: UpperR,
    LowerE1 >: LowerE,
    UpperE1 <: UpperE,
    LowerA1 >: LowerA,
    UpperA1 <: UpperA
  ](
    that: ZIOAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerA1, UpperA1]
  ): ZIOAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerA1, UpperA1] =
    new ZIOAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerA1, UpperA1] {
      def apply[R >: LowerR1 <: UpperR1, E >: LowerE1 <: UpperE1, A >: LowerA1 <: UpperA1](
        zio: ZIO[R, E, A]
      ): ZIO[R, E, A] =
        that(self(zio))
    }
}

object ZIOAspect {

  /**
   * An aspect that prints the results of effects to the console for debugging
   * purposes.
   */
  val debug: ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.debug
    }

  /**
   * An aspect that disables logging for the specified effect.
   */
  val disableLogging: ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        ZIO.runtimeConfig.flatMap { runtimeConfig =>
          zio.withRuntimeConfig(runtimeConfig.copy(logger = ZLogger.none))
        }
    }

  /**
   * As aspect that runs effects on the specified `Executor`.
   */
  @deprecated("use onExecutor", "2.0.0")
  def lock(executor: Executor): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    onExecutor(executor)

  /**
   * An aspect that logs values by using [[ZIO.log]].
   */
  val logged: ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.tap(value => ZIO.log(String.valueOf(value)))
    }

  /**
   * An aspect that logs values using a specified user-defined prefix label,
   * using [[ZIO.log]].
   */
  def logged(label: String): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      override def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.tap(value => ZIO.log(label + ": " + String.valueOf(value)))
    }

  /**
   * An aspect that logs values using a specified function that convers the value
   * into a log message. The log message is logged using [[ZIO.log]].
   */
  def loggedWith[A](f: A => String): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, A] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, A] {
      override def apply[R, E, A0 <: A](zio: ZIO[R, E, A0]): ZIO[R, E, A0] =
        zio.tap(value => ZIO.log(f(value)))
    }

  /**
   * As aspect that runs effects on the specified `ExecutionContext`.
   */
  @deprecated("use onExecutionContext", "2.0.0")
  def lockExecutionContext(ec: ExecutionContext): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    onExecutionContext(ec)

  /**
   * As aspect that runs effects on the specified `ExecutionContext`.
   */
  def onExecutionContext(ec: ExecutionContext): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.onExecutionContext(ec)
    }

  /**
   * As aspect that runs effects on the specified `Executor`.
   */
  def onExecutor(executor: Executor): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.onExecutor(executor)
    }

  /**
   * An aspect that retries effects according to the specified schedule.
   */
  def retry[R1 <: Has[Clock], E1](schedule: Schedule[R1, E1, Any]): ZIOAspect[Nothing, R1, Nothing, E1, Nothing, Any] =
    new ZIOAspect[Nothing, R1, Nothing, E1, Nothing, Any] {
      def apply[R <: R1, E <: E1, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.retry(schedule)
    }

  /**
   * An aspect that times out effects.
   */
  def timeoutFail[E1](e: => E1)(d: Duration): ZIOAspect[Nothing, Has[Clock], E1, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Has[Clock], E1, Any, Nothing, Any] {
      def apply[R <: Has[Clock], E >: E1, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.timeoutFail(e)(d)
    }

  /**
   * As aspect that enables tracing for effects.
   */
  val traced: ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.traced
    }

  /**
   * As aspect that disables tracing for effects.
   */
  val untraced: ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.untraced
    }
}
