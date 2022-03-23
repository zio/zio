package zio.test

import zio._

object ExecutionEventSinkSpec extends ZIOSpecDefault {
  val uuid = SuiteId(0)

  override def spec = suite("ExecutionEventSinkSpec")(
    test("process single test") {
      val event = ExecutionEvent.Test(
        List("add", "ConcurrentSetSpec"),
        Right(TestSuccess.Succeeded(BoolAlgebra.Value(()))),
        TestAnnotationMap.empty,
        List.empty,
        0,
        uuid
      )
      for {
        _       <- ExecutionEventSink.process(event)
        summary <- ExecutionEventSink.getSummary
      } yield assertTrue(summary.success == 1)
    },
    test("process single test failure") {
      val event = ExecutionEvent.Test(
        List("add", "ConcurrentSetSpec"),
        Left(TestFailure.fail("You lose! Good day, sir!")),
        TestAnnotationMap.empty,
        List.empty,
        0,
        uuid
      )
      for {
        _       <- ExecutionEventSink.process(event)
        summary <- ExecutionEventSink.getSummary
      } yield assertTrue(summary.fail == 1)
    },
    test("process with ancestor") {
      val event = ExecutionEvent.Test(
        List("add", "ConcurrentSetSpec"),
        Right(TestSuccess.Succeeded(BoolAlgebra.Value(()))),
        TestAnnotationMap.empty,
        List(SuiteId(1)),
        0L,
        uuid
      )
      for {
        _       <- ExecutionEventSink.process(event)
        summary <- ExecutionEventSink.getSummary
      } yield assertTrue(summary.success == 1)
    },
    test("process section") {
      val startEvent =
        ExecutionEvent.SectionStart(
          labelsReversed = List("startLabel"),
          id = SuiteId(2),
          ancestors = List.empty
        )

      val testEvent = ExecutionEvent.Test(
        List("add", "ConcurrentSetSpec"),
        Right(TestSuccess.Succeeded(BoolAlgebra.Value(()))),
        TestAnnotationMap.empty,
        ancestors = List(startEvent.id),
        0L,
        uuid
      )

      val endEvent =
        ExecutionEvent.SectionEnd(
          labelsReversed = List("startLabel"),
          id = SuiteId(3),
          ancestors = List.empty
        )
      for {
        _       <- ExecutionEventSink.process(startEvent)
        _       <- ExecutionEventSink.process(testEvent)
        _       <- ExecutionEventSink.process(endEvent)
        summary <- ExecutionEventSink.getSummary
      } yield assertTrue(summary.success == 1)
    }
  ).provide(
    Console.live,
    TestLogger.fromConsole,
    ExecutionEventSink.live,
    TestOutput.live
  )
  // TODO Rewrite these tests for the final Sink implementation

}
