package zio.test

import zio.test.Assertion._
import zio.test.ReportingTestUtils._
import zio.test.TestAspect.silent

object SummaryBuilderSpec extends ZIOBaseSpec {

  def summarize(log: Vector[String]): String =
    log.filter(!_.contains(green("+"))).mkString.stripLineEnd

  def labelOnly(log: Vector[String]): String =
    log.take(1).mkString.stripLineEnd

  def spec: ZSpec[Environment, Failure] =
    suite("SummaryBuilderSpec")(
      test("doesn't generate summary for a successful test") {
        assertM(runSummary(test1))(equalTo(""))
      },
      test("includes a failed test") {
        assertM(runSummary(test3))(equalTo(summarize(test3Expected)))
      },
      test("doesn't generate summary for a successful test suite") {
        assertM(runSummary(suite1))(equalTo(""))
      },
      test("correctly reports failed test suite") {
        assertM(runSummary(suite2))(equalTo(summarize(suite2Expected)))
      },
      test("correctly reports multiple test suites") {
        assertM(runSummary(suite3))(equalTo(summarize(suite3Expected)))
      },
      test("correctly reports failure of simple assertion") {
        assertM(runSummary(test5))(equalTo(summarize(test5Expected)))
      }
    ) @@ silent
}
