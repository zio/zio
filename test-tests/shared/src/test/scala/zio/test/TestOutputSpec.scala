package zio.test
import zio.Scope

import zio._

object TestOutputSpec extends ZIOSpecDefault {
  override def spec: ZSpec[TestEnvironment with Scope, Any] = suite("TestOutputSpec")(
    test("TestOutput.run") {
      for {
        testConsole <- ZIO.service[TestConsole]
        testOutput <-  ZIO.service[TestOutput]
        _ <- testOutput.printOrQueue(SuiteId(2), List(SuiteId(1)), ExecutionEvent.Test(
          labelsReversed = List("a", "b"),
          test = Right(TestSuccess.Succeeded(BoolAlgebra.unit)),
          annotations = TestAnnotationMap.empty,
          ancestors = List.empty,
          duration = 0L,
          id = SuiteId(1)
        ))
        output <- testConsole.output
        _ <- ZIO.debug(output)
        _ <- ZIO.succeed("blah")
      } yield assertTrue(1 == 1)
    }
  ).provideSome[
      TestConsole
      with TestOutput
  ](ExecutionEventSink.live, TestLogger.fromConsole)


}
