package zio.stream.experimental

import java.io.{ IOException, InputStream }
import java.{ util => ju }

import zio._
import zio.blocking.Blocking

trait ZStreamPlatformSpecificConstructors { self: ZStream.type =>

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
                bytesRead <- blocking
                              .effectBlocking(capturedIs.read(bufArray))
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
   * Creates a stream from a Java stream
   */
  final def fromJavaStream[R, E, A](stream: => ju.stream.Stream[A]): ZStream[R, E, A] =
    ZStream.fromJavaIterator(stream.iterator())

  /**
   * Creates a stream from a managed Java stream
   */
  final def fromJavaStreamManaged[R, E, A](stream: ZManaged[R, E, ju.stream.Stream[A]]): ZStream[R, E, A] =
    ZStream.fromJavaIteratorManaged(stream.mapM(s => UIO(s.iterator())))
}
