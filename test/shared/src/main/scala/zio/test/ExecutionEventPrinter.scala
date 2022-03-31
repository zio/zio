package zio.test

import zio.{ZIO, ZLayer}

trait ExecutionEventPrinter {
  def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit]
}

object ExecutionEventPrinter {
  val live: ZLayer[TestLogger, Nothing, ExecutionEventPrinter] = {
    ZLayer.fromZIO(for {
      testLogger <- ZIO.service[TestLogger]
    } yield new Live(testLogger))
  }

  def print(event: ExecutionEvent): ZIO[ExecutionEventPrinter, Nothing, Unit] =
    ZIO.serviceWithZIO[ExecutionEventPrinter](_.print(event))

  class Live(logger: TestLogger) extends ExecutionEventPrinter {
    override def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit] =
      logger.logLine(
        ReporterEventRenderer.render(event).mkString("\n")
      )
  }
}
