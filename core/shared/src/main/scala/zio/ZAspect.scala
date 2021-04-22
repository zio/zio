package zio

import zio.internal.Executor
import scala.concurrent.ExecutionContext

trait ZAspect[-R, +E] {
  def apply[R1 <: R, E1 >: E, A](zio: ZIO[R1, E1, A]): ZIO[R1, E1, A]
}

object ZAspect {

  /**
   * An aspect that prints the results of effects to the console for debugging
   * purposes.
   */
  val debug: ZAspect[Any, Nothing] =
    new ZAspect[Any, Nothing] {
      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.debug
    }

  /**
   * As aspect that runs effects on the specified `Executor`.
   */
  def lock(executor: Executor): ZAspect[Any, Nothing] =
    new ZAspect[Any, Nothing] {
      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.lock(executor)
    }

  /**
   * As aspect that runs effects on the specified `ExecutionContext`.
   */
  def on(ec: ExecutionContext): ZAspect[Any, Nothing] =
    new ZAspect[Any, Nothing] {
      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.on(ec)
    }

  /**
   * As aspect that enables tracing for effects.
   */
  val traced: ZAspect[Any, Nothing] =
    new ZAspect[Any, Nothing] {
      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.traced
    }

  /**
   * As aspect that disables tracing for effects.
   */
  val untraced: ZAspect[Any, Nothing] =
    new ZAspect[Any, Nothing] {
      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
        zio.untraced
    }
}
