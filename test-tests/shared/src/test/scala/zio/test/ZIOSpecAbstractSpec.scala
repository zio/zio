package zio.test
import zio.{Scope, ZIO, ZIOAppArgs}

object ZIOSpecAbstractSpec extends ZIOSpecDefault {
  override def spec = suite("ZIOSpecAbstractSpec")(
    test("highlighting composed layer failures")(
      for {
        _ <- ZIO.debug("==================== New Test Run ====================")
        composedSpec: ZIOSpecAbstract = Spec1 <> SpecWithBrokenLayer <> Spec2
        _ <- ZIO.consoleWith(console =>  composedSpec.runSpecInfallible(composedSpec.spec, TestArgs.empty, console))
        console <- testConsole
        output  <- console.output
      } yield assertTrue(output.mkString("\n").contains("NotImplementedError"))
    ) @@ TestAspect.nonFlaky(1000) @@ TestAspect.ignore,

    test("run method reports successes sanely") (
      for {
        res <- Spec1.run
      } yield assertTrue( equalsTimeLess(res, Summary(1, 0, 0, "")))
    ),

    test("run method reports failures sanely") (
      for {
        failureRes <- FailingSpec.run.flip
      } yield assertTrue( failureRes.contains("Result was false"))
    )
  )
      .provide(
        ZIOAppArgs.empty,
        testEnvironment,
        Scope.default
      )

  private def equalsTimeLess(a: Summary, b: Summary) =
    a.success == b.success &&
      a.fail == b.fail &&
      a.ignore == b.ignore &&
      a.summary == b.summary
}
