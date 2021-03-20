package zio.test.mock

import zio.duration._
import zio.test.{Assertion, ZIOBaseSpec, ZSpec, assertM}
import zio.{Clock, Console, Has, Random, System, Tag, ULayer, ZIO}

object ComposedMockSpec extends ZIOBaseSpec {

  import Assertion._
  import Expectation._

  private def testValueComposed[R1 <: Has[_]: Tag, E, A](name: String)(
    mock: ULayer[R1],
    app: ZIO[R1, E, A],
    check: Assertion[A]
  ) = testM(name) {
    val result = mock.build.use[R1, E, A](app.provide _)
    assertM(result)(check)
  }

  def spec: ZSpec[Environment, Failure] = suite("ComposedMockSpec")(
    suite("mocking composed environments")(
      {
        val cmd1     = MockClock.NanoTime(value(42L))
        val cmd2     = MockConsole.PrintLine(equalTo("42"))
        val composed = cmd1 ++ cmd2

        val program =
          for {
            time <- Clock.nanoTime
            _    <- Console.printLine(time.toString)
          } yield ()

        testValueComposed[Has[Clock] with Has[Console], Nothing, Unit]("Has[Console] with Clock")(
          composed,
          program,
          isUnit
        )
      }, {
        val cmd1 = MockRandom.NextInt(value(42))
        val cmd2 = MockClock.Sleep(equalTo(42.seconds))
        val cmd3 = MockSystem.Property(equalTo("foo"), value(None))
        val cmd4 = MockConsole.PrintLine(equalTo("None"))

        val composed = cmd1 ++ cmd2 ++ cmd3 ++ cmd4

        val program =
          for {
            n <- Random.nextInt
            _ <- Clock.sleep(n.seconds)
            v <- System.property("foo")
            _ <- Console.printLine(v.toString)
          } yield ()

        testValueComposed[Has[Random] with Has[Clock] with Has[System] with Has[Console], Throwable, Unit](
          "Has[Random] with Clock with Has[System] with Has[Console]"
        )(composed, program, isUnit)
      }
    )
  )
}
