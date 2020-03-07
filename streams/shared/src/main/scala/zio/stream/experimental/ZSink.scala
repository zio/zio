package zio.stream.experimental

import zio._

abstract class ZSink[-R, +E, -I, +Z](
  override val run: ZManaged[R, Nothing, Chunk[I] => ZIO[R, Either[E, Z], Chunk[Unit]]]
) extends ZConduit[R, E, I, Unit, Z](run)
