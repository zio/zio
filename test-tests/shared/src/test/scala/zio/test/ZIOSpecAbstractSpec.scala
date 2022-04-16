package zio.test
import zio.{Scope, ZIO, ZIOAppArgs, ZLayer}

object ZIOSpecAbstractSpec extends ZIOSpecDefault {
  private val basicSpec: ZIOSpecAbstract = new ZIOSpecDefault {
    override def spec =
      test("basic test") {
        assertTrue(true)
      }
  }
  override def spec = suite("ZIOSpecAbstractSpec")(
    test("highlighting composed layer failures") {
      // We must define this here rather than as a standalone spec, because it will prevent all the tests from running
      val specWithBrokenLayer = new ZIOSpec[Int] {
        override val environmentLayer = ZLayer.fromZIO(ZIO.attempt(???))
        override def spec =
          test("should never see this label printed") {
            assertTrue(true)
          }
      }
      val composedSpec: ZIOSpecAbstract = basicSpec <> specWithBrokenLayer <> basicSpec
      for {
        _       <- ZIO.consoleWith(console => composedSpec.runSpecInfallible(composedSpec.spec, TestArgs.empty, console))
        console <- testConsole
        output  <- console.output.map(_.mkString("\n"))
      } yield assertTrue(output.contains("scala.NotImplementedError: an implementation is missing")) &&
        assertTrue(
          output.contains( // Brittle with the line numbers
            "ZIOSpecAbstractSpec.scala:15"
          )
        ) &&
        assertTrue(output.contains("ZIOSpecAbstractSpec.scala:23")) &&
        assertTrue(output.contains("java.lang.InterruptedException"))
    } @@ TestAspect.flaky,
    test("run method reports successes sanely")(
      for {
        res <- basicSpec.run
      } yield assertTrue(equalsTimeLess(res, Summary(1, 0, 0, "")))
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
