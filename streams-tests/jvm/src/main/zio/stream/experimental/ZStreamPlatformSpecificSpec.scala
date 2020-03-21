package zio.stream.experimental

import zio._
import zio.test._
import zio.test.Assertion._

object ZStreamPlatformSpecificSpec extends ZIOBaseSpec {
  def spec = suite("ZStream JVM")(
    suite("Constructors")(
      testM("effectAsync")(checkM(Gen.listOf(Gen.anyInt)) { list =>
        val s = ZStream.effectAsync[Any, Throwable, Int] { k =>
          inParallel {
            list.foreach(a => k(Task.succeed(Chunk.single(a))))
          }(global)
        }

        assertM(s.take(list.size).runCollect)(equalTo(list))
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
            inParallel {
              list.foreach(a => k(Task.succeed(Chunk.single(a))))
            }(global)
            None
          }

          assertM(s.take(list.size).runCollect)(equalTo(list))
        }),
        testM("effectAsyncMaybe back pressure") {
          for {
            refCnt  <- Ref.make(0)
            refDone <- Ref.make[Boolean](false)
            stream = ZStream.effectAsyncMaybe[Any, Throwable, Int](
              cb => {
                if (zio.internal.Platform.isJVM) {
                  inParallel {
                    // 1st consumed by sink, 2-6 – in queue, 7th – back pressured
                    (1 to 7).foreach(i => cb(refCnt.set(i) *> ZIO.succeedNow(Chunk.single(1))))
                    cb(refDone.set(true) *> ZIO.fail(None))
                  }(global)
                } else {
                  (1 to 7).foreach(i => cb(refCnt.set(i) *> ZIO.succeedNow(Chunk.single(1))))
                  cb(refDone.set(true) *> ZIO.fail(None))
                }
                None
              },
              5
            )
            run    <- stream.run(ZSink.fromEffect(ZIO.never)).fork
            _      <- waitForValue(refCnt.get, 7)
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
                        inParallel {
                          list.foreach(a => k(Task.succeed(Chunk.single(a))))
                        }(global)
                        latch.succeed(()) *>
                          Task.unit
                      }
                      .take(list.size)
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
                         inParallel {
                           k(IO.fail(None))
                         }(global)
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
                inParallel {
                  // 1st consumed by sink, 2-6 – in queue, 7th – back pressured
                  (1 to 7).foreach(i => cb(refCnt.set(i) *> ZIO.succeedNow(Chunk.single(1))))
                  cb(refDone.set(true) *> ZIO.fail(None))
                }(global)
                UIO.unit
              },
              5
            )
            run    <- stream.run(ZSink.fromEffect(ZIO.never)).fork
            _      <- waitForValue(refCnt.get, 7)
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
                        inParallel {
                          offer(ZIO.succeedNow(Chunk.unit))
                        }(global)
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

          assertM(s.take(list.size).runCollect)(equalTo(list))
        }),
        testM("effectAsyncInterrupt signal end stream ") {
          for {
            result <- ZStream
                       .effectAsyncInterrupt[Any, Nothing, Int] { k =>
                         inParallel {
                           k(IO.fail(None))
                         }(global)
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
                inParallel {
                  // 1st consumed by sink, 2-6 – in queue, 7th – back pressured
                  (1 to 7).foreach(i => cb(refCnt.set(i) *> ZIO.succeedNow(Chunk.single(1))))
                  cb(refDone.set(true) *> ZIO.fail(None))
                }(global)
                Left(UIO.unit)
              },
              5
            )
            run    <- stream.run(ZSink.fromEffect(ZIO.never)).fork
            _      <- waitForValue(refCnt.get, 7)
            isDone <- refDone.get
            exit   <- run.interrupt
          } yield assert(isDone)(isFalse) &&
            assert(exit.untraced)(failsCause(containsCause(Cause.interrupt(selfId))))
        }
      ),

    )
  )
}
