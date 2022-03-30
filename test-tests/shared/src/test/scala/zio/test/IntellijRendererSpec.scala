package zio.test

import zio.test.Assertion.equalTo
import zio.test.ReportingTestUtils._
import zio.test.TestAspect.silent
import zio.test.render.IntelliJRenderer
import zio.{Random, Scope, ZIO, ZLayer, ZTraceElement}

object IntellijRendererSpec extends ZIOBaseSpec {
  import IntelliJRenderUtils._

  def spec =
    suite("IntelliJ Renderer")(
      test("correctly reports a successful test") {
        assertM(runLog(test1))(equalTo(test1Expected.mkString))
      },
      test("correctly reports a failed test") {
        assertM(runLog(test3))(equalTo(test3Expected.mkString))
      },
      test("correctly reports successful test suite") {
        assertM(runLog(suite1))(equalTo(suite1Expected.mkString))
      },
      test("correctly reports failed test suite") {
        assertM(runLog(suite2))(equalTo(suite2Expected.mkString))
      },
      test("correctly reports multiple test suites") {
        assertM(runLog(suite3))(equalTo(suite3Expected.mkString))
      },
      test("correctly reports empty test suite") {
        assertM(runLog(suite4))(equalTo(suite4Expected.mkString))
      },
      test("correctly reports failure of simple assertion") {
        assertM(runLog(test5))(equalTo(test5Expected.mkString))
      },
      test("correctly reports multiple nested failures") {
        assertM(runLog(test6))(equalTo(test6Expected.mkString))
      },
      test("correctly reports labeled failures") {
        assertM(runLog(test7))(equalTo(test7Expected.mkString))
      },
      test("correctly reports negated failures") {
        runLog(test8).map(str => assertTrue(str == test8Expected.mkString))
      }
    ) @@ silent @@ TestAspect.ignore
  // TODO Investigate these expectations once ZIO-intellij plugin is updated

  def test1Expected(implicit trace: ZTraceElement): Vector[String] = Vector(
    testStarted("Addition works fine"),
    testFinished("Addition works fine")
  )

  def test2Expected(implicit trace: ZTraceElement): Vector[String] = Vector(
    testStarted("Subtraction works fine"),
    testFinished("Subtraction works fine")
  )

  def test3Expected(implicit trace: ZTraceElement): Vector[String] = Vector(
    testStarted("Value falls within range"),
    testFailed(
      "Value falls within range",
      Vector(
        withOffset(2)(s"${blue("52")} did not satisfy ${cyan("equalTo(42)")}\n"),
        withOffset(2)(
          s"${blue("52")} did not satisfy ${cyan("(") + yellow("equalTo(42)") + cyan(" || (isGreaterThan(5) && isLessThan(10)))")}\n"
        ),
        withOffset(2)(assertSourceLocation() + "\n"),
        "\n",
        withOffset(2)(s"${blue("52")} did not satisfy ${cyan("isLessThan(10)")}\n"),
        withOffset(2)(
          s"${blue("52")} did not satisfy ${cyan("(equalTo(42) || (isGreaterThan(5) && ") + yellow("isLessThan(10)") + cyan("))")}\n"
        ),
        withOffset(2)(assertSourceLocation()),
        "\n"
      )
    )
  )

  def suite1Expected(implicit trace: ZTraceElement): Vector[String] = Vector(
    suiteStarted("Suite1")
  ) ++ test1Expected ++ test2Expected ++
    Vector(
      suiteFinished("Suite1")
    )

  def suite2Expected(implicit trace: ZTraceElement): Vector[String] = Vector(
    suiteStarted("Suite2")
  ) ++ test1Expected ++ test2Expected ++ test3Expected ++
    Vector(
      suiteFinished("Suite2")
    )

  def suite3Expected(implicit trace: ZTraceElement): Vector[String] = Vector(
    suiteStarted("Suite3")
  ) ++ suite1Expected ++ suite2Expected ++ test3Expected ++ Vector(
    suiteFinished("Suite3")
  )

  def suite4Expected(implicit trace: ZTraceElement): Vector[String] = Vector(
    suiteStarted("Suite4")
  ) ++ suite1Expected ++ Vector(suiteStarted("Empty"), suiteFinished("Empty")) ++
    test3Expected ++ Vector(suiteFinished("Suite4"))

  def test5Expected(implicit trace: ZTraceElement): Vector[String] = Vector(
    testStarted("Addition works fine"),
    testFailed(
      "Addition works fine",
      Vector(
        withOffset(2)(
          s"${blue(expressionIfNotRedundant(showExpression(1 + 1), 2))} did not satisfy ${cyan("equalTo(3)")}\n"
        ),
        withOffset(2)(assertSourceLocation()),
        "\n"
      )
    )
  )

  def test6Expected(implicit trace: ZTraceElement): Vector[String] = Vector(
    testStarted("Multiple nested failures"),
    testFailed(
      "Multiple nested failures",
      Vector(
        withOffset(2)(s"${blue("3")} did not satisfy ${cyan("isGreaterThan(4)")}\n"),
        withOffset(2)(
          s"${blue("Some(3)")} did not satisfy ${cyan("isSome(") + yellow("isGreaterThan(4)") + cyan(")")}\n"
        ),
        withOffset(2)(
          s"${blue(s"Right(Some(3))")} did not satisfy ${cyan("isRight(") + yellow("isSome(isGreaterThan(4))") + cyan(")")}\n"
        ),
        withOffset(2)(assertSourceLocation()),
        "\n"
      )
    )
  )

  def test7Expected(implicit trace: ZTraceElement): Vector[String] = Vector(
    testStarted("labeled failures"),
    testFailed(
      "labeled failures",
      Vector(
        withOffset(2)(s"${blue("0")} did not satisfy ${cyan("equalTo(1)")}\n"),
        withOffset(2)(
          s"${blue("`c` = Some(0)")} did not satisfy ${cyan("(isSome(") + yellow("equalTo(1)") + cyan(") ?? \"third\")")}\n"
        ),
        withOffset(2)(assertSourceLocation()),
        "\n"
      )
    )
  )

  def test8Expected(implicit trace: ZTraceElement): Vector[String] = Vector(
    testStarted("Not combinator"),
    testFailed(
      "Not combinator",
      Vector(
        withOffset(2)(s"${blue("100")} satisfied ${cyan("equalTo(100)")}\n"),
        withOffset(2)(
          s"${blue("100")} did not satisfy ${cyan("not(") + yellow("equalTo(100)") + cyan(")")}\n"
        ),
        withOffset(2)(assertSourceLocation()),
        "\n"
      )
    )
  )
}
object IntelliJRenderUtils {
  import IntelliJRenderer.escape

  def suiteStarted(name: String): String =
    s"##teamcity[testSuiteStarted name='$name']" + "\n"

  def suiteFinished(name: String): String =
    s"##teamcity[testSuiteFinished name='$name']" + "\n"

  def testStarted(name: String)(implicit trace: ZTraceElement): String = {
    val location = Option(trace).collect { case ZTraceElement(_, file, line) =>
      (file, line)
    }

    val loc = location.fold("") { case (file, line) => s"file://$file:$line" }
    s"##teamcity[testStarted name='$name' locationHint='$loc']" + "\n"
  }

  def testFinished(name: String): String =
    s"##teamcity[testFinished name='$name' duration='']" + "\n"

  def testFailed(name: String, error: Vector[String]): String =
    s"##teamcity[testFailed name='$name' message='Assertion failed:' details='${escape(error.mkString)}']" + "\n"

  def runLog(
    spec: ZSpec[TestEnvironment, String]
  )(implicit trace: ZTraceElement): ZIO[TestEnvironment with Scope, Nothing, String] =
    for {
      _ <-
        IntelliJTestRunner(testEnvironment)
          .run(spec)
          .provideLayer[Nothing, TestEnvironment with Scope](
            TestClock.default ++ (TestLogger.fromConsole >>> ExecutionEventPrinter.live >>> TestOutput.live >>> ExecutionEventSink.live) ++ Random.live
          )
      output <- TestConsole.output
    } yield output.mkString

  private[this] def IntelliJTestRunner(
    testEnvironment: ZLayer[Scope, Nothing, TestEnvironment]
  )(implicit trace: ZTraceElement) =
    TestRunner[TestEnvironment, String](
      executor = TestExecutor.default[TestEnvironment, String](testEnvironment),
      reporter = DefaultTestReporter(IntelliJRenderer, TestAnnotationRenderer.default)
    )
}
