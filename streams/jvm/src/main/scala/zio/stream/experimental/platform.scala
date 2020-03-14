package zio.stream.experimental

import java.io.{ InputStream, IOException }

import zio._
import zio.blocking.Blocking

trait ZStreamPlatformSpecificConstructors {
  import ZStream.Pull

  /**
   * Creates a stream from a [[java.io.InputStream]]
   */
  def fromInputStream(
    is: => InputStream,
    chunkSize: Int = ZStream.DefaultChunkSize
  ): ZStream[Blocking, IOException, Byte] =
    ZStream {
      for {
        done       <- Ref.make(false).toManaged_
        buf        <- Ref.make(Array.ofDim[Byte](chunkSize)).toManaged_
        capturedIs <- Managed.effectTotal(is)
        pull = {
          def go: ZIO[Blocking, Option[IOException], Chunk[Byte]] = done.get.flatMap {
            if (_) Pull.end
            else
              for {
                bufArray <- buf.get
                bytesRead <- blocking.effectBlocking(capturedIs.read(bufArray))
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


}
