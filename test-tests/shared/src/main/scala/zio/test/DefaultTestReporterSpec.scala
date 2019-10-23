package zio.test

import zio._
import zio.clock.Clock
import zio.test.environment._
import zio.test.Assertion._
import zio.test.ReportingTestUtils._
import zio.test.TestUtils.label

import scala.concurrent.Future

object DefaultTestReporterSpec extends AsyncBaseSpec {

  val run: List[Async[(Boolean, String)]] = List(
    label(reportSuccess, "correctly reports a successful test"),
    label(reportFailure, "correctly reports a failed test"),
    label(reportError, "correctly reports an error in a test"),
    label(reportSuite1, "correctly reports successful test suite"),
    label(reportSuite2, "correctly reports failed test suite"),
    label(reportSuites, "correctly reports multiple test suites"),
    label(simpleAssertion, "correctly reports failure of simple assertion"),
    label(multipleNestedFailures, "correctly reports multiple nested failures")
  )

  def makeTest[L](label: L)(assertion: => TestResult): ZSpec[Any, Nothing, L, Unit] =
    zio.test.test(label)(assertion)

  val test1 = makeTest("Addition works fine") {
    assert(1 + 1, equalTo(2))
  }

  val test1Expected = expectedSuccess("Addition works fine")

  val test2 = makeTest("Subtraction works fine") {
    assert(1 - 1, equalTo(0))
  }

  val test2Expected = expectedSuccess("Subtraction works fine")

  val test3 = makeTest("Value falls within range") {
    assert(52, equalTo(42) || (isGreaterThan(5) && isLessThan(10)))
  }

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

  val test4 = Spec.test("Failing test", fail(Cause.fail("Fail")))

  val test4Expected = Vector(
    expectedFailure("Failing test"),
    withOffset(2)("Fiber failed.\n") +
      withOffset(2)("A checked error was not handled.\n") +
      withOffset(2)("Fail\n") +
      withOffset(2)("No ZIO Trace available.\n")
  )

  val test5 = makeTest("Addition works fine") {
    assert(1 + 1, equalTo(3))
  }

  val test5Expected = Vector(
    expectedFailure("Addition works fine"),
    withOffset(2)(s"${blue("2")} did not satisfy ${cyan("equalTo(3)")}\n")
  )

  val test6 = makeTest("Multiple nested failures") {
    assert(Right(Some(3)), isRight(isSome(isGreaterThan(4))))
  }

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

  def reportStats(success: Int, ignore: Int, failure: Int) = {
    val total = success + ignore + failure
    cyan(
      s"Ran $total test${if (total == 1) "" else "s"} in 0 ns: $success succeeded, $ignore ignored, $failure failed"
    ) + "\n"
  }

  def reportSuccess =
    check(test1, Vector(test1Expected, reportStats(1, 0, 0)))

  def reportFailure =
    check(test3, test3Expected :+ reportStats(0, 0, 1))

  def reportError =
    check(test4, test4Expected :+ reportStats(0, 0, 1))

  def reportSuite1 =
    check(suite1, suite1Expected :+ reportStats(2, 0, 0))

  def reportSuite2 =
    check(suite2, suite2Expected :+ reportStats(2, 0, 1))

  def reportSuites =
    check(
      suite("Suite3")(suite1, test3),
      Vector(expectedFailure("Suite3")) ++ suite1Expected.map(withOffset(2)) ++ test3Expected
        .map(withOffset(2)) :+ reportStats(2, 0, 1)
    )

  def simpleAssertion =
    check(test5, test5Expected :+ reportStats(0, 0, 1))

  def multipleNestedFailures =
    check(test6, test6Expected :+ reportStats(0, 0, 1))

  def check[E](spec: ZSpec[TestEnvironment, String, String, Unit], expected: Vector[String]): Future[Boolean] =
    unsafeRunWith(testEnvironmentManaged) { r =>
      val zio = for {
        _ <- TestTestRunner(r)
              .run(spec)
              .provideSomeM(for {
                logSvc   <- TestLogger.fromConsoleM
                clockSvc <- TestClock.make(TestClock.DefaultData)
              } yield new TestLogger with Clock {
                override def testLogger: TestLogger.Service = logSvc.testLogger
                override val clock: Clock.Service[Any]      = clockSvc.clock
              })
        output <- TestConsole.output
      } yield output == expected
      zio.provide(r)
    }

  def unsafeRunWith[R, E <: Throwable, A](r: Managed[Nothing, R])(f: R => IO[E, A]): Future[A] =
    unsafeRunToFuture(r.use[Any, E, A](f))

  def TestTestRunner(testEnvironment: TestEnvironment) =
    TestRunner[TestEnvironment, String, Either[TestFailure[Nothing], TestSuccess[Unit]], String, Unit](
      executor = TestExecutor.managed[TestEnvironment, String, String, Unit](Managed.succeed(testEnvironment)),
      reporter = DefaultTestReporter()
    )
}
