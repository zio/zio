package zio.stream.experimental

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.concurrent.Future

trait ZSinkPlatformSpecificConstructors

trait ZStreamPlatformSpecificConstructors {
  self: ZStream.type =>

  /**
   * Creates a stream from an asynchronous callback that can be called multiple times.
   * The optionality of the error type `E` can be used to signal the end of the stream,
   * by setting it to `None`.
   */
  def async[R, E, A](
    register: ZStream.Emit[R, E, A, Future[Boolean]] => Unit,
    outputBuffer: Int = 16
  )(implicit trace: ZTraceElement): ZStream[R, E, A] =
    asyncMaybe(
      callback => {
        register(callback)
        None
      },
      outputBuffer
    )

  /**
   * Creates a stream from an asynchronous callback that can be called multiple times.
   * The registration of the callback returns either a canceler or synchronously returns a stream.
   * The optionality of the error type `E` can be used to signal the end of the stream, by
   * setting it to `None`.
   */
  def asyncInterrupt[R, E, A](
    register: ZStream.Emit[R, E, A, Future[Boolean]] => Either[Canceler[R], ZStream[R, E, A]],
    outputBuffer: Int = 16
  )(implicit trace: ZTraceElement): ZStream[R, E, A] =
    ZStream.unwrapManaged(for {
      output  <- Queue.bounded[stream.Take[E, A]](outputBuffer).toManagedWith(_.shutdown)
      runtime <- ZManaged.runtime[R]
      eitherStream <- ZManaged.succeed {
                        register { k =>
                          try {
                            runtime.unsafeRunToFuture(stream.Take.fromPull(k).flatMap(output.offer))
                          } catch {
                            case FiberFailure(c) if c.isInterrupted =>
                              Future.successful(false)
                          }
                        }
                      }
    } yield {
      eitherStream match {
        case Right(value) => ZStream.unwrap(output.shutdown as value)
        case Left(canceler) =>
          lazy val loop: ZChannel[Any, Any, Any, Any, E, Chunk[A], Unit] =
            ZChannel.unwrap(
              output.take
                .flatMap(_.done)
                .fold(
                  maybeError =>
                    ZChannel.fromZIO(output.shutdown) *>
                      maybeError
                        .fold[ZChannel[Any, Any, Any, Any, E, Chunk[A], Unit]](ZChannel.end(()))(ZChannel.fail(_)),
                  a => ZChannel.write(a) *> loop
                )
            )

          new ZStream(loop).ensuring(canceler)
      }
    })

  /**
   * Creates a stream from an asynchronous callback that can be called multiple times.
   * The registration of the callback itself returns an a managed resource.
   * The optionality of the error type `E` can be used to signal the end of the
   * stream, by setting it to `None`.
   */
  def asyncManaged[R, E, A](
    register: (ZIO[R, Option[E], Chunk[A]] => Future[Boolean]) => ZManaged[R, E, Any],
    outputBuffer: Int = 16
  )(implicit trace: ZTraceElement): ZStream[R, E, A] =
    managed {
      for {
        output  <- Queue.bounded[stream.Take[E, A]](outputBuffer).toManagedWith(_.shutdown)
        runtime <- ZIO.runtime[R].toManaged
        _ <- register { k =>
               try {
                 runtime.unsafeRunToFuture(stream.Take.fromPull(k).flatMap(output.offer))
               } catch {
                 case FiberFailure(c) if c.isInterrupted =>
                   Future.successful(false)
               }
             }
        done <- ZRef.makeManaged(false)
        pull = done.get.flatMap {
                 if (_)
                   Pull.end
                 else
                   output.take.flatMap(_.done).onError(_ => done.set(true) *> output.shutdown)
               }
      } yield pull
    }.flatMap(repeatZIOChunkOption(_))

  /**
   * Creates a stream from an asynchronous callback that can be called multiple times
   * The registration of the callback itself returns an effect. The optionality of the
   * error type `E` can be used to signal the end of the stream, by setting it to `None`.
   */
  def asyncZIO[R, E, A](
    register: ZStream.Emit[R, E, A, Future[Boolean]] => ZIO[R, E, Any],
    outputBuffer: Int = 16
  )(implicit trace: ZTraceElement): ZStream[R, E, A] =
    new ZStream(ZChannel.unwrapManaged(for {
      output  <- Queue.bounded[stream.Take[E, A]](outputBuffer).toManagedWith(_.shutdown)
      runtime <- ZManaged.runtime[R]
      _ <- register { k =>
             try {
               runtime.unsafeRunToFuture(stream.Take.fromPull(k).flatMap(output.offer))
             } catch {
               case FiberFailure(c) if c.isInterrupted =>
                 Future.successful(false)
             }
           }.toManaged
    } yield {
      lazy val loop: ZChannel[Any, Any, Any, Any, E, Chunk[A], Unit] = ZChannel.unwrap(
        output.take
          .flatMap(_.done)
          .fold(
            maybeError =>
              ZChannel.fromZIO(output.shutdown) *>
                maybeError.fold[ZChannel[Any, Any, Any, Any, E, Chunk[A], Unit]](ZChannel.end(()))(ZChannel.fail(_)),
            a => ZChannel.write(a) *> loop
          )
      )

      loop
    }))

  /**
   * Creates a stream from an asynchronous callback that can be called multiple times.
   * The registration of the callback can possibly return the stream synchronously.
   * The optionality of the error type `E` can be used to signal the end of the stream,
   * by setting it to `None`.
   */
  def asyncMaybe[R, E, A](
    register: ZStream.Emit[R, E, A, Future[Boolean]] => Option[ZStream[R, E, A]],
    outputBuffer: Int = 16
  )(implicit trace: ZTraceElement): ZStream[R, E, A] =
    asyncInterrupt(k => register(k).toRight(UIO.unit), outputBuffer)

  /**
   * Creates a stream from an asynchronous callback that can be called multiple times.
   * The optionality of the error type `E` can be used to signal the end of the stream,
   * by setting it to `None`.
   */
  @deprecated("use async", "2.0.0")
  def effectAsync[R, E, A](
    register: ZStream.Emit[R, E, A, Future[Boolean]] => Unit,
    outputBuffer: Int = 16
  )(implicit trace: ZTraceElement): ZStream[R, E, A] =
    async(register, outputBuffer)

  /**
   * Creates a stream from an asynchronous callback that can be called multiple times.
   * The registration of the callback returns either a canceler or synchronously returns a stream.
   * The optionality of the error type `E` can be used to signal the end of the stream, by
   * setting it to `None`.
   */
  @deprecated("use asyncInterrupt", "2.0.0")
  def effectAsyncInterrupt[R, E, A](
    register: ZStream.Emit[R, E, A, Future[Boolean]] => Either[Canceler[R], ZStream[R, E, A]],
    outputBuffer: Int = 16
  )(implicit trace: ZTraceElement): ZStream[R, E, A] =
    asyncInterrupt(register, outputBuffer)

  /**
   * Creates a stream from an asynchronous callback that can be called multiple times
   * The registration of the callback itself returns an effect. The optionality of the
   * error type `E` can be used to signal the end of the stream, by setting it to `None`.
   */
  @deprecated("use asyncZIO", "2.0.0")
  def effectAsyncM[R, E, A](
    register: ZStream.Emit[R, E, A, Future[Boolean]] => ZIO[R, E, Any],
    outputBuffer: Int = 16
  )(implicit trace: ZTraceElement): ZStream[R, E, A] =
    asyncZIO(register, outputBuffer)

  /**
   * Creates a stream from an asynchronous callback that can be called multiple times.
   * The registration of the callback can possibly return the stream synchronously.
   * The optionality of the error type `E` can be used to signal the end of the stream,
   * by setting it to `None`.
   */
  @deprecated("use asyncMaybe", "2.0.0")
  def effectAsyncMaybe[R, E, A](
    register: ZStream.Emit[R, E, A, Future[Boolean]] => Option[ZStream[R, E, A]],
    outputBuffer: Int = 16
  )(implicit trace: ZTraceElement): ZStream[R, E, A] =
    asyncMaybe(register, outputBuffer)

  trait ZStreamConstructorPlatformSpecific extends ZStreamConstructorLowPriority1
}

trait ZSinkPlatformSpecificConstructors
