package zio.test.mock

import zio.console.Console
import zio.test.mock.internal.MockException
import zio.test.{Assertion, ZIOBaseSpec, ZSpec}
import zio.{Has, ZIO, console}

object EmptyMockSpec extends ZIOBaseSpec with MockSpecUtils[Has[Console]] {

  import Assertion._
  import MockException._

  def spec: ZSpec[Environment, Failure] = suite("EmptyMockSpec")(
    suite("expect no calls on empty mocks")(
      testValue("should succeed when no call")(
        MockConsole.empty,
        ZIO.when(false)(console.putStrLn("foo")),
        isUnit
      ), {

        type M = Capability[Has[Console], String, Nothing, Unit]
        type X = UnexpectedCallException[Has[Console], String, Nothing, Unit]

        testDied("should fail when call happened")(
          MockConsole.empty,
          ZIO.when(true)(console.putStrLn("foo")),
          isSubtype[X](
            hasField[X, M]("capability", _.capability, equalTo(MockConsole.PutStrLn)) &&
              hasField[X, Any]("args", _.args, equalTo("foo"))
          )
        )
      }
    )
  )
}
