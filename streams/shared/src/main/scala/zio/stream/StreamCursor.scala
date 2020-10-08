package zio.stream

import zio._

/**
 * A cursor that allows effectful iteration over a stream. Usage is similar to
 * [[ZStream#Pull]], but additionally offers the option to traverse a stream multiple
 * times.
 */
trait StreamCursor[-R, +E, +A] {

  /**
   * Pull the next value of the stream. As with [[ZStream#Pull]], `fail(None)` signals end of stream.
   * If any cursor has advanced past this point already, the effect will not be reevaluated.
   */
  val next: ZIO[R, Option[E], Chunk[A]]

  /**
   * Creates a new cursor at the current position in the stream. The new cursor is guranteed to
   * see the same elements as the original cursor when advancing.
   */
  val split: UIO[StreamCursor[Any, E, A]]

  /**
   * Create a stream backed by this cursor.
   */
  final lazy val stream: ZStream[R, E, A] =
    ZStream(ZManaged.succeedNow(next))

}
