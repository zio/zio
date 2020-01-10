package zio.test

import zio._
import zio.test.Assertion._
import zio.test.ReportingTestUtils._
import zio.test.environment._

object DefaultTestReporterSpec extends ZIOBaseSpec {

  def spec = suite("DefaultTestReporterSpec")(
    testM("correctly reports a successful test") {
      assertM(runLog(test1))(equalTo(test1Expected.mkString + reportStats(1, 0, 0)))
    },
    testM("correctly reports a failed test") {
      assertM(runLog(test3))(equalTo(test3Expected.mkString + reportStats(0, 0, 1)))
    },
    testM("correctly reports an error in a test") {
      assertM(runLog(test4))(equalTo(test4Expected.mkString + reportStats(0, 0, 1)))
    },
    testM("correctly reports successful test suite") {
      assertM(runLog(suite1))(equalTo(suite1Expected.mkString + reportStats(2, 0, 0)))
    },
    testM("correctly reports failed test suite") {
      assertM(runLog(suite2))(equalTo(suite2Expected.mkString + reportStats(2, 0, 1)))
    },
    testM("correctly reports multiple test suites") {
      assertM(runLog(suite3))(equalTo(suite3Expected.mkString + reportStats(4, 0, 2)))
    },
    testM("correctly reports empty test suite") {
      assertM(runLog(suite4))(equalTo(suite4Expected.mkString + reportStats(2, 0, 1)))
    },
    testM("correctly reports failure of simple assertion") {
      assertM(runLog(test5))(equalTo(test5Expected.mkString + reportStats(0, 0, 1)))
    },
    testM("correctly reports multiple nested failures") {
      assertM(runLog(test6))(equalTo(test6Expected.mkString + reportStats(0, 0, 1)))
    },
    testM("correctly reports labeled failures") {
      assertM(runLog(test7))(equalTo(test7Expected.mkString + reportStats(0, 0, 1)))
    },
    testM("correctly reports negated failures") {
      assertM(runLog(test8))(equalTo(test8Expected.mkString + reportStats(0, 0, 1)))
    }
  )

  val test1         = zio.test.test("Addition works fine")(assert(1 + 1)(equalTo(2)))
  val test1Expected = expectedSuccess("Addition works fine")

  val test2         = zio.test.test("Subtraction works fine")(assert(1 - 1)(equalTo(0)))
  val test2Expected = expectedSuccess("Subtraction works fine")

  val test3 = zio.test.test("Value falls within range")(assert(52)(equalTo(42) || (isGreaterThan(5) && isLessThan(10))))
  val test3Expected = Vector(
    expectedFailure("Value falls within range"),
    withOffset(2)(s"${blue("52")} did not satisfy ${cyan("equalTo(42)")}\n"),
    withOffset(2)(
      s"${blue("52")} did not satisfy ${cyan("(" + yellowThenCyan("equalTo(42)") + " || (isGreaterThan(5) && isLessThan(10)))")}\n"
    ),
    withOffset(2)(s"${blue("52")} did not satisfy ${cyan("isLessThan(10)")}\n"),
    withOffset(2)(
      s"${blue("52")} did not satisfy ${cyan("(equalTo(42) || (isGreaterThan(5) && " + yellowThenCyan("isLessThan(10)") + "))")}\n"
    )
  )

  val test4 = Spec.test("Failing test", failed(Cause.fail("Fail")))
  val test4Expected = Vector(
    expectedFailure("Failing test"),
    withOffset(2)("Fiber failed.\n") +
      withOffset(2)("A checked error was not handled.\n") +
      withOffset(2)("Fail\n") +
      withOffset(2)("No ZIO Trace available.\n")
  )

  val test5 = zio.test.test("Addition works fine")(assert(1 + 1)(equalTo(3)))
  val test5Expected = Vector(
    expectedFailure("Addition works fine"),
    withOffset(2)(s"${blue("2")} did not satisfy ${cyan("equalTo(3)")}\n")
  )

  val test6 = zio.test.test("Multiple nested failures")(assert(Right(Some(3)))(isRight(isSome(isGreaterThan(4)))))
  val test6Expected = Vector(
    expectedFailure("Multiple nested failures"),
    withOffset(2)(s"${blue("3")} did not satisfy ${cyan("isGreaterThan(4)")}\n"),
    withOffset(2)(
      s"${blue("Some(3)")} did not satisfy ${cyan("isSome(" + yellowThenCyan("isGreaterThan(4)") + ")")}\n"
    ),
    withOffset(2)(
      s"${blue("Right(Some(3))")} did not satisfy ${cyan("isRight(" + yellowThenCyan("isSome(isGreaterThan(4))") + ")")}\n"
    )
  )

  val test7 = testM("labeled failures") {
    for {
      a <- ZIO.effectTotal(Some(1))
      b <- ZIO.effectTotal(Some(1))
      c <- ZIO.effectTotal(Some(0))
      d <- ZIO.effectTotal(Some(1))
    } yield assert(a)(isSome(equalTo(1)).label("first")) &&
      assert(b)(isSome(equalTo(1)).label("second")) &&
      assert(c)(isSome(equalTo(1)).label("third")) &&
      assert(d)(isSome(equalTo(1)).label("fourth"))
  }
  val test7Expected = Vector(
    expectedFailure("labeled failures"),
    withOffset(2)(s"${blue("0")} did not satisfy ${cyan("equalTo(1)")}\n"),
    withOffset(2)(
      s"${blue("Some(0)")} did not satisfy ${cyan("(isSome(" + yellowThenCyan("equalTo(1)") + ") ?? \"third\")")}\n"
    )
  )

  val test8 = zio.test.test("Not combinator") {
    assert(100)(not(equalTo(100)))
  }
  val test8Expected = Vector(
    expectedFailure("Not combinator"),
    withOffset(2)(s"${blue("100")} satisfied ${cyan("equalTo(100)")}\n"),
    withOffset(2)(
      s"${blue("100")} did not satisfy ${cyan("not(" + yellowThenCyan("equalTo(100)") + ")")}\n"
    )
  )

  val suite1 = suite("Suite1")(test1, test2)
  val suite1Expected = Vector(
    expectedSuccess("Suite1"),
    withOffset(2)(test1Expected),
    withOffset(2)(test2Expected)
  )

  val suite2 = suite("Suite2")(test1, test2, test3)
  val suite2Expected = Vector(
    expectedFailure("Suite2"),
    withOffset(2)(test1Expected),
    withOffset(2)(test2Expected)
  ) ++ test3Expected.map(withOffset(2)(_))

  val suite3 = suite("Suite3")(suite1, test3)
  val suite3Expected = Vector(expectedFailure("Suite3")) ++
    suite1Expected.map(withOffset(2)) ++
    test3Expected.map(withOffset(2))

  val suite4 = suite("Suite4")(suite1, suite("Empty")(), test3)
  val suite4Expected = Vector(expectedFailure("Suite4")) ++
    suite1Expected.map(withOffset(2)) ++
    test3Expected.map(withOffset(2))

  def reportStats(success: Int, ignore: Int, failure: Int) = {
    val total = success + ignore + failure
    cyan(
      s"Ran $total test${if (total == 1) "" else "s"} in 0 ns: $success succeeded, $ignore ignored, $failure failed"
    ) + "\n"
  }

  def runLog[E](spec: ZSpec[TestEnvironment, String, String, Unit]) =
    for {
      _ <- TestTestRunner(testEnvironmentManaged)
            .run(spec)
            .provideSomeManaged((TestLogger.fromConsole ++ TestClock.default).value)
      output <- TestConsole.output
    } yield output.mkString

  private[this] def TestTestRunner(testEnvironment: Managed[Nothing, TestEnvironment]) =
    TestRunner[TestEnvironment, String, String, Unit, Unit](
      executor = TestExecutor.managed[TestEnvironment, String, String, Unit](testEnvironment),
      reporter = DefaultTestReporter(TestAnnotationRenderer.default)
    )
}
