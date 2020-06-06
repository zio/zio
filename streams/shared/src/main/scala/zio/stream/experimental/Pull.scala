package zio.stream.experimental

import zio.{ Cause, ZIO }

object Pull {

  def apply[R, E, A](z: ZIO[R, E, A]): Pull[R, E, A] =
    z.mapError(Some(_))

  def emit[A](a: A): Pull[Any, Nothing, A] =
    ZIO.succeedNow(a)

  val end: Pull[Any, Nothing, Nothing] =
    ZIO.fail(None)

  def halt[E](cause: Cause[E]): Pull[Any, E, Nothing] =
    ZIO.halt(cause.map(Option.apply))

  def recover[R, E, A](z: Pull[R, E, A]): Cause[Option[E]] => Pull[R, E, A] =
    Cause.sequenceCauseOption(_).fold(z)(halt)

  val unit: Pull[Any, Nothing, Unit] = ZIO.unit
}
