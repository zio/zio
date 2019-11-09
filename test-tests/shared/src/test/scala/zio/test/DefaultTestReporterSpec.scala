package zio.test

import zio._
import zio.clock.Clock
import zio.test.Assertion._
import zio.test.ReportingTestUtils._
import zio.test.environment.{ testEnvironmentManaged, TestClock, TestConsole, TestEnvironment }

object DefaultTestReporterSpec
  extends ZIOBaseSpec(
    suite("DefaultTestReporterSpec")(
      testM("correctly reports a successful test") {
        val x             = zio.test.test("Addition works fine")(assert(1 + 1, equalTo(2)))
        val test1Expected = expectedSuccess("Addition works fine")
        assertM(
          DefaultTestReporterSpecUtil.runLog(x),
          equalTo(Vector(test1Expected, DefaultTestReporterSpecUtil.reportStats(1, 0, 0)))
        )
      }
    )
  )

object DefaultTestReporterSpecUtil {

  def reportStats(success: Int, ignore: Int, failure: Int) = {
    val total = success + ignore + failure
    cyan(
      s"Ran $total test${if (total == 1) "" else "s"} in 0 ns: $success succeeded, $ignore ignored, $failure failed"
    ) + "\n"
  }

  def runLog[E](spec: ZSpec[TestEnvironment, String, String, Unit]) = {
    val zio = for {
      _ <- TestTestRunner(testEnvironmentManaged)
        .run(spec)
        .provideSomeManaged(for {
          logSvc   <- TestLogger.fromConsoleM.toManaged_
          clockSvc <- TestClock.make(TestClock.DefaultData)
        } yield new TestLogger with Clock {
          override def testLogger: TestLogger.Service = logSvc.testLogger

          override val clock: Clock.Service[Any] = clockSvc.clock
        })
      output <- TestConsole.output
    } yield output
    zio
  }

  def TestTestRunner(testEnvironment: Managed[Nothing, TestEnvironment]) =
    TestRunner[TestEnvironment, String, Unit, String, Unit](
      executor = TestExecutor.managed[TestEnvironment, String, String, Unit](testEnvironment),
      reporter = DefaultTestReporter()
    )
}