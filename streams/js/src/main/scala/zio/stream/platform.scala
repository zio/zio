/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.stream

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.scalajs.js.typedarray._
import scala.concurrent.Future
import scala.scalajs.js

private[stream] trait ZStreamPlatformSpecificConstructors {
  self: ZStream.type =>

  /**
   * Creates a stream from an asynchronous callback that can be called multiple
   * times. `register` receives the callback and is called once, when the stream
   * starts.
   *
   * Each call of the callback runs the effect it is given: a chunk emits its
   * values, a failure with `Some(e)` fails the stream with `e`, and a failure
   * with `None` ends the stream. The methods of `ZStream.Emit` (`single`,
   * `chunk`, `fail`, `die`, `end`, ...) build these effects.
   *
   * Up to `outputBuffer` chunks wait in a buffer until the stream pulls them.
   *
   * On the JVM and Scala Native, each call of the callback blocks the calling
   * thread until its chunk is in the buffer. On Scala.js, each call returns at
   * once with a `Future[Boolean]` that completes with `true` when the chunk is
   * in the buffer, and fails when the stream ends first.
   *
   * {{{
   * import zio._
   *
   * val numbers: ZStream[Any, Nothing, Int] =
   *   ZStream.async[Any, Nothing, Int] { emit =>
   *     emit.single(1)
   *     emit.chunk(Chunk(2, 3))
   *     emit.end
   *   }
   * // numbers.runCollect succeeds with Chunk(1, 2, 3)
   * }}}
   *
   * @see
   *   [[https://zio.dev/reference/stream/zstream/creating-zio-streams/ Creating ZIO Streams]]
   */
  def async[R, E, A](
    register: ZStream.Emit[R, E, A, Future[Boolean]] => Unit,
    outputBuffer: => Int = 16
  )(implicit trace: Trace): ZStream[R, E, A] =
    asyncMaybe(
      callback => {
        register(callback)
        None
      },
      outputBuffer
    )

  /**
   * Creates a stream from an asynchronous callback that can be called multiple
   * times, like `async`. `register` receives the callback, is called once when
   * the stream starts, and returns either `Left(canceler)` or `Right(stream)`.
   * The canceler runs when the stream ends: by the end signal, a failure,
   * interruption or a downstream `take`. With `Right(stream)`, the result is
   * `stream`, and the callback is ignored.
   *
   * Each call of the callback runs the effect it is given: a chunk emits its
   * values, a failure with `Some(e)` fails the stream with `e`, and a failure
   * with `None` ends the stream. The methods of `ZStream.Emit` (`single`,
   * `chunk`, `fail`, `die`, `end`, ...) build these effects.
   *
   * Up to `outputBuffer` chunks wait in a buffer until the stream pulls them.
   *
   * On the JVM and Scala Native, each call of the callback blocks the calling
   * thread until its chunk is in the buffer. On Scala.js, each call returns at
   * once with a `Future[Boolean]` that completes with `true` when the chunk is
   * in the buffer, and fails when the stream ends first.
   */
  def asyncInterrupt[R, E, A](
    register: ZStream.Emit[R, E, A, Future[Boolean]] => Either[URIO[R, Any], ZStream[R, E, A]],
    outputBuffer: => Int = 16
  )(implicit trace: Trace): ZStream[R, E, A] =
    ZStream.unwrapScoped[R](for {
      output  <- ZIO.acquireRelease(Queue.bounded[stream.Take[E, A]](outputBuffer))(_.shutdown)
      runtime <- ZIO.runtime[R]
      eitherStream <-
        ZIO.succeed {
          register { k =>
            try {
              runtime.unsafe.runToFuture(stream.Take.fromPull(k).flatMap(output.offer))(trace, Unsafe.unsafe)
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
                .flatMap(_.exit)
                .fold(
                  maybeError =>
                    ZChannel.fromZIO(output.shutdown) *>
                      maybeError
                        .fold[ZChannel[Any, Any, Any, Any, E, Chunk[A], Unit]](ZChannel.unit)(ZChannel.fail(_)),
                  a => ZChannel.write(a) *> loop
                )
            )

          ZStream.fromChannel(loop).ensuring(canceler)
      }
    })

  /**
   * Creates a stream from an asynchronous callback that can be called multiple
   * times, like `async`. The registration of the callback itself returns a
   * scoped resource: its scope closes when the stream ends, and a failure of
   * `register` fails the stream.
   *
   * Each call of the callback runs the effect it is given: a chunk emits its
   * values, a failure with `Some(e)` fails the stream with `e`, and a failure
   * with `None` ends the stream.
   *
   * Up to `outputBuffer` chunks wait in a buffer until the stream pulls them.
   *
   * On the JVM and Scala Native, each call of the callback blocks the calling
   * thread until its chunk is in the buffer. On Scala.js, each call returns at
   * once with a `Future[Boolean]` that completes with `true` when the chunk is
   * in the buffer, and fails when the stream ends first.
   */
  def asyncScoped[R, E, A](
    register: (ZIO[R, Option[E], Chunk[A]] => Future[Boolean]) => ZIO[R with Scope, E, Any],
    outputBuffer: => Int = 16
  )(implicit trace: Trace): ZStream[R, E, A] =
    scoped[R] {
      for {
        output  <- ZIO.acquireRelease(Queue.bounded[stream.Take[E, A]](outputBuffer))(_.shutdown)
        runtime <- ZIO.runtime[R]
        _ <- register { k =>
               try {
                 runtime.unsafe.runToFuture(stream.Take.fromPull(k).flatMap(output.offer))(trace, Unsafe.unsafe)
               } catch {
                 case FiberFailure(c) if c.isInterrupted =>
                   Future.successful(false)
               }
             }
        done <- Ref.make(false)
        pull = done.get.flatMap {
                 if (_)
                   Pull.end
                 else
                   output.take.flatMap(_.exit).onError(_ => done.set(true) *> output.shutdown)
               }
      } yield pull
    }.flatMap(repeatZIOChunkOption(_))

  /**
   * Creates a stream from an asynchronous callback that can be called multiple
   * times, like `async`. The registration of the callback itself returns an
   * effect, which runs when the stream starts; a failure of that effect fails
   * the stream.
   *
   * Each call of the callback runs the effect it is given: a chunk emits its
   * values, a failure with `Some(e)` fails the stream with `e`, and a failure
   * with `None` ends the stream. The methods of `ZStream.Emit` (`single`,
   * `chunk`, `fail`, `die`, `end`, ...) build these effects.
   *
   * Up to `outputBuffer` chunks wait in a buffer until the stream pulls them.
   *
   * On the JVM and Scala Native, each call of the callback blocks the calling
   * thread until its chunk is in the buffer. On Scala.js, each call returns at
   * once with a `Future[Boolean]` that completes with `true` when the chunk is
   * in the buffer, and fails when the stream ends first.
   */
  def asyncZIO[R, E, A](
    register: ZStream.Emit[R, E, A, Future[Boolean]] => ZIO[R, E, Any],
    outputBuffer: => Int = 16
  )(implicit trace: Trace): ZStream[R, E, A] =
    ZStream.fromChannel(ZChannel.unwrapScoped[R](for {
      output  <- ZIO.acquireRelease(Queue.bounded[stream.Take[E, A]](outputBuffer))(_.shutdown)
      runtime <- ZIO.runtime[R]
      _ <- register { k =>
             try {
               runtime.unsafe.runToFuture(stream.Take.fromPull(k).flatMap(output.offer))(trace, Unsafe.unsafe)
             } catch {
               case FiberFailure(c) if c.isInterrupted =>
                 Future.successful(false)
             }
           }
    } yield {
      lazy val loop: ZChannel[Any, Any, Any, Any, E, Chunk[A], Unit] = ZChannel.unwrap(
        output.take
          .flatMap(_.exit)
          .fold(
            maybeError =>
              ZChannel.fromZIO(output.shutdown) *>
                maybeError.fold[ZChannel[Any, Any, Any, Any, E, Chunk[A], Unit]](ZChannel.unit)(ZChannel.fail(_)),
            a => ZChannel.write(a) *> loop
          )
      )

      loop
    }))

  /**
   * Creates a stream from an asynchronous callback that can be called multiple
   * times, like `async`. The registration of the callback can return the stream
   * synchronously: with `Some(stream)`, the result is `stream`, and the
   * callback is ignored; with `None`, the stream emits what the callback
   * receives.
   *
   * Each call of the callback runs the effect it is given: a chunk emits its
   * values, a failure with `Some(e)` fails the stream with `e`, and a failure
   * with `None` ends the stream. The methods of `ZStream.Emit` (`single`,
   * `chunk`, `fail`, `die`, `end`, ...) build these effects.
   *
   * Up to `outputBuffer` chunks wait in a buffer until the stream pulls them.
   *
   * On the JVM and Scala Native, each call of the callback blocks the calling
   * thread until its chunk is in the buffer. On Scala.js, each call returns at
   * once with a `Future[Boolean]` that completes with `true` when the chunk is
   * in the buffer, and fails when the stream ends first.
   */
  def asyncMaybe[R, E, A](
    register: ZStream.Emit[R, E, A, Future[Boolean]] => Option[ZStream[R, E, A]],
    outputBuffer: => Int = 16
  )(implicit trace: Trace): ZStream[R, E, A] =
    asyncInterrupt(k => register(k).toRight(ZIO.unit), outputBuffer)

  private[stream] trait ZStreamConstructorPlatformSpecific extends ZStreamConstructorLowPriority1

  /**
   * Creates a stream of bytes from the specified file, in chunks of at most
   * `chunkSize` bytes.
   *
   * On the JVM and Scala Native, `file` is a `java.io.File`, read like
   * `fromPath`. On Scala.js, `file` is a file name, read with the Node.js
   * `fs.createReadStream` function. On Scala.js, a missing file fails the
   * stream with a `Throwable` whose message contains `ENOENT`.
   */
  def fromFile(file: => String, chunkSize: => Int = ZStream.DefaultChunkSize)(implicit
    trace: Trace
  ): ZStream[Any, Throwable, Byte] = {
    import scalajs.js.Dynamic.{global => g}
    val fs = g.require("fs")
    val reader = fs.createReadStream(
      file,
      new js.Object {
        val highWaterMark = chunkSize
      }
    )
    ZStream.async[Any, Throwable, Byte] { cb =>
      reader
        .on(
          "data",
          (data: js.typedarray.ArrayBuffer) =>
            cb(
              ZIO.succeed(
                Chunk.fromArray(new Int8Array(data).toArray)
              )
            )
        )
        .on("end", () => cb(Exit.failNone))
        .on("error", (err: js.Dynamic) => cb(ZIO.fail(Some(new Throwable(err.toString)))))
    }
  }
}

private[stream] trait ZSinkPlatformSpecificConstructors

private[stream] trait ZPipelinePlatformSpecificConstructors
