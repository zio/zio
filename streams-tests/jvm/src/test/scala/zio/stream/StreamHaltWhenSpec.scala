package zio.stream

import zio._
import zio.test._
import zio.test.Assertion.{ equalTo, isFalse, isLeft }

object StreamHaltWhenSpec extends ZIOBaseSpec {
  def spec = suite("ZStream.haltWhen")(
    testM("halts after the current element") {
      for {
        interrupted <- Ref.make(false)
        latch       <- Promise.make[Nothing, Unit]
        halt        <- Promise.make[Nothing, Unit]
        _ <- ZStream
              .fromEffect(latch.await.onInterrupt(interrupted.set(true)))
              .haltWhen(halt)
              .runDrain
              .fork
        _      <- halt.succeed(())
        _      <- latch.succeed(())
        result <- interrupted.get
      } yield assert(result, isFalse)
    },
    testM("propagates errors") {
      for {
        halt <- Promise.make[String, Nothing]
        _    <- halt.fail("Fail")
        result <- ZStream(1)
                   .haltWhen(halt)
                   .runDrain
                   .either
      } yield assert(result, isLeft(equalTo("Fail")))
    }
  )
}
