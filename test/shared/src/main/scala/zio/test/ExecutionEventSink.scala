package zio.test

import zio.{Chunk, Ref, UIO, ZIO, ZLayer}

trait ExecutionEventSink {
  def getSummary: UIO[Summary]

  def process(event: ExecutionEvent): ZIO[StreamingTestOutput with ExecutionEventSink with TestLogger, Nothing, Unit]
}

object ExecutionEventSink {

  def process(event: ExecutionEvent): ZIO[StreamingTestOutput with ExecutionEventSink with TestLogger, Nothing, Unit] =
    ZIO.serviceWithZIO[ExecutionEventSink](_.process(event))

  val ExecutionEventSinkLive =
    for {
      summary <- Ref.make[Summary](Summary(0, 0, 0, ""))
    } yield new ExecutionEventSink {

      override def process(
        event: ExecutionEvent
      ): ZIO[StreamingTestOutput with ExecutionEventSink with TestLogger, Nothing, Unit] =
        event match {
          case testEvent @ ExecutionEvent.Test(labelsReversed, test, annotations, ancestors, duration, sectionId) =>
            summary.update(
              _.add(SectionState(Chunk(testEvent), sectionId))
            ) *>
              StreamingTestOutput.printOrQueue(
                sectionId,
                ancestors,
                SectionState(Chunk(testEvent), sectionId)
              )

          case ExecutionEvent.SectionStart(labelsReversed, id, ancestors) =>
            for {
              // TODO Get result from this line and use in printOrQueue
              _ <- StreamingTestOutput.attemptToGetPrintingControl(id, ancestors)
              _ <- StreamingTestOutput.printOrQueue(
                     id,
                     ancestors,
                     SectionHeader(labelsReversed, id)
                   )
            } yield ()

          case ExecutionEvent.SectionEnd(labelsReversed, id, ancestors) =>
            StreamingTestOutput.printOrFlush(id, ancestors) *>
              StreamingTestOutput.relinquishPrintingControl(id)

          case ExecutionEvent.RuntimeFailure(labelsReversed, failure, ancestors) =>
            ZIO.unit // TODO Decide how to report this
        }

      override def getSummary: UIO[Summary] = summary.get

    }

  val live: ZLayer[StreamingTestOutput, Nothing, ExecutionEventSink] =
    ZLayer.fromZIO(
      ExecutionEventSinkLive
    )
}
