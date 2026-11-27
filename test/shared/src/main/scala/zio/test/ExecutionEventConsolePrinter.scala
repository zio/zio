package zio.test

import zio._

/**
 * Prints execution events to the console of a test run.
 */
private[test] trait ExecutionEventConsolePrinter {

  /**
   * Renders `event` and writes the result to the console. Events that render to
   * an empty chunk write nothing.
   */
  def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit]
}

private[test] object ExecutionEventConsolePrinter {

  /**
   * Builds a printer that renders events with `renderer` and writes them to the
   * `TestLogger` in the environment.
   */
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
