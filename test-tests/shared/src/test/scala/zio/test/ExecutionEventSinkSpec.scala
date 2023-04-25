package zio.test

import zio.{Random, ZIO}
import zio.test.ReporterEventRenderer.ConsoleEventRenderer

object ExecutionEventSinkSpec extends ZIOBaseSpec {
  val uuid = SuiteId(0)

  override def spec = suite("ExecutionEventSinkSpec")(
    test("process single test") {
      for {
        randomId <- ZIO.withRandom(Random.RandomLive)(Random.nextInt).map("test_case_" + _)
        event = ExecutionEvent.Test(
                  List("add", "ConcurrentSetSpec"),
                  Right(TestSuccess.Succeeded()),
                  TestAnnotationMap.empty,
                  List.empty,
                  0,
                  uuid,
                  randomId
                )
        _       <- TestDebug.createDebugFile(randomId)
        _       <- ExecutionEventSink.process(event)
        _       <- TestDebug.deleteIfEmpty(randomId)
        summary <- ExecutionEventSink.getSummary
      } yield assertTrue(summary.success == 1)
    },
    test("process single test failure") {
      for {
        randomId <- ZIO.withRandom(Random.RandomLive)(Random.nextInt).map("test_case_" + _)
        event = ExecutionEvent.Test(
                  List("add", "ConcurrentSetSpec"),
                  Left(TestFailure.fail("You lose! Good day, sir!")),
                  TestAnnotationMap.empty,
                  List.empty,
                  0,
                  uuid,
                  randomId
                )
        _       <- TestDebug.createDebugFile(randomId)
        _       <- ExecutionEventSink.process(event)
        _       <- TestDebug.deleteIfEmpty(randomId)
        summary <- ExecutionEventSink.getSummary
      } yield assertTrue(summary.fail == 1)
    },
    test("process with ancestor") {
      for {
        randomId <- ZIO.withRandom(Random.RandomLive)(Random.nextInt).map("test_case_" + _)
        event = ExecutionEvent.Test(
                  List("add", "ConcurrentSetSpec"),
                  Right(TestSuccess.Succeeded()),
                  TestAnnotationMap.empty,
                  List(SuiteId(1)),
                  0L,
                  uuid,
                  randomId
                )
        _       <- TestDebug.createDebugFile(randomId)
        _       <- ExecutionEventSink.process(event)
        _       <- TestDebug.deleteIfEmpty(randomId)
        summary <- ExecutionEventSink.getSummary
      } yield assertTrue(summary.success == 1)
    }
  ).provide(ExecutionEventSink.live(zio.Console.ConsoleLive, ConsoleEventRenderer))

}
