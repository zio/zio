package zio.stream.experimental

import zio.stream.compression.{CompressionLevel, CompressionStrategy, FlushMode, Gzipper}
import zio.{Chunk, ZIO, ZManaged}

object Gzip {
  def makeGzipper[Err, Done](
    bufferSize: Int = 64 * 1024,
    level: CompressionLevel = CompressionLevel.DefaultCompression,
    strategy: CompressionStrategy = CompressionStrategy.DefaultStrategy,
    flushMode: FlushMode = FlushMode.NoFlush
  ): ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[Byte], Done] =
    ZChannel.managed {
      ZManaged
        .make(
          Gzipper.make(bufferSize, level, strategy, flushMode)
        ) { gzipper =>
          ZIO.effectTotal(gzipper.close())
        }
    } {
      case gzipper => {

        lazy val loop: ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[Byte], Done] =
          ZChannel.readWithCause(
            chunk =>
              ZChannel.fromEffect {
                gzipper.onChunk(chunk)
              }.flatMap(chunk => ZChannel.write(chunk) *> loop),
            ZChannel.halt(_),
            done =>
              ZChannel.fromEffect {
                gzipper.onNone
              }.flatMap(chunk => ZChannel.write(chunk).as(done))
          )

        loop
      }
    }
}
