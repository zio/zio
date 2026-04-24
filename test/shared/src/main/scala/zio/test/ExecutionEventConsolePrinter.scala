package zio.test

import zio._

private[test] trait ExecutionEventConsolePrinter {
  def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit]
}

private[test] object ExecutionEventConsolePrinter {
  def live(renderer: ReporterEventRenderer): ZLayer[TestLogger, Nothing, ExecutionEventConsolePrinter] =
    ZLayer {
      for {
        testLogger <- ZIO.service[TestLogger]
      } yield Live(testLogger, renderer)
    }

  case class Live(logger: TestLogger, eventRenderer: ReporterEventRenderer) extends ExecutionEventConsolePrinter {
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
