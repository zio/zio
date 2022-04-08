package zio.stream

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stream.compression.{CompressionLevel, CompressionStrategy, FlushMode, Gzipper}
import zio.{Chunk, ZIO, ZTraceElement}

object Gzip {
  def makeGzipper[Err, Done](
    bufferSize: Int = 64 * 1024,
    level: CompressionLevel = CompressionLevel.DefaultCompression,
    strategy: CompressionStrategy = CompressionStrategy.DefaultStrategy,
    flushMode: FlushMode = FlushMode.NoFlush
  )(implicit trace: ZTraceElement): ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[Byte], Done] =
    ZChannel.unwrapScoped {
      ZIO
        .acquireRelease(
          Gzipper.make(bufferSize, level, strategy, flushMode)
        ) { gzipper =>
          ZIO.succeed(gzipper.close())
        }
        .map { case gzipper =>
          lazy val loop: ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[Byte], Done] =
            ZChannel.readWithCause(
              chunk =>
                ZChannel.fromZIO {
                  gzipper.onChunk(chunk)
                }.flatMap(chunk => ZChannel.write(chunk) *> loop),
              ZChannel.failCause(_),
              done =>
                ZChannel.fromZIO {
                  gzipper.onNone
                }.flatMap(chunk => ZChannel.write(chunk).as(done))
            )

          loop
        }
    }
}
