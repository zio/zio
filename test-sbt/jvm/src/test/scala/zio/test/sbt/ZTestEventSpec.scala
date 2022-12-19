package zio.test.sbt

import sbt.testing.TaskDef
import zio._
import zio.test.render.ConsoleRenderer
import zio.test.{ExecutionEvent, SuiteId, TestAnnotationMap, TestSuccess, ZIOSpecDefault, assertCompletes}

object ZTestEventSpec extends ZIOSpecDefault {
  def spec =
    test("hi")(
      for {
        _ <- ZIO.debug("blah")
        test = ExecutionEvent.Test(
          labelsReversed = List("hi"),
          test = Right(TestSuccess.Succeeded()),
          annotations = TestAnnotationMap.empty,
          ancestors = List.empty,
          duration = 0,
          id = SuiteId(0)
        )
        result = ZTestEvent.convertEvent(test, new TaskDef("hi", ZioSpecFingerprint, false, Array.empty), ConsoleRenderer)
        _ <- ZIO.debug(result)
      } yield assertCompletes
    )

}
