package zio

import zio.clock.Clock
import zio.duration._
import zio.internal.Executor

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
   * As aspect that runs effects on the specified `Executor`.
   */
  def lock(executor: Executor): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.lock(executor)
    }

  /**
   * As aspect that runs effects on the specified `ExecutionContext`.
   */
  def on(ec: ExecutionContext): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.on(ec)
    }

  /**
   * An aspect that retries effects according to the specified schedule.
   */
  def retry[R1 <: Clock, E1](schedule: Schedule[R1, E1, Any]): ZIOAspect[Nothing, R1, E1, E1, Nothing, Any] =
    new ZIOAspect[Nothing, R1, E1, E1, Nothing, Any] {
      def apply[R <: R1, E >: E1 <: E1, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.retry(schedule)
    }

  /**
   * An aspect that times out effects.
   */
  def timeoutFail[E1](e: => E1)(d: Duration): ZIOAspect[Nothing, Clock, E1, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Clock, E1, Any, Nothing, Any] {
      def apply[R <: Clock, E >: E1, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
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
