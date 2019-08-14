package examples

import examples.EffectSuites.basicSuite
import zio.ZIO
import zio.test.Predicate._
import zio.test.{ assertM, suite, testM, DefaultRunnableSpec, Predicate }

object EffectsExampleSpec
    extends DefaultRunnableSpec(
      suite("Effect examples")(
        suite("Basic effectful operations")(
          testM("Effect succeeds") {
            assertM(ZIO.succeed(10), Predicate.equals(10))
          },
          testM("Effect failures") {
            assertM(ZIO.fail("Failure").run, fails(Predicate.equals("Failure")))
          },
          testM("Effect succeed(through Exit)") {
            assertM(ZIO.succeed("Success").run, succeeds(Predicate.equals("Success")))
          },
          testM("Environment (through effect-elimination)") {
            case class Config(something: String)
            val env = Config("value")
            assertM(ZIO.access[Config](_.something).provide(env), Predicate.equals(env.something))
          }
        )
      )
    )
