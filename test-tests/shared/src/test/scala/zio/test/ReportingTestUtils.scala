package zio.test

import zio.internal.stacktracer.SourceLocation
import zio.test.Assertion._
import zio.test.ReporterEventRenderer.ConsoleEventRenderer
import zio.{Cause, Scope, Trace, ULayer, ZIO, ZIOAppArgs, ZLayer}

import scala.{Console => SConsole}

object ReportingTestUtils {

  def expectedSuccess(label: String): String =
    green("+") + " " + label + "\n"

  def expectedFailure(label: String): String =
    red("- " + label) + "\n"

  def expectedFailureZ(label: String): String =
    red("- " + label)

  def expectedIgnored(label: String): String =
    yellow("- " + label) + " - " + TestAnnotation.ignored.identifier + " suite" + "\n"

  def withOffset(n: Int)(s: String): String =
    " " * n + s

  def green(s: String): String =
    SConsole.GREEN + s + SConsole.RESET

  def red(s: String): String =
    SConsole.RED + s + SConsole.RESET

  def blue(s: String): String =
    SConsole.BLUE + s + SConsole.RESET

  def cyan(s: String): String =
    SConsole.CYAN + s + SConsole.RESET

  def yellow(s: String): String =
    SConsole.YELLOW + s + SConsole.RESET

  def reportStats(success: Int, ignore: Int, failure: Int): String = {
    val total = success + ignore + failure
    cyan(
      s"Ran $total test${if (total == 1) "" else "s"} in 0 ns: $success succeeded, $ignore ignored, $failure failed"
    ) + "\n"
  }

  // TODO de-dup layers?
  def runLog(
    spec: Spec[TestEnvironment, String]
  )(implicit trace: Trace, sourceLocation: SourceLocation): ZIO[TestEnvironment with Scope, Nothing, String] =
    for {
      console <- ZIO.console
      _       <- TestTestRunner(testEnvironment, sinkLayer(console, ConsoleEventRenderer)).run(spec)
      output  <- TestConsole.output
    } yield output.mkString

  def runSummary(
    spec: Spec[TestEnvironment, String]
  )(implicit trace: Trace, sourceLocation: SourceLocation): ZIO[TestEnvironment, Nothing, String] =
    for {
      console <- ZIO.console
      summary <- TestTestRunner(testEnvironment, sinkLayer(console, ConsoleEventRenderer)).run(spec)
    } yield summary.failureDetails

  private[test] def TestTestRunner(
    testEnvironment: ZLayer[Scope, Nothing, TestEnvironment],
    sinkLayer: ULayer[ExecutionEventSink]
  )(implicit
    trace: Trace,
    sourceLocation: SourceLocation
  ) = TestRunner[TestEnvironment, String](
    executor = TestExecutor.default[TestEnvironment, String](
      Scope.default >>> testEnvironment,
      (liveEnvironment ++ Scope.default) >+> TestEnvironment.live ++ ZIOAppArgs.empty,
      sinkLayer,
      ZTestEventHandler.silent
    )
  )

  def test1(implicit sourceLocation: SourceLocation): Spec[Any, Nothing] =
    test("Addition works fine")(assert(1 + 1)(equalTo(2)))
  val test1Expected: String = expectedSuccess("Addition works fine")

  def test2(implicit sourceLocation: SourceLocation): Spec[Any, Nothing] =
    test("Subtraction works fine")(assert(1 - 1)(equalTo(0)))
  val test2Expected: String = expectedSuccess("Subtraction works fine")

  def test3(implicit sourceLocation: SourceLocation): Spec[Any, Nothing] =
    test("Value falls within range")(assert(52)(equalTo(42) || (isGreaterThan(5) && isLessThan(10))))
  def test3Expected(implicit sourceLocation: SourceLocation): Vector[String] = Vector(
    expectedFailure("Value falls within range"),
    s"52 did not satisfy equalTo(42)",
    s"52 did not satisfy equalTo(42) || (isGreaterThan(5) && isLessThan(10)",
    assertSourceLocation(),
    s"52 was not less than 10",
    s"52 did not satisfy equalTo(42) || (isGreaterThan(5) && isLessThan(10)",
    assertSourceLocation()
  )

  def test3ExpectedZ(implicit sourceLocation: SourceLocation): Vector[String] = Vector(
    expectedFailureZ("Value falls within range"),
    s"  ✗ 52 was not equal to 42",
    s"  52 did not satisfy equalTo(42) || (isGreaterThan(5) && isLessThan(10))",
    "  " + assertSourceLocation(),
    s"  ✗ 52 was not less than 10",
    s"  52 did not satisfy equalTo(42) || (isGreaterThan(5) && isLessThan(10))",
    "  " + assertSourceLocation()
  )

  def test3ExpectedZNotIndented(implicit sourceLocation: SourceLocation): Vector[String] = Vector(
    expectedFailureZ("Value falls within range"),
    s"✗ 52 was not equal to 42",
    s"52 did not satisfy equalTo(42) || (isGreaterThan(5) && isLessThan(10))",
    "" + assertSourceLocation(),
    s"✗ 52 was not less than 10",
    s"52 did not satisfy equalTo(42) || (isGreaterThan(5) && isLessThan(10))",
    "" + assertSourceLocation()
  )

  def test4(implicit sourceLocation: SourceLocation): Spec[Any, String] =
    Spec.labeled("Failing test", Spec.test(failed(Cause.fail("Test 4 Fail")), TestAnnotationMap.empty))

  def test5(implicit sourceLocation: SourceLocation): Spec[Any, Nothing] =
    test("Addition works fine")(assert(1 + 1)(equalTo(3)))
  // the captured expression for `1+1` is different between dotty and 2.x
  def expressionIfNotRedundant(expr: String, value: Any): String =
    Option(expr).filterNot(_ == value.toString).fold(value.toString)(e => s"`$e` = $value")
  def test5Expected(implicit sourceLocation: SourceLocation): Vector[String] = Vector(
    expectedFailure("Addition works fine"),
    "2 was not equal to 3",
    assertSourceLocation()
  )

  def test6(implicit sourceLocation: SourceLocation): Spec[Any, Nothing] =
    test("Multiple nested failures")(assert(Right(Some(3)))(isRight(isSome(isGreaterThan(4)))))
  def test6Expected(implicit sourceLocation: SourceLocation): Vector[String] = Vector(
    expectedFailure("Multiple nested failures"),
    "3 was not greater than 4",
    s"${blue(s"Right(Some(3))")} did not satisfy ${cyan("isRight(") + yellow("isSome(isGreaterThan(4))") + cyan(")")}",
    assertSourceLocation()
  )

  def test7(implicit sourceLocation: SourceLocation): Spec[Any, Nothing] = test("labeled failures") {
    for {
      a <- ZIO.succeed(Some(1))
      b <- ZIO.succeed(Some(1))
      c <- ZIO.succeed(Some(0))
      d <- ZIO.succeed(Some(1))
    } yield assert(a)(isSome(equalTo(1)).label("first")) &&
      assert(b)(isSome(equalTo(1)).label("second")) &&
      assert(c)(isSome(equalTo(1)).label("third")) &&
      assert(d)(isSome(equalTo(1)).label("fourth"))
  }
  def test7Expected(implicit sourceLocation: SourceLocation): Vector[String] = Vector(
    expectedFailure("labeled failures"),
    s"0 was not equal to 1",
    s"""${blue("c")} did not satisfy isSome(equalTo(1)).label("third")""",
    assertSourceLocation()
  )

  def test8(implicit sourceLocation: SourceLocation): Spec[Any, Nothing] = test("Not combinator") {
    assert(100)(not(equalTo(100)))
  }
  def test8Expected(implicit sourceLocation: SourceLocation): Vector[String] = Vector(
    expectedFailure("Not combinator"),
    "100 was equal to 100",
    s"${blue("100")} did not satisfy ${cyan("not(") + yellow("equalTo(100)") + cyan(")")}",
    assertSourceLocation()
  )

  def test9(implicit sourceLocation: SourceLocation): Spec[Any, Nothing] = test("labeled failures") {
    assertTrue(1 == 1).label("first") &&
    assertTrue(1 == 1).label("second") &&
    assertTrue(1 == 0).label("third") &&
    assertTrue(1 == 0).label("fourth")
  }

  def suite1(implicit sourceLocation: SourceLocation): Spec[Any, Nothing] =
    suite("Suite1")(test1, test2)
  def suite1Expected(implicit sourceLocation: SourceLocation): Vector[String] = Vector(
    expectedSuccess("Suite1"),
    test1Expected,
    test2Expected
  )

  def suite2(implicit sourceLocation: SourceLocation): Spec[Any, Nothing] =
    suite("Suite2")(test1, test2, test3)
  def suite2Expected(implicit sourceLocation: SourceLocation): Vector[String] = Vector(
    expectedSuccess("Suite2"),
    test1Expected,
    test2Expected
  ) ++ test3Expected

  def suite3(implicit sourceLocation: SourceLocation): Spec[Any, Nothing] =
    suite("Suite3")(suite1, suite2, test3)
  def suite3Expected(implicit sourceLocation: SourceLocation): Vector[String] = Vector(expectedSuccess("Suite3")) ++
    suite1Expected ++
    suite2Expected ++
    Vector("\n") ++
    test3Expected

  def suite3ExpectedZ(implicit sourceLocation: SourceLocation): Vector[String] = Vector(expectedSuccess("Suite3 / Suite 2")) ++
    suite1Expected ++
    suite2Expected ++
    Vector("\n") ++
    test3ExpectedZNotIndented

  def suite4(implicit sourceLocation: SourceLocation): Spec[Any, Nothing] =
    suite("Suite4")(suite1, suite("Empty")(), test3)
  def suite4Expected(implicit sourceLocation: SourceLocation): Vector[String] = {

    def suite1ExpectedLocal(implicit sourceLocation: SourceLocation): Vector[String] = Vector(
      expectedSuccess("Suite1"),
      test1Expected,
      test2Expected
    )

    Vector(expectedSuccess("Suite4")) ++
      suite1ExpectedLocal ++
      Vector(expectedSuccess("Empty")) ++
      test3Expected
  }

  def assertSourceLocation()(implicit sourceLocation: SourceLocation): String =
    cyan(s"at ${sourceLocation.path}:${sourceLocation.line}")

  def assertSourceLocationZ()(implicit sourceLocation: SourceLocation): String =
    cyan(s"at ${sourceLocation.path}:${sourceLocation.line - 4}")
}
