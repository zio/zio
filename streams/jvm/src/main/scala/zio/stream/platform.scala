package zio.stream

import java.io.{ IOException, InputStream, OutputStream }

import zio.{ blocking => _, _ }
import zio.blocking._

import scala.util.control.NonFatal

trait StreamEffectPlatformSpecific {
  import StreamEffect.{ end, fail }

  final def fromInputStream[R <: Blocking](
    is: ZManaged[R, Throwable, InputStream],
    chunkSize: Int = ZStreamChunk.DefaultChunkSize
  ): StreamEffectChunk[R, Throwable, Byte] =
    StreamEffectChunk {
      StreamEffect(
        is.flatMap { is =>
          Managed.effectTotal { () =>
            {
              val buf = Array.ofDim[Byte](chunkSize)
              val bytesRead = try {
                is.read(buf)
              } catch {
                case NonFatal(e) => fail(e)
              }

              if (bytesRead < 0) end
              else if (0 < bytesRead && bytesRead < buf.length) Chunk.fromArray(buf).take(bytesRead)
              else if (bytesRead == buf.length) Chunk.fromArray(buf)
              else fail(new RuntimeException("Misbehaved InputStream: read more bytes than buffer length"))
            }
          }
        },
        blockingExecutor
      )
    }

  final def fromInputStream[R <: Blocking](
    is: ZIO[R, Throwable, InputStream],
    chunkSize: Int
  ): StreamEffectChunk[R, Throwable, Byte] =
    fromInputStream(is.toManaged(is => blocking(Task(is.close())).orDie), chunkSize)
}

trait ZStreamPlatformSpecific {

  /**
   * Creates a stream from a [[java.io.InputStream]]
   */
  final def fromInputStream[R <: Blocking](
    is: ZManaged[R, Throwable, InputStream],
    chunkSize: Int = ZStreamChunk.DefaultChunkSize
  ): ZStreamChunk[R, Throwable, Byte] =
    StreamEffect.fromInputStream(is, chunkSize)

  /**
   * Creates a stream from a [[java.io.InputStream]]
   */
  final def fromInputStream[R <: Blocking](
    is: ZIO[R, Throwable, InputStream],
    chunkSize: Int
  ): ZStreamChunk[R, Throwable, Byte] =
    StreamEffect.fromInputStream(is, chunkSize)
}

trait StreamPlatformSpecific {

  /**
   * See [[ZStream.fromInputStream]]
   */
  final def fromInputStream(
    is: Managed[Throwable, InputStream],
    chunkSize: Int = ZStreamChunk.DefaultChunkSize
  ): ZStreamChunk[Blocking, Throwable, Byte] =
    StreamEffect.fromInputStream(is, chunkSize)

  /**
   * See [[ZStream.fromInputStream]]
   */
  final def fromInputStream(
    is: IO[Throwable, InputStream],
    chunkSize: Int
  ): ZStreamChunk[Blocking, Throwable, Byte] =
    StreamEffect.fromInputStream(is, chunkSize)
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
