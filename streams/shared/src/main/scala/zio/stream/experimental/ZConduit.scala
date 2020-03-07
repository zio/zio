package zio.stream.experimental

import zio._

abstract class ZConduit[-R, +E, -I, +O, +Z] private[stream] (
  val run: ZManaged[R, Nothing, Option[Chunk[I]] => ZIO[R, Either[E, Z], Chunk[O]]]
) extends Serializable
