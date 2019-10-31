package zio

import zio.test.{ test => test0, _ }

object NeedsEnvSpec
    extends ZIOBaseSpec(
      suite("NeedsEnvSpec")(
        test0("useful combinators compile") {
          assertCompiles {
            """
            import zio._
            import zio.console._

            val sayHello = console.putStrLn("Hello, World!")

            sayHello.provide(Console.Live)
            """
          }
        },
        test0("useless combinators don't compile") {
          !assertCompiles {
            """
            import zio._
            import zio.console._

            val uio = UIO.succeed("Hello, World!")

            uio.provide(Clock.Live)
            """
          }
        }
      )
    )
