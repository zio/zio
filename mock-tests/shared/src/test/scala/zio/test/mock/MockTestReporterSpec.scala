package zio.mock

import zio.mock.ReportingTestUtils._
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

object MockTestReporterSpec extends ZIOBaseSpec {

  def spec =
    suite("MockTestReporterSpec")(
      test("correctly reports a successful test") {
        assertM(runLog(test1))(equalTo(test1Expected.mkString + reportStats(1, 0, 0)))
      },
      test("correctly reports a failed test") {
        assertM(runLog(test3))(equalTo(test3Expected.mkString + "\n" + reportStats(0, 0, 1)))
      },
      test("correctly reports an error in a test") {
        for {
          log <- runLog(test4)
        } yield assertTrue(log.contains("Test 4 Fail"))
      },
      test("correctly reports successful test suite") {
        assertM(runLog(suite1))(equalTo(suite1Expected.mkString + reportStats(2, 0, 0)))
      },
      test("correctly reports failed test suite") {
        assertM(runLog(suite2))(equalTo(suite2Expected.mkString + "\n" + reportStats(2, 0, 1)))
      },
      test("correctly reports multiple test suites") {
        assertM(runLog(suite3))(equalTo(suite3Expected.mkString + "\n" + reportStats(4, 0, 2)))
      },
      test("correctly reports empty test suite") {
        assertM(runLog(suite4))(equalTo(suite4Expected.mkString + "\n" + reportStats(2, 0, 1)))
      },
      test("correctly reports failure of simple assertion") {
        assertM(runLog(test5))(equalTo(test5Expected.mkString + "\n" + reportStats(0, 0, 1)))
      },
      test("correctly reports multiple nested failures") {
        assertM(runLog(test6))(equalTo(test6Expected.mkString + "\n" + reportStats(0, 0, 1)))
      },
      test("correctly reports labeled failures") {
        assertM(runLog(test7))(equalTo(test7Expected.mkString + "\n" + reportStats(0, 0, 1)))
      },
      test("correctly reports negated failures") {
        assertM(runLog(test8))(equalTo(test8Expected.mkString + "\n" + reportStats(0, 0, 1)))
      },
      test("correctly reports mock failure of invalid call") {
        runLog(mock1).map(str => assertTrue(str == mock1Expected.mkString + reportStats(0, 0, 1)))
      },
      test("correctly reports mock failure of unmet expectations") {
        runLog(mock2).map(str => assertTrue(str == mock2Expected.mkString + reportStats(0, 0, 1)))
      },
      test("correctly reports mock failure of unexpected call") {
        assertM(runLog(mock3))(equalTo(mock3Expected.mkString + reportStats(0, 0, 1)))
      },
      test("correctly reports mock failure of invalid range") {
        assertM(runLog(mock4))(equalTo(mock4Expected.mkString + reportStats(0, 0, 1)))
      }
    ) @@ silent
}
