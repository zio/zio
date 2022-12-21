package zio.test

import zio.test.ReporterEventRenderer.ConsoleEventRenderer

object ExecutionEventSinkSpec extends ZIOSpecDefault {
  val uuid = SuiteId(0)

  override def spec = suite("ExecutionEventSinkSpec")(
    test("process single test") {
      val event = ExecutionEvent.Test(
        List("add", "ConcurrentSetSpec"),
        Right(TestSuccess.Succeeded()),
        TestAnnotationMap.empty,
        List.empty,
        0,
        uuid,
        "some.ClassName"
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
        uuid,
        "some.ClassName"
      )
      for {
        _       <- ExecutionEventSink.process(event)
        summary <- ExecutionEventSink.getSummary
      } yield assertTrue(summary.fail == 1)
    },
    test("process with ancestor") {
      val event = ExecutionEvent.Test(
        List("add", "ConcurrentSetSpec"),
        Right(TestSuccess.Succeeded()),
        TestAnnotationMap.empty,
        List(SuiteId(1)),
        0L,
        uuid,
        "some.ClassName"
      )
      for {
        _       <- ExecutionEventSink.process(event)
        summary <- ExecutionEventSink.getSummary
      } yield assertTrue(summary.success == 1)
    }
  ).provide(sinkLayer(zio.Console.ConsoleLive, ConsoleEventRenderer))

}
