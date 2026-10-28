package zio.stream

import zio._
import zio.test._
import zio.test.TestAspect.nonFlaky

object ZStreamTapSinkSpec extends ZIOBaseSpec {
  def spec =
    suite("ZStreamTapSinkSpec")(
      test("does not read ahead") {
        for {
          ref    <- Ref.make(0)
          stream  = ZStream(1, 2, 3, 4, 5).rechunk(1).forever
          sink    = ZSink.foreach((n: Int) => ZIO.yieldNow *> ref.update(_ + n))
          _      <- stream.tapSink(sink).take(3).runDrain
          result <- ref.get
        } yield assertTrue(result == 6)
      } @@ nonFlaky,
      test("early termination with a slow sink") {
        for {
          ref    <- Ref.make(0)
          stream  = ZStream(1, 2, 3, 4, 5).rechunk(1).forever
          sink    = ZSink.foreach((n: Int) => ZIO.sleep(2.seconds) *> ref.update(_ + n))
          fib    <- stream.tapSink(sink).take(1).mapZIO(a => ZIO.sleep(1.second).as(a)).runCollect.fork
          _      <- TestClock.adjust(3.seconds)
          res    <- fib.join
          result <- ref.get
        } yield assertTrue(res == Chunk(1) && result == 1)
      } @@ nonFlaky,
      test("downstream take completes summary sink with emitted elements") {
        for {
          ref    <- Ref.make(Chunk.empty[Int])
          stream  = ZStream.fromChunks(Chunk(1, 2), Chunk(3), Chunk(4, 5))
          sink    = ZSink.collectAll[Int].mapZIO(ref.set)
          out    <- stream.tapSink(sink).take(3).runCollect
          tapped <- ref.get
        } yield assertTrue(out == Chunk(1, 2, 3) && tapped == Chunk(1, 2, 3))
      } @@ nonFlaky,
      test("propagates sink failure on end of stream") {
        for {
          err <- ZStream(1, 2, 3)
                   .rechunk(1)
                   .tapSink(ZSink.collectAll[Int].mapZIO(_ => ZIO.fail("sink-end")))
                   .runCollect
                   .either
          early <- ZStream(1, 2, 3, 4, 5)
                     .rechunk(1)
                     .tapSink(ZSink.collectAll[Int].mapZIO(_ => ZIO.fail("sink-end")))
                     .take(3)
                     .runCollect
                     .either
        } yield assertTrue(err == Left("sink-end") && early == Right(Chunk(1, 2, 3)))
      } @@ nonFlaky,
      test("error doesn't truncate elements before failure") {
        for {
          res <- ZStream(1, 2, 3)
                   .rechunk(1)
                   .tapSink(ZSink.foreach((x: Int) => ZIO.when(x == 3)(ZIO.fail("error"))))
                   .either
                   .runCollect
        } yield assertTrue(res == Chunk(Right(1), Right(2), Left("error")))
      } @@ nonFlaky,
      test("sink that completes or fails before reading any input") {
        for {
          out1 <- ZStream.fromChunks(Chunk(1, 2), Chunk(3, 4)).tapSink(ZSink.succeed(0)).runCollect
          err1 <- ZStream.never.tapSink(ZSink.fail("boom")).runCollect.flip
          err2 <- ZStream(1, 2, 3).tapSink(ZSink.fail("boom")).runCollect.flip
        } yield assertTrue(out1 == Chunk(1, 2, 3, 4) && err1 == "boom" && err2 == "boom")
      } @@ nonFlaky,
      test("upstream failure and fiber interruption run sink finalizer cleanly") {
        for {
          tapped    <- Ref.make(Chunk.empty[Int])
          finalized <- Promise.make[Nothing, Boolean]
          started   <- Promise.make[Nothing, Unit]
          blocked   <- Promise.make[Nothing, Unit]
          upErr <- (ZStream(1, 2) ++ ZStream.fail("up-err"))
                     .tapSink(ZSink.foreach((x: Int) => tapped.update(_ :+ x)))
                     .runCollect
                     .flip
          seen <- tapped.get
          sink = ZSink.unwrapScoped(
                   ZIO
                     .acquireRelease(ZIO.unit)(_ => finalized.succeed(true))
                     .as(
                       ZSink.foreach((_: Int) => started.succeed(()) *> blocked.await)
                     )
                 )
          fib <- ZStream(1, 2, 3).rechunk(1).tapSink(sink).runDrain.fork
          _   <- started.await
          _   <- fib.interrupt
          fin <- finalized.await
        } yield assertTrue(upErr == "up-err" && seen == Chunk(1, 2) && fin)
      } @@ nonFlaky
    )
}
