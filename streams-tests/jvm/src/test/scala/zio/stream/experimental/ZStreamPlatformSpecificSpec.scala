package zio.stream.experimental

import zio._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.{Gen, assert, assertCompletes, assertM, check}

import java.io.{FileNotFoundException, FileReader, IOException, OutputStream, Reader}
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousSocketChannel
import java.nio.file.{Files, NoSuchFileException, Paths}
import java.nio.{Buffer, ByteBuffer}
import java.util.concurrent.CountDownLatch
import scala.concurrent.ExecutionContext.global

object ZStreamPlatformSpecificSpec extends ZIOBaseSpec {
  def socketClient(port: Int): ZManaged[Any, Throwable, AsynchronousSocketChannel] =
    ZManaged.acquireReleaseWith(ZIO.attemptBlockingIO(AsynchronousSocketChannel.open()).flatMap { client =>
      ZIO
        .fromFutureJava(client.connect(new InetSocketAddress("localhost", port)))
        .map(_ => client)
    })(c => ZIO.succeed(c.close()))

  def spec = suite("ZStream JVM experimental")(
    suite("Constructors")(
      suite("asyncMaybe")(
        test("asyncMaybe signal end stream") {
          for {
            result <- ZStream
                        .asyncMaybe[Any, Nothing, Int] { k =>
                          k(IO.fail(None))
                          None
                        }
                        .runCollect
          } yield assert(result)(equalTo(Chunk.empty))
        },
        test("asyncMaybe Some")(check(Gen.chunkOf(Gen.int)) { chunk =>
          val s = ZStream.asyncMaybe[Any, Throwable, Int](_ => Some(ZStream.fromIterable(chunk)))

          assertM(s.runCollect.map(_.take(chunk.size)))(equalTo(chunk))
        }),
        test("asyncMaybe None")(check(Gen.chunkOf(Gen.int)) { chunk =>
          val s = ZStream.asyncMaybe[Any, Throwable, Int] { k =>
            global.execute(() => chunk.foreach(a => k(Task.succeed(Chunk.single(a)))))
            None
          }

          assertM(s.take(chunk.size.toLong).runCollect)(equalTo(chunk))
        }),
        test("asyncMaybe back pressure") {
          for {
            refCnt  <- Ref.make(0)
            refDone <- Ref.make[Boolean](false)
            stream = ZStream.asyncMaybe[Any, Throwable, Int](
                       cb => {
                         global.execute { () =>
                           // 1st consumed by sink, 2-6 – in queue, 7th – back pressured
                           (1 to 7).foreach(i => cb(refCnt.set(i) *> ZIO.succeedNow(Chunk.single(1))))
                           cb(refDone.set(true) *> ZIO.fail(None))
                         }
                         None
                       },
                       5
                     )
            run    <- stream.run(ZSink.take(1) *> ZSink.never).fork
            _      <- refCnt.get.repeatWhile(_ != 7)
            isDone <- refDone.get
            _      <- run.interrupt
          } yield assert(isDone)(isFalse)
        }
      ),
      suite("asyncZIO")(
        test("asyncZIO")(check(Gen.chunkOf(Gen.int).filter(_.nonEmpty)) { chunk =>
          for {
            latch <- Promise.make[Nothing, Unit]
            fiber <- ZStream
                       .asyncZIO[Any, Throwable, Int] { k =>
                         global.execute(() => chunk.foreach(a => k(Task.succeed(Chunk.single(a)))))
                         latch.succeed(()) *>
                           Task.unit
                       }
                       .take(chunk.size.toLong)
                       .run(ZSink.collectAll[Throwable, Int])
                       .fork
            _ <- latch.await
            s <- fiber.join
          } yield assert(s)(equalTo(chunk))
        }),
        test("asyncZIO signal end stream") {
          for {
            result <- ZStream
                        .asyncZIO[Any, Nothing, Int] { k =>
                          global.execute(() => k(IO.fail(None)))
                          UIO.unit
                        }
                        .runCollect
          } yield assert(result)(equalTo(Chunk.empty))
        },
        test("asyncZIO back pressure") {
          for {
            refCnt  <- Ref.make(0)
            refDone <- Ref.make[Boolean](false)
            stream = ZStream.asyncZIO[Any, Throwable, Int](
                       cb => {
                         global.execute { () =>
                           // 1st consumed by sink, 2-6 – in queue, 7th – back pressured
                           (1 to 7).foreach(i => cb(refCnt.set(i) *> ZIO.succeedNow(Chunk.single(1))))
                           cb(refDone.set(true) *> ZIO.fail(None))
                         }
                         UIO.unit
                       },
                       5
                     )
            run    <- stream.run(ZSink.take(1) *> ZSink.never).fork
            _      <- refCnt.get.repeatWhile(_ != 7)
            isDone <- refDone.get
            _      <- run.interrupt
          } yield assert(isDone)(isFalse)
        }
      ),
      suite("asyncManaged")(
        test("asyncManaged")(check(Gen.chunkOf(Gen.int).filter(_.nonEmpty)) { chunk =>
          for {
            latch <- Promise.make[Nothing, Unit]
            fiber <- ZStream
                       .asyncManaged[Any, Throwable, Int] { k =>
                         global.execute(() => chunk.foreach(a => k(Task.succeed(Chunk.single(a)))))
                         latch.succeed(()).toManaged *>
                           Task.unit.toManaged
                       }
                       .take(chunk.size.toLong)
                       .run(ZSink.collectAll)
                       .fork
            _ <- latch.await
            s <- fiber.join
          } yield assert(s)(equalTo(chunk))
        }),
        test("asyncManaged signal end stream") {
          for {
            result <- ZStream
                        .asyncManaged[Any, Nothing, Int] { k =>
                          global.execute(() => k(IO.fail(None)))
                          UIO.unit.toManaged
                        }
                        .runCollect
          } yield assert(result)(equalTo(Chunk.empty))
        },
        test("asyncManaged back pressure") {
          for {
            refCnt  <- Ref.make(0)
            refDone <- Ref.make[Boolean](false)
            stream = ZStream.asyncManaged[Any, Throwable, Int](
                       cb => {
                         global.execute { () =>
                           // 1st consumed by sink, 2-6 – in queue, 7th – back pressured
                           (1 to 7).foreach(i => cb(refCnt.set(i) *> ZIO.succeedNow(Chunk.single(1))))
                           cb(refDone.set(true) *> ZIO.fail(None))
                         }
                         UIO.unit.toManaged
                       },
                       5
                     )
            run    <- stream.run(ZSink.take(1) *> ZSink.never).fork
            _      <- refCnt.get.repeatWhile(_ != 7)
            isDone <- refDone.get
            _      <- run.interrupt
          } yield assert(isDone)(isFalse)
        }
      ),
      suite("asyncInterrupt")(
        test("asyncInterrupt Left") {
          for {
            cancelled <- Ref.make(false)
            latch     <- Promise.make[Nothing, Unit]
            fiber <- ZStream
                       .asyncInterrupt[Any, Nothing, Unit] { offer =>
                         global.execute(() => offer(ZIO.succeedNow(Chunk.unit)))
                         Left(cancelled.set(true))
                       }
                       .tap(_ => latch.succeed(()))
                       .runDrain
                       .fork
            _      <- latch.await
            _      <- fiber.interrupt
            result <- cancelled.get
          } yield assert(result)(isTrue)
        },
        test("asyncInterrupt Right")(check(Gen.chunkOf(Gen.int)) { chunk =>
          val s = ZStream.asyncInterrupt[Any, Throwable, Int](_ => Right(ZStream.fromIterable(chunk)))

          assertM(s.take(chunk.size.toLong).runCollect)(equalTo(chunk))
        }),
        test("asyncInterrupt signal end stream ") {
          for {
            result <- ZStream
                        .asyncInterrupt[Any, Nothing, Int] { k =>
                          global.execute(() => k(IO.fail(None)))
                          Left(UIO.succeedNow(()))
                        }
                        .runCollect
          } yield assert(result)(equalTo(Chunk.empty))
        },
        test("asyncInterrupt back pressure") {
          for {
            selfId  <- ZIO.fiberId
            refCnt  <- Ref.make(0)
            refDone <- Ref.make[Boolean](false)
            stream = ZStream.asyncInterrupt[Any, Throwable, Int](
                       cb => {
                         global.execute { () =>
                           // 1st consumed by sink, 2-6 – in queue, 7th – back pressured
                           (1 to 7).foreach(i => cb(refCnt.set(i) *> ZIO.succeedNow(Chunk.single(1))))
                           cb(refDone.set(true) *> ZIO.fail(None))
                         }
                         Left(UIO.unit)
                       },
                       5
                     )
            run    <- stream.run(ZSink.take(1) *> ZSink.never).fork
            _      <- refCnt.get.repeatWhile(_ != 7)
            isDone <- refDone.get
            exit   <- run.interrupt
          } yield assert(isDone)(isFalse) &&
            assert(exit.untraced)(failsCause(containsCause(Cause.interrupt(selfId))))
        }
      ),
      suite("fromFile")(
        test("reads from an existing file") {
          val data = (0 to 100).mkString

          Task(Files.createTempFile("stream", "fromFile")).acquireReleaseWith(path => Task(Files.delete(path)).orDie) {
            path =>
              Task(Files.write(path, data.getBytes("UTF-8"))) *>
                assertM(
                  ZStream
                    .fromFile(path, 24)
                    .transduce(ZSink.utf8Decode)
                    .runCollect
                    .map(_.collect { case Some(str) => str }.mkString)
                )(
                  equalTo(data)
                )
          }
        },
        test("fails on a nonexistent file") {
          assertM(ZStream.fromFile(Paths.get("nonexistent"), 24).runDrain.exit)(
            fails(isSubtype[NoSuchFileException](anything))
          )
        }
      ),
      suite("fromReader")(
        test("reads non-empty file") {
          Task(Files.createTempFile("stream", "reader")).acquireReleaseWith(path => UIO(Files.delete(path))) { path =>
            for {
              data <- UIO((0 to 100).mkString)
              _    <- Task(Files.write(path, data.getBytes("UTF-8")))
              read <- ZStream.fromReader(new FileReader(path.toString)).runCollect.map(_.mkString)
            } yield assert(read)(equalTo(data))
          }
        },
        test("reads empty file") {
          Task(Files.createTempFile("stream", "reader-empty")).acquireReleaseWith(path => UIO(Files.delete(path))) {
            path =>
              ZStream
                .fromReader(new FileReader(path.toString))
                .runCollect
                .map(_.mkString)
                .map(assert(_)(isEmptyString))
          }
        },
        test("fails on a failing reader") {
          final class FailingReader extends Reader {
            def read(x: Array[Char], a: Int, b: Int): Int = throw new IOException("failed")

            def close(): Unit = ()
          }

          ZStream
            .fromReader(new FailingReader)
            .runDrain
            .exit
            .map(assert(_)(fails(isSubtype[IOException](anything))))
        }
      ),
      suite("fromResource")(
        test("returns the content of the resource") {
          ZStream
            .fromResource("zio/stream/bom/quickbrown-UTF-8-with-BOM.txt")
            .transduce(ZSink.utf8Decode)
            .runCollect
            .map(b => assert(b.collect { case Some(str) => str }.mkString)(startsWithString("Sent")))
        },
        test("fails with FileNotFoundException if the stream does not exist") {
          assertM(ZStream.fromResource("does_not_exist").runDrain.exit)(
            fails(isSubtype[FileNotFoundException](hasMessage(containsString("does_not_exist"))))
          )
        }
      ),
      suite("fromSocketServer")(
        test("read data")(check(Gen.string.filter(_.nonEmpty)) { message =>
          for {
            refOut <- Ref.make("")

            server <- ZStream
                        .fromSocketServer(8896)
                        .foreach { c =>
                          c.read
                            .transduce(ZSink.utf8Decode)
                            .runCollect
                            .map(_.collect { case Some(str) => str }.mkString)
                            .flatMap(s => refOut.update(_ + s))
                        }
                        .fork

            _ <- socketClient(8896)
                   .use(c => ZIO.fromFutureJava(c.write(ByteBuffer.wrap(message.getBytes))))
                   .retry(Schedule.forever)

            receive <- refOut.get.repeatWhileZIO(s => ZIO.succeed(s.isEmpty))

            _ <- server.interrupt
          } yield assert(receive)(equalTo(message))
        }),
        test("write data")(check(Gen.string.filter(_.nonEmpty)) { message =>
          (for {
            refOut <- Ref.make("")

            server <- ZStream
                        .fromSocketServer(8897)
                        .foreach(c => ZStream.fromIterable(message.getBytes).run(c.write))
                        .fork

            _ <- socketClient(8897).use { c =>
                   val buffer = ByteBuffer.allocate(message.getBytes.length)

                   ZIO
                     .fromFutureJava(c.read(buffer))
                     .repeatUntil(_ < 1)
                     .flatMap { _ =>
                       (buffer: Buffer).flip()
                       refOut.update(_ => new String(buffer.array))
                     }
                 }.retry(Schedule.forever)

            receive <- refOut.get.repeatWhileZIO(s => ZIO.succeed(s.isEmpty))

            _ <- server.interrupt
          } yield assert(receive)(equalTo(message)))
        })
      ),
      suite("fromOutputStreamWriter")(
        test("reads what is written") {
          check(Gen.listOf(Gen.chunkOf(Gen.byte)), Gen.int(1, 10)) { (bytess, chunkSize) =>
            val write    = (out: OutputStream) => for (bytes <- bytess) out.write(bytes.toArray)
            val expected = bytess.foldLeft[Chunk[Byte]](Chunk.empty)(_ ++ _)
            ZStream.fromOutputStreamWriter(write, chunkSize).runCollect.map(assert(_)(equalTo(expected)))
          }
        },
        test("captures errors") {
          val write = (_: OutputStream) => throw new Exception("boom")
          ZStream.fromOutputStreamWriter(write).runDrain.exit.map(assert(_)(fails(hasMessage(equalTo("boom")))))
        },
        test("is not affected by closing the output stream") {
          val data  = Array.tabulate[Byte](ZStream.DefaultChunkSize * 5 / 2)(_.toByte)
          val write = (out: OutputStream) => { out.write(data); out.close() }
          ZStream.fromOutputStreamWriter(write).runCollect.map(assert(_)(equalTo(Chunk.fromArray(data))))
        } @@ timeout(10.seconds) @@ flaky,
        test("is interruptable") {
          val latch = new CountDownLatch(1)
          val write = (out: OutputStream) => { latch.await(); out.write(42); }
          ZStream.fromOutputStreamWriter(write).runDrain.fork.flatMap(_.interrupt).map(assert(_)(isInterrupted))
        }
      )
    ),
    suite("from")(
      suite("java.util.stream.Stream")(
        test("JavaStream") {
          trait A
          trait JavaStreamLike[A] extends java.util.stream.Stream[A]
          lazy val javaStream: JavaStreamLike[A]        = ???
          lazy val actual                               = ZStream.from(javaStream)
          lazy val expected: ZStream[Any, Throwable, A] = actual
          lazy val _                                    = expected
          assertCompletes
        },
        test("JavaStreamManaged") {
          trait R
          trait E extends Throwable
          trait A
          trait JavaStreamLike[A] extends java.util.stream.Stream[A]
          lazy val javaStreamManaged: ZManaged[R, E, JavaStreamLike[A]] = ???
          lazy val actual                                               = ZStream.from(javaStreamManaged)
          lazy val expected: ZStream[R, Throwable, A]                   = actual
          lazy val _                                                    = expected
          assertCompletes
        },
        test("JavaStreamZIO") {
          trait R
          trait E extends Throwable
          trait A
          trait JavaStreamLike[A] extends java.util.stream.Stream[A]
          lazy val javaStreamZIO: ZIO[R, E, JavaStreamLike[A]] = ???
          lazy val actual                                      = ZStream.from(javaStreamZIO)
          lazy val expected: ZStream[R, Throwable, A]          = actual
          lazy val _                                           = expected
          assertCompletes
        }
      )
    )
  )
}
