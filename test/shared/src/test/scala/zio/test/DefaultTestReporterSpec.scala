package zio.test

import scala.Predef.{ assert => SAssert, _ }

import zio._
import zio.test.mock._

object DefaultTestReporterSpec extends DefaultRuntime {

  def run(): Unit = {
    SAssert(reportSuccess, "DefaultTestReporter correctly reports a successful test")
    SAssert(reportFailure, "DefaultTestReporter correctly reports a failed test")
    SAssert(reportError, "DefaultTestReporter correctly reports an error in a test")
    SAssert(reportSuite1, "DefaultTestReporter correctly reports successful test suite")
    SAssert(reportSuite2, "DefaultTestReporter correctly reports failed test suite")
    SAssert(reportSuites, "DefaultTestReporter correctly reports multiple test suites")
    SAssert(simplePredicate, "DefaultTestReporter correctly reports failure of simple predicate")
  }

  def makeTest[L](label: L)(assertion: => TestResult): ZSpec[Any, Nothing, L] =
    zio.test.test(label)(assertion)

  val test1 = makeTest("Addition works fine") {
    assert(1 + 1, Predicate.equals(2))
  }

  val test1Expected = expectedSuccess("Addition works fine")

  val test2 = makeTest("Subtraction works fine") {
    assert(1 - 1, Predicate.equals(0))
  }

  val test2Expected = expectedSuccess("Subtraction works fine")

  val test3 = makeTest("Value falls within range") {
    assert(52, Predicate.equals(42) || (Predicate.isGreaterThan(5) && Predicate.isLessThan(10)))
  }

  val test3Expected = Vector(
    expectedFailure("Value falls within range"),
    withOffset(2)(
      s"${blue("52")} did not satisfy ${cyan("(equals(42) || (" + yellow("isGreaterThan(5)") + " && isLessThan(10)))")}\n"
    ),
    withOffset(2)(s"${blue("52")} did not satisfy ${cyan("isGreaterThan(5)")}\n")
  )

  val test4 = makeTest("Failing test") {
    fail(Cause.fail("Fail"))
  }

  val test4Expected = Vector(
    expectedFailure("Failing test"),
    withOffset(2)("Fiber failed.\n") +
      withOffset(2)("A checked error was not handled.\n") +
      withOffset(2)("Fail\n") +
      withOffset(2)("No ZIO Trace available.\n")
  )

  val test5 = makeTest("Addition works fine") {
    assert(1 + 1, Predicate.equals(3))
  }

  val test5Expected = Vector(
    expectedFailure("Addition works fine"),
    withOffset(2)(s"${blue("2")} did not satisfy ${cyan("equals(3)")}\n")
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

  def reportSuccess =
    check(test1, Vector(test1Expected))

  def reportFailure =
    check(test3, test3Expected)

  def reportError =
    check(test4, test4Expected)

  def reportSuite1 =
    check(suite1, suite1Expected)

  def reportSuite2 =
    check(suite2, suite2Expected)

  def reportSuites =
    check(
      suite("Suite3")(suite1, test3),
      Vector(expectedFailure("Suite3")) ++ suite1Expected.map(withOffset(2)) ++ test3Expected.map(withOffset(2))
    )

  def simplePredicate =
    check(
      test5,
      test5Expected
    )

  def expectedSuccess(label: String): String =
    green("+") + " " + label + "\n"

  def expectedFailure(label: String): String =
    red("- " + label) + "\n"

  def withOffset(n: Int)(s: String): String =
    " " * n + s

  def green(s: String): String =
    Console.GREEN + s + Console.RESET

  def red(s: String): String =
    Console.RED + s + Console.RESET

  def blue(s: String): String =
    Console.BLUE + s + Console.RESET

  def cyan(s: String): String =
    Console.CYAN + s + Console.RESET

  def yellow(s: String): String =
    Console.YELLOW + s + Console.CYAN

  def check[E](spec: ZSpec[MockEnvironment, E, String], expected: Vector[String]): Boolean =
    unsafeRunWith(mockEnvironmentManaged) { r =>
      val zio = for {
        _      <- MockTestRunner(r).run(spec)
        output <- MockConsole.output
        // _      <- ZIO.effectTotal {
        //   if (output != expected) {
        //     output.zip(expected)
        //       .filter { case (x, y) => x != y }
        //       .foreach { case (x, y) =>
        //         println(x)
        //         println(y)
        //       }

        //   }
        // }
      } yield output == expected
      zio.provide(r)
    }

  def unsafeRunWith[R, E, A](r: Managed[Nothing, R])(f: R => IO[E, A]): A =
    unsafeRun(r.use[Any, E, A](f))

  case class MockTestRunner(mockEnvironment: MockEnvironment)
      extends TestRunner[String, ZTest[MockEnvironment, Any]](
        executor = TestExecutor.managed(Managed.succeed(mockEnvironment)),
        reporter = DefaultTestReporter(mockEnvironment)
      )
}
