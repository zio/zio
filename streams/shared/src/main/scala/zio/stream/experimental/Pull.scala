package zio.stream.experimental

import zio.{ Cause, ZIO }

object Pull {

  def apply[R, E, A](z: ZIO[R, E, A]): Pull[R, E, A] =
    z.mapError(Some(_))

  def emit[A](a: A): Pull[Any, Nothing, A] =
    ZIO.succeedNow(a)

  val end: Pull[Any, Nothing, Nothing] = ZIO.fail(None)

  def fail[E](e: E): Pull[Any, E, Nothing] =
    halt(Cause.fail(e))

  def halt[E](c: Cause[E]): Pull[Any, E, Nothing] =
    ZIO.halt(c.map(Option.apply))

  def recover[R, E, A](p: Pull[R, E, A]): Cause[Option[E]] => Pull[R, E, A] =
    Cause.sequenceCauseOption(_).fold(p)(halt)
}
