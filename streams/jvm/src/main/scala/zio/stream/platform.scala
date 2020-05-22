package zio.stream

import java.io.{ IOException, InputStream, OutputStream }
import java.{ util => ju }

import zio._
import zio.blocking.Blocking

trait ZSinkPlatformSpecificConstructors { self: ZSink.type =>

  /**
   * Uses the provided `OutputStream` to create a [[ZSink]] that consumes byte chunks
   * and writes them to the `OutputStream`. The sink will yield the count of bytes written.
   *
   * The caller of this function is responsible for closing the `OutputStream`.
   */
  final def fromOutputStream(
    os: OutputStream
  ): ZSink[Blocking, IOException, Byte, Long] =
    ZSink.foldLeftChunksM(0L) { (bytesWritten, byteChunk: Chunk[Byte]) =>
      blocking.effectBlockingInterrupt {
        val bytes = byteChunk.toArray
        os.write(bytes)
        bytesWritten + bytes.length
      }.refineOrDie {
        case e: IOException => e
      }
    }
}

trait ZStreamPlatformSpecificConstructors { self: ZStream.type =>

  /**
   * Creates a stream from an asynchronous callback that can be called multiple times.
   * The optionality of the error type `E` can be used to signal the end of the stream,
   * by setting it to `None`.
   */
  def effectAsync[R, E, A](
    register: (ZIO[R, Option[E], Chunk[A]] => Unit) => Unit,
    outputBuffer: Int = 16
  ): ZStream[R, E, A] =
    effectAsyncMaybe(callback => {
      register(callback)
      None
    }, outputBuffer)

  /**
   * Creates a stream from an asynchronous callback that can be called multiple times.
   * The registration of the callback returns either a canceler or synchronously returns a stream.
   * The optionality of the error type `E` can be used to signal the end of the stream, by
   * setting it to `None`.
   */
  def effectAsyncInterrupt[R, E, A](
    register: (ZIO[R, Option[E], Chunk[A]] => Unit) => Either[Canceler[R], ZStream[R, E, A]],
    outputBuffer: Int = 16
  ): ZStream[R, E, A] =
    ZStream {
      for {
        output  <- Queue.bounded[stream.Take[E, A]](outputBuffer).toManaged(_.shutdown)
        runtime <- ZIO.runtime[R].toManaged_
        eitherStream <- ZManaged.effectTotal {
                         register(k =>
                           try {
                             runtime.unsafeRun(stream.Take.fromPull(k).flatMap(output.offer))
                             ()
                           } catch {
                             case FiberFailure(c) if c.interrupted =>
                           }
                         )
                       }
        pull <- eitherStream match {
                 case Left(canceler) =>
                   (for {
                     done <- ZRef.makeManaged(false)
                   } yield done.get.flatMap {
                     if (_) Pull.end
                     else
                       output.take.flatMap(_.done).onError(_ => done.set(true) *> output.shutdown)
                   }).ensuring(canceler)
                 case Right(stream) => output.shutdown.toManaged_ *> stream.process
               }
      } yield pull
    }

  /**
   * Creates a stream from an asynchronous callback that can be called multiple times
   * The registration of the callback itself returns an effect. The optionality of the
   * error type `E` can be used to signal the end of the stream, by setting it to `None`.
   */
  def effectAsyncM[R, E, A](
    register: (ZIO[R, Option[E], Chunk[A]] => Unit) => ZIO[R, E, Any],
    outputBuffer: Int = 16
  ): ZStream[R, E, A] =
    managed {
      for {
        output  <- Queue.bounded[stream.Take[E, A]](outputBuffer).toManaged(_.shutdown)
        runtime <- ZIO.runtime[R].toManaged_
        _ <- register { k =>
              try {
                runtime.unsafeRun(stream.Take.fromPull(k).flatMap(output.offer))
                ()
              } catch {
                case FiberFailure(c) if c.interrupted =>
              }
            }.toManaged_
        done <- ZRef.makeManaged(false)
        pull = done.get.flatMap {
          if (_)
            Pull.end
          else
            output.take.flatMap(_.done).onError(_ => done.set(true) *> output.shutdown)
        }
      } yield pull
    }.flatMap(repeatEffectChunkOption(_))

  /**
   * Creates a stream from an asynchronous callback that can be called multiple times.
   * The registration of the callback can possibly return the stream synchronously.
   * The optionality of the error type `E` can be used to signal the end of the stream,
   * by setting it to `None`.
   */
  def effectAsyncMaybe[R, E, A](
    register: (ZIO[R, Option[E], Chunk[A]] => Unit) => Option[ZStream[R, E, A]],
    outputBuffer: Int = 16
  ): ZStream[R, E, A] =
    ZStream {
      for {
        output  <- Queue.bounded[stream.Take[E, A]](outputBuffer).toManaged(_.shutdown)
        runtime <- ZIO.runtime[R].toManaged_
        maybeStream <- ZManaged.effectTotal {
                        register { k =>
                          try {
                            runtime.unsafeRun(stream.Take.fromPull(k).flatMap(output.offer))
                            ()
                          } catch {
                            case FiberFailure(c) if c.interrupted =>
                          }
                        }
                      }
        pull <- maybeStream match {
                 case Some(stream) => output.shutdown.toManaged_ *> stream.process
                 case None =>
                   for {
                     done <- ZRef.makeManaged(false)
                   } yield done.get.flatMap {
                     if (_)
                       Pull.end
                     else
                       output.take.flatMap(_.done).onError(_ => done.set(true) *> output.shutdown)
                   }
               }
      } yield pull
    }

  /**
   * Creates a stream from a [[java.io.InputStream]]
   * Note: the input stream will not be explicitly closed after
   * it is exhausted.
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
   * Creates a stream from a [[java.io.InputStream]]. Ensures that the input
   * stream is closed after it is exhausted.
   */
  def fromInputStreamEffect[R](
    is: ZIO[R, IOException, InputStream],
    chunkSize: Int = ZStream.DefaultChunkSize
  ): ZStream[R with Blocking, IOException, Byte] =
    fromInputStreamManaged(is.toManaged(is => ZIO.effectTotal(is.close())), chunkSize)

  /**
   * Creates a stream from a managed [[java.io.InputStream]] value.
   */
  def fromInputStreamManaged[R](
    is: ZManaged[R, IOException, InputStream],
    chunkSize: Int = ZStream.DefaultChunkSize
  ): ZStream[R with Blocking, IOException, Byte] =
    ZStream
      .managed(is)
      .flatMap(fromInputStream(_, chunkSize))

  /**
   * Creates a stream from a Java stream
   */
  final def fromJavaStream[R, A](stream: => ju.stream.Stream[A]): ZStream[R, Throwable, A] =
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

  /**
   * Creates a stream from a Java stream
   */
  final def fromJavaStreamTotal[A](stream: => ju.stream.Stream[A]): ZStream[Any, Nothing, A] =
    ZStream.fromJavaIteratorTotal(stream.iterator())
}
