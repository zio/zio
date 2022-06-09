package zio.test

import zio.{ZIO, ZLayer}

trait ExecutionEventPrinter {
  def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit]
}
object ExecutionEventPrinter {
  def live(renderer: ReporterEventRenderer): ZLayer[TestLogger, Nothing, ExecutionEventPrinter] =
    ZLayer {
      for {
        testLogger <- ZIO.service[TestLogger]
      } yield new Live(testLogger, renderer)
    }

  def print(event: ExecutionEvent): ZIO[ExecutionEventPrinter, Nothing, Unit] =
    ZIO.serviceWithZIO(_.print(event))

  class Live(logger: TestLogger, eventRenderer: ReporterEventRenderer) extends ExecutionEventPrinter {
    override def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit] = {
      val rendered = eventRenderer.render(event)
      ZIO
        .when(rendered.nonEmpty)(
          logger.logLine(
            rendered.mkString("\n")
          )
        )
        .unit
    }
  }
}
