package zio

import cats.effect.unsafe.implicits.global
import cats.effect.{IO => CIO}
import cats.syntax.all._
import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.net.{InetSocketAddress, ServerSocket, Socket}
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 10, timeUnit = TimeUnit.SECONDS, time = 10)
@Warmup(iterations = 10, timeUnit = TimeUnit.SECONDS, time = 10)
@Fork(1)
class LoomBenchmark {
  val serverAccepts            = new AtomicInteger(0)
  val serverConnectionFinishes = new AtomicInteger(0)
  val clientCount              = new AtomicInteger(0)
  val serverChunkWrite         = new AtomicInteger(0)
  val clientChunkRead          = new AtomicInteger(0)

  def resetMetrics(): Unit = {
    serverAccepts.set(0)
    serverConnectionFinishes.set(0)
    clientCount.set(0)
    serverChunkWrite.set(0)
    clientChunkRead.set(0)
  }

  def dumpMetrics(label: String): Unit = {
    println(s"=== $label ===")
    println(s"serverAccepts: ${serverAccepts.get()}")
    println(s"serverConnectionFinishes: ${serverConnectionFinishes.get()}")
    println(s"clientCount: ${clientCount.get()}")
    println(s"serverChunkWrite: ${serverChunkWrite.get()}")
    println(s"clientChunkRead: ${clientChunkRead.get()}")
  }

  def beforeBenchmark(): Unit = resetMetrics()
  def afterBenchmark(): Unit  = dumpMetrics("after benchmark")

  @Param(Array("1024"))
  var chunkSize = 0

  @Param(Array("1024"))
  var chunkCount = 0

  @Param(Array("100"))
  var concurrency = 0

  @Setup
  def setup(): Unit = Thread.sleep(1000)

  def makeAsyncServerSocket(): AsynchronousServerSocketChannel =
    AsynchronousServerSocketChannel.open().bind(null, concurrency)

  def cioAccept(server: AsynchronousServerSocketChannel): CIO[AsynchronousSocketChannel] =
    CIO.async_[AsynchronousSocketChannel] { k =>
      server.accept(
        (),
        new CompletionHandler[AsynchronousSocketChannel, Unit] {
          def completed(result: AsynchronousSocketChannel, attachment: Unit): Unit =
            k(Right(result))
          def failed(exc: Throwable, attachment: Unit): Unit =
            k(Left(exc))
        }
      )
    }

  def zioAcceptAsync(server: AsynchronousServerSocketChannel): Task[AsynchronousSocketChannel] =
    ZIO.async[Any, Throwable, AsynchronousSocketChannel] { k =>
      server.accept(
        (),
        new CompletionHandler[AsynchronousSocketChannel, Unit] {
          def completed(result: AsynchronousSocketChannel, attachment: Unit): Unit =
            k(Exit.succeed(result))
          def failed(exc: Throwable, attachment: Unit): Unit =
            k(Exit.fail(exc))
        }
      )
    }

  def cioWrite(channel: AsynchronousSocketChannel, data: Array[Byte]): CIO[Unit] =
    CIO {
      val buf = java.nio.ByteBuffer.wrap(data)

      def writeMore: CIO[Boolean] =
        CIO.async_[Boolean] { k =>
          channel.write(
            buf,
            (),
            new CompletionHandler[Integer, Unit] {
              def completed(result: Integer, attachment: Unit): Unit =
                k(Right(buf.remaining() > 0))
              def failed(exc: Throwable, attachment: Unit): Unit =
                k(Left(exc))
            }
          )
        }

      lazy val loop: CIO[Unit] =
        writeMore.flatMap { isMore =>
          if (isMore) loop
          else CIO.unit
        }

      loop
    }.flatten

  def zioWriteAsync(channel: AsynchronousSocketChannel, data: Array[Byte]): Task[Unit] =
    ZIO.suspendSucceed {
      val buf = java.nio.ByteBuffer.wrap(data)

      def writeMore: Task[Boolean] =
        ZIO.async[Any, Throwable, Boolean] { k =>
          channel.write(
            buf,
            (),
            new CompletionHandler[Integer, Unit] {
              def completed(result: Integer, attachment: Unit): Unit =
                k(Exit.succeed(buf.remaining() > 0))
              def failed(exc: Throwable, attachment: Unit): Unit =
                k(Exit.fail(exc))
            }
          )
        }

      lazy val loop: Task[Unit] =
        writeMore.flatMap { isMore =>
          if (isMore) loop
          else ZIO.unit
        }

      loop
    }

  def cioRead(channel: AsynchronousSocketChannel, totalBytes: Int): CIO[Array[Byte]] =
    CIO {
      val buffer = new Array[Byte](totalBytes)
      val buf    = java.nio.ByteBuffer.wrap(buffer)

      def readMore: CIO[Boolean] =
        CIO.async_[Boolean] { k =>
          channel.read(
            buf,
            (),
            new CompletionHandler[Integer, Unit] {
              def completed(result: Integer, attachment: Unit): Unit =
                k(Right(result != -1))
              def failed(exc: Throwable, attachment: Unit): Unit =
                k(Left(exc))
            }
          )
        }

      lazy val loop: CIO[Array[Byte]] =
        readMore.flatMap { isMore =>
          if (isMore && buf.remaining() > 0) loop
          else CIO(buffer)
        }

      loop
    }.flatten

  def zioReadAsync(channel: AsynchronousSocketChannel, totalBytes: Int): Task[Array[Byte]] =
    ZIO.suspendSucceed {
      val buffer = new Array[Byte](totalBytes)
      val buf    = java.nio.ByteBuffer.wrap(buffer)

      def readMore: Task[Boolean] =
        ZIO.async[Any, Throwable, Boolean] { k =>
          channel.read(
            buf,
            (),
            new CompletionHandler[Integer, Unit] {
              def completed(result: Integer, attachment: Unit): Unit =
                k(Exit.succeed(result != -1))
              def failed(exc: Throwable, attachment: Unit): Unit =
                k(Exit.fail(exc))
            }
          )
        }

      lazy val loop: Task[Array[Byte]] =
        readMore.flatMap { isMore =>
          if (isMore && buf.remaining() > 0) loop
          else ZIO.succeed(buffer)
        }

      loop
    }

  def cioMakeAsyncServerSocket: CIO[AsynchronousServerSocketChannel] =
    CIO(makeAsyncServerSocket())

  def zioMakeAsyncServerSocket: Task[AsynchronousServerSocketChannel] =
    ZIO.attempt(makeAsyncServerSocket())

  def cioMakeClient(server: AsynchronousServerSocketChannel): CIO[AsynchronousSocketChannel] =
    CIO.async_[AsynchronousSocketChannel] { k =>
      val channel = AsynchronousSocketChannel.open()

      val address = server.getLocalAddress().asInstanceOf[InetSocketAddress]

      channel
        .bind(null)
        .connect(
          new InetSocketAddress("localhost", address.getPort()),
          (),
          new CompletionHandler[Void, Unit] {
            def completed(result: Void, attachment: Unit): Unit =
              k(Right(channel))
            def failed(exc: Throwable, attachment: Unit): Unit = {
              exc.printStackTrace()

              k(Left(exc))
            }
          }
        )
    }

  def zioMakeClientAsync(server: AsynchronousServerSocketChannel): Task[AsynchronousSocketChannel] =
    ZIO.async[Any, Throwable, AsynchronousSocketChannel] { k =>
      val channel = AsynchronousSocketChannel.open()

      val address = server.getLocalAddress().asInstanceOf[InetSocketAddress]

      channel
        .bind(null)
        .connect(
          new InetSocketAddress("localhost", address.getPort()),
          (),
          new CompletionHandler[Void, Unit] {
            def completed(result: Void, attachment: Unit): Unit =
              k(Exit.succeed(channel))
            def failed(exc: Throwable, attachment: Unit): Unit = {
              exc.printStackTrace()

              k(Exit.fail(exc))
            }
          }
        )
    }

  def cioClientReadAll(server: AsynchronousServerSocketChannel): CIO[Unit] =
    for {
      client <- cioMakeClient(server)
      _      <- catsRepeat(chunkCount)(cioRead(client, chunkSize)).guarantee(CIO(client.close()).attempt.void)
    } yield ()

  def zioClientReadAllAsync(server: AsynchronousServerSocketChannel): Task[Unit] =
    for {
      client <- zioMakeClientAsync(server)
      _      <- repeat(chunkCount)(zioReadAsync(client, chunkSize)).ensuring(ZIO.attempt(client.close()).ignore)
    } yield ()

  def cioServerAcceptAll(server: AsynchronousServerSocketChannel): CIO[Unit] =
    catsRepeat(concurrency)(cioAccept(server).flatMap { channel =>
      catsRepeat(chunkCount)(cioWrite(channel, new Array[Byte](chunkSize)))
        .guarantee(CIO(channel.close()))
        .start
    }).void

  def zioServerAcceptAllAsync(server: AsynchronousServerSocketChannel): Task[Unit] =
    repeat(concurrency)(zioAcceptAsync(server).flatMap { channel =>
      repeat(chunkCount)(zioWriteAsync(channel, new Array[Byte](chunkSize)))
        .ensuring(ZIO.succeed(channel.close()))
        .forkDaemon
    }).unit

  def zioRead(is: InputStream, totalBytes: Int): Task[Array[Byte]] =
    ZIO.attempt {
      clientChunkRead.incrementAndGet()

      val buffer    = new Array[Byte](totalBytes)
      var index     = 0
      var moreData  = true
      var remaining = totalBytes

      while (moreData && remaining > 0) {
        val bytesRead = is.read(buffer, index, remaining)
        if (bytesRead == -1) {
          moreData = false
        } else {
          index += bytesRead
          remaining -= bytesRead
        }
      }

      buffer
    }

  def zioWrite(os: OutputStream, data: Array[Byte]): Task[Unit] =
    ZIO.attempt { os.write(data); serverChunkWrite.incrementAndGet(); () }.eventually

  def zioMakeClient(server: ServerSocket): Task[Socket] =
    ZIO.attempt {
      val socket = new Socket()

      socket.setReuseAddress(true)

      socket.connect(new InetSocketAddress(server.getInetAddress(), server.getLocalPort()))

      clientCount.incrementAndGet()

      socket
    }

  def zioClientReadAll(server: ServerSocket): Task[Unit] =
    for {
      client <- zioMakeClient(server)
      _      <- repeat(chunkCount)(zioRead(client.getInputStream(), chunkSize)).ensuring(ZIO.attempt(client.close()).ignore)
    } yield ()

  def zioServerAcceptAll(server: ServerSocket): Task[Unit] =
    repeat(concurrency)(ZIO.attempt { serverAccepts.incrementAndGet(); server.accept() }.flatMap { socket =>
      val os          = socket.getOutputStream()
      val serverWrite = repeat(chunkCount)(zioWrite(os, new Array[Byte](chunkSize)))

      serverWrite
        .tapErrorCause(ZIO.debug(_))
        .ensuring(ZIO.succeed(socket.close()))
        .forkDaemon
    }).unit

  @Benchmark
  def catsReadWrite(): Unit = {
    def doTest(server: AsynchronousServerSocketChannel) =
      for {
        _ <- cioServerAcceptAll(server).start
        _ <- (1 to concurrency).toList.parTraverse_(_ => cioClientReadAll(server))
      } yield ()

    (for {
      server <- cioMakeAsyncServerSocket
      _      <- doTest(server).guarantee(CIO(server.close()))
    } yield ()).unsafeRunSync()
  }

  @Benchmark
  def zioReadWritePostLoom(): Unit = {
    def doTest(server: ServerSocket) =
      for {
        fiber <- zioServerAcceptAll(server).fork
        _     <- ZIO.foreachParDiscard(1 to concurrency)(_ => zioClientReadAll(server))
        _     <- fiber.interrupt
      } yield ()

    def makeServerSocket(): ServerSocket = {
      val serverSocket = new ServerSocket()

      serverSocket.setReuseAddress(true)

      serverSocket.bind(null, concurrency)

      serverSocket
    }

    unsafeRun {
      for {
        server <- ZIO.attempt(makeServerSocket())
        _      <- doTest(server).ensuring(ZIO.succeed(server.close()))
      } yield ()
    }
  }

  // unsafeRun {
  //   (Fiber.dumpAll *> ZIO.succeed(dumpMetrics("regular dump"))).delay(2.minutes).forever.forkDaemon
  // }

  @Benchmark
  def zioReadWritePreLoom(): Unit = {
    def doTest(server: AsynchronousServerSocketChannel) =
      for {
        _ <- zioServerAcceptAllAsync(server).forkDaemon
        _ <- ZIO.foreachParDiscard(1 to concurrency)(_ => zioClientReadAllAsync(server))
      } yield ()

    unsafeRun {
      for {
        server <- zioMakeAsyncServerSocket
        _      <- doTest(server).ensuring(ZIO.succeed(server.close()))
      } yield ()
    }
  }
}
