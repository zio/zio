package zio.test
import zio.{Scope, ZIO, ZIOAppArgs, ZLayer}
import zio.internal.ansi.AnsiStringOps

object ZIOSpecAbstractSpec extends ZIOSpecDefault {
  private val basicSpec: ZIOSpecAbstract = new ZIOSpecDefault {
    override def spec =
      test("basic test") {
        assertTrue(true)
      }
  }
  private val basicFailSpec: ZIOSpecAbstract = new ZIOSpecDefault {
    override def spec =
      test("basic fail test") {
        assertTrue(false)
      }
  }
  override def spec = suite("ZIOSpecAbstractSpec")(
    test("highlighting composed layer failures") {
      // We must define this here rather than as a standalone spec, because it will prevent all the tests from running
      val specWithBrokenLayer = new ZIOSpec[Int] {
        override val bootstrap = ZLayer.fromZIO(ZIO.attempt(???))
        override def spec =
          test("should never see this label printed") {
            assertTrue(true)
          }
      }
      val composedSpec: ZIOSpecAbstract = basicSpec <> specWithBrokenLayer <> basicSpec
      for {
        _ <- ZIO.consoleWith { console =>
               composedSpec
                 .runSpecAsApp(composedSpec.spec, TestArgs.empty, console)
                 .provideSome[zio.Scope with TestEnvironment](composedSpec.bootstrap)
                 .catchAllCause(t => console.printLine(t.toString))
                 .exitCode
             }
        output <- TestConsole.output.map(_.mkString("\n"))
      } yield assertTrue(output.contains("scala.NotImplementedError: an implementation is missing")) &&
        assertTrue(
          // Brittle with the line numbers
          // number of line with "override val bootstrap = ZLayer.fromZIO(ZIO.attempt(???))"
          output.contains("ZIOSpecAbstractSpec.scala:22")
        ) &&
        assertTrue(
          // number of line with "_ <- ZIO.consoleWith { console =>"
          output.contains("ZIOSpecAbstractSpec.scala:30")
        ) &&
        assertTrue(
          // number of line with ".provideSome[zio.Scope with TestEnvironment](composedSpec.bootstrap)"
          output.contains("ZIOSpecAbstractSpec.scala:33")
        )
    } @@ TestAspect.flaky,
    test("run method reports successes sanely")(
      for {
        res <- basicSpec.run.provideSome[zio.ZIOAppArgs with zio.Scope](basicSpec.bootstrap)
      } yield assertTrue(equalsTimeLess(res, Summary(1, 0, 0, "")))
    ),
    test("runSpec produces a summary with fully-qualified failures") {
      val suiteName = "parent"
      val testName  = "failing test"
      val failingSpec: ZIOSpecDefault = new ZIOSpecDefault {
        override def spec = suite(suiteName)(
          test(testName)(
            assertTrue(false)
          )
        )
      }
      for {
        res <- ZIO.consoleWith(console => failingSpec.runSpecAsApp(failingSpec.spec, TestArgs.empty, console))
      } yield assertTrue(res.fail == 1) &&
        assertTrue(res.failureDetails.contains(s"${suiteName.red}${" / ".red.faint}${testName.red}"))
    },
    test("run method reports exitcode=1 sanely")(
      for {
        exitCode <- basicFailSpec.run.provideSome[zio.ZIOAppArgs with zio.Scope](basicFailSpec.bootstrap).exitCode
      } yield assertTrue(exitCode.code == 1)
    ),
    test("run method reports exitcode=0 sanely")(
      for {
        exitCode <- basicSpec.run.provideSome[zio.ZIOAppArgs with zio.Scope](basicSpec.bootstrap).exitCode
      } yield assertTrue(exitCode.code == 0)
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
      a.failureDetails == b.failureDetails
}
