package zio

import zio.test._
import zio.test.Assertion._
import zio.Clock

object ZIOAppSpec extends ZIOSpecDefault {
  def spec = suite("ZIOAppSpec")(
    test("a successful ZIO effect completes with a value") {
      val successEffect = ZIO.succeed(42)
      assertZIO(successEffect)(equalTo(42))
    },
    test("a failed ZIO effect returns a typed error") {
      val failedEffect = ZIO.fail("Uh oh!")
      assertZIO(failedEffect.exit)(Assertion.fails(equalTo("Uh oh!")))
    },
    test("a dying ZIO effect returns a defect") {
      val dyingEffect = ZIO.die(new Exception("Boom!"))
      assertZIO(dyingEffect.exit)(Assertion.dies(hasMessage(equalTo("Boom!"))))
    },
    test("a ZIO effect using default services executes successfully") {
      val effectWithClock = for {
        _ <- ZIO.logInfo("Hello from a ZIO effect")
        _ <- Clock.sleep(1.millisecond)
      } yield 42

      for {
        fiber  <- effectWithClock.fork
        _      <- TestClock.adjust(1.millisecond)
        result <- fiber.await
      } yield assert(result)(Assertion.succeeds(equalTo(42)))
    }
  )
}
