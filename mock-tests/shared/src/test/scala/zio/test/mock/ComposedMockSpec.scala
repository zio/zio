package zio.mock

import zio._
import zio.test.{Assertion, assertM}

import java.io.IOException

object ComposedMockSpec extends ZIOBaseSpec {

  import Assertion._
  import Expectation._

  private def testValueComposed[R1: Tag, E, A](name: String)(
    mock: ULayer[R1],
    app: ZIO[R1, E, A],
    check: Assertion[A]
  ) = test(name) {
    val result = mock.build.use[R1, E, A](app.provideEnvironment(_))
    assertM(result)(check)
  }

  def spec = suite("ComposedMockSpec")(
    suite("mocking composed environments")(
      {
        val cmd1     = MockClock.NanoTime(value(42L))
        val cmd2     = MockConsole.PrintLine(equalTo("42"), unit)
        val composed = cmd1 ++ cmd2

        val program =
          for {
            time <- Clock.nanoTime
            _    <- Console.printLine(time.toString)
          } yield ()

        testValueComposed[Clock with Console, IOException, Unit]("Console with Clock")(
          composed,
          program,
          isUnit
        )
      }, {
        val cmd1 = MockRandom.NextInt(value(42))
        val cmd2 = MockClock.Sleep(equalTo(42.seconds))
        val cmd3 = MockSystem.Property(equalTo("foo"), value(None))
        val cmd4 = MockConsole.PrintLine(equalTo("None"), unit)

        val composed = cmd1 ++ cmd2 ++ cmd3 ++ cmd4

        val program =
          for {
            n <- Random.nextInt
            _ <- Clock.sleep(n.seconds)
            v <- System.property("foo")
            _ <- Console.printLine(v.toString)
          } yield ()

        testValueComposed[Random with Clock with System with Console, Throwable, Unit](
          "Random with Clock with System with Console"
        )(composed, program, isUnit)
      }
    )
  )
}
