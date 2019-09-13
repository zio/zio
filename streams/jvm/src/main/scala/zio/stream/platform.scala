package zio.stream

import java.io.{ IOException, InputStream, OutputStream }

import zio._
import zio.blocking._

trait ZStreamPlatformSpecific {

  /**
   * Uses the provided `RIO` value to create a [[ZStream]] of byte chunks, backed by
   * the resulting `InputStream`. When data from the `InputStream` is exhausted,
   * the stream will close it.
   */
  def fromInputStream(
    is: InputStream,
    chunkSize: Int = ZStreamChunk.DefaultChunkSize
  ): ZStreamChunk[Blocking, IOException, Byte] =
    ZStreamChunk {
      ZStream.unfoldM(()) { _ =>
        effectBlocking {
          val buf       = Array.ofDim[Byte](chunkSize)
          val bytesRead = is.read(buf)

          if (bytesRead < 0) None
          else if (0 < bytesRead && bytesRead < buf.length) Some((Chunk.fromArray(buf).take(bytesRead), ()))
          else Some((Chunk.fromArray(buf), ()))
        } refineOrDie {
          case e: IOException => e
        }
      }
    }
}

trait ZSinkPlatformSpecific {

  /**
   * Uses the provided `OutputStream` to create a [[ZSink]] that consumes byte chunks
   * and writes them to the `OutputStream`. The sink will yield the count of bytes written.
   *
   * The caller of this function is responsible for closing the `OutputStream`.
   */
  def fromOutputStream(
    os: OutputStream
  ): ZSink[Blocking, IOException, Nothing, Chunk[Byte], Int] =
    ZSink.foldM(0)(_ => true) { (bytesWritten, byteChunk: Chunk[Byte]) =>
      effectBlocking {
        val bytes = byteChunk.toArray
        os.write(bytes)
        (bytesWritten + bytes.length, Chunk.empty)
      }.refineOrDie {
        case e: IOException => e
      }
    }
}
