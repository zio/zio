package zio.stream

import zio._
import zio.test.Assertion.{ equalTo, isLeft, isTrue }
import zio.test._

object StreamInterruptWhenSpec extends ZIOBaseSpec {

  import ZIOTag._

  def spec =
    suite("ZStream.interruptWhen")(
      suite("ZStream.interruptWhen(Promise)")(
        testM("interrupts the current element") {
          for {
            interrupted <- Ref.make(false)
            latch       <- Promise.make[Nothing, Unit]
            halt        <- Promise.make[Nothing, Unit]
            started     <- Promise.make[Nothing, Unit]
            fiber <- ZStream
                      .fromEffect((started.succeed(()) *> latch.await).onInterrupt(interrupted.set(true)))
                      .interruptWhen(halt)
                      .runDrain
                      .fork
            _      <- started.await *> halt.succeed(())
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
        } @@ zioTag(errors)
      ) @@ zioTag(interruption),
      suite("ZStream.interruptWhen(IO)")(
        testM("interrupts the current element") {
          for {
            interrupted <- Ref.make(false)
            latch       <- Promise.make[Nothing, Unit]
            halt        <- Promise.make[Nothing, Unit]
            started     <- Promise.make[Nothing, Unit]
            fiber <- ZStream
                      .fromEffect((started.succeed(()) *> latch.await).onInterrupt(interrupted.set(true)))
                      .interruptWhen(halt.await)
                      .runDrain
                      .fork
            _      <- started.await *> halt.succeed(())
            _      <- fiber.await
            result <- interrupted.get
          } yield assert(result)(isTrue)
        },
        testM("propagates errors") {
          for {
            halt <- Promise.make[String, Nothing]
            _    <- halt.fail("Fail")
            result <- ZStream(1)
                       .haltWhen(halt.await)
                       .runDrain
                       .either
          } yield assert(result)(isLeft(equalTo("Fail")))
        } @@ zioTag(errors)
      ) @@ zioTag(interruption)
    )
}
