package zio

import zio.test.Assertion._
import zio.test._

object NeedsEnvSpec extends ZIOBaseNewSpec {

  def spec = suite("NeedsEnvSpec")(
    test("useful combinators compile") {
      val result = typeCheck {
        """
            import zio._
            val sayHello = Console.printLine("Hello, World!")
            sayHello.provideServices(Console.live)
            """
      }
      assertM(result)(isRight(isUnit))
    },
    test("useless combinators don't compile") {
      val result = typeCheck {
        """
            import zio._
            val uio = UIO.succeed("Hello, World!")
            uio.provideServices(Console.Service.live)
            """
      }
      assertM(result)(isLeft(anything))
    }
  )
}
