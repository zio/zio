package zio.stream.experimental

import zio._

abstract class ZSink[-R, +E, -I, +Z] private (
  val push: ZManaged[R, Nothing, Option[Chunk[I]] => ZIO[R, Either[E, Z], Unit]]
) extends ZConduit[R, E, I, Unit, Z](push.map(push => input => push(input).as(Chunk.empty))) { self =>
  def map[Z2](f: Z => Z2): ZSink[R, E, I, Z2] =
    new ZSink[R, E, I, Z2](self.push.map(push => input => push(input).mapError(_.right.map(f)))) {}
}

object ZSink {
  def apply[R, E, I, Z](push: ZManaged[R, Nothing, Option[Chunk[I]] => ZIO[R, Either[E, Z], Unit]]) =
    new ZSink(push) {}

  /**
   * A sink that counts the number of elements fed to it.
   */
  val count: ZSink[Any, Nothing, Any, Long] =
    fold(0L)((s, _) => s + 1)

  def fromPush[R, E, I, Z](push: Option[Chunk[I]] => ZIO[R, Either[E, Z], Unit]): ZSink[R, E, I, Z] =
    ZSink(Managed.succeed(push))

  /**
   * A sink that immediately ends with the specified value.
   */
  def succeed[Z](z: Z): ZSink[Any, Nothing, Any, Z] =
    fromPush {
      case Some(_) => UIO.unit
      case None    => ZIO.fail(Right(z))
    }

  /**
   * A sink that effectfully folds its inputs with the provided function and initial state.
   */
  def foldM[R, E, I, S](z: S)(f: (S, I) => ZIO[R, E, S]): ZSink[R, E, I, S] =
    ZSink {
      for {
        state <- Ref.make(z).toManaged_
        push = { (inputs: Option[Chunk[I]]) =>
          inputs match {
            case None => state.get.flatMap(s => ZIO.fail(Right(s)))
            case Some(value) =>
              state.get
                .flatMap(value.foldM(_)(f))
                .flatMap(state.set)
                .mapError(Left(_))
          }
        }
      } yield push
    }

  /**
   * A sink that folds its inputs with the provided function and initial state.
   */
  def fold[I, S](z: S)(f: (S, I) => S): ZSink[Any, Nothing, I, S] =
    ZSink {
      for {
        state <- Ref.make(z).toManaged_
        push = { (inputs: Option[Chunk[I]]) =>
          inputs match {
            case None => state.get.flatMap(s => ZIO.fail(Right(s)))
            case Some(value) =>
              state.update(value.fold(_)(f))
          }
        }
      } yield push
    }
}
