package zio.stream

import scala.{ Stream => _ }

import zio._
import zio.test.Assertion.{ equalTo, fails }
import zio.test._

object StreamBufferSpec extends ZIOBaseSpec {

  import ZIOTag._

  def spec = suite("StreamBufferSpec")(
    suite("Stream.buffer")(
      testM("buffer the Stream")(checkM(Gen.listOf(Gen.anyInt)) { list =>
        assertM(
          Stream
            .fromIterable(list)
            .buffer(2)
            .run(Sink.collectAll[Int])
        )(equalTo(list))
      }),
      testM("buffer the Stream with Error") {
        val e = new RuntimeException("boom")
        assertM(
          (Stream.range(0, 10) ++ Stream.fail(e))
            .buffer(2)
            .run(Sink.collectAll[Int])
            .run
        )(fails(equalTo(e)))
      } @@ zioTag(errors),
      testM("fast producer progress independently") {
        for {
          ref   <- Ref.make(List[Int]())
          latch <- Promise.make[Nothing, Unit]
          s     = Stream.range(1, 5).tap(i => ref.update(i :: _) *> latch.succeed(()).when(i == 4)).buffer(2)
          l <- s.process.use { as =>
                for {
                  _ <- as
                  _ <- latch.await
                  l <- ref.get
                } yield l
              }
        } yield assert(l.reverse)(equalTo((1 to 4).toList))
      }
    ),
    suite("Stream.bufferDropping")(
      testM("buffer the Stream with Error") {
        val e = new RuntimeException("boom")
        assertM(
          (Stream.range(1, 1000) ++ Stream.fail(e) ++ Stream.range(1001, 2000))
            .bufferDropping(2)
            .runCollect
            .run
        )(fails(equalTo(e)))
      } @@ zioTag(errors),
      testM("fast producer progress independently") {
        for {
          ref    <- Ref.make(List.empty[Int])
          latch1 <- Promise.make[Nothing, Unit]
          latch2 <- Promise.make[Nothing, Unit]
          latch3 <- Promise.make[Nothing, Unit]
          latch4 <- Promise.make[Nothing, Unit]
          s1 = Stream(0) ++ Stream
            .fromEffect(latch1.await)
            .flatMap(_ => Stream.range(1, 17).ensuring(latch2.succeed(())))
          s2 = Stream
            .fromEffect(latch3.await)
            .flatMap(_ => Stream.range(17, 25).ensuring(latch4.succeed(())))
          s = (s1 ++ s2).bufferDropping(8)
          snapshots <- s.process.use { as =>
                        for {
                          zero      <- as
                          _         <- latch1.succeed(())
                          _         <- latch2.await
                          _         <- as.flatMap(a => ref.update(a :: _)).repeat(Schedule.recurs(7))
                          snapshot1 <- ref.get
                          _         <- latch3.succeed(())
                          _         <- latch4.await
                          _         <- as.flatMap(a => ref.update(a :: _)).repeat(Schedule.recurs(7))
                          snapshot2 <- ref.get
                        } yield (zero, snapshot1, snapshot2)
                      }
        } yield assert(snapshots._1)(equalTo(0)) && assert(snapshots._2)(equalTo(List(8, 7, 6, 5, 4, 3, 2, 1))) &&
          assert(snapshots._3)(equalTo(List(24, 23, 22, 21, 20, 19, 18, 17, 8, 7, 6, 5, 4, 3, 2, 1)))
      }
    ),
    suite("Stream.bufferSliding")(
      testM("buffer the Stream with Error") {
        val e = new RuntimeException("boom")
        assertM(
          (Stream.range(1, 1000) ++ Stream.fail(e) ++ Stream.range(1001, 2000))
            .bufferSliding(2)
            .runCollect
            .run
        )(fails(equalTo(e)))
      } @@ zioTag(errors),
      testM("fast producer progress independently") {
        for {
          ref    <- Ref.make(List.empty[Int])
          latch1 <- Promise.make[Nothing, Unit]
          latch2 <- Promise.make[Nothing, Unit]
          latch3 <- Promise.make[Nothing, Unit]
          latch4 <- Promise.make[Nothing, Unit]
          s1 = Stream(0) ++ Stream
            .fromEffect(latch1.await)
            .flatMap(_ => Stream.range(1, 17).ensuring(latch2.succeed(())))
          s2 = Stream
            .fromEffect(latch3.await)
            .flatMap(_ => Stream.range(17, 25).ensuring(latch4.succeed(())))
          s = (s1 ++ s2).bufferSliding(8)
          snapshots <- s.process.use { as =>
                        for {
                          zero      <- as
                          _         <- latch1.succeed(())
                          _         <- latch2.await
                          _         <- as.flatMap(a => ref.update(a :: _)).repeat(Schedule.recurs(7))
                          snapshot1 <- ref.get
                          _         <- latch3.succeed(())
                          _         <- latch4.await
                          _         <- as.flatMap(a => ref.update(a :: _)).repeat(Schedule.recurs(7))
                          snapshot2 <- ref.get
                        } yield (zero, snapshot1, snapshot2)
                      }
        } yield assert(snapshots._1)(equalTo(0)) && assert(snapshots._2)(equalTo(List(16, 15, 14, 13, 12, 11, 10, 9))) &&
          assert(snapshots._3)(equalTo(List(24, 23, 22, 21, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9)))
      }
    ),
    suite("Stream.bufferUnbounded")(
      testM("buffer the Stream")(checkM(Gen.listOf(Gen.anyInt)) { list =>
        assertM(
          Stream
            .fromIterable(list)
            .bufferUnbounded
            .runCollect
        )(equalTo(list))
      }),
      testM("buffer the Stream with Error") {
        val e = new RuntimeException("boom")
        assertM((Stream.range(0, 10) ++ Stream.fail(e)).bufferUnbounded.runCollect.run)(fails(equalTo(e)))
      } @@ zioTag(errors),
      testM("fast producer progress independently") {
        for {
          ref   <- Ref.make(List[Int]())
          latch <- Promise.make[Nothing, Unit]
          s = Stream
            .fromEffect(UIO.succeedNow(()))
            .flatMap(_ => Stream.range(1, 1000).tap(i => ref.update(i :: _)).ensuring(latch.succeed(())))
            .bufferUnbounded
          l <- s.process.use { as =>
                for {
                  _ <- as
                  _ <- latch.await
                  l <- ref.get
                } yield l
              }
        } yield assert(l.reverse)(equalTo(Range(1, 1000).toList))
      }
    )
  )
}
