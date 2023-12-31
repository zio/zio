package zio.stream

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stream.compression.{CompressionLevel, CompressionStrategy, FlushMode}
import zio.{Chunk, ZIO, Trace}

import java.util.zip.Deflater
import java.{util => ju}
import scala.annotation.tailrec

private object Deflate {
  def makeDeflater[Err, Done](
    bufferSize: Int = 64 * 1024,
    noWrap: Boolean = false,
    level: CompressionLevel = CompressionLevel.DefaultCompression,
    strategy: CompressionStrategy = CompressionStrategy.DefaultStrategy,
    flushMode: FlushMode = FlushMode.NoFlush
  )(implicit trace: Trace): ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[Byte], Done] =
    ZChannel.unwrapScoped {
      ZIO
        .acquireRelease(ZIO.succeed {
          val deflater = new Deflater(level.jValue, noWrap)
          deflater.setStrategy(strategy.jValue)
          (deflater, new Array[Byte](bufferSize))
        }) { case (deflater, _) =>
          ZIO.succeed(deflater.end())
        }
        .map {
          case (deflater, buffer) => {

            lazy val loop: ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[Byte], Done] =
              ZChannel.readWithCause(
                chunk =>
                  ZChannel.succeed {
                    deflater.setInput(chunk.toArray)
                    pullOutput(deflater, buffer, flushMode)
                  }.flatMap(chunk => ZChannel.write(chunk) *> loop),
                ZChannel.refailCause,
                done =>
                  ZChannel.succeed {
                    deflater.finish()
                    val out = pullOutput(deflater, buffer, flushMode)
                    deflater.reset()
                    out
                  }.flatMap(chunk => ZChannel.write(chunk).as(done))
              )

            loop
          }
        }
    }

  private def pullOutput(deflater: Deflater, buffer: Array[Byte], flushMode: FlushMode): Chunk[Byte] = {
    @tailrec
    def next(acc: Chunk[Byte]): Chunk[Byte] = {
      val size    = deflater.deflate(buffer, 0, buffer.length, flushMode.jValue)
      val current = Chunk.fromArray(ju.Arrays.copyOf(buffer, size))
      if (current.isEmpty) acc
      else next(acc ++ current)
    }

    next(Chunk.empty)
  }
}
