package zio.stream.compression

import zio.{ Chunk, ZIO, ZManaged }

import java.util.zip.Deflater
import java.{ util => ju }
import scala.annotation.tailrec

object Deflate {

  def makeDeflater(
    bufferSize: Int = 64 * 1024,
    noWrap: Boolean = false,
    level: CompressionLevel,
    strategy: CompressionStrategy,
    flushMode: FlushMode
  ): ZManaged[Any, Nothing, Option[Chunk[Byte]] => ZIO[Any, Nothing, Chunk[Byte]]] =
    ZManaged
      .make(ZIO.effectTotal {
        val deflater = new Deflater(level.jValue, noWrap)
        deflater.setStrategy(strategy.jValue)
        (deflater, new Array[Byte](bufferSize))
      }) { case (deflater, _) =>
        ZIO.effectTotal(deflater.end())
      }
      .map {
        case (deflater, buffer) => {
          case Some(chunk) =>
            ZIO.effectTotal {
              deflater.setInput(chunk.toArray)
              Deflate.pullOutput(deflater, buffer, flushMode)
            }
          case None =>
            ZIO.effectTotal {
              deflater.finish()
              val out = Deflate.pullOutput(deflater, buffer, flushMode)
              deflater.reset()
              out
            }
        }
      }

  private[compression] def pullOutput(deflater: Deflater, buffer: Array[Byte], flushMode: FlushMode): Chunk[Byte] = {
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
