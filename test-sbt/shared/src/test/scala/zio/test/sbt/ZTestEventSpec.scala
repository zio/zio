package zio.test.sbt

import sbt.testing.{Event, Status, TaskDef, TestSelector}
import zio.{Cause, Clock, durationInt, ZIO}
import zio.test._
import zio.test.render.ConsoleRenderer

object ZTestEventSpec extends ZIOSpecDefault {
  private def executionEvent[E](
    labelsReversed: List[String],
    fullyQualifiedName: String,
    test: Either[TestFailure[E], TestSuccess]
  ): ExecutionEvent.Test[E] =
    ExecutionEvent.Test(
      labelsReversed = labelsReversed,
      test = test,
      annotations = TestAnnotationMap.empty,
      ancestors = List.empty,
      duration = 0L,
      id = SuiteId(0),
      fullyQualifiedName = fullyQualifiedName
    )

  private def convert[T](test: ExecutionEvent.Test[T]): Event =
    ZTestEvent.convertTestEvent(
      test,
      new TaskDef("zio.dev.test", ZioSpecFingerprint, false, Array.empty),
      ConsoleRenderer
    )

  private def expectedEvent(
    testName: String,
    status: Status,
    maybeThrowable: Option[Throwable]
  ): ZTestEvent =
    ZTestEvent(
      "zio.dev.test",
      new TestSelector(testName),
      status,
      maybeThrowable,
      0L
    )

  def spec =
    suite("exhaustive conversions")(
      test("just zio sleep")(
        ZIO.withClock(Clock.ClockLive)(ZIO.sleep(2.second)).as(assertCompletes)
      ),
      test("success") {
        val test = executionEvent(
          labelsReversed = List("test", "specific", "realm"),
          test = Right(TestSuccess.Succeeded()),
          fullyQualifiedName = "test.specific.realm"
        )
        val expected: Event = expectedEvent(
          "realm - specific - test",
          Status.Success,
          None
        )
        assertEqualEvents(convert(test), expected)
      },
      test("failure") {
        val test = executionEvent(
          labelsReversed = List("test", "specific", "realm"),
          test = Left(TestFailure.Runtime(Cause.fail("boom"))),
          fullyQualifiedName = "test.specific.realm"
        )
        val expected: Event = expectedEvent(
          "realm - specific - test",
          Status.Failure,
          Some(
            new Exception(
              s"""|    ${ConsoleUtils.bold(Colors.red("- test"))}
                  |      java.lang.String: boom""".stripMargin
            )
          )
        )
        assertEqualEvents(convert(test), expected)
      }
    )
  // Required because
  //  - `Selector` equality isn't working
  //  - Ansi colors make comparisons horrible to work with
  def assertEqualEvents(result: Event, expected: Event): TestResult =
    assertTrue(
      result.fullyQualifiedName() == expected.fullyQualifiedName()
    ) &&
      assertTrue(
        result.selector().toString == expected.selector().toString
      ) &&
      assertTrue(
        result.status() == expected.status()
      ) &&
      assertTrue(
        stripAnsi(result.throwable())
          == stripAnsi(expected.throwable())
      ) &&
      assertTrue(
        result.duration() == expected.duration()
      ) &&
      assertCompletes

  private def stripAnsi(input: Any) =
    // Note: ESC is represented in a way that both Scala on JVM and Scala Native can handle.
    input.toString
      .replaceAll("\u001b\\[[\\d;]*[^\\d;]", "")
      .replaceAll("\u001b\\[[\\d;]*[^\\d;]", "")
}
