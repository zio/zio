package zio.stream

import java.nio.file.{ Files, NoSuchFileException, Paths }

import scala.concurrent.ExecutionContext.global

import zio._
import zio.test.Assertion._
import zio.test._

object ZStreamPlatformSpecificSpec extends ZIOBaseSpec {
  def spec = suite("ZStream JVM")(
    suite("Constructors")(
      testM("effectAsync")(checkM(Gen.listOf(Gen.anyInt)) { list =>
        val s = ZStream.effectAsync[Any, Throwable, Int] { k =>
          global.execute(() => list.foreach(a => k(Task.succeed(Chunk.single(a)))))
        }

        assertM(s.take(list.size.toLong).runCollect)(equalTo(list))
      }),
      suite("effectAsyncMaybe")(
        testM("effectAsyncMaybe signal end stream") {
          for {
            result <- ZStream
                       .effectAsyncMaybe[Any, Nothing, Int] { k =>
                         k(IO.fail(None))
                         None
                       }
                       .runCollect
          } yield assert(result)(equalTo(Nil))
        },
        testM("effectAsyncMaybe Some")(checkM(Gen.listOf(Gen.anyInt)) { list =>
          val s = ZStream.effectAsyncMaybe[Any, Throwable, Int](_ => Some(ZStream.fromIterable(list)))

          assertM(s.runCollect.map(_.take(list.size)))(equalTo(list))
        }),
        testM("effectAsyncMaybe None")(checkM(Gen.listOf(Gen.anyInt)) { list =>
          val s = ZStream.effectAsyncMaybe[Any, Throwable, Int] { k =>
            global.execute(() => list.foreach(a => k(Task.succeed(Chunk.single(a)))))
            None
          }

          assertM(s.take(list.size.toLong).runCollect)(equalTo(list))
        }),
        testM("effectAsyncMaybe back pressure") {
          for {
            refCnt  <- Ref.make(0)
            refDone <- Ref.make[Boolean](false)
            stream = ZStream.effectAsyncMaybe[Any, Throwable, Int](
              cb => {
                if (zio.internal.Platform.isJVM) {
                  global.execute { () =>
                    // 1st consumed by sink, 2-6 – in queue, 7th – back pressured
                    (1 to 7).foreach(i => cb(refCnt.set(i) *> ZIO.succeedNow(Chunk.single(1))))
                    cb(refDone.set(true) *> ZIO.fail(None))
                  }
                } else {
                  (1 to 7).foreach(i => cb(refCnt.set(i) *> ZIO.succeedNow(Chunk.single(1))))
                  cb(refDone.set(true) *> ZIO.fail(None))
                }
                None
              },
              5
            )
            run    <- stream.run(ZSink.fromEffect(ZIO.never)).fork
            _      <- refCnt.get.repeat(Schedule.doWhile(_ != 7))
            isDone <- refDone.get
            _      <- run.interrupt
          } yield assert(isDone)(isFalse)
        }
      ),
      suite("effectAsyncM")(
        testM("effectAsyncM")(checkM(Gen.listOf(Gen.anyInt).filter(_.nonEmpty)) { list =>
          for {
            latch <- Promise.make[Nothing, Unit]
            fiber <- ZStream
                      .effectAsyncM[Any, Throwable, Int] { k =>
                        global.execute(() => list.foreach(a => k(Task.succeed(Chunk.single(a)))))
                        latch.succeed(()) *>
                          Task.unit
                      }
                      .take(list.size.toLong)
                      .run(ZSink.collectAll[Int])
                      .fork
            _ <- latch.await
            s <- fiber.join
          } yield assert(s)(equalTo(list))
        }),
        testM("effectAsyncM signal end stream") {
          for {
            result <- ZStream
                       .effectAsyncM[Any, Nothing, Int] { k =>
                         global.execute(() => k(IO.fail(None)))
                         UIO.unit
                       }
                       .runCollect
          } yield assert(result)(equalTo(Nil))
        },
        testM("effectAsyncM back pressure") {
          for {
            refCnt  <- Ref.make(0)
            refDone <- Ref.make[Boolean](false)
            stream = ZStream.effectAsyncM[Any, Throwable, Int](
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
            run    <- stream.run(ZSink.fromEffect(ZIO.never)).fork
            _      <- refCnt.get.repeat(Schedule.doWhile(_ != 7))
            isDone <- refDone.get
            _      <- run.interrupt
          } yield assert(isDone)(isFalse)
        }
      ),
      suite("effectAsyncInterrupt")(
        testM("effectAsyncInterrupt Left") {
          for {
            cancelled <- Ref.make(false)
            latch     <- Promise.make[Nothing, Unit]
            fiber <- ZStream
                      .effectAsyncInterrupt[Any, Nothing, Unit] { offer =>
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
        testM("effectAsyncInterrupt Right")(checkM(Gen.listOf(Gen.anyInt)) { list =>
          val s = ZStream.effectAsyncInterrupt[Any, Throwable, Int](_ => Right(ZStream.fromIterable(list)))

          assertM(s.take(list.size.toLong).runCollect)(equalTo(list))
        }),
        testM("effectAsyncInterrupt signal end stream ") {
          for {
            result <- ZStream
                       .effectAsyncInterrupt[Any, Nothing, Int] { k =>
                         global.execute(() => k(IO.fail(None)))
                         Left(UIO.succeedNow(()))
                       }
                       .runCollect
          } yield assert(result)(equalTo(Nil))
        },
        testM("effectAsyncInterrupt back pressure") {
          for {
            selfId  <- ZIO.fiberId
            refCnt  <- Ref.make(0)
            refDone <- Ref.make[Boolean](false)
            stream = ZStream.effectAsyncInterrupt[Any, Throwable, Int](
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
            run    <- stream.run(ZSink.fromEffect(ZIO.never)).fork
            _      <- refCnt.get.repeat(Schedule.doWhile(_ != 7))
            isDone <- refDone.get
            exit   <- run.interrupt
          } yield assert(isDone)(isFalse) &&
            assert(exit.untraced)(failsCause(containsCause(Cause.interrupt(selfId))))
        }
      ),
      suite("fromFile")(
        testM("reads from an existing file") {
          val data = (0 to 100).mkString

          Task(Files.createTempFile("stream", "fromFile")).bracket(path => Task(Files.delete(path)).orDie) { path =>
            Task(Files.write(path, data.getBytes("UTF-8"))) *>
              assertM(ZStream.fromFile(path, 24).transduce(ZTransducer.utf8Decode).runCollect.map(_.mkString))(
                equalTo(data)
              )
          }
        },
        testM("fails on a nonexistent file") {
          assertM(ZStream.fromFile(Paths.get("nonexistent"), 24).runDrain.run)(
            fails(isSubtype[NoSuchFileException](anything))
          )
        }
      )
    )
  )
}
