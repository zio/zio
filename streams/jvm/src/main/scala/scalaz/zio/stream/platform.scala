package scalaz.zio.stream

import java.io.{ InputStream, OutputStream }

import scalaz.zio._
import scalaz.zio.blocking._

trait ZStreamPlatformSpecific {

  /**
   * Uses the provided `TaskR` value to create a [[ZStream]] of byte chunks, backed by
   * the resulting `InputStream`. When data from the `InputStream` is exhausted,
   * the stream will close it.
   */
  def fromInputStream[R](
    is: TaskR[R, InputStream],
    chunkSize: Int = ZStreamChunk.DefaultChunkSize
  ): ZStreamChunk[R with Blocking, Throwable, Byte] =
    ZStreamChunk {
      ZStream.managed(is.toManaged(is => effectBlocking(is.close()).orDie)).flatMap { is =>
        ZStream.unfoldM(()) { _ =>
          effectBlocking {
            val buf       = Array.ofDim[Byte](chunkSize)
            val bytesRead = is.read(buf)

            if (bytesRead < 0) None
            else if (0 < bytesRead && bytesRead < buf.size) Some((Chunk.fromArray(buf).take(buf.size), ()))
            else Some((Chunk.fromArray(buf), ()))
          }
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
  ): ZSink[Blocking, Throwable, Nothing, Chunk[Byte], Int] =
    ZSink.foldM(0) { (bytesWritten, byteChunk: Chunk[Byte]) =>
      effectBlocking {
        val bytes = byteChunk.toArray
        os.write(bytes)
        ZSink.Step.more(bytesWritten + bytes.size)
      }
    }
}
