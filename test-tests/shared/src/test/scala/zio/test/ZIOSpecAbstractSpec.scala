package zio.test
import zio.{Scope, ZIO, ZIOAppArgs}

object ZIOSpecAbstractSpec extends ZIOSpecDefault {
  override def spec = test("highlighting composed layer failures")(for {
    _ <- ZIO.debug("==================== New Test Run ====================")
    composedSpec: ZIOSpecAbstract = AMinimalSpec <> SmallMinimalSpec <> SlowMinimalSpec
    _ <- ZIO.consoleWith(console =>  composedSpec.runSpec(composedSpec.spec, TestArgs.empty, console))
    output <- testOutput

  } yield assertTrue(output.mkString("\n").contains("NotImplementedError"))
  ).provide(ZIOAppArgs.empty, testEnvironment, Scope.default) @@ TestAspect.nonFlaky

  private val testOutput =
    for {
      console <- testConsole
      output  <- console.output
    } yield output

}
