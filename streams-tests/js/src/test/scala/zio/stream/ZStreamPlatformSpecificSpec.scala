package zio.stream

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import zio._
import zio.test.Assertion._
import zio.test._

object ZStreamPlatformSpecificSpec extends ZIOBaseSpec {
  def spec = suite("ZStream JS")(
    testM("effectAsync")(checkM(Gen.chunkOf(Gen.anyInt)) { chunk =>
      val s = ZStream.effectAsync[Any, Throwable, Int](k => chunk.foreach(a => k(Task.succeed(Chunk.single(a)))))

      assertM(s.take(chunk.size.toLong).runCollect)(equalTo(chunk))
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
        } yield assert(result)(equalTo(Chunk.empty))
      },
      testM("effectAsyncMaybe Some")(checkM(Gen.chunkOf(Gen.anyInt)) { chunk =>
        val s = ZStream.effectAsyncMaybe[Any, Throwable, Int](_ => Some(ZStream.fromIterable(chunk)))

        assertM(s.runCollect.map(_.take(chunk.size)))(equalTo(chunk))
      }),
      testM("effectAsyncMaybe None")(checkM(Gen.chunkOf(Gen.anyInt)) { chunk =>
        val s = ZStream.effectAsyncMaybe[Any, Throwable, Int] { k =>
          chunk.foreach(a => k(Task.succeed(Chunk.single(a))))
          None
        }

        assertM(s.take(chunk.size.toLong).runCollect)(equalTo(chunk))
      }),
      testM("effectAsyncMaybe back pressure") {
        for {
          refCnt  <- Ref.make(0)
          refDone <- Ref.make[Boolean](false)
          stream = ZStream.effectAsyncMaybe[Any, Throwable, Int](
            cb => {
              Future
                .sequence(
                  (1 to 7).map(i => cb(refCnt.set(i) *> ZIO.succeedNow(Chunk.single(1))))
                )
                .flatMap(_ => cb(refDone.set(true) *> ZIO.fail(None)))
              None
            },
            5
          )
          run    <- stream.run(ZSink.fromEffect[Any, Nothing, Int, Nothing](ZIO.never)).fork
          _      <- refCnt.get.repeat(Schedule.doWhile(_ != 7))
          isDone <- refDone.get
          _      <- run.interrupt
        } yield assert(isDone)(isFalse)
      }
    )
  )
}
