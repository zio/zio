import com.github.ghik.silencer.silent
import zio.test._

object StreamREPLSpec extends DefaultRunnableSpec {

  @silent("Unused import")
  def spec: ZSpec[Environment, Failure] = suite("StreamREPLSpec")(
    test("settings compile") {
      import zio.Runtime.default._
      import zio._
      import zio.console._
      import zio.duration._
      import zio.stream._
      @silent("never used")
      implicit class RunSyntax[A](io: ZIO[ZEnv, Any, A]) {
        def unsafeRun: A =
          Runtime.default.unsafeRun(io.provideLayerManual(ZEnv.live))
      }
      assertCompletes
    }
  )
}
