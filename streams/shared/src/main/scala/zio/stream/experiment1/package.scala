package zio.stream

import zio.{ Cause, ZIO }

package object experiment1 {

  type Pull[+E, +I] = ZIO[Any, Option[E], I]

  object Pull {

    def emit[I](i: I): Pull[Nothing, I] =
      ZIO.succeedNow(i)

    val end: Pull[Nothing, Nothing] =
      ZIO.fail(None)

    def fail[E](e: E): Pull[E, Nothing] =
      ZIO.fail(Some(e))

    def halt[E](cause: Cause[E]): Pull[E, Nothing] =
      ZIO.halt(cause.map(Some(_)))

    def recover[E, I](pull: Pull[E, I]): Cause[Option[E]] => Pull[E, I] =
      Cause.sequenceCauseOption(_).fold(pull)(halt)
  }
}
