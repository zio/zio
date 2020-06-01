package zio.stream

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption._
import java.nio.file.{ OpenOption, Path }

import scala.jdk.CollectionConverters._

import zio._
import zio.blocking.Blocking

trait ZSinkFromFileConstructor { self: ZSink.type =>

  /**
   * Uses the provided `Path` to create a [[ZSink]] that consumes byte chunks
   * and writes them to the `File`. The sink will yield count of bytes written.
   */
  final def fromFile(
    path: => Path,
    position: Long = 0L,
    options: Set[OpenOption] = Set(WRITE, TRUNCATE_EXISTING, CREATE)
  ): ZSink[Blocking, Throwable, Byte, Long] =
    ZSink {
      for {
        state <- Ref.make(0L).toManaged_
        channel <- ZManaged.make(
                    blocking.effectBlockingInterrupt(FileChannel.open(path, options.asJava).position(position)).orDie
                  )(chan => blocking.effectBlocking(chan.close()).orDie)
        push = (is: Option[Chunk[Byte]]) =>
          is match {
            case None => state.get.flatMap(Push.emit)
            case Some(byteChunk) =>
              for {
                justWritten <- blocking.effectBlockingInterrupt {
                                channel.write(ByteBuffer.wrap(byteChunk.toArray))
                              }.mapError(Left(_))
                more <- state.update(_ + justWritten) *> Push.more
              } yield more
          }
      } yield push
    }
}
