package zio.test
import zio.{Scope, ZIO, ZIOAppArgs}

object ZIOSpecAbstractSpec extends ZIOSpecDefault {
  override def spec =
    test("highlighting composed layer failures")(
      for {
        _ <- ZIO.debug("==================== New Test Run ====================")
        composedSpec: ZIOSpecAbstract = Spec1 <> SpecWithBrokenLayer <> Spec2
        _ <- ZIO.consoleWith(console =>  composedSpec.runSpec(composedSpec.spec, TestArgs.empty, console))
        console <- testConsole
        output  <- console.output
      } yield assertTrue(output.mkString("\n").contains("NotImplementedError"))
    )
      .provide(
        ZIOAppArgs.empty,
        testEnvironment,
        Scope.default
      ) @@ TestAspect.nonFlaky(1000)
}
