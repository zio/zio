package examples

import zio.ZIO
import zio.test.Assertion._
import zio.test.{ assertM, suite, testM, DefaultRunnableSpec }

object EffectsExampleSpec
    extends DefaultRunnableSpec(
      suite("Effect examples")(
        suite("Basic effectful operations")(
          testM("Effect succeeds") {
            assertM(ZIO.succeed(10), equalTo(10))
          },
          testM("Effect failures") {
            assertM(ZIO.fail("Failure").run, fails(equalTo("Failure")))
          },
          testM("Effect succeed (through Exit)") {
            assertM(ZIO.succeed("Success").run, succeeds(equalTo("Success")))
          },
          testM("Environment (through effect-elimination)") {
            case class Config(something: String)
            val env = Config("value")
            assertM(ZIO.access[Config](identity).provide(env), equalTo(env))
          },
          testM("Effect failure (through effect-elimination") {
            assertM(ZIO.fail("Failure").either, isLeft(equalTo("Failure")))
          },
          testM("Effect success (through effect-elimination") {
            assertM(ZIO.succeed("Success").either, isRight(equalTo("Success")))
          }
        )
      )
    )
