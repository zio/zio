package zio.stream.experimental

import zio._

class ZSink[-R, +E, +I, -A, +B](
  val process: ZManaged[R, E, ZSink.Control[R, E, I, A, B]]
) extends AnyVal { self =>
  import ZSink.Control

  def map[C](f: B => C): ZSink[R, E, I, A, C] =
    ZSink {
      self.process.map { control =>
        Control(
          a => control.push(a).mapError(_.map(f)),
          control.query
        )
      }
    }
}

object ZSink {

  final case class Control[-R, +E, +I, -A, +B](
    push: A => ZIO[R, Either[E, B], Any],
    query: ZIO[R, E, I]
  )

  def apply[R, E, I, A, B](process: ZManaged[R, E, ZSink.Control[R, E, I, A, B]]): ZSink[R, E, I, A, B] =
    new ZSink(process)
}
