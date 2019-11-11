package zio

import zio.test._
import zio.test.Assertion._

object NeedsEnvSpec
    extends ZIOBaseSpec(
      suite("NeedsEnvSpec")(
        testM("useful combinators compile") {
          val result = compile {
            """
            import zio._
            import zio.console._

            val sayHello = console.putStrLn("Hello, World!")

            sayHello.provide(Console.Live)
            """
          }
          assertM(result, isRight(isUnit))
        },
        testM("useless combinators don't compile") {
          val result = compile {
            """
            import zio._
            import zio.console._

            val uio = UIO.succeed("Hello, World!")

            uio.provide(Console.Live)
            """
          }
          val expected = "This operation only makes sense for effects that need an environment."
          if (TestVersion.isScala2) assertM(result, isLeft(equalTo(expected)))
          else assertM(result, isLeft(anything))
        }
      )
    )
