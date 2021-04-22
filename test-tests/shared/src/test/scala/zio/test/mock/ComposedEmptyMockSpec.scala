package zio.test.mock

import zio.test.mock.internal.MockException
import zio.test.{Assertion, ZIOBaseSpec, ZSpec}
import zio.{Clock, Console, Has, ZIO}

import java.io.IOException

object ComposedEmptyMockSpec extends ZIOBaseSpec with MockSpecUtils[ComposedEmptyMockSpecCompat.Environment] {

  import Assertion._
  import Expectation._
  import MockException._

  def branchingProgram(predicate: Boolean): ZIO[Has[Console] with Has[Clock], IOException, Unit] =
    ZIO
      .succeed(predicate)
      .flatMap {
        case true  => Console.readLine
        case false => Clock.nanoTime
      }
      .unit

  def spec: ZSpec[Environment, Failure] = suite("ComposedEmptyMockSpec")(
    suite("expect no calls on empty mocks")(
      testValue("should succeed when no calls on Console")(
        MockConsole.empty ++ MockClock.NanoTime(value(42L)),
        branchingProgram(false),
        isUnit
      ), {
        type M = Capability[Has[Console], Unit, IOException, String]
        type X = UnexpectedCallException[Has[Console], Unit, IOException, String]

        testDied("should fail when call on Console happened")(
          MockConsole.empty ++ MockClock.NanoTime(value(42L)),
          branchingProgram(true),
          isSubtype[X](
            hasField[X, M]("capability", _.capability, equalTo(MockConsole.ReadLine)) &&
              hasField[X, Any]("args", _.args, equalTo(()))
          )
        )
      },
      testValue("should succeed when no calls on Clock")(
        MockClock.empty ++ MockConsole.ReadLine(value("foo")),
        branchingProgram(true),
        isUnit
      ), {

        type M = Capability[Has[Clock], Unit, Nothing, Long]
        type X = UnexpectedCallException[Has[Clock], Unit, Nothing, Long]

        testDied("should fail when call on Clock happened")(
          MockClock.empty ++ MockConsole.ReadLine(value("foo")),
          branchingProgram(false),
          isSubtype[X](
            hasField[X, M]("capability", _.capability, equalTo(MockClock.NanoTime)) &&
              hasField[X, Any]("args", _.args, equalTo(()))
          )
        )
      }
    )
  )
}

object ComposedEmptyMockSpecCompat {
  type Environment = Has[Console] with Has[Clock]
}
