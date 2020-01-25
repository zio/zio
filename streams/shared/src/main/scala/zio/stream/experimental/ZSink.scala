package zio.stream.experimental

import zio._

class ZSink[-R, +E, +I, -A, +B](val process: ZManaged[R, E, ZSink.Push[R, E, I, A, B]]) {
  def map[C](f: B => C): ZSink[R, E, I, A, C] =
    ZSink(process.map(push => a => push(a).mapError(_.left.map(f))))

}

object ZSink {
  type Push[-R, +E, +I, -A, +B] = A => ZIO[R, Either[B, E], I]

  def apply[R, E, I, A, B](process: ZManaged[R, E, ZSink.Push[R, E, I, A, B]]): ZSink[R, E, I, A, B] =
    new ZSink(process)
}
