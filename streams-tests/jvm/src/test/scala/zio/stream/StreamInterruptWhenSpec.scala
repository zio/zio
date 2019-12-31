package zio.stream

import zio._
import zio.test._
import zio.test.Assertion.{ equalTo, isLeft, isTrue }

object StreamInterruptWhenSpec extends ZIOBaseSpec {
  def spec = suite("ZStream.interruptWhen")(
    testM("interrupts the current element") {
      for {
        interrupted <- Ref.make(false)
        latch       <- Promise.make[Nothing, Unit]
        halt        <- Promise.make[Nothing, Unit]
        fiber <- ZStream
                  .fromEffect(latch.await.onInterrupt(interrupted.set(true)))
                  .interruptWhen(halt)
                  .runDrain
                  .fork
        _      <- halt.succeed(())
        _      <- fiber.await
        result <- interrupted.get
      } yield assert(result)(isTrue)
    },
    testM("propagates errors") {
      for {
        halt <- Promise.make[String, Nothing]
        _    <- halt.fail("Fail")
        result <- ZStream(1)
                   .haltWhen(halt)
                   .runDrain
                   .either
      } yield assert(result)(isLeft(equalTo("Fail")))
    }
  )
}
