package zio.stream

import zio._

trait StreamCursor[-R, +E, +A] {
  val next: ZIO[R, Option[E], Chunk[A]]
  val split: UIO[StreamCursor[R, E, A]]
  final lazy val stream: ZStream[R, E, A] = ZStream(ZManaged.succeedNow(next))
}
