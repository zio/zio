package zio.test.results

import zio.ZIO
import zio.test.ExecutionEvent

trait TestResultPrinter {
  def print[E](event: ExecutionEvent.Test[E]): ZIO[Any, Nothing, Unit]
}

object TestResultPrinter {
  val json = TestResultPrinterJson.live
}
