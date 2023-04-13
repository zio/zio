package zio.test

import zio.test.results.TestResultPrinter
import zio.{Console, ZIO, ZLayer}

private[test] trait ExecutionEventPrinter {
  def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit]
}

private[test] object ExecutionEventPrinter {
  case class Live(console: ExecutionEventConsolePrinter, file: TestResultPrinter) extends ExecutionEventPrinter {
    override def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit] = {
      // NOTE This is where we write execution events in the console and in the file
      console.print(event) *>
        (event match {
          case testResult: ExecutionEvent.Test[_] => file.print(testResult) // TODO Should we report/ignore failures here?
          case _                                  => ZIO.unit
        })
    }
  }

  // TODO Name this better, or just in-line it below
  val lively: ZLayer[ExecutionEventConsolePrinter with TestResultPrinter, Nothing, ExecutionEventPrinter] =
    ZLayer.fromFunction(Live.apply _)

  def live(console: Console, eventRenderer: ReporterEventRenderer): ZLayer[Any, Nothing, ExecutionEventPrinter] =
    ZLayer.make[ExecutionEventPrinter](
      TestResultPrinter.json,
      ExecutionEventConsolePrinter.live(eventRenderer),
      TestLogger.fromConsole(console),
      lively
    )

  def print(event: ExecutionEvent): ZIO[ExecutionEventPrinter, Nothing, Unit] =
    ZIO.serviceWithZIO(_.print(event))

}
