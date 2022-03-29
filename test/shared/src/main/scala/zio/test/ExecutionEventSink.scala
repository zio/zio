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
          case testEvent: ExecutionEvent.Test[_] =>
            summary.update(
              _.add(testEvent)
            ) *>
              TestOutput.print(
                testEvent
              )

          case otherEvents =>
            TestOutput.print(
              otherEvents
            )
        }

      override def getSummary: UIO[Summary] = summary.get

    }

  val live: ZLayer[TestOutput, Nothing, ExecutionEventSink] =
    ZLayer.fromZIO(
      ExecutionEventSinkLive
    )
}
