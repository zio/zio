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
import zio.stream.compression.{CompressionException, CompressionLevel, CompressionStrategy, FlushMode}

import java.io.{
  File,
  FileNotFoundException,
  IOException,
  InputStream,
  OutputStream,
  PipedInputStream,
  PipedOutputStream,
  Reader
}
import java.net.{InetSocketAddress, SocketAddress, URI}
import java.nio.channels.{AsynchronousServerSocketChannel, AsynchronousSocketChannel, CompletionHandler, FileChannel}
import java.nio.file.StandardOpenOption._
import java.nio.file.{OpenOption, Path, Paths}
import java.nio.{Buffer, ByteBuffer}
import java.security.MessageDigest
import java.util.zip.{DataFormatException, Inflater}
import java.{util => ju}
import scala.annotation.tailrec

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
    register: ZStream.Emit[R, E, A, Unit] => Unit,
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
    register: ZStream.Emit[R, E, A, Unit] => Either[URIO[R, Any], ZStream[R, E, A]],
    outputBuffer: => Int = 16
  )(implicit trace: Trace): ZStream[R, E, A] =
    ZStream.unwrapScoped[R](for {
      output  <- ZIO.acquireRelease(Queue.bounded[stream.Take[E, A]](outputBuffer))(_.shutdown)
      runtime <- ZIO.runtime[R]
      eitherStream <- ZIO.succeed {
                        register { k =>
                          try {
                            runtime.unsafe
                              .run(stream.Take.fromPull(k).flatMap(output.offer))(trace, Unsafe.unsafe)
                              .getOrThrowFiberFailure()(Unsafe.unsafe)
                          } catch {
                            case FiberFailure(c) if c.isInterrupted =>
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
    register: (ZIO[R, Option[E], Chunk[A]] => Unit) => ZIO[Scope with R, E, Any],
    outputBuffer: => Int = 16
  )(implicit trace: Trace): ZStream[R, E, A] =
    scoped[R] {
      for {
        output  <- ZIO.acquireRelease(Queue.bounded[stream.Take[E, A]](outputBuffer))(_.shutdown)
        runtime <- ZIO.runtime[R]
        _ <- register { k =>
               try {
                 runtime.unsafe
                   .run(stream.Take.fromPull(k).flatMap(output.offer))(trace, Unsafe.unsafe)
                   .getOrThrowFiberFailure()(Unsafe.unsafe)
               } catch {
                 case FiberFailure(c) if c.isInterrupted =>
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
    register: ZStream.Emit[R, E, A, Unit] => ZIO[R, E, Any],
    outputBuffer: => Int = 16
  )(implicit trace: Trace): ZStream[R, E, A] =
    ZStream.fromChannel(ZChannel.unwrapScoped[R](for {
      output  <- ZIO.acquireRelease(Queue.bounded[stream.Take[E, A]](outputBuffer))(_.shutdown)
      runtime <- ZIO.runtime[R]
      _ <- register { k =>
             try {
               runtime.unsafe
                 .run(stream.Take.fromPull(k).flatMap(output.offer))(trace, Unsafe.unsafe)
                 .getOrThrowFiberFailure()(Unsafe.unsafe)
             } catch {
               case FiberFailure(c) if c.isInterrupted =>
             }
           }
    } yield {
      lazy val loop: ZChannel[Any, Any, Any, Any, E, Chunk[A], Unit] = ZChannel.unwrap(
        output.take
          .flatMap(_.exit)
          .foldCauseZIO(
            maybeError =>
              output.shutdown as (maybeError.failureOrCause match {
                case Left(Some(failure)) =>
                  ZChannel.fail(failure)
                case Left(None) =>
                  ZChannel.unit
                case Right(cause) =>
                  ZChannel.refailCause(cause)
              }),
            a => ZIO.succeed(ZChannel.write(a) *> loop)
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
    register: ZStream.Emit[R, E, A, Unit] => Option[ZStream[R, E, A]],
    outputBuffer: => Int = 16
  )(implicit trace: Trace): ZStream[R, E, A] =
    asyncInterrupt(k => register(k).toRight(ZIO.unit), outputBuffer)

  /**
   * Creates a stream of bytes from the specified file, in chunks of at most
   * `chunkSize` bytes.
   *
   * On the JVM and Scala Native, `file` is a `java.io.File`, read like
   * `fromPath`. On Scala.js, `file` is a file name, read with the Node.js
   * `fs.createReadStream` function. On Scala.js, a missing file fails the
   * stream with a `Throwable` whose message contains `ENOENT`.
   */
  final def fromFile(file: => File, chunkSize: => Int = ZStream.DefaultChunkSize)(implicit
    trace: Trace
  ): ZStream[Any, Throwable, Byte] =
    ZStream
      .fromZIO(ZIO.attempt(file.toPath))
      .flatMap(path => self.fromPath(path, chunkSize))

  /**
   * Creates a stream of bytes from the file with the specified name, in chunks
   * of at most `chunkSize` bytes. The name is converted with
   * `java.nio.file.Paths.get`, and the file is read like `fromPath`. An invalid
   * name fails the stream with `java.nio.file.InvalidPathException`.
   */
  final def fromFileName(name: => String, chunkSize: => Int = ZStream.DefaultChunkSize)(implicit
    trace: Trace
  ): ZStream[Any, Throwable, Byte] =
    ZStream
      .fromZIO(ZIO.attempt(Paths.get(name)))
      .flatMap(path => self.fromPath(path, chunkSize))

  /**
   * Creates a stream of bytes from the file at the specified URI, in chunks of
   * at most `chunkSize` bytes, read like `fromPath`. A URI whose scheme has no
   * installed file system provider, such as `http`, fails the stream with
   * `java.nio.file.FileSystemNotFoundException`.
   */
  final def fromFileURI(uri: => URI, chunkSize: => Int = ZStream.DefaultChunkSize)(implicit
    trace: Trace
  ): ZStream[Any, Throwable, Byte] =
    ZStream
      .fromZIO(ZIO.attempt(Paths.get(uri)))
      .flatMap(path => self.fromPath(path, chunkSize))

  /**
   * Creates a stream of bytes from the file at the specified path, read in
   * chunks of `chunkSize` bytes; the last chunk may be smaller. The file is
   * opened when the stream runs and closed when it ends, and each run reads the
   * file again. Reads run on ZIO's blocking thread pool. A missing file fails
   * the stream with `java.nio.file.NoSuchFileException`.
   *
   * {{{
   * import java.nio.file.Paths
   *
   * val lines: ZStream[Any, Throwable, String] =
   *   ZStream
   *     .fromPath(Paths.get("data.csv"))
   *     .via(ZPipeline.utf8Decode >>> ZPipeline.splitLines)
   * }}}
   *
   * @see
   *   [[https://zio.dev/reference/stream/zstream/creating-zio-streams/ Creating ZIO Streams]]
   */
  final def fromPath(path: => Path, chunkSize: => Int = ZStream.DefaultChunkSize)(implicit
    trace: Trace
  ): ZStream[Any, Throwable, Byte] =
    ZStream.blocking {
      ZStream
        .acquireReleaseWith(ZIO.attempt(FileChannel.open(path)))(chan => ZIO.succeed(chan.close()))
        .flatMap { channel =>
          ZStream.fromZIO(ZIO.succeed(ByteBuffer.allocate(chunkSize))).flatMap { reusableBuffer =>
            ZStream.repeatZIOChunkOption(
              for {
                bytesRead <- ZIO.attempt(channel.read(reusableBuffer)).asSomeError
                _         <- Exit.failNone.whenDiscard(bytesRead == -1)
                chunk <- ZIO.succeed {
                           reusableBuffer.flip()
                           Chunk.fromByteBuffer(reusableBuffer)
                         }
              } yield chunk
            )
          }
        }
    }

  /**
   * Creates a stream of bytes from the class path resource at `path`, in chunks
   * of at most `chunkSize` bytes. The resource is looked up with the class
   * loader that loaded ZIO Streams, after each backslash in `path` is replaced
   * with `/`. A leading `/` is kept, so a path such as `/data.txt` is not
   * found. A missing resource fails the stream with a
   * `java.io.FileNotFoundException` whose message names `path`. The resource is
   * closed when the stream ends.
   */
  final def fromResource(
    path: => String,
    chunkSize: => Int = ZStream.DefaultChunkSize
  )(implicit trace: Trace): ZStream[Any, IOException, Byte] =
    ZStream.scoped {
      ZIO.fromAutoCloseable {
        ZIO.succeed(path).flatMap { path =>
          ZIO.attemptBlockingIO(getClass.getClassLoader.getResourceAsStream(path.replace('\\', '/'))).flatMap { x =>
            if (x == null)
              ZIO.fail(new FileNotFoundException(s"No such resource: '$path'"))
            else
              ZIO.succeed(x)
          }
        }
      }
    }.flatMap(is => fromInputStream(is, chunkSize = chunkSize))

  /**
   * Creates a stream of characters from a `java.io.Reader`, in chunks of at
   * most `chunkSize` characters. Reads run on ZIO's blocking thread pool. An
   * `IOException` thrown by the reader fails the stream; other exceptions make
   * it die. The stream does not close the reader: `fromReaderZIO` does.
   */
  def fromReader(reader: => Reader, chunkSize: => Int = ZStream.DefaultChunkSize)(implicit
    trace: Trace
  ): ZStream[Any, IOException, Char] =
    ZStream.succeed((reader, chunkSize)).flatMap { case (reader, chunkSize) =>
      ZStream.repeatZIOChunkOption {
        for {
          bufArray  <- ZIO.succeed(Array.ofDim[Char](chunkSize))
          bytesRead <- ZIO.attemptBlockingIO(reader.read(bufArray)).asSomeError
          chars <- if (bytesRead < 0)
                     Exit.failNone
                   else if (bytesRead == 0)
                     Exit.emptyChunk
                   else if (bytesRead < chunkSize)
                     ZIO.succeed(Chunk.fromArray(bufArray).take(bytesRead))
                   else
                     ZIO.succeed(Chunk.fromArray(bufArray))
        } yield chars
      }
    }

  /**
   * Creates a stream of characters from a scoped `java.io.Reader`, like
   * `fromReader`. The scope closes when the stream ends; the reader is closed
   * then only if the scoped effect adds a finalizer that closes it.
   */
  def fromReaderScoped[R](
    reader: => ZIO[Scope with R, IOException, Reader],
    chunkSize: => Int = ZStream.DefaultChunkSize
  )(implicit trace: Trace): ZStream[R, IOException, Char] =
    ZStream.scoped[R](reader).flatMap(fromReader(_, chunkSize))

  /**
   * Creates a stream of characters from an effect producing a `java.io.Reader`,
   * like `fromReader`, and closes the reader when the stream ends.
   */
  def fromReaderZIO[R](
    reader: => ZIO[R, IOException, Reader],
    chunkSize: => Int = ZStream.DefaultChunkSize
  )(implicit trace: Trace): ZStream[R, IOException, Char] =
    fromReaderScoped[R](ZIO.acquireRelease(reader)(reader => ZIO.succeed(reader.close())), chunkSize)

  /**
   * Creates a stream of bytes from a callback that writes to a
   * `java.io.OutputStream`, in chunks of at most `chunkSize` bytes. `write`
   * runs on ZIO's blocking thread pool, in a fiber of its own, and the
   * `OutputStream` it receives is closed when `write` returns or throws. An
   * exception thrown by `write` fails the stream. Interrupting the stream
   * interrupts the thread that runs `write`.
   */
  def fromOutputStreamWriter(
    write: OutputStream => Unit,
    chunkSize: => Int = ZStream.DefaultChunkSize
  )(implicit trace: Trace): ZStream[Any, Throwable, Byte] = {
    def from(in: InputStream, out: OutputStream, done: Promise[None.type, Nothing]): ZStream[Any, Throwable, Byte] =
      (fromInputStream(in, chunkSize) ++ fromZIOOption(done.await)).drainFork(
        fromZIO(ZIO.attemptBlockingInterrupt {
          try write(out)
          finally out.close()
        }) ++ fromZIO(done.fail(None))
      )

    for {
      out    <- fromZIO(ZIO.attempt(new PipedOutputStream()))
      in     <- scoped(ZIO.fromAutoCloseable(ZIO.attempt(new PipedInputStream(out))))
      done   <- fromZIO(Promise.make[None.type, Nothing])
      result <- from(in, out, done)
    } yield result
  }

  /**
   * Creates a stream from a `java.util.stream.Stream`, in chunks of at most
   * `ZStream.DefaultChunkSize` (4096) elements. The Java stream is closed when
   * the stream ends, also when it ends early, for example after a `take`. An
   * exception thrown by the Java stream fails the stream.
   *
   * {{{
   * val numbers: ZStream[Any, Throwable, Int] =
   *   ZStream.fromJavaStream(java.util.stream.Stream.of(1, 2, 3))
   * // numbers.runCollect succeeds with Chunk(1, 2, 3)
   * }}}
   */
  final def fromJavaStream[A](stream: => java.util.stream.Stream[A])(implicit
    trace: Trace
  ): ZStream[Any, Throwable, A] =
    fromJavaStream(stream, ZStream.DefaultChunkSize)

  /**
   * Creates a stream from a `java.util.stream.Stream`, in chunks of at most
   * `chunkSize` elements. The Java stream is closed when the stream ends, also
   * when it ends early, for example after a `take`. An exception thrown by the
   * Java stream fails the stream.
   */
  final def fromJavaStream[A](
    stream: => java.util.stream.Stream[A],
    chunkSize: Int
  )(implicit trace: Trace): ZStream[Any, Throwable, A] =
    ZStream.fromJavaIteratorScoped(
      ZIO.acquireRelease(ZIO.attempt(stream))(stream => ZIO.succeed(stream.close())).map(_.iterator()),
      chunkSize
    )

  /**
   * Creates a stream from a scoped `java.util.stream.Stream`, in chunks of at
   * most `ZStream.DefaultChunkSize` (4096) elements. The scope closes when the
   * stream ends, and the Java stream is closed then too, also when the scoped
   * effect adds no finalizer.
   */
  final def fromJavaStreamScoped[R, A](stream: => ZIO[Scope with R, Throwable, java.util.stream.Stream[A]])(implicit
    trace: Trace
  ): ZStream[R, Throwable, A] =
    fromJavaStreamScoped[R, A](stream, ZStream.DefaultChunkSize)

  /**
   * Creates a stream from a scoped `java.util.stream.Stream`, in chunks of at
   * most `chunkSize` elements. The scope closes when the stream ends, and the
   * Java stream is closed then too, also when the scoped effect adds no
   * finalizer.
   */
  final def fromJavaStreamScoped[R, A](
    stream: => ZIO[Scope with R, Throwable, java.util.stream.Stream[A]],
    chunkSize: Int
  )(implicit trace: Trace): ZStream[R, Throwable, A] =
    ZStream.scoped[R](stream).flatMap(ZStream.fromJavaStream(_, chunkSize))

  /**
   * Creates a stream from a `java.util.stream.Stream` that is not expected to
   * throw, in chunks of at most `ZStream.DefaultChunkSize` (4096) elements. An
   * exception thrown by the Java stream makes the stream die. The Java stream
   * is not closed.
   */
  final def fromJavaStreamSucceed[R, A](stream: => java.util.stream.Stream[A])(implicit
    trace: Trace
  ): ZStream[R, Nothing, A] =
    fromJavaStreamSucceed(stream, ZStream.DefaultChunkSize)

  /**
   * Creates a stream from a `java.util.stream.Stream` that is not expected to
   * throw, in chunks of at most `chunkSize` elements. An exception thrown by
   * the Java stream makes the stream die. The Java stream is not closed.
   */
  final def fromJavaStreamSucceed[R, A](
    stream: => java.util.stream.Stream[A],
    chunkSize: Int
  )(implicit
    trace: Trace
  ): ZStream[R, Nothing, A] =
    ZStream.fromJavaIteratorSucceed(stream.iterator(), chunkSize)

  /**
   * Creates a stream from an effect producing a `java.util.stream.Stream`, in
   * chunks of at most `ZStream.DefaultChunkSize` (4096) elements. A failure of
   * the effect fails the stream. The Java stream is closed when the stream
   * ends.
   */
  final def fromJavaStreamZIO[R, A](stream: => ZIO[R, Throwable, java.util.stream.Stream[A]])(implicit
    trace: Trace
  ): ZStream[R, Throwable, A] =
    fromJavaStreamZIO(stream, ZStream.DefaultChunkSize)

  /**
   * Creates a stream from an effect producing a `java.util.stream.Stream`, in
   * chunks of at most `chunkSize` elements. A failure of the effect fails the
   * stream. The Java stream is closed when the stream ends.
   */
  final def fromJavaStreamZIO[R, A](
    stream: => ZIO[R, Throwable, java.util.stream.Stream[A]],
    chunkSize: Int
  )(implicit
    trace: Trace
  ): ZStream[R, Throwable, A] =
    ZStream.fromZIO(stream).flatMap(ZStream.fromJavaStream(_, chunkSize))

  /**
   * Creates a stream of the connections accepted by a server socket bound to
   * `port`, on every local address, or on `host` when it is given. A failure to
   * bind or to accept fails the stream. The server socket is closed when the
   * stream ends.
   *
   * {{{
   * import zio._
   *
   * val echo: ZIO[Any, Throwable, Unit] =
   *   ZStream
   *     .fromSocketServer(8080)
   *     .mapZIOParUnordered(16) { connection =>
   *       connection.read.run(connection.write) *> connection.closeWrite()
   *     }
   *     .runDrain
   * }}}
   */
  def fromSocketServer(
    port: => Int,
    host: => Option[String] = None
  )(implicit trace: Trace): ZStream[Any, Throwable, Connection] =
    for {
      server <- ZStream.scoped(ZIO.fromAutoCloseable(ZIO.attemptBlocking {
                  AsynchronousServerSocketChannel
                    .open()
                    .bind(
                      host.fold(new InetSocketAddress(port))(new InetSocketAddress(_, port))
                    )
                }))

      registerConnection <- ZStream.scoped(ZIO.acquireReleaseExit(Scope.make)((scope, exit) => scope.close(exit)))

      conn <- ZStream.repeatZIO {
                ZIO
                  .async[Any, Throwable, ZIO[Scope, Nothing, Connection]] { callback =>
                    server.accept(
                      null,
                      new CompletionHandler[AsynchronousSocketChannel, Void]() {
                        self =>
                        override def completed(socket: AsynchronousSocketChannel, attachment: Void): Unit =
                          callback(ZIO.succeed(Connection.make(socket)))

                        override def failed(exc: Throwable, attachment: Void): Unit = callback(ZIO.fail(exc))
                      }
                    )
                  }
                  .flatMap(scopedConn => registerConnection.extend(scopedConn))
              }
    } yield conn

  /**
   * A connection accepted by `fromSocketServer`, which wraps an
   * `AsynchronousSocketChannel`. Read from it with `read`, and write to it with
   * `write`.
   */
  final class Connection private (socket: AsynchronousSocketChannel) {

    /**
     * Returns the remote address of this connection: the address of the
     * connected client.
     */
    def remoteAddress(implicit trace: Trace): IO[IOException, SocketAddress] = ZIO
      .attempt(socket.getRemoteAddress)
      .refineToOrDie[IOException]

    /**
     * Returns the local address of this connection: the address of the server.
     */
    def localAddress(implicit trace: Trace): IO[IOException, SocketAddress] = ZIO
      .attempt(socket.getLocalAddress)
      .refineToOrDie[IOException]

    /**
     * Returns a stream of the bytes received on this connection, which ends
     * when the client closes its output. At the end of the input, it emits an
     * empty chunk, then ends.
     */
    def read(implicit trace: Trace): ZStream[Any, Throwable, Byte] =
      ZStream.fromZIO(ZIO.succeed(ByteBuffer.allocate(ZStream.DefaultChunkSize))).flatMap { reusableBuffer =>
        ZStream.unfoldChunkZIO(0) {
          case -1 => ZIO.succeed(Option.empty)
          case _ =>
            ZIO.async[Any, Throwable, Option[(Chunk[Byte], Int)]] { callback =>
              socket.read(
                reusableBuffer,
                null,
                new CompletionHandler[Integer, Void] {
                  override def completed(bytesRead: Integer, attachment: Void): Unit = {
                    (reusableBuffer: Buffer).flip()
                    callback(ZIO.succeed(Option(Chunk.fromByteBuffer(reusableBuffer) -> bytesRead.toInt)))
                  }

                  override def failed(error: Throwable, attachment: Void): Unit = callback(ZIO.fail(error))
                }
              )
            }
        }
      }

    /**
     * Returns a sink that writes the bytes it receives to this connection and
     * yields the number of bytes written.
     */
    def write(implicit trace: Trace): ZSink[Any, Throwable, Byte, Nothing, Int] =
      ZSink.foldLeftChunksZIO[Any, Throwable, Byte, Int](0) { case (nbBytesWritten, c) =>
        ZIO.async[Any, Throwable, Int] { callback =>
          socket.write(
            ByteBuffer.wrap(c.toArray),
            null,
            new CompletionHandler[Integer, Void] {
              override def completed(result: Integer, attachment: Void): Unit =
                callback(ZIO.succeed(nbBytesWritten + result.toInt))

              override def failed(error: Throwable, attachment: Void): Unit = callback(ZIO.fail(error))
            }
          )
        }
      }

    /**
     * Closes the underlying socket.
     */
    private[stream] def close()(implicit trace: Trace): UIO[Unit] = ZIO.succeed(socket.close())

    /**
     * Shuts down the output of the socket, so the remote end reads the end of
     * the stream. Reading from the connection still works.
     */
    def closeWrite()(implicit trace: Trace): IO[IOException, Unit] =
      ZIO.attempt(socket.shutdownOutput()).unit.refineToOrDie[IOException]

  }

  /**
   * Constructors of `Connection`.
   */
  object Connection {

    /**
     * Creates a scoped connection that wraps `socket`, and closes `socket` when
     * the scope closes.
     */
    def make(socket: AsynchronousSocketChannel)(implicit trace: Trace): ZIO[Scope, Nothing, Connection] =
      ZIO.acquireRelease(ZIO.succeed(new Connection(socket)))(_.close())
  }

  private[stream] trait ZStreamConstructorPlatformSpecific extends ZStreamConstructorLowPriority1 {

    /**
     * Constructs a `ZStream[Any, Throwable, A]` from a
     * `java.util.stream.Stream[A]` with `ZStream.fromJavaStream`.
     */
    implicit def JavaStreamConstructor[A, StreamLike[A] <: java.util.stream.Stream[A]]
      : WithOut[StreamLike[A], ZStream[Any, Throwable, A]] =
      new ZStreamConstructor[StreamLike[A]] {
        type Out = ZStream[Any, Throwable, A]
        def make(input: => StreamLike[A])(implicit trace: Trace): ZStream[Any, Throwable, A] =
          ZStream.fromJavaStream(input)
      }

    /**
     * Constructs a `ZStream[R, Throwable, A]` from a `ZIO[Scope with R, E,
     * java.util.stream.Stream[A]]` with `ZStream.fromJavaStreamScoped`.
     */
    implicit def JavaStreamScopedConstructor[R, E <: Throwable, A, StreamLike[A] <: java.util.stream.Stream[A]]
      : WithOut[ZIO[Scope with R, E, StreamLike[A]], ZStream[R, Throwable, A]] =
      new ZStreamConstructor[ZIO[Scope with R, E, StreamLike[A]]] {
        type Out = ZStream[R, Throwable, A]
        def make(input: => ZIO[Scope with R, E, StreamLike[A]])(implicit
          trace: Trace
        ): ZStream[R, Throwable, A] =
          ZStream.fromJavaStreamScoped[R, A](input)
      }

    /**
     * Constructs a `ZStream[R, Throwable, A]` from a `ZIO[R, E,
     * java.util.stream.Stream[A]]` with `ZStream.fromJavaStreamZIO`.
     */
    implicit def JavaStreamZIOConstructor[R, E <: Throwable, A, StreamLike[A] <: java.util.stream.Stream[A]]
      : WithOut[ZIO[R, E, StreamLike[A]], ZStream[R, Throwable, A]] =
      new ZStreamConstructor[ZIO[R, E, StreamLike[A]]] {
        type Out = ZStream[R, Throwable, A]
        def make(input: => ZIO[R, E, StreamLike[A]])(implicit trace: Trace): ZStream[R, Throwable, A] =
          ZStream.fromJavaStreamZIO(input)
      }

  }
}

private[stream] trait ZSinkPlatformSpecificConstructors {
  self: ZSink.type =>

  /**
   * Creates a sink that feeds the incoming bytes to a
   * `java.security.MessageDigest`, and yields the digest when the input ends.
   * `digest` is evaluated each time the sink runs. At the end, the sink calls
   * `digest()` on it, which resets it.
   *
   * {{{
   * import zio._
   * import java.security.MessageDigest
   *
   * val sha256: UIO[Chunk[Byte]] =
   *   ZStream
   *     .fromChunk(Chunk.fromArray("abc".getBytes("UTF-8")))
   *     .run(ZSink.digest(MessageDigest.getInstance("SHA-256")))
   * // sha256 succeeds with the 32 bytes of the SHA-256 digest of "abc"
   * }}}
   */
  def digest(digest: => MessageDigest): ZSink[Any, Nothing, Byte, Nothing, Chunk[Byte]] =
    ZSink.suspend {

      def loop(digest: MessageDigest): ZChannel[Any, Nothing, Chunk[Byte], Any, Nothing, Nothing, Chunk[Byte]] =
        ZChannel.readWithCause[Any, Nothing, Chunk[Byte], Any, Nothing, Nothing, Chunk[Byte]](
          in =>
            ZChannel
              .succeedNow(digest.update(in.toArray))
              .zipRight[Any, Nothing, Chunk[Byte], Any, Nothing, Nothing, Chunk[Byte]](loop(digest)),
          cause => ZChannel.refailCause(cause),
          _ => ZChannel.succeedNow(Chunk.fromArray(digest.digest))
        )

      ZSink.fromChannel(loop(digest))
    }

  /**
   * Creates a sink that writes the incoming bytes to the specified file, like
   * `fromPath`, and yields the number of bytes written.
   */
  final def fromFile(
    file: => File,
    position: => Long = 0L,
    options: => Set[OpenOption] = Set(WRITE, TRUNCATE_EXISTING, CREATE)
  )(implicit
    trace: Trace
  ): ZSink[Any, Throwable, Byte, Byte, Long] =
    ZSink
      .fromZIO(ZIO.attempt(file.toPath))
      .flatMap(path => self.fromPath(path, position, options))

  /**
   * Creates a sink that writes the incoming bytes to the file with the
   * specified name, like `fromPath`, and yields the number of bytes written.
   * The name is converted with `java.nio.file.Paths.get`.
   */
  final def fromFileName(
    name: => String,
    position: => Long = 0L,
    options: => Set[OpenOption] = Set(WRITE, TRUNCATE_EXISTING, CREATE)
  )(implicit
    trace: Trace
  ): ZSink[Any, Throwable, Byte, Byte, Long] =
    ZSink
      .fromZIO(ZIO.attempt(Paths.get(name)))
      .flatMap(path => self.fromPath(path, position, options))

  /**
   * Creates a sink that writes the incoming bytes to the file at the specified
   * URI, like `fromPath`, and yields the number of bytes written.
   */
  final def fromFileURI(
    uri: => URI,
    position: => Long = 0L,
    options: => Set[OpenOption] = Set(WRITE, TRUNCATE_EXISTING, CREATE)
  )(implicit
    trace: Trace
  ): ZSink[Any, Throwable, Byte, Byte, Long] =
    ZSink
      .fromZIO(ZIO.attempt(Paths.get(uri)))
      .flatMap(path => self.fromPath(path, position, options))

  /**
   * Creates a sink that writes the incoming bytes to the file at the specified
   * path, and yields the number of bytes written. The file is opened with
   * `options` when the sink runs, and closed when the sink is done; by default
   * it is created if missing and truncated otherwise. Writing starts at byte
   * `position`. Opening and writing run on ZIO's blocking thread pool. A
   * missing parent directory fails the sink with
   * `java.nio.file.NoSuchFileException`.
   *
   * {{{
   * import zio._
   * import java.nio.file.Paths
   *
   * val written: ZIO[Any, Throwable, Long] =
   *   ZStream
   *     .fromChunk(Chunk.fromArray("hello".getBytes("UTF-8")))
   *     .run(ZSink.fromPath(Paths.get("out.txt")))
   * // written succeeds with 5
   * }}}
   *
   * @see
   *   [[https://zio.dev/reference/stream/zsink/creating-sinks/ Creating Sinks]]
   */
  final def fromPath(
    path: => Path,
    position: => Long = 0L,
    options: => Set[OpenOption] = Set(WRITE, TRUNCATE_EXISTING, CREATE)
  )(implicit
    trace: Trace
  ): ZSink[Any, Throwable, Byte, Byte, Long] = {

    val scopedChannel = ZIO.acquireRelease(
      ZIO
        .attemptBlockingInterrupt(
          FileChannel
            .open(
              path,
              options.foldLeft(
                new ju.HashSet[OpenOption]()
              ) { (acc, op) =>
                acc.add(op)
                acc
              } // for avoiding usage of different Java collection converters for different scala versions
            )
            .position(position)
        )
    )(chan => ZIO.attemptBlocking(chan.close()).orDie)

    ZSink.unwrapScoped {
      scopedChannel.map { chan =>
        ZSink.foldLeftChunksZIO(0L) { (bytesWritten, byteChunk: Chunk[Byte]) =>
          ZIO.attemptBlockingInterrupt {
            val bytes = byteChunk.toArray

            chan.write(ByteBuffer.wrap(bytes))

            bytesWritten + bytes.length.toLong
          }
        }
      }
    }
  }

  /**
   * Creates a sink that writes the incoming bytes to the specified
   * `OutputStream`, and yields the number of bytes written. Writes run on ZIO's
   * blocking thread pool. An `IOException` fails the sink; other exceptions
   * make it die.
   *
   * The caller of this function is responsible for closing the `OutputStream`.
   *
   * {{{
   * import zio._
   * import java.io.ByteArrayOutputStream
   *
   * val out = new ByteArrayOutputStream()
   *
   * val written: ZIO[Any, java.io.IOException, Long] =
   *   ZStream
   *     .fromChunk(Chunk.fromArray("hello".getBytes("UTF-8")))
   *     .run(ZSink.fromOutputStream(out))
   * // written succeeds with 5, and out then holds the bytes of "hello"
   * }}}
   */
  final def fromOutputStream(
    os: => OutputStream
  )(implicit trace: Trace): ZSink[Any, IOException, Byte, Byte, Long] = fromOutputStreamScoped(
    ZIO.succeed(os)
  )

  /**
   * Creates a sink that writes the incoming bytes to the `OutputStream` of the
   * specified scoped effect, like `fromOutputStream`, and yields the number of
   * bytes written.
   *
   * The `Scope` of the provided effect will be automatically closed after the
   * stream is finished or an error occurred. It's the responsibility of the
   * user to attach the closing of the `OutputStream` to the `Scope`.
   */
  final def fromOutputStreamScoped(
    os: => ZIO[Scope, IOException, OutputStream]
  )(implicit trace: Trace): ZSink[Any, IOException, Byte, Byte, Long] =
    ZSink.unwrapScoped {
      os.map { out =>
        ZSink.foldLeftChunksZIO(0L) { (bytesWritten, byteChunk: Chunk[Byte]) =>
          ZIO.attemptBlockingInterrupt {
            val bytes = byteChunk.toArray
            out.write(bytes)
            bytesWritten + bytes.length
          }.refineOrDie { case e: IOException =>
            e
          }
        }
      }
    }

}

private[stream] trait ZPipelinePlatformSpecificConstructors {

  /**
   * Compresses a stream of bytes with `java.util.zip.Deflater`. The output is
   * in the zlib format, or raw deflate data when `noWrap` is `true`.
   * `bufferSize` is the size in bytes of the buffer that compressed output is
   * read into; `level`, `strategy` and `flushMode` configure the `Deflater`.
   *
   * @see
   *   [[https://zio.dev/reference/stream/zpipeline/ ZPipeline]]
   */
  def deflate(
    bufferSize: => Int = 64 * 1024,
    noWrap: => Boolean = false,
    level: => CompressionLevel = CompressionLevel.DefaultCompression,
    strategy: => CompressionStrategy = CompressionStrategy.DefaultStrategy,
    flushMode: => FlushMode = FlushMode.NoFlush
  )(implicit trace: Trace): ZPipeline[Any, Nothing, Byte, Byte] =
    ZPipeline.fromChannel(
      Deflate.makeDeflater(
        bufferSize,
        noWrap,
        level,
        strategy,
        flushMode
      )
    )

  /**
   * Decompresses a stream of deflate-compressed bytes with
   * `java.util.zip.Inflater`: zlib-format input, or raw deflate data when
   * `noWrap` is `true`. Input that is not in that format fails the stream with
   * a `CompressionException`.
   *
   * @see
   *   [[https://zio.dev/reference/stream/zpipeline/ ZPipeline]]
   */
  def inflate(
    bufferSize: => Int = 64 * 1024,
    noWrap: => Boolean = false
  )(implicit trace: Trace): ZPipeline[Any, CompressionException, Byte, Byte] =
    ZPipeline.fromChannel(
      Inflate.makeInflater(bufferSize, noWrap)
    )

  /**
   * Compresses a stream of bytes in the gzip format (RFC 1952). `bufferSize` is
   * the size in bytes of the buffer that compressed output is read into;
   * `level`, `strategy` and `flushMode` configure the deflate compression.
   *
   * {{{
   * import zio._
   * import zio.stream.compression.CompressionException
   *
   * val text: Chunk[Byte] = Chunk.fromArray("hello hello".getBytes("UTF-8"))
   *
   * val roundTrip: ZIO[Any, CompressionException, Chunk[Byte]] =
   *   ZStream
   *     .fromChunk(text)
   *     .via(ZPipeline.gzip() >>> ZPipeline.gunzip())
   *     .runCollect
   * // roundTrip succeeds with the bytes of text
   * }}}
   *
   * @see
   *   [[https://zio.dev/reference/stream/zpipeline/ ZPipeline]]
   */
  def gzip(
    bufferSize: => Int = 64 * 1024,
    level: => CompressionLevel = CompressionLevel.DefaultCompression,
    strategy: => CompressionStrategy = CompressionStrategy.DefaultStrategy,
    flushMode: => FlushMode = FlushMode.NoFlush
  )(implicit trace: Trace): ZPipeline[Any, Nothing, Byte, Byte] =
    ZPipeline.fromChannel(
      Gzip.makeGzipper(bufferSize, level, strategy, flushMode)
    )

  /**
   * Decompresses a stream of gzip-format bytes (RFC 1952). Concatenated gzip
   * members are decompressed one after the other, and an empty input gives an
   * empty output. Input that is not gzip data, or that ends inside the gzip
   * data, fails the stream with a `CompressionException`.
   *
   * @see
   *   [[https://zio.dev/reference/stream/zpipeline/ ZPipeline]]
   */
  def gunzip[Env](bufferSize: => Int = 64 * 1024)(implicit
    trace: Trace
  ): ZPipeline[Any, CompressionException, Byte, Byte] =
    ZPipeline.fromChannel(
      Gunzip.makeGunzipper(bufferSize)
    )

  /**
   * Decompresses the stream like `gunzip` when its first two bytes are the gzip
   * magic bytes `0x1f 0x8b`, and passes it through unchanged otherwise, also
   * when it has fewer than two bytes.
   *
   * @see
   *   [[https://zio.dev/reference/stream/zpipeline/ ZPipeline]]
   */
  def gunzipAuto[Env](
    bufferSize: => Int = 64 * 1024
  )(implicit trace: Trace): ZPipeline[Any, CompressionException, Byte, Byte] = {
    val GZIP_PREFIX: Chunk[Byte] = Chunk(0x1f.toByte, 0x8b.toByte)
    ZPipeline.branchAfter[Any, CompressionException, Byte, Byte](2) { chunk =>
      if (chunk == GZIP_PREFIX) {
        ZPipeline.prepend(chunk) >>> ZPipeline.gunzip(bufferSize)
      } else {
        ZPipeline.prepend(chunk)
      }
    }
  }
}
