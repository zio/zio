package zio.stream

import zio._
import zio.duration._
import zio.stream.ZStream.GroupedWithin

private[stream] object ZStreamGroupedWithin {

  def apply[R, E, O](
    stream: ZStream[R, E, O],
    chunkSize: Int,
    within: Duration
  ): ZStream[R, E, Chunk[O]] =
    ZStream.unwrap {
      for {
        ref <- Ref.make[Chunk[O]](Chunk.empty)
        p   <- Promise.make[E, Unit]
      } yield new ZStream[R, E, Chunk[O]] {
        override def fold[R1 <: R, E1 >: E, A](
          z: A
        )(f: (A, Chunk[O]) => ZIO[R1, E1, A])(
          implicit trace: Trace
        ): ZIO[R1, E1, A] = {
          def loop(
            acc: A,
            current: Chunk[O]
          ): ZIO[R1, E1, A] =
            stream
              .fold(z)(f)
              .flatMap { _ =>
                if (current.isEmpty) ZIO.succeed(acc)
                else f(acc, current)
              }
          ???
        }
      }
    }
}
