package zio

import zio.test.{ test => test0, _ }

object CanSucceedSpec
    extends ZIOBaseSpec(
      suite("CanSucceedSpec")(
        test0("useful combinators compile") {
          assertCompiles {
            """
            import zio._

            val io =  ZIO.succeed(1)

            io.map(_ + 1)
            """
          }
        },
        test0("useless combinators don't compile") {
          !assertCompiles {
            """
            import zio._

            val io =  ZIO.never

            io.map(_ + 1)
            """
          }
        }
      )
    )
