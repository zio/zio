package zio

import zio.test.Assertion._
import zio.test._

object NeedsEnvSpec extends ZIOBaseSpec {

  def spec = suite("NeedsEnvSpec")(
    testM("useful combinators compile") {
      val result = typeCheck {
        """
            import zio._
            import zio.console._
            val sayHello = console.putStrLn("Hello, World!")
            sayHello.provide(Console.Live)
            """
      }
      assertM(result)(isRight(isUnit))
    },
    testM("useless combinators don't compile") {
      val result = typeCheck {
        """
            import zio._
            import zio.console._
            val uio = UIO.succeed("Hello, World!")
            uio.provide(Console.Live)
            """
      }
      assertM(result)(isLeft(anything))
    }
  )
}
