package zio.stream

import zio._
import zio.duration._
import zio.internal.FiberRuntime
import zio.stream.ZStream.{ChannelReader, GroupedWithin, Pull}
import zio.stream.internal.{AutoCloseable, AutoReader, AutoWriter, Emit, EmitBuilder, Loop, Pull}
import zio.stream.internal.ZStreamInternal._

import scala.annotation.tailrec

object ZStream {

  /**
   * Creates a stream from an effect that produces a chunk of elements.
   */
  def fromChunk[R, E, O](chunk: Chunk[O]): ZStream[R, E, O] =
    ZStream.fromEffect(ZIO.succeed(chunk)).flatMap(ZStream.chunk(_))

  /**
   * Creates a stream from an effect that produces a chunk of elements.
   */
  def chunk[R, E, O](chunk: Chunk[O]): ZStream.fromEffect(ZIO.succeed(chunk)).flatMap(ZStream.chunk(_))

  /**
   * Creates a stream from an effect that produces a chunk of elements.
   ...
   // truncated for brevity
   ...
}
