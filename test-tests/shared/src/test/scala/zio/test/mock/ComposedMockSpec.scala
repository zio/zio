package zio.test.mock

import zio.duration._
import zio.random.Random
import zio.system.System
import zio.test.{Assertion, ZIOBaseSpec, ZSpec, assertM}
import zio.{Clock, Has, Tag, ULayer, ZIO, Console, random, system}

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
        val cmd2     = MockConsole.PutStrLn(equalTo("42"))
        val composed = (cmd1 ++ cmd2)

        val program =
          for {
            time <- Clock.nanoTime
            _    <- Console.putStrLn(time.toString)
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
        val cmd4 = MockConsole.PutStrLn(equalTo("None"))

        val composed = (cmd1 ++ cmd2 ++ cmd3 ++ cmd4)

        val program =
          for {
            n <- random.nextInt
            _ <- Clock.sleep(n.seconds)
            v <- system.property("foo")
            _ <- Console.putStrLn(v.toString)
          } yield ()

        testValueComposed[Has[Random] with Has[Clock] with Has[System] with Has[Console], Throwable, Unit](
          "Has[Random] with Clock with Has[System] with Has[Console]"
        )(composed, program, isUnit)
      }
    )
  )
}
