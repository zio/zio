package zio.stream.encoding1

import zio.{ Cause, Exit, UIO, ZIO }

final case class Take[+E, +I](exit: Exit[Option[E], I]) extends AnyVal {

  def isDone: Boolean =
    exit.fold(Cause.sequenceCauseOption(_).isEmpty, _ => false)

  def toPull: Pull[E, I] =
    ZIO.done(exit)
}

object Take {

  def fromPull[E, I](pull: Pull[E, I]): UIO[Take[E, I]] =
    pull.foldCause(c => Take(Exit.halt(c)), i => Take(Exit.succeed(i)))
}
