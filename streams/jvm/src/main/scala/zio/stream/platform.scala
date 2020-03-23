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
  final def fromJavaStreamTotal[A](stream: => ju.stream.Stream[A]): ZStream[Any, Nothing, A] =
    ZStream.fromJavaIteratorTotal(stream.iterator())

  /**
   * Creates a stream from a Java stream
   */
  final def fromJavaStream[A](stream: => ju.stream.Stream[A]): ZStream[Any, Throwable, A] =
    ZStream.fromJavaIterator(stream.iterator())

  /**
   * Creates a stream from a Java stream
   */
  final def fromJavaStreamEffect[R, A](stream: ZIO[R, Throwable, ju.stream.Stream[A]]): ZStream[R, Throwable, A] =
    ZStream.fromJavaIteratorEffect(stream.flatMap(s => UIO(s.iterator())))

  /**
   * Creates a stream from a managed Java stream
   */
  final def fromJavaStreamManaged[R, A](stream: ZManaged[R, Throwable, ju.stream.Stream[A]]): ZStream[R, Throwable, A] =
    ZStream.fromJavaIteratorManaged(stream.mapM(s => UIO(s.iterator())))
}

trait StreamPlatformSpecificConstructors {

  /**
   * See [[ZStream.fromJavaStreamTotal]]
   */
  final def fromJavaStreamTotal[A](stream: => ju.stream.Stream[A]): Stream[Nothing, A] =
    ZStream.fromJavaStreamTotal(stream)

  /**
   * See [[ZStream.fromJavaStream]]
   */
  final def fromJavaStream[A](stream: => ju.stream.Stream[A]): Stream[Throwable, A] =
    ZStream.fromJavaStream(stream)

  /**
   * See [[ZStream.fromJavaStreamEffect]]
   */
  final def fromJavaStreamEffect[A](stream: IO[Throwable, ju.stream.Stream[A]]): Stream[Throwable, A] =
    ZStream.fromJavaStreamEffect(stream)

  /**
   * See [[ZStream.fromJavaStreamManaged]]
   */
  final def fromJavaStreamManaged[A](stream: Managed[Throwable, ju.stream.Stream[A]]): Stream[Throwable, A] =
    ZStream.fromJavaStreamManaged(stream)
}
