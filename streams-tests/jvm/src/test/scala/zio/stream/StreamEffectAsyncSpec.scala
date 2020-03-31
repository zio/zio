package zio.stream

import scala.concurrent.ExecutionContext.global

import StreamUtils.inParallel

import zio.ZQueueSpecUtil.waitForValue
import zio._
import zio.test.Assertion._
import zio.test._
import zio.{ IO, Promise, Ref, Task, UIO, ZIO }

object StreamEffectAsyncSpec extends ZIOBaseSpec {

  import ZIOTag._

  def spec = suite("StreamEffectAsyncSpec")(
    suite("Stream.effectAsync")(
      testM("effectAsync")(checkM(Gen.listOf(Gen.anyInt)) { list =>
        val s = Stream.effectAsync[Throwable, Int] { k =>
          inParallel {
            list.foreach(a => k(Task.succeed(a)))
          }(global)
        }

        assertM(s.take(list.size.toLong).runCollect)(equalTo(list))
      })
    ),
    suite("Stream.effectAsyncMaybe")(
      testM("effectAsyncMaybe signal end stream") {
        for {
          result <- Stream
                     .effectAsyncMaybe[Nothing, Int] { k =>
                       k(IO.fail(None))
                       None
                     }
                     .runCollect
        } yield assert(result)(equalTo(Nil))
      },
      testM("effectAsyncMaybe Some")(checkM(Gen.listOf(Gen.anyInt)) { list =>
        val s = Stream.effectAsyncMaybe[Throwable, Int](_ => Some(Stream.fromIterable(list)))

        assertM(s.runCollect.map(_.take(list.size)))(equalTo(list))
      }),
      testM("effectAsyncMaybe None")(checkM(Gen.listOf(Gen.anyInt)) { list =>
        val s = Stream.effectAsyncMaybe[Throwable, Int] { k =>
          inParallel {
            list.foreach(a => k(Task.succeed(a)))
          }(global)
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
              inParallel {
                // 1st consumed by sink, 2-6 – in queue, 7th – back pressured
                (1 to 7).foreach(i => cb(refCnt.set(i) *> ZIO.succeed(1)))
                cb(refDone.set(true) *> ZIO.fail(None))
              }(global)
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
    suite("Stream.effectAsyncM")(
      testM("effectAsyncM")(checkM(Gen.listOf(Gen.anyInt).filter(_.nonEmpty)) { list =>
        for {
          latch <- Promise.make[Nothing, Unit]
          fiber <- ZStream
                    .effectAsyncM[Any, Throwable, Int] { k =>
                      inParallel {
                        list.foreach(a => k(Task.succeed(a)))
                      }(global)
                      latch.succeed(()) *>
                        Task.unit
                    }
                    .take(list.size.toLong)
                    .run(Sink.collectAll[Int])
                    .fork
          _ <- latch.await
          s <- fiber.join
        } yield assert(s)(equalTo(list))
      }),
      testM("effectAsyncM signal end stream") {
        for {
          result <- Stream
                     .effectAsyncM[Nothing, Int] { k =>
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
                (1 to 7).foreach(i => cb(refCnt.set(i) *> ZIO.succeed(1)))
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
    suite("Stream.effectAsyncInterrupt")(
      testM("effectAsyncInterrupt Left") {
        for {
          cancelled <- Ref.make(false)
          latch     <- Promise.make[Nothing, Unit]
          fiber <- Stream
                    .effectAsyncInterrupt[Nothing, Unit] { offer =>
                      inParallel {
                        offer(ZIO.succeed(()))
                      }(global)
                      Left(cancelled.set(true))
                    }
                    .tap(_ => latch.succeed(()))
                    .run(Sink.collectAll[Unit])
                    .fork
          _      <- latch.await
          _      <- fiber.interrupt
          result <- cancelled.get
        } yield assert(result)(isTrue)
      },
      testM("effectAsyncInterrupt Right")(checkM(Gen.listOf(Gen.anyInt)) { list =>
        val s = Stream.effectAsyncInterrupt[Throwable, Int](_ => Right(Stream.fromIterable(list)))

        assertM(s.take(list.size.toLong).runCollect)(equalTo(list))
      }),
      testM("effectAsyncInterrupt signal end stream ") {
        for {
          result <- Stream
                     .effectAsyncInterrupt[Nothing, Int] { k =>
                       inParallel {
                         k(IO.fail(None))
                       }(global)
                       Left(UIO.succeed(()))
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
                (1 to 7).foreach(i => cb(refCnt.set(i) *> ZIO.succeed(1)))
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
    ) @@ zioTag(interruption)
  )
}
