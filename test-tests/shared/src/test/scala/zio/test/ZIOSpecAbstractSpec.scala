package zio.test
import zio.{Scope, ZIO, ZIOAppArgs}

object ZIOSpecAbstractSpec extends ZIOSpecDefault {
  override def spec = test("highlighting composed layer failures")(for {
    _ <- ZIO.debug("Let's compose things")
    composedSpec: ZIOSpecAbstract = AMinimalSpec <> BMinimalSpec
    _ <- ZIO.consoleWith(console =>  composedSpec.runSpec(composedSpec.spec, TestArgs.empty, console))

  } yield assertCompletes
  ).provide(ZIOAppArgs.empty, testEnvironment, Scope.default)
}
