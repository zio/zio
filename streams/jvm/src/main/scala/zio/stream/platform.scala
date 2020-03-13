package zio.stream

import java.io.{ IOException, OutputStream }
import java.{ util => ju }

import zio._
import zio.blocking._

trait ZSinkPlatformSpecificConstructors {

  /**
   * Uses the provided `OutputStream` to create a [[ZSink]] that consumes byte chunks
   * and writes them to the `OutputStream`. The sink will yield the count of bytes written.
   *
   * The caller of this function is responsible for closing the `OutputStream`.
   */
  final def fromOutputStream(
    os: OutputStream
  ): ZSink[Blocking, IOException, Nothing, Chunk[Byte], Int] =
    ZSink.foldM(0)(_ => true) { (bytesWritten, byteChunk: Chunk[Byte]) =>
      effectBlockingInterrupt {
        val bytes = byteChunk.toArray
        os.write(bytes)
        (bytesWritten + bytes.length, Chunk.empty)
      }.refineOrDie {
        case e: IOException => e
      }
    }
}

trait ZStreamPlatformSpecificConstructors {

  /**
   * Creates a stream from a Java stream
   */
  final def fromJavaStream[R, E, A](stream: ZIO[R, E, ju.stream.Stream[A]]): ZStream[R, E, A] =
    ZStream.fromJavaIterator(stream.flatMap(s => UIO(s.iterator())))

  /**
   * Creates a stream from a managed Java stream
   */
  final def fromJavaStreamManaged[R, E, A](stream: ZManaged[R, E, ju.stream.Stream[A]]): ZStream[R, E, A] =
    ZStream.fromJavaIteratorManaged(stream.mapM(s => UIO(s.iterator())))
}

trait StreamPlatformSpecificConstructors {

  /**
   * See [[ZStream.fromJavaIterator]]
   */
  final def fromJavaStream[E, A](stream: IO[E, ju.stream.Stream[A]]): Stream[E, A] =
    ZStream.fromJavaStream(stream)

  /**
   * See [[ZStream.fromJavaIteratorManaged]]
   */
  final def fromJavaStreamManaged[E, A](stream: Managed[E, ju.stream.Stream[A]]): Stream[E, A] =
    ZStream.fromJavaStreamManaged(stream)
}
