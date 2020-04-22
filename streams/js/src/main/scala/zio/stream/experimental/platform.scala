package zio.stream.experimental

import java.io.{ IOException, InputStream }

import zio._

trait ZStreamPlatformSpecificConstructors {
  import ZStream.Pull

  /**
   * Creates a stream from a [[java.io.InputStream]]
   */
  def fromInputStream(
    is: => InputStream,
    chunkSize: Int = ZStream.DefaultChunkSize
  ): ZStream[Any, IOException, Byte] =
    ZStream {
      for {
        done       <- Ref.make(false).toManaged_
        buf        <- Ref.make(Array.ofDim[Byte](chunkSize)).toManaged_
        capturedIs <- Managed.effectTotal(is)
        pull = {
          def go: ZIO[Any, Option[IOException], Chunk[Byte]] = done.get.flatMap {
            if (_) Pull.end
            else
              for {
                bufArray <- buf.get
                bytesRead <- Task(capturedIs.read(bufArray))
                              .refineToOrDie[IOException]
                              .mapError(Some(_))
                bytes <- if (bytesRead < 0)
                          done.set(true) *> Pull.end
                        else if (bytesRead == 0)
                          go
                        else if (bytesRead < bufArray.length)
                          Pull.emit(Chunk.fromArray(bufArray).take(bytesRead))
                        else
                          Pull.emit(Chunk.fromArray(bufArray))
              } yield bytes
          }

          go
        }
      } yield pull
    }

  /**
   * Creates a stream from a [[java.io.InputStream]]. Ensures that the input
   * stream is closed after it is exhausted.
   */
  def fromInputStreamEffect[R](
    is: ZIO[R, IOException, InputStream],
    chunkSize: Int = ZStream.DefaultChunkSize
  ): ZStream[R, IOException, Byte] =
    fromInputStreamManaged(is.toManaged(is => ZIO.effectTotal(is.close())), chunkSize)

  /**
   * Creates a stream from a managed [[java.io.InputStream]] value.
   */
  def fromInputStreamManaged[R](
    is: ZManaged[R, IOException, InputStream],
    chunkSize: Int = ZStream.DefaultChunkSize
  ): ZStream[R, IOException, Byte] =
    ZStream
      .managed(is)
      .flatMap(fromInputStream(_, chunkSize))
}
