package zio.stream.experimental

import zio._

abstract class ZTransducer[-R, +E, -I, +O](
  val push: ZManaged[R, Nothing, Option[Chunk[I]] => ZIO[R, Either[E, Unit], Chunk[O]]]
) extends ZConduit[R, E, I, O, Unit](push)

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
