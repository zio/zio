package zio.stream.experimental

import java.io.{ IOException, InputStream }
import java.{ util => ju }

import zio._
import zio.blocking.Blocking

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
        output  <- Queue.bounded[Take[E, A]](outputBuffer).toManaged(_.shutdown)
        runtime <- ZIO.runtime[R].toManaged_
        eitherStream <- ZManaged.effectTotal {
                         register(k =>
                           try {
                             runtime.unsafeRun(k.run.flatMap(output.offer))
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
                       output.take.flatMap(Pull.fromTake).onError(_ => done.set(true) *> output.shutdown)
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
        output  <- Queue.bounded[Take[E, A]](outputBuffer).toManaged(_.shutdown)
        runtime <- ZIO.runtime[R].toManaged_
        _ <- register { k =>
              try {
                runtime.unsafeRun(k.run.flatMap(output.offer))
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
            output.take.flatMap(Pull.fromTake).onError(_ => done.set(true) *> output.shutdown)
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
        output  <- Queue.bounded[Take[E, A]](outputBuffer).toManaged(_.shutdown)
        runtime <- ZIO.runtime[R].toManaged_
        maybeStream <- ZManaged.effectTotal {
                        register { k =>
                          try {
                            runtime.unsafeRun(k.run.flatMap(output.offer))
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
                       output.take.flatMap(Pull.fromTake).onError(_ => done.set(true) *> output.shutdown)
                   }
               }
      } yield pull
    }

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
