import zio.test._

import scala.annotation.nowarn

object StreamREPLSpec extends ZIOSpecDefault {

  def spec = suite("StreamREPLSpec")(
    test("settings compile") {
      import zio._
      @nowarn("msg=never used")
      implicit class RunSyntax[A](io: ZIO[Any, Any, A]) {
        def unsafeRun: A =
          Unsafe.unsafe { implicit unsafe =>
            Runtime.default.unsafe.run(io).getOrThrowFiberFailure()
          }
      }
      assertCompletes
    }
  )
}
