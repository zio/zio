package zio.stream.experimental

import zio._

class ZStream[-R, +E, -I, +B, +A](
  val process: ZManaged[R, Nothing, ZStream.Control[R, E, I, B, A]]
) extends AnyVal { self =>
  import ZStream.Control

  def map[C](f: A => C): ZStream[R, E, I, B, C] =
    ZStream {
      self.process.map { control =>
        Control(
          control.pull.map(f),
          control.command
        )
      }
    }
}

object ZStream {

  final case class Control[-R, +E, -I, +B, +A](
    pull: ZIO[R, Either[E, B], A],
    command: I => ZIO[R, E, Any]
  )

  object Pull {
    def end[B](b: => B): IO[Either[Nothing, B], Nothing] = IO.fail(Right(b))

    val endUnit: IO[Either[Nothing, Unit], Nothing] = end(())
  }

  object Command {
    val noop: Any => UIO[Unit] = _ => UIO.unit
  }

  def apply[R, E, I, B, A](process: ZManaged[R, Nothing, Control[R, E, I, B, A]]): ZStream[R, E, I, B, A] =
    new ZStream(process)

  def fromEffect[R, E, A](zio: ZIO[R, E, A]): ZStream[R, E, Any, Unit, A] =
    managed(zio.toManaged_)

  /**
   * Creates a single-valued stream from a managed resource
   */
  def managed[R, E, A](managed: ZManaged[R, E, A]): ZStream[R, E, Any, Unit, A] =
    ZStream {
      for {
        doneRef   <- Ref.make(false).toManaged_
        finalizer <- ZManaged.finalizerRef[R](_ => UIO.unit)
        pull = ZIO.uninterruptibleMask { restore =>
          doneRef.get.flatMap { done =>
            if (done) IO.fail(Right(()))
            else
              (for {
                _           <- doneRef.set(true)
                reservation <- managed.reserve
                _           <- finalizer.set(reservation.release)
                a           <- restore(reservation.acquire)
              } yield a).mapError(Left(_))
          }
        }
      } yield Control(pull, Command.noop)
    }
}
