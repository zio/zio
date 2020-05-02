package zio.stream

import zio._

abstract class ZConduit[-R, +E, -I, +O, +Z] private[stream] (
  val run: ZManaged[R, Nothing, Option[Chunk[I]] => ZIO[R, Either[E, Z], Chunk[O]]]
) extends Serializable { self =>

  /**
   * Composes this conduit with another, resulting in a composite conduit that pipes elements
   * through both conduits but keeps only the result of the `that` conduit.
   */
  def >>>[R1 <: R, E1 >: E, O2, Z2](that: ZConduit[R1, E1, O, O2, Z2]): ZConduit[R1, E1, I, O2, Z2] =
    ZConduit {
      for {
        pushLeft  <- self.run
        pushRight <- that.run
        push = (input: Option[Chunk[I]]) =>
          pushLeft(input).foldM(
            {
              case Left(e)  => ZIO.fail(Left(e))
              case Right(_) => pushRight(None)
            },
            chunk => pushRight(Some(chunk))
          )
      } yield push
    }

  /**
   * Effectfully transforms the results of this conduit.
   */
  def mapResultM[R1 <: R, E1 >: E, Z2](f: Z => ZIO[R1, E1, Z2]): ZConduit[R1, E1, I, O, Z2] =
    ZConduit[R1, E1, I, O, Z2](
      run.map(push =>
        input =>
          push(input).catchAll {
            case Left(e)  => ZIO.fail(Left(e))
            case Right(z) => f(z).foldM(e => ZIO.fail(Left(e)), z2 => ZIO.fail(Right(z2)))
          }
      )
    )

  def mapResult[Z2](f: Z => Z2): ZConduit[R, E, I, O, Z2] = mapResultM(z => UIO.succeedNow(f(z)))

  def mapOutput[O2](f: O => O2): ZConduit[R, E, I, O2, Z] =
    mapOutputChunks(_.map(f))

  def mapOutputM[R1 <: R, E1 >: E, O2](f: O => ZIO[R1, E1, O2]): ZConduit[R1, E1, I, O2, Z] =
    ZConduit {
      run.map(push => (input: Option[Chunk[I]]) => push(input).flatMap(_.mapM(f).mapError(Left(_))))
    }

  def mapOutputChunks[O2](f: Chunk[O] => Chunk[O2]): ZConduit[R, E, I, O2, Z] =
    ZConduit {
      run.map(push => (input: Option[Chunk[I]]) => push(input).map(f))
    }

  def mapOutputChunksM[R1 <: R, E1 >: E, O2](f: Chunk[O] => ZIO[R1, E1, Chunk[O2]]): ZConduit[R1, E1, I, O2, Z] =
    ZConduit {
      run.map(push => (input: Option[Chunk[I]]) => push(input).flatMap(f(_).mapError(Left(_))))
    }
}

object ZConduit {
  def apply[R, E, I, O, Z](
    run: ZManaged[R, Nothing, Option[Chunk[I]] => ZIO[R, Either[E, Z], Chunk[O]]]
  ): ZConduit[R, E, I, O, Z] =
    new ZConduit(run) {}
}
