package zio.stream.experimental

import zio.stream.compression.{CompressionLevel, CompressionStrategy, FlushMode}
import zio.{Chunk, ZIO, ZManaged}

import java.util.zip.Deflater
import java.{util => ju}
import scala.annotation.tailrec

object Deflate {
  def makeDeflater[Err, Done](
    bufferSize: Int = 64 * 1024,
    noWrap: Boolean = false,
    level: CompressionLevel = CompressionLevel.DefaultCompression,
    strategy: CompressionStrategy = CompressionStrategy.DefaultStrategy,
    flushMode: FlushMode = FlushMode.NoFlush
  ): ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[Byte], Done] =
    ZChannel.managed {
      ZManaged
        .make(ZIO.effectTotal {
          val deflater = new Deflater(level.jValue, noWrap)
          deflater.setStrategy(strategy.jValue)
          (deflater, new Array[Byte](bufferSize))
        }) { case (deflater, _) =>
          ZIO.effectTotal(deflater.end())
        }
    } {
      case (deflater, buffer) => {

        def loop(): ZChannel[Any, Err, Chunk[Byte], Done, Err, Chunk[Byte], Done] =
          ZChannel.readWithCause(
            chunk =>
              ZChannel.fromEffect {
                ZIO.effectTotal {
                  deflater.setInput(chunk.toArray)
                  pullOutput(deflater, buffer, flushMode)
                }
              }.flatMap(chunk => ZChannel.write(chunk) *> loop()),
            ZChannel.halt(_),
            done =>
              ZChannel.fromEffect {
                ZIO.effectTotal {
                  deflater.finish()
                  val out = pullOutput(deflater, buffer, flushMode)
                  deflater.reset()
                  out
                }
              }.flatMap(chunk => ZChannel.write(chunk).as(done))
          )

        loop()
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
