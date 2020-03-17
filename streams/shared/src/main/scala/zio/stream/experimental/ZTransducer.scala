package zio.stream.experimental

import zio._

abstract class ZTransducer[-R, +E, -I, +O](
  val push: ZManaged[R, Nothing, Option[Chunk[I]] => ZIO[R, Either[E, Unit], Chunk[O]]]
) extends ZConduit[R, E, I, O, Unit](push) { self =>

  /**
   * Compose this transducer with another transducer, resulting in a composite transducer.
   */
  def >>>[R1 <: R, E1 >: E, O2 >: O, O3](that: ZTransducer[R1, E1, O2, O3]): ZTransducer[R1, E1, I, O3] =
    ZTransducer {
      for {
        pushLeft  <- self.push
        pushRight <- that.push
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
   * Compose this transducer with a sink, resulting in a sink that processes elements by piping
   * them through this transducer and piping the results into the sink.
   */
  def >>>[R1 <: R, E1 >: E, O2 >: O, Z](that: ZSink[R1, E1, O2, Z]): ZSink[R1, E1, I, Z] =
    ZSink {
      for {
        pushSelf <- self.push
        pushThat <- that.push
        push = (input: Option[Chunk[I]]) =>
          pushSelf(input).foldM(
            {
              case Left(e)  => ZIO.fail(Left(e))
              case Right(_) => pushThat(None)
            },
            chunk => pushThat(Some(chunk))
          )
      } yield push
    }
}

object ZTransducer {
  def apply[R, E, I, O](
    push: ZManaged[R, Nothing, Option[Chunk[I]] => ZIO[R, Either[E, Unit], Chunk[O]]]
  ): ZTransducer[R, E, I, O] =
    new ZTransducer(push) {}

  def fromPush[R, E, I, O](push: Option[Chunk[I]] => ZIO[R, Either[E, Unit], Chunk[O]]): ZTransducer[R, E, I, O] =
    ZTransducer(Managed.succeed(push))

  /**
   * A transducer that re-chunks the elements fed to it into chunks of up to
   * `n` elements each.
   */
  def chunkN[I](n: Int): ZTransducer[Any, Nothing, I, I] =
    ZTransducer {
      for {
        buffered <- Ref.make[Chunk[I]](Chunk.empty).toManaged_
        done     <- Ref.make(false).toManaged_
        push = { (input: Option[Chunk[I]]) =>
          input match {
            case None => done.set(true) *> buffered.getAndSet(Chunk.empty)
            case Some(is) =>
              buffered.modify { buffered =>
                val concat = buffered ++ is
                if (concat.size >= n) (concat.take(n), concat.drop(n))
                else (concat, Chunk.empty)
              }
          }
        }
      } yield push
    }
}
