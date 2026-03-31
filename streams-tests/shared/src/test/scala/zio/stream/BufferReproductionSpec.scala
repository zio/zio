package zio.stream

import zio._
import zio.test._
import zio.test.Assertion._

object BufferReproductionSpec extends ZIOSpecDefault {
  def spec = suite("BufferReproductionSpec")(
    test("buffer(1) should not pull more than 1 element ahead of downstream") {
      for {
        upstreamCount <- Ref.make(0)
        downstreamCount <- Ref.make(0)
        latch <- Promise.make[Nothing, Unit]
        _ <- ZStream.fromIterable(1 to 10)
               .rechunk(1)
               .tap(_ => upstreamCount.update(_ + 1))
               .buffer(1)
               .mapZIO { i =>
                 downstreamCount.update(_ + 1) *> (if (i == 1) latch.await else ZIO.unit)
               }
               .runDrain
               .fork
        _ <- ZIO.yieldNow
        // Give some time for upstream to fill the buffer
        _ <- ZIO.sleep(50.millis)
        u1 <- upstreamCount.get
        d1 <- downstreamCount.get
        // Currently: downstream is busy with element 1.
        // buffer(1) should allow exactly ONE element ahead (element 2).
        // So upstream should have pulled 2 elements total (1 for downstream, 1 for buffer).
        // If it pulled 3, it's over-buffering.
        _ <- latch.succeed(()) // Release first element
        _ <- ZIO.yieldNow
        _ <- ZIO.sleep(50.millis)
        u2 <- upstreamCount.get
        d2 <- downstreamCount.get
      } yield assert(u1)(equalTo(2)) && assert(d1)(equalTo(1))
    }
  )
}
