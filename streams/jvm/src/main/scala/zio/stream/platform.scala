package zio.stream

import java.io.{ IOException, InputStream, OutputStream, Reader }
import java.net.InetSocketAddress
import java.nio.channels.FileChannel
import java.nio.channels.{ AsynchronousServerSocketChannel, AsynchronousSocketChannel, CompletionHandler }
import java.nio.file.StandardOpenOption._
import java.nio.file.{ OpenOption, Path }
import java.nio.{ Buffer, ByteBuffer }
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
  ): ZSink[Blocking, IOException, Byte, Byte, Long] = fromOutputStreamManaged(ZManaged.succeedNow(os))

  /**
   * Uses the provided `OutputStream` resource to create a [[ZSink]] that consumes byte chunks
   * and writes them to the `OutputStream`. The sink will yield the count of bytes written.
   *
   * The `OutputStream` will be automatically closed after the stream is finished or an error occurred.
   */
  final def fromOutputStreamManaged(
    os: ZManaged[Blocking, IOException, OutputStream]
  ): ZSink[Blocking, IOException, Byte, Byte, Long] =
    ZSink.managed(os) { out =>
      ZSink.foldLeftChunksM(0L) { (bytesWritten, byteChunk: Chunk[Byte]) =>
        blocking.effectBlockingInterrupt {
          val bytes = byteChunk.toArray
          out.write(bytes)
          bytesWritten + bytes.length
        }.refineOrDie {
          case e: IOException => e
        }
      }
    }

  /**
   * Uses the provided `Path` to create a [[ZSink]] that consumes byte chunks
   * and writes them to the `File`. The sink will yield count of bytes written.
   */
  final def fromFile(
    path: => Path,
    position: Long = 0L,
    options: Set[OpenOption] = Set(WRITE, TRUNCATE_EXISTING, CREATE)
  ): ZSink[Blocking, Throwable, Byte, Byte, Long] = {
    val managedChannel = ZManaged.make(
      blocking
        .effectBlockingInterrupt(
          FileChannel
            .open(
              path,
              options.foldLeft(new ju.HashSet[OpenOption]()) { (acc, op) =>
                acc.add(op); acc
              } // for avoiding usage of different Java collection converters for different scala versions
            )
            .position(position)
        )
    )(chan => blocking.effectBlocking(chan.close()).orDie)

    val writer: ZSink[Blocking, Throwable, Byte, Byte, Unit] = ZSink.managed(managedChannel) { chan =>
      ZSink.foreachChunk[Blocking, Throwable, Byte](byteChunk =>
        blocking.effectBlockingInterrupt {
          chan.write(ByteBuffer.wrap(byteChunk.toArray))
        }
      )
    }
    writer &> ZSink.count
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
   * Creates a stream of bytes from a file at the specified path.
   */
  def fromFile(path: => Path, chunkSize: Int = ZStream.DefaultChunkSize): ZStream[Blocking, Throwable, Byte] =
    ZStream
      .bracket(blocking.effectBlockingInterrupt(FileChannel.open(path)))(chan =>
        blocking.effectBlocking(chan.close()).orDie
      )
      .flatMap { channel =>
        ZStream.fromEffect(UIO(ByteBuffer.allocate(chunkSize))).flatMap { reusableBuffer =>
          ZStream.repeatEffectChunkOption(
            for {
              bytesRead <- blocking.effectBlockingInterrupt(channel.read(reusableBuffer)).mapError(Some(_))
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
        capturedIs <- Managed.effectTotal(is)
        pull = {
          def go: ZIO[Blocking, Option[IOException], Chunk[Byte]] = done.get.flatMap {
            if (_) Pull.end
            else
              for {
                bufArray  <- UIO(Array.ofDim[Byte](chunkSize))
                bytesRead <- blocking.effectBlockingIO(capturedIs.read(bufArray)).mapError(Some(_))
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
   * Creates a stream from [[java.io.Reader]].
   */
  def fromReader(reader: => Reader, chunkSize: Int = ZStream.DefaultChunkSize): ZStream[Blocking, IOException, Char] =
    ZStream.fromEffect(UIO(reader)).flatMap { capturedReader =>
      ZStream.repeatEffectChunkOption {
        for {
          bufArray  <- UIO(Array.ofDim[Char](chunkSize))
          bytesRead <- blocking.effectBlockingIO(capturedReader.read(bufArray)).mapError(Some(_))
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
   * Creates a stream from an effect producing [[java.io.Reader]].
   */
  def fromReaderEffect[R](
    reader: => ZIO[R, IOException, Reader],
    chunkSize: Int = ZStream.DefaultChunkSize
  ): ZStream[R with Blocking, IOException, Char] =
    fromReaderManaged(reader.toManaged(r => ZIO.effectTotal(r.close())), chunkSize)

  /**
   * Creates a stream from managed [[java.io.Reader]].
   */
  def fromReaderManaged[R](
    reader: => ZManaged[R, IOException, Reader],
    chunkSize: Int = ZStream.DefaultChunkSize
  ): ZStream[R with Blocking, IOException, Char] =
    ZStream.managed(reader).flatMap(fromReader(_, chunkSize))

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

  /**
   * Create a stream of accepted connection from server socket
   * Emit socket `Connection` from which you can read / write and ensure it is closed after it is used
   */
  def fromSocketServer(
    port: Int,
    host: Option[String] = None
  ): ZStream[Blocking, Throwable, Connection] =
    for {
      server <- ZStream.managed(ZManaged.fromAutoCloseable(blocking.effectBlocking {
                 AsynchronousServerSocketChannel
                   .open()
                   .bind(
                     host.fold(new InetSocketAddress(port))(new InetSocketAddress(_, port))
                   )
               }))

      registerConnection <- ZStream.managed(ZManaged.scope)

      conn <- ZStream.repeatEffect {
               IO.effectAsync[Throwable, UManaged[Connection]] { callback =>
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
  class Connection(socket: AsynchronousSocketChannel) {

    /**
     * Read the entire `AsynchronousSocketChannel` by emitting a `Chunk[Byte]`
     * The caller of this function is NOT responsible for closing the `AsynchronousSocketChannel`.
     */
    def read: Stream[Throwable, Byte] =
      ZStream.unfoldChunkM(0) {
        case -1 => ZIO.succeed(Option.empty)
        case _ =>
          val buff = ByteBuffer.allocate(ZStream.DefaultChunkSize)

          IO.effectAsync[Throwable, Option[(Chunk[Byte], Int)]] { callback =>
            socket.read(
              buff,
              null,
              new CompletionHandler[Integer, Void] {
                override def completed(bytesRead: Integer, attachment: Void): Unit = {
                  (buff: Buffer).flip()
                  callback(ZIO.succeed(Option(Chunk.fromByteBuffer(buff) -> bytesRead.toInt)))
                }

                override def failed(error: Throwable, attachment: Void): Unit = callback(ZIO.fail(error))
              }
            )
          }
      }

    /**
     * Write the entire Chuck[Byte] to the socket channel.
     * The caller of this function is NOT responsible for closing the `AsynchronousSocketChannel`.
     *
     * The sink will yield the count of bytes written.
     */
    def write: Sink[Throwable, Byte, Nothing, Int] =
      ZSink.foldLeftChunksM(0) {
        case (nbBytesWritten, c) =>
          IO.effectAsync[Throwable, Int] { callback =>
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
    def close(): UIO[Unit] = ZIO.effectTotal(socket.close())
  }

  object Connection {

    /**
     * Create a `Managed` connection
     */
    def make(socket: AsynchronousSocketChannel): UManaged[Connection] =
      Managed.make(ZIO.succeed(new Connection(socket)))(_.close())
  }
}
