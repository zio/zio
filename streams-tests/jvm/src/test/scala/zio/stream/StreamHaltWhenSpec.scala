package zio.stream

import zio._
import zio.test.Assertion.{ equalTo, isFalse, isLeft }
import zio.test._

object StreamHaltWhenSpec extends ZIOBaseSpec {

  import ZIOTag._

  def spec = suite("ZStream.haltWhen")(
    suite("ZStream.haltWhen(Promise)")(
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
        } yield assert(result)(isFalse)
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
    ),
    suite("ZStream.haltWhen(IO)")(
      testM("halts after the current element") {
        for {
          interrupted <- Ref.make(false)
          latch       <- Promise.make[Nothing, Unit]
          halt        <- Promise.make[Nothing, Unit]
          _ <- ZStream
            .fromEffect(latch.await.onInterrupt(interrupted.set(true)))
            .haltWhen(halt.await)
            .runDrain
            .fork
          _      <- halt.succeed(())
          _      <- latch.succeed(())
          result <- interrupted.get
        } yield assert(result)(isFalse)
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
    )
  )
}
