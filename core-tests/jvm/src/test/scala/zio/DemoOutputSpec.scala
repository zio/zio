package zio
import zio.Console.printLine
import zio._
import zio.test._

object DemoOutputSpec extends ZIOSpecDefault {
  def spec =
    suite("suite")(
      test("A")(
        for {
          _   <- printLine("A1")
          _ <- ZIO.withClock(Clock.ClockLive)(ZIO.sleep(1.second))
          _   <- printLine("A2")
        } yield assertNever("Test Failed!")
      ),
      test("B")(
        for {
          _   <- printLine("B1")
          _ <- ZIO.withClock(Clock.ClockLive)(ZIO.sleep(1.second))
          _   <- printLine("B2")
        } yield assertNever("Test Failed!")
      )
    )

}
