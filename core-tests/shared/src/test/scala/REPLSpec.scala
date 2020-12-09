import com.github.ghik.silencer.silent
import zio.test._

object REPLSpec extends DefaultRunnableSpec {

  @silent("Unused import")
  def spec: ZSpec[Environment, Failure] = suite("REPLSpec")(
    test("settings compile") {
      import zio.Runtime.default._
      import zio._
      import zio.console._
      import zio.duration._
      @silent("never used")
      implicit class RunSyntax[A](io: ZIO[ZEnv, Any, A]) {
        def unsafeRun: A =
          Runtime.default.unsafeRun(io.provideLayer(ZEnv.live))
      }
      assertCompletes
    }
  )
}
