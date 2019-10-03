package zio.stream

import org.specs2.ScalaCheck

import scala.{ Stream => _ }
import zio._

class StreamBufferSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime with GenIO with ScalaCheck {

  import Exit.{ Cause => _, _ }
  import zio.Cause

  def is = "StreamBufferSpec".title ^ s2"""
  Stream.buffer
    buffer the Stream                    $bufferStream
    buffer the Stream with Error         $bufferStreamError
    fast producer progress independently $bufferFastProducerSlowConsumer

  Stream.bufferDropping
    buffer the Stream with Error         $bufferDroppingError
    fast producer progress independently $bufferDroppingFastProducerSlowConsumer

  Stream.bufferSliding
    buffer the Stream with Error         $bufferSlidingError
    fast producer progress independently $bufferSlidingFastProducerSlowConsumer

  Stream.bufferUnbounded
    buffer the Stream                    $bufferUnboundedStream
    buffer the Stream with Error         $bufferUnboundedStreamError
    fast producer progress independently $bufferUnboundedFastProducerSlowConsumer
  """

  private def bufferStream = prop { list: List[Int] =>
    unsafeRunSync(
      Stream
        .fromIterable(list)
        .buffer(2)
        .run(Sink.collectAll[Int])
    ) must_== (Success(list))
  }

  private def bufferStreamError = {
    val e = new RuntimeException("boom")
    unsafeRunSync(
      (Stream.range(0, 10) ++ Stream.fail(e))
        .buffer(2)
        .run(Sink.collectAll[Int])
    ) must_== Failure(Cause.Fail(e))
  }

  private def bufferFastProducerSlowConsumer =
    unsafeRun(
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
      } yield l.reverse must_=== (1 to 4).toList
    )

  private def bufferDroppingError = {
    val e = new RuntimeException("boom")
    unsafeRunSync(
      (Stream.range(1, 1000) ++ Stream.fail(e) ++ Stream.range(1001, 2000))
        .bufferDropping(2)
        .runCollect
    ) must_== Failure(Cause.Fail(e))
  }

  private def bufferDroppingFastProducerSlowConsumer =
    unsafeRun(
      for {
        ref    <- Ref.make(List.empty[Int])
        latch1 <- Promise.make[Nothing, Unit]
        latch2 <- Promise.make[Nothing, Unit]
        latch3 <- Promise.make[Nothing, Unit]
        latch4 <- Promise.make[Nothing, Unit]
        s1     = Stream(0) ++ Stream.fromEffect(latch1.await).flatMap(_ => Stream.range(1, 16).ensuring(latch2.succeed(())))
        s2 = Stream
          .fromEffect(latch3.await)
          .flatMap(_ => Stream.range(17, 24).ensuring(latch4.succeed(())))
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
      } yield (snapshots._1 must_== 0) and (snapshots._2 must_== List(8, 7, 6, 5, 4, 3, 2, 1)) and
        (snapshots._3 must_== List(24, 23, 22, 21, 20, 19, 18, 17, 8, 7, 6, 5, 4, 3, 2, 1))
    )

  private def bufferSlidingError = {
    val e = new RuntimeException("boom")
    unsafeRunSync(
      (Stream.range(1, 1000) ++ Stream.fail(e) ++ Stream.range(1001, 2000))
        .bufferSliding(2)
        .runCollect
    ) must_== Failure(Cause.Fail(e))
  }

  private def bufferSlidingFastProducerSlowConsumer =
    unsafeRun(
      for {
        ref    <- Ref.make(List.empty[Int])
        latch1 <- Promise.make[Nothing, Unit]
        latch2 <- Promise.make[Nothing, Unit]
        latch3 <- Promise.make[Nothing, Unit]
        latch4 <- Promise.make[Nothing, Unit]
        s1     = Stream(0) ++ Stream.fromEffect(latch1.await).flatMap(_ => Stream.range(1, 16).ensuring(latch2.succeed(())))
        s2 = Stream
          .fromEffect(latch3.await)
          .flatMap(_ => Stream.range(17, 24).ensuring(latch4.succeed(())))
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
      } yield (snapshots._1 must_== 0) and (snapshots._2 must_== List(16, 15, 14, 13, 12, 11, 10, 9)) and
        (snapshots._3 must_== List(24, 23, 22, 21, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9))
    )

  private def bufferUnboundedStream = prop { list: List[Int] =>
    unsafeRunSync(
      Stream
        .fromIterable(list)
        .bufferUnbounded
        .runCollect
    ) must_== Success(list)
  }

  private def bufferUnboundedStreamError = {
    val e = new RuntimeException("boom")
    unsafeRunSync(
      (Stream.range(0, 10) ++ Stream.fail(e)).bufferUnbounded.runCollect
    ) must_== Failure(Cause.Fail(e))
  }

  private def bufferUnboundedFastProducerSlowConsumer =
    unsafeRun(
      for {
        ref   <- Ref.make(List[Int]())
        latch <- Promise.make[Nothing, Unit]
        s = Stream
          .fromEffect(UIO.succeed(()))
          .flatMap(_ => Stream.range(1, 1000).tap(i => ref.update(i :: _)).ensuring(latch.succeed(())))
          .bufferUnbounded
        l <- s.process.use { as =>
              for {
                _ <- as
                _ <- latch.await
                l <- ref.get
              } yield l
            }
      } yield l.reverse must_== (1 to 1000).toList
    )
}
