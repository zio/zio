package zio

import zio.test.Assertion._
import zio.test._

object CanFailSpec extends ZIOBaseSpec {

  def spec = suite("CanFailSpec")(
    test("useful combinators compile") {
      val result = typeCheck {
        """
            import zio._
            val io =  ZIO.attempt("io")
            val uio = ZIO.succeed("uio")
            io.orElse(uio)
            """
      }
      assertZIO(result)(isRight(anything))
    } @@ TestAspect.scala2Only,
    test("useless combinators don't compile") {
      val result = typeCheck {
        """
            import zio._
            val io =  ZIO.attempt("io")
            val uio = ZIO.succeed("uio")
            uio.orElse(io)
            """
      }
      assertZIO(result)(isLeft(anything))
    }
  )
}
