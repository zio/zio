package zio.stream.experimental

import zio._
import zio.stream.compression.{CompressionException, CompressionLevel, CompressionStrategy, FlushMode}

import java.io._
import java.net.InetSocketAddress
import java.nio.channels.{AsynchronousServerSocketChannel, AsynchronousSocketChannel, CompletionHandler, FileChannel}
import java.nio.file.Path
import java.nio.{Buffer, ByteBuffer}

trait ZStreamPlatformSpecificConstructors {
  self: ZStream.type =>

  /**
   * Creates a stream from an asynchronous callback that can be called multiple times.
   * The optionality of the error type `E` can be used to signal the end of the stream,
   * by setting it to `None`.
   */
  def async[R, E, A](
    register: ZStream.Emit[R, E, A, Unit] => Unit,
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
    register: ZStream.Emit[R, E, A, Unit] => Either[Canceler[R], ZStream[R, E, A]],
    outputBuffer: Int = 16
  )(implicit trace: ZTraceElement): ZStream[R, E, A] =
    ZStream.unwrapManaged(for {
      output  <- Queue.bounded[stream.Take[E, A]](outputBuffer).toManagedWith(_.shutdown)
      runtime <- ZManaged.runtime[R]
      eitherStream <- ZManaged.succeed {
                        register { k =>
                          try {
                            runtime.unsafeRun(stream.Take.fromPull(k).flatMap(output.offer))
                            ()
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
    register: (ZIO[R, Option[E], Chunk[A]] => Unit) => ZManaged[R, E, Any],
    outputBuffer: Int = 16
  )(implicit trace: ZTraceElement): ZStream[R, E, A] =
    managed {
      for {
        output  <- Queue.bounded[stream.Take[E, A]](outputBuffer).toManagedWith(_.shutdown)
        runtime <- ZIO.runtime[R].toManaged
        _ <- register { k =>
               try {
                 runtime.unsafeRun(stream.Take.fromPull(k).flatMap(output.offer))
                 ()
               } catch {
                 case FiberFailure(c) if c.isInterrupted =>
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
    register: ZStream.Emit[R, E, A, Unit] => ZIO[R, E, Any],
    outputBuffer: Int = 16
  )(implicit trace: ZTraceElement): ZStream[R, E, A] =
    new ZStream(ZChannel.unwrapManaged(for {
      output  <- Queue.bounded[stream.Take[E, A]](outputBuffer).toManagedWith(_.shutdown)
      runtime <- ZManaged.runtime[R]
      _ <- register { k =>
             try {
               runtime.unsafeRun(stream.Take.fromPull(k).flatMap(output.offer))
               ()
             } catch {
               case FiberFailure(c) if c.isInterrupted =>
             }
           }.toManaged
    } yield {
      lazy val loop: ZChannel[Any, Any, Any, Any, E, Chunk[A], Unit] = ZChannel.unwrap(
        output.take
          .flatMap(_.done)
          .foldCauseZIO(
            maybeError =>
              output.shutdown as (maybeError.failureOrCause match {
                case Left(Some(failure)) =>
                  ZChannel.fail(failure)
                case Left(None) =>
                  ZChannel.end(())
                case Right(cause) =>
                  ZChannel.failCause(cause)
              }),
            a => ZIO.succeedNow(ZChannel.write(a) *> loop)
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
    register: ZStream.Emit[R, E, A, Unit] => Option[ZStream[R, E, A]],
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
    register: ZStream.Emit[R, E, A, Unit] => Unit,
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
    register: ZStream.Emit[R, E, A, Unit] => Either[Canceler[R], ZStream[R, E, A]],
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
    register: ZStream.Emit[R, E, A, Unit] => ZIO[R, E, Any],
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
    register: ZStream.Emit[R, E, A, Unit] => Option[ZStream[R, E, A]],
    outputBuffer: Int = 16
  )(implicit trace: ZTraceElement): ZStream[R, E, A] =
    asyncMaybe(register, outputBuffer)

  /**
   * Creates a stream of bytes from a file at the specified path.
   */
  def fromFile(path: => Path, chunkSize: Int = ZStream.DefaultChunkSize)(implicit
    trace: ZTraceElement
  ): ZStream[Any, Throwable, Byte] =
    ZStream
      .acquireReleaseWith(ZIO.attemptBlockingInterrupt(FileChannel.open(path)))(chan =>
        ZIO.attemptBlocking(chan.close()).orDie
      )
      .flatMap { channel =>
        ZStream.fromZIO(UIO(ByteBuffer.allocate(chunkSize))).flatMap { reusableBuffer =>
          ZStream.repeatZIOChunkOption(
            for {
              bytesRead <- ZIO.attemptBlockingInterrupt(channel.read(reusableBuffer)).asSomeError
              _         <- ZIO.fail(None).when(bytesRead == -1)
              chunk <- UIO {
                         reusableBuffer.flip()
                         Chunk.fromByteBuffer(reusableBuffer)
                       }
            } yield chunk
          )
        }
      }

  /**
   * Creates a stream from a `java.io.InputStream`.
   * Note: the input stream will not be explicitly closed after it is exhausted.
   */
  def fromInputStream(
    is: => InputStream,
    chunkSize: Int = ZStream.DefaultChunkSize
  )(implicit trace: ZTraceElement): ZStream[Any, IOException, Byte] =
    ZStream.fromZIO(UIO(is)).flatMap { capturedIs =>
      ZStream.repeatZIOChunkOption {
        for {
          bufArray  <- UIO(Array.ofDim[Byte](chunkSize))
          bytesRead <- ZIO.attemptBlockingIO(capturedIs.read(bufArray)).asSomeError
          bytes <- if (bytesRead < 0)
                     ZIO.fail(None)
                   else if (bytesRead == 0)
                     UIO(Chunk.empty)
                   else if (bytesRead < chunkSize)
                     UIO(Chunk.fromArray(bufArray).take(bytesRead))
                   else
                     UIO(Chunk.fromArray(bufArray))
        } yield bytes
      }
    }

  /**
   * Creates a stream from the resource specified in `path`
   */
  final def fromResource(
    path: String,
    chunkSize: Int = ZStream.DefaultChunkSize
  )(implicit trace: ZTraceElement): ZStream[Any, IOException, Byte] =
    ZStream.managed {
      ZManaged.fromAutoCloseable {
        ZIO.attemptBlockingIO(getClass.getClassLoader.getResourceAsStream(path.replace('\\', '/'))).flatMap { x =>
          if (x == null)
            ZIO.fail(new FileNotFoundException(s"No such resource: '$path'"))
          else
            ZIO.succeedNow(x)
        }
      }
    }.flatMap(is => fromInputStream(is, chunkSize = chunkSize))

  /**
   * Creates a stream from a `java.io.InputStream`. Ensures that the input
   * stream is closed after it is exhausted.
   */
  def fromInputStreamEffect[R](
    is: ZIO[R, IOException, InputStream],
    chunkSize: Int = ZStream.DefaultChunkSize
  )(implicit trace: ZTraceElement): ZStream[R, IOException, Byte] =
    fromInputStreamManaged(is.toManagedWith(is => ZIO.succeed(is.close())), chunkSize)

  /**
   * Creates a stream from a managed `java.io.InputStream` value.
   */
  def fromInputStreamManaged[R](
    is: ZManaged[R, IOException, InputStream],
    chunkSize: Int = ZStream.DefaultChunkSize
  )(implicit trace: ZTraceElement): ZStream[R, IOException, Byte] =
    ZStream.managed(is).flatMap(fromInputStream(_, chunkSize))

  /**
   * Creates a stream from `java.io.Reader`.
   */
  def fromReader(reader: => Reader, chunkSize: Int = ZStream.DefaultChunkSize)(implicit
    trace: ZTraceElement
  ): ZStream[Any, IOException, Char] =
    ZStream.fromZIO(UIO(reader)).flatMap { capturedReader =>
      ZStream.repeatZIOChunkOption {
        for {
          bufArray  <- UIO(Array.ofDim[Char](chunkSize))
          bytesRead <- ZIO.attemptBlockingIO(capturedReader.read(bufArray)).asSomeError
          chars <- if (bytesRead < 0)
                     ZIO.fail(None)
                   else if (bytesRead == 0)
                     UIO(Chunk.empty)
                   else if (bytesRead < chunkSize)
                     UIO(Chunk.fromArray(bufArray).take(bytesRead))
                   else
                     UIO(Chunk.fromArray(bufArray))
        } yield chars
      }
    }

  /**
   * Creates a stream from an effect producing `java.io.Reader`.
   */
  def fromReaderEffect[R](
    reader: => ZIO[R, IOException, Reader],
    chunkSize: Int = ZStream.DefaultChunkSize
  )(implicit trace: ZTraceElement): ZStream[R, IOException, Char] =
    fromReaderManaged(reader.toManagedWith(r => ZIO.succeed(r.close())), chunkSize)

  /**
   * Creates a stream from managed `java.io.Reader`.
   */
  def fromReaderManaged[R](
    reader: => ZManaged[R, IOException, Reader],
    chunkSize: Int = ZStream.DefaultChunkSize
  )(implicit trace: ZTraceElement): ZStream[R, IOException, Char] =
    ZStream.managed(reader).flatMap(fromReader(_, chunkSize))

  /**
   * Creates a stream from a callback that writes to `java.io.OutputStream`.
   * Note: the input stream will be closed after the `write` is done.
   */
  def fromOutputStreamWriter(
    write: OutputStream => Unit,
    chunkSize: Int = ZStream.DefaultChunkSize
  )(implicit trace: ZTraceElement): ZStream[Any, Throwable, Byte] = {
    def from(in: InputStream, out: OutputStream, err: Promise[Throwable, None.type]) = {
      val readIn = fromInputStream(in, chunkSize).ensuring(ZIO.succeed(in.close()))
      val writeOut = ZStream.fromZIO {
        ZIO
          .attemptBlockingInterrupt(write(out))
          .exit
          .tap(exit => err.done(exit.as(None)))
          .ensuring(ZIO.succeed(out.close()))
      }

      val handleError = ZStream.fromZIOOption(err.await.some)
      readIn.drainFork(writeOut) ++ handleError
    }

    for {
      out    <- ZStream.fromZIO(ZIO.succeed(new PipedOutputStream()))
      in     <- ZStream.fromZIO(ZIO.succeed(new PipedInputStream(out)))
      err    <- ZStream.fromZIO(Promise.make[Throwable, None.type])
      result <- from(in, out, err)
    } yield result
  }

  /**
   * Creates a stream from a Java stream
   */
  final def fromJavaStream[R, A](stream: => java.util.stream.Stream[A])(implicit
    trace: ZTraceElement
  ): ZStream[R, Throwable, A] =
    ZStream.fromJavaIterator(stream.iterator())

  /**
   * Creates a stream from a Java stream
   */
  final def fromJavaStreamEffect[R, A](
    stream: ZIO[R, Throwable, java.util.stream.Stream[A]]
  )(implicit trace: ZTraceElement): ZStream[R, Throwable, A] =
    ZStream.fromJavaIteratorZIO(stream.flatMap(s => UIO(s.iterator())))

  /**
   * Creates a stream from a managed Java stream
   */
  final def fromJavaStreamManaged[R, A](
    stream: ZManaged[R, Throwable, java.util.stream.Stream[A]]
  )(implicit trace: ZTraceElement): ZStream[R, Throwable, A] =
    ZStream.fromJavaIteratorManaged(stream.mapZIO(s => UIO(s.iterator())))

  /**
   * Creates a stream from a Java stream
   */
  final def fromJavaStreamTotal[A](stream: => java.util.stream.Stream[A])(implicit
    trace: ZTraceElement
  ): ZStream[Any, Nothing, A] =
    ZStream.fromJavaIteratorSucceed(stream.iterator())

  /**
   * Create a stream of accepted connection from server socket
   * Emit socket `Connection` from which you can read / write and ensure it is closed after it is used
   */
  def fromSocketServer(
    port: Int,
    host: Option[String] = None
  )(implicit trace: ZTraceElement): ZStream[Any, Throwable, Connection] =
    for {
      server <- ZStream.managed(ZManaged.fromAutoCloseable(ZIO.attemptBlocking {
                  AsynchronousServerSocketChannel
                    .open()
                    .bind(
                      host.fold(new InetSocketAddress(port))(new InetSocketAddress(_, port))
                    )
                }))

      registerConnection <- ZStream.managed(ZManaged.scope)

      conn <- ZStream.repeatZIO {
                ZIO
                  .async[Any, Throwable, UManaged[Connection]] { callback =>
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
                  .flatMap(managedConn => registerConnection(managedConn).map(_._2))
              }
    } yield conn

  /**
   * Accepted connection made to a specific channel `AsynchronousServerSocketChannel`
   */
  final class Connection private (socket: AsynchronousSocketChannel) {

    /**
     * Read the entire `AsynchronousSocketChannel` by emitting a `Chunk[Byte]`
     */
    def read(implicit trace: ZTraceElement): ZStream[Any, Throwable, Byte] =
      ZStream.fromZIO(UIO(ByteBuffer.allocate(ZStream.DefaultChunkSize))).flatMap { reusableBuffer =>
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
     * Write the entire Chuck[Byte] to the socket channel.
     *
     * The sink will yield the count of bytes written.
     */
    def write(implicit trace: ZTraceElement): ZSink[Any, Throwable, Byte, Throwable, Nothing, Int] =
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
     * Close the underlying socket
     */
    private[stream] def close()(implicit trace: ZTraceElement): UIO[Unit] = ZIO.succeed(socket.close())
  }

  object Connection {

    /**
     * Create a `Managed` connection
     */
    def make(socket: AsynchronousSocketChannel)(implicit trace: ZTraceElement): UManaged[Connection] =
      Managed.acquireReleaseWith(ZIO.succeed(new Connection(socket)))(_.close())
  }

  trait ZStreamConstructorPlatformSpecific extends ZStreamConstructorLowPriority1
}

trait ZPipelinePlatformSpecificConstructors {
  def deflate(
    bufferSize: Int = 64 * 1024,
    noWrap: Boolean = false,
    level: CompressionLevel = CompressionLevel.DefaultCompression,
    strategy: CompressionStrategy = CompressionStrategy.DefaultStrategy,
    flushMode: FlushMode = FlushMode.NoFlush
  )(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, Byte, Byte] =
    ZPipeline.fromChannel(
      Deflate.makeDeflater(
        bufferSize,
        noWrap,
        level,
        strategy,
        flushMode
      )
    )

  def inflate(
    bufferSize: Int = 64 * 1024,
    noWrap: Boolean = false
  )(implicit trace: ZTraceElement): ZPipeline[Any, CompressionException, Byte, Byte] =
    ZPipeline.fromChannel(
      Inflate.makeInflater(bufferSize, noWrap)
    )

  def gzip(
    bufferSize: Int = 64 * 1024,
    level: CompressionLevel = CompressionLevel.DefaultCompression,
    strategy: CompressionStrategy = CompressionStrategy.DefaultStrategy,
    flushMode: FlushMode = FlushMode.NoFlush
  )(implicit trace: ZTraceElement): ZPipeline[Any, Nothing, Byte, Byte] =
    ZPipeline.fromChannel(
      Gzip.makeGzipper(bufferSize, level, strategy, flushMode)
    )

  def gunzip(bufferSize: Int = 64 * 1024)(implicit
    trace: ZTraceElement
  ): ZPipeline[Any, CompressionException, Byte, Byte] =
    ZPipeline.fromChannel(
      Gunzip.makeGunzipper(bufferSize)
    )
}
