package zio

import zio.test._
import zio.test.Assertion._

object CanFailSpec
    extends ZIOBaseSpec(
      suite("CanFailSpec")(
        testM("useful combinators compile") {
          val result = compile {
            """
            import zio._

            val io =  IO(1 / 0)
            val uio = UIO(0)

            io.orElse(uio)
            """
          }
          assertM(result, isRight(anything))
        },
        testM("useless combinators don't compile") {
          val result = compile {
            """
            import zio._

            val io =  IO(1 / 0)
            val uio = UIO(0)

            uio.orElse(io)
            """
          }
          val expected = "This operation only makes sense for effects that can fail."
          if (TestVersion.isScala2) assertM(result, isLeft(equalTo(expected)))
          else assertM(result, isLeft(anything))
        }
      )
    )
