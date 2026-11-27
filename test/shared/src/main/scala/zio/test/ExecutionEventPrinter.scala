package zio.test

import zio.test.results.ResultPrinter
import zio.{Console, ZIO, ZLayer}

/**
 * Prints execution events to the destinations of a test run.
 */
private[test] trait ExecutionEventPrinter {

  /**
   * Prints one event.
   */
  def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit]
}

private[test] object ExecutionEventPrinter {
  case class Live(console: ExecutionEventConsolePrinter, file: ResultPrinter) extends ExecutionEventPrinter {
    override def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit] =
      console.print(event) *>
        (event match {
          case testResult: ExecutionEvent.Test[_] => file.print(testResult)
          case _                                  => ZIO.unit
        })
  }

  /**
   * Builds a printer that writes each event to `console` with `eventRenderer`
   * and, on the JVM and Scala Native, each test result to the JSON results file
   * under `reportsParent`.
   */
  def live(
    console: Console,
    eventRenderer: ReporterEventRenderer,
    reportsParent: String
  ): ZLayer[Any, Nothing, ExecutionEventPrinter] =
    ZLayer.make[ExecutionEventPrinter](
      ResultPrinter.json(reportsParent),
      ExecutionEventConsolePrinter.live(eventRenderer),
      TestLogger.fromConsole(console),
      ZLayer.fromFunction(Live.apply _)
    )

  def print(event: ExecutionEvent): ZIO[ExecutionEventPrinter, Nothing, Unit] =
    ZIO.serviceWithZIO(_.print(event))

}
