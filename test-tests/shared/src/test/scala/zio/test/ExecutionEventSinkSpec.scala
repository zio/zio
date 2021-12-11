package zio.test

import zio._
import java.util.UUID

object ExecutionEventSinkSpec extends ZIOSpecDefault {
  val uuid = TestSectionId(UUID.randomUUID())

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
        _ <- ExecutionEventSink.process(event)
        _ <- ZIO.debug("Finished sink spec test!")
      } yield assertTrue(true)
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
        _ <- ExecutionEventSink.process(event)
        _ <- ZIO.debug("Finished sink spec test!")
      } yield assertTrue(true)
    },
    test("process with ancestor") {
      val event = ExecutionEvent.Test(
        List("add", "ConcurrentSetSpec"),
        Right(TestSuccess.Succeeded(BoolAlgebra.Value(()))),
        TestAnnotationMap.empty,
        List(TestSectionId(UUID.fromString("44bcc3b1-68ff-4140-9dc2-fffbddb8c70f"))),
        0L,
        uuid
      )
      for {
        _ <- ExecutionEventSink.process(event)
      } yield assertTrue(true)
    },
    test("process section") {
      val startEvent =
        ExecutionEvent.SectionStart(
          labelsReversed = List("startLabel"),
          id = TestSectionId(UUID.fromString("7f2ce7f5-2d66-496f-9270-6565f04d0d48")),
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
          id = TestSectionId(UUID.fromString("7f2ce7f5-2d66-496f-9270-6565f04d0d48")),
          ancestors = List.empty
        )
      for {
        _ <- ExecutionEventSink.process(startEvent)
        _ <- ExecutionEventSink.process(testEvent)
        _ <- ExecutionEventSink.process(endEvent)
      } yield assertTrue(true)
    }
  ).provide(
    Console.live,
    TestLogger.fromConsole,
    ExecutionEventSink.live,
    StreamingTestOutput.live
  ) @@ TestAspect.ignore
  // TODO Rewrite these tests for the final Sink implementation

}
