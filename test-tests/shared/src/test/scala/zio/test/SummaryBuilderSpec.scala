package zio.test

import zio.test.Assertion._
import zio.test.ReportingTestUtils._
import zio.test.TestAspect.silent

object SummaryBuilderSpec extends ZIOBaseSpec {

  def summarize(log: Vector[String]): String =
//     log.filter(!_.contains(green("+"))).mkString.stripLineEnd + "\n"
    log.mkString("\n").stripLineEnd + "\n"

  def labelOnly(log: Vector[String]): String =
    log.take(1).mkString.stripLineEnd

  import zio.internal.macros.StringUtils.StringOps
//  import zio.internal.stacktracer.SourceLocation
  def containsUnstyled(string: String, substring: String): TestResult =
    assertTrue(string.unstyled.contains(substring.unstyled))

  def spec =
    suite("SummaryBuilderSpec")(
      test("doesn't generate summary for a successful test") {
        assertZIO(runSummary(test1))(equalTo(""))
      },
      test("includes a failed test") {
//        for {
//         str <- runSummary(test3)
//          _ <- ZIO.debug("RES: \n" + str.unstyled + "\n===========")
//         _ <- ZIO.debug("EXPECTED: \n" + summarize(test3ExpectedZ).unstyled + "\n===========")
//        } yield assertCompletes && containsUnstyled(str, summarize(test3ExpectedZ))

        runSummary(test3).map(str => containsUnstyled(str, summarize(test3ExpectedZ)))
//          _ <- ZIO.debug("RES: \n" + str.unstyled + "\n===========")
//          _ <- ZIO.debug("EXPECTED: \n" + summarize(test3ExpectedZ).unstyled + "\n===========")
      },
      test("doesn't generate summary for a successful test suite") {
        assertZIO(runSummary(suite1))(equalTo(""))
      },
      test("correctly reports failed test suite") {
        assertZIO(runSummary(suite2))(equalTo(summarize(suite2Expected)))
      } @@ TestAspect.ignore,
      test("correctly reports multiple test suites") {
        runSummary(suite3).map{str => println(str.unstyled); println(summarize(suite3ExpectedZ).unstyled); assertTrue(str == summarize(suite3ExpectedZ))}
      } @@ TestAspect.ignore,
      test("correctly reports failure of simple assertion") {
        runSummary(test5).map(str => assertTrue(str == summarize(test5Expected)))
      } @@ TestAspect.ignore
    ) @@ silent // TODO Restore in next PR
}
