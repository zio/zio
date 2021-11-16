package zio

import zio.test.Assertion._
import zio.test._

object CanFailSpec extends ZIOBaseNewSpec {

  def spec = suite("CanFailSpec")(
    test("useful combinators compile") {
      val result = typeCheck {
        """
            import zio._
            val io =  IO(1 / 0)
            val uio = UIO(0)
            io.orElse(uio)
            """
      }
      assertM(result)(isRight(anything))
    },
    test("useless combinators don't compile") {
      val result = typeCheck {
        """
            import zio._
            val io =  IO(1 / 0)
            val uio = UIO(0)
            uio.orElse(io)
            """
      }
      assertM(result)(isLeft(anything))
    }
  )
}
