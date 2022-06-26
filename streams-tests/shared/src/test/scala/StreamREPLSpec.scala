import com.github.ghik.silencer.silent
import zio.test._

object StreamREPLSpec extends ZIOSpecDefault {

  def spec = suite("StreamREPLSpec")(
    test("settings compile") {
      import zio._
      @silent("never used")
      implicit class RunSyntax[A](io: ZIO[Any, Any, A]) {
        def unsafeRun: A =
          Unsafe.unsafeCompat { implicit unsafe =>
            Runtime.default.unsafe.run(io).getOrThrowFiberFailure()
          }
      }
      assertCompletes
    }
  )
}
