package zio.test

import zio.clock.Clock
import zio.test.Assertion.{ equalTo, isGreaterThan, isLessThan }
import zio.test.ReportingTestUtils._
import zio.test.TestUtils.label
import zio.test.environment._
import zio.{ Cause, IO, Managed }

import scala.concurrent.Future

object SummaryBuilderSpec extends AsyncBaseSpec {
  val run: List[Async[(Boolean, String)]] = List(
    label(reportSuccess, "doesn't generate summary for a successful test"),
    label(reportFailure, "includes a failed test"),
    label(reportError, "correctly reports an error in a test"),
    label(reportSuite1, "doesn't generate summary for a successful test suite"),
    label(reportSuite2, "correctly reports failed test suite"),
    label(reportSuites, "correctly reports multiple test suites"),
    label(simpleAssertion, "correctly reports failure of simple assertion")
  )

  def makeTest[L](label: L)(assertion: => TestResult): ZSpec[Any, Nothing, L, Unit] =
    zio.test.test(label)(assertion)

  val test1 = makeTest("Addition works fine") {
    assert(1 + 1, equalTo(2))
  }

  val test2 = makeTest("Subtraction works fine") {
    assert(1 - 1, equalTo(0))
  }

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

  val suite1 = suite("Suite1")(test1, test2)

  val suite2 = suite("Suite2")(test1, test2, test3)

  def reportSuccess =
    check(test1, noSummary)

  def reportFailure =
    check(test3, test3Expected)

  def reportError =
    check(test4, test4Expected)

  def reportSuite1 =
    check(suite1, noSummary)

  def reportSuite2 =
    check(suite2, expectedFailure("Suite2") +: test3Expected.map(withOffset(2)))

  def reportSuites =
    check(
      suite("Suite3")(suite1, suite2, test3),
      Vector(expectedFailure("Suite3")) ++
        (expectedFailure("Suite2") +: test3Expected.map(withOffset(2))).map(withOffset(2)) ++
        test3Expected.map(withOffset(2))
    )

  def simpleAssertion =
    check(test5, test5Expected)

  val noSummary: Vector[String] = Vector.empty

  def check[E](spec: ZSpec[TestEnvironment, String, String, Unit], expectedSummary: Vector[String]): Future[Boolean] =
    unsafeRunWith(testEnvironmentManaged) { r =>
      val zio = for {
        results <- TestTestRunner(r)
                    .run(spec)
                    .provideSomeM(for {
                      logSvc   <- TestLogger.fromConsoleM
                      clockSvc <- TestClock.make(TestClock.DefaultData)
                    } yield new TestLogger with Clock {
                      override def testLogger: TestLogger.Service = logSvc.testLogger
                      override val clock: Clock.Service[Any]      = clockSvc.clock
                    })
        actualSummary <- SummaryBuilder.buildSummary(results)
      } yield actualSummary == expectedSummary.mkString("").stripLineEnd

      zio.provide(r)
    }

  def unsafeRunWith[R, E <: Throwable, A](r: Managed[Nothing, R])(f: R => IO[E, A]): Future[A] =
    unsafeRunToFuture(r.use[Any, E, A](f))

  def TestTestRunner(testEnvironment: TestEnvironment) =
    TestRunner[TestEnvironment, String, String, Unit, Unit](
      executor = TestExecutor.managed[TestEnvironment, String, String, Unit](Managed.succeed(testEnvironment)),
      reporter = DefaultTestReporter()
    )
}
