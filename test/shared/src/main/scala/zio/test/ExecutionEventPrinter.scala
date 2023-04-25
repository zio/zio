package zio.test

import zio.test.results.ResultPrinter
import zio.{Console, ZIO, ZLayer}

private[test] trait ExecutionEventPrinter {
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

  def live(console: Console, eventRenderer: ReporterEventRenderer): ZLayer[Any, Nothing, ExecutionEventPrinter] =
    ZLayer.make[ExecutionEventPrinter](
      ResultPrinter.json,
      ExecutionEventConsolePrinter.live(eventRenderer),
      TestLogger.fromConsole(console),
      ZLayer.fromFunction(Live.apply _)
    )

  def print(event: ExecutionEvent): ZIO[ExecutionEventPrinter, Nothing, Unit] =
    ZIO.serviceWithZIO(_.print(event))

}
