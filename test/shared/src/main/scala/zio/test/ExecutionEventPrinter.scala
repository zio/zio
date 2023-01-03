package zio.test

import zio.test.results.TestResultPrinter
import zio.{ZIO, ZLayer}

private[test] trait ExecutionEventPrinter {
  def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit]
}

private[test] object ExecutionEventPrinter {
  case class Live(console: ExecutionEventConsolePrinter, file: TestResultPrinter) extends ExecutionEventPrinter {
    override def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit] =
      console.print(event) *>
        (event match {
          case testResult: ExecutionEvent.Test[_] => file.print(testResult)
          case _                                  => ZIO.unit
        })
  }

  val live: ZLayer[ExecutionEventConsolePrinter with TestResultPrinter, Nothing, ExecutionEventPrinter] =
    ZLayer.fromFunction(Live.apply _)

  def print(event: ExecutionEvent): ZIO[ExecutionEventPrinter, Nothing, Unit] =
    ZIO.serviceWithZIO(_.print(event))

}
