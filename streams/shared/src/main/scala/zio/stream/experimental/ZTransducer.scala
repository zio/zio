package zio.stream.experimental

import zio._

abstract class ZTransducer[-R, +E, -I, +O](
  override val run: ZManaged[R, Nothing, Chunk[I] => ZIO[R, Either[E, Nothing], Chunk[O]]]
) extends ZConduit[R, E, I, O, Nothing](run)
