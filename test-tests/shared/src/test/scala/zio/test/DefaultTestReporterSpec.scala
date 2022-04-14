package zio.test

import zio.test.ReportingTestUtils._
import zio.test.TestAspect._

object DefaultTestReporterSpec extends ZIOBaseSpec {

  def spec =
    suite("DefaultTestReporterSpec")(
      suite("reports")(
        test("a successful test") {
          runLog(test1).map(res => assertTrue(test1Expected == res))
        },
        test("a failed test") {
          runLog(test3).map(res => test3Expected.map(expected => assertTrue(res.contains(expected))).reduce(_ && _))
        },
        test("an error in a test") {
          runLog(test4).map(log => assertTrue(log.contains("Test 4 Fail")))
        },
        test("successful test suite") {
          runLog(suite1).map(res => suite1Expected.map(expected => assertTrue(res.contains(expected))).reduce(_ && _))
        },
        test("failed test suite") {
          runLog(suite2).map(res => suite2Expected.map(expected => assertTrue(res.contains(expected))).reduce(_ && _))
        },
        test("multiple test suites") {
          runLog(suite3).map(res => suite3Expected.map(expected => assertTrue(res.contains(expected))).reduce(_ && _))
        },
        test("empty test suite") {
          runLog(suite4).map(res => suite4Expected.map(expected => assertTrue(res.contains(expected))).reduce(_ && _))
        },
        test("failure of simple assertion") {
          runLog(test5).map(res => test5Expected.map(expected => assertTrue(res.contains(expected))).reduce(_ && _))
        },
        test("multiple nested failures") {
          runLog(test6).map(res => test6Expected.map(expected => assertTrue(res.contains(expected))).reduce(_ && _))
        },
        test("labeled failures") {
          runLog(test7).map(res => test7Expected.map(expected => assertTrue(res.contains(expected))).reduce(_ && _))
        },
        test("labeled failures for assertTrue") {
          for {
            log <- runLog(test9)
          } yield assertTrue(log.contains("""?? "third""""), log.contains("""?? "fourth""""))
        },
        test("negated failures") {
          runLog(test8).map(res => test8Expected.map(expected => assertTrue(res.contains(expected))).reduce(_ && _))
        }
      )
    ) @@ silent
}
