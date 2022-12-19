package zio.test.sbt

import sbt.testing.{Event, Status, TaskDef, TestSelector}
import zio._
import zio.test.render.ConsoleRenderer
import zio.test._

object ZTestEventSpec extends ZIOSpecDefault {
  def spec = {
    suite("exhaustive conversions")(
      test("succeeded"){
        val test = ExecutionEvent.Test(
          labelsReversed = List("test", "specific", "realm"),
          test = Right(TestSuccess.Succeeded()),
          annotations = TestAnnotationMap.empty,
          ancestors = List.empty,
          duration = 0L,
          id = SuiteId(0)
        )
        val result: Event =
          ZTestEvent.convertEvent(
            test,
            new TaskDef("zio.dev.test", ZioSpecFingerprint, false, Array.empty),
            ConsoleRenderer
          )
        val expected: Event = ZTestEvent(
            fullyQualifiedName = "zio.dev.test",
            selector = new TestSelector("realm - specific - test"),
            status = Status.Success,
            maybeThrowable = None,
            duration = 0L,
            fingerprint = ZioSpecFingerprint
          )
        assertEqualEvents(result, expected)
      }
    )
  }
  // Required because `Selector` equality isn't working
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
        result.throwable() == expected.throwable()
      ) &&
      assertTrue(
        result.duration() == expected.duration()
      ) &&
      assertCompletes

}
