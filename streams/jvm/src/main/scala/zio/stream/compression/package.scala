package zio.stream

import java.util.zip.{ DataFormatException, Inflater }
import java.{ util => ju }

import scala.annotation.tailrec

import zio._

package object compression {

  /**
   * Decompresses deflated stream. Compression method is described in https://tools.ietf.org/html/rfc1951.
   *
   * @param noWrap  Whether is wrapped in ZLIB header and trailer, see https://tools.ietf.org/html/rfc1951.
   *                For HTTP 'deflate' content-encoding should be false, see https://tools.ietf.org/html/rfc2616.
   * @param bufferSize Size of buffer used internally, affects performance.
   **/
  def inflate(
    bufferSize: Int = 64 * 1024,
    noWrap: Boolean = false
  ): ZTransducer[Any, CompressionException, Byte, Byte] = {
    def makeInflater(
      bufferSize: Int
    ): ZManaged[Any, Nothing, Option[zio.Chunk[Byte]] => ZIO[Any, CompressionException, Chunk[Byte]]] =
      ZManaged
        .make(ZIO.effectTotal((new Array[Byte](bufferSize), new Inflater(noWrap)))) {
          case (_, inflater) => ZIO.effectTotal(inflater.end())
        }
        .map {
          case (buffer, inflater) => {
            case None =>
              ZIO.succeed {
                //No need to pull, because after `pullAllOutput` there is nothing left in inflater.
                inflater.reset
                Chunk.empty
              }
            case Some(chunk) =>
              ZIO.effect {
                inflater.setInput(chunk.toArray)
                pullAllOutput(inflater, buffer, chunk)
              }.refineOrDie {
                case e: DataFormatException => CompressionException(e)
              }
          }
        }

    // Pulls all available output from the inflater.
    def pullAllOutput(
      inflater: Inflater,
      buffer: Array[Byte],
      input: Chunk[Byte]
    ): Chunk[Byte] = {
      @tailrec
      def next(acc: Chunk[Byte]): Chunk[Byte] = {
        val read      = inflater.inflate(buffer)
        val remaining = inflater.getRemaining()
        val current   = Chunk.fromArray(ju.Arrays.copyOf(buffer, read))
        if (remaining > 0) {
          if (read > 0) next(acc ++ current)
          else if (inflater.finished()) {
            val leftover = input.takeRight(remaining)
            inflater.reset()
            inflater.setInput(leftover.toArray)
            next(acc ++ current)
          } else {
            // Impossible happened (aka programmer error). Die.
            throw new Exception("read = 0, remaining > 0, not finished")
          }
        } else acc ++ current
      }
      if (inflater.needsInput()) Chunk.empty else next(Chunk.empty)
    }

    ZTransducer(makeInflater(bufferSize))
  }

  /**
   * Decompresses gzipped stream. Compression method is described in https://tools.ietf.org/html/rfc1952.
   *
   * @param bufferSize Size of buffer used internally, affects performance.
   **/
  def gunzip(bufferSize: Int = 64 * 1024): ZTransducer[Any, CompressionException, Byte, Byte] =
    ZTransducer(
      ZManaged
        .make(Gunzipper.make(bufferSize))(gunzipper => ZIO.effectTotal(gunzipper.close))
        .map { gunzipper =>
          {
            case None =>
              ZIO.succeed {
                gunzipper.reset
                Chunk.empty
              }
            case Some(chunk) => gunzipper.onChunk(chunk)
          }
        }
    )

  private[compression] def u8(b: Byte): Int = b & 0xff

  private[compression] def u16(b1: Byte, b2: Byte): Int = u8(b1) | (u8(b2) << 8)

  private[compression] def u32(b1: Byte, b2: Byte, b3: Byte, b4: Byte) = u16(b1, b2) | (u16(b3, b4) << 16)
}
