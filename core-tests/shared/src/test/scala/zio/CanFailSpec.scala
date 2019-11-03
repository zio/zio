package zio

import zio.test.{ test => test0, _ }

object CanFailSpec
    extends ZIOBaseSpec(
      suite("CanFailSpec")(
        test0("useful combinators compile") {
          assertCompiles {
            """
            import zio._

            val io =  IO(1 / 0)
            val uio = UIO(0)

            io.orElse(uio)
            """
          }
        },
        test0("useless combinators don't compile") {
          !assertCompiles {
            """
            import zio._

            val io =  IO(1 / 0)
            val uio = UIO(0)

            uio.orElse(io)
            """
          }
        }
      )
    )
