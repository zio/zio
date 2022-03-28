package zio.test

import zio.{Ref, UIO, ZIO, ZLayer}

trait ExecutionEventSink {
  def getSummary: UIO[Summary]

  def process(event: ExecutionEvent): ZIO[TestOutput with ExecutionEventSink with TestLogger, Nothing, Unit]
}

object ExecutionEventSink {
  def getSummary: ZIO[ExecutionEventSink, Nothing, Summary] =
    ZIO.serviceWithZIO[ExecutionEventSink](_.getSummary)

  def process(event: ExecutionEvent): ZIO[TestOutput with ExecutionEventSink with TestLogger, Nothing, Unit] =
    ZIO.serviceWithZIO[ExecutionEventSink](_.process(event))

  val ExecutionEventSinkLive: ZIO[Any, Nothing, ExecutionEventSink] =
    for {
      summary <- Ref.make[Summary](Summary(0, 0, 0, ""))
    } yield new ExecutionEventSink {

      override def process(
        event: ExecutionEvent
      ): ZIO[TestOutput with ExecutionEventSink with TestLogger, Nothing, Unit] =
        event match {
          case testEvent @ ExecutionEvent.Test(_, _, _, ancestors, _, sectionId) =>
            summary.update(
              _.add(testEvent)
            ) *>
              TestOutput.printOrQueue(
                sectionId,
                ancestors,
                testEvent
              )

          case start @ ExecutionEvent.SectionStart(_, id, ancestors) =>
            TestOutput.printOrQueue(
              id,
              ancestors,
              start
            )

          case ExecutionEvent.SectionEnd(_, id, ancestors) =>
            TestOutput.printOrFlush(id, ancestors)

          case runtimeFailure @ ExecutionEvent.RuntimeFailure(id, _, _, ancestors) =>
            TestOutput.printOrQueue(id, ancestors, runtimeFailure)
        }

      override def getSummary: UIO[Summary] = summary.get

    }

  val live: ZLayer[TestOutput, Nothing, ExecutionEventSink] =
    ZLayer.fromZIO(
      ExecutionEventSinkLive
    )
}
