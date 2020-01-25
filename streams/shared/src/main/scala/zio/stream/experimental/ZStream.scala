package zio.stream.experimental

import zio._

class ZStream[-R, +E, -I, +O, +A](val process: ZManaged[R, Nothing, ZStream.Pull[R, E, I, O, A]]) { self =>
  def map[B](f: A => B): ZStream[R, E, I, O, B] =
    ZStream(self.process.map(pull => i => pull(i).map(f)))
}

object ZStream {
  type Pull[-R, +E, -I, +O, +A] = I => ZIO[R, Either[O, E], A]
  object Pull {
    val end: Pull[Any, Nothing, Any, Unit, Nothing] = _ => IO.fail(Left(()))
  }

  def apply[R, E, I, O, A](process: ZManaged[R, Nothing, Pull[R, E, I, O, A]]): ZStream[R, E, I, O, A] =
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
        pull = (_: Any) =>
          ZIO.uninterruptibleMask { restore =>
            doneRef.get.flatMap { done =>
              if (done) IO.fail(Left(()))
              else
                (for {
                  _           <- doneRef.set(true)
                  reservation <- managed.reserve
                  _           <- finalizer.set(reservation.release)
                  a           <- restore(reservation.acquire)
                } yield a).mapError(Right(_))
            }
          }
      } yield pull
    }
}
