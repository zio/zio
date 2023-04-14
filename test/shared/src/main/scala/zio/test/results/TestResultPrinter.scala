package zio.test.results

import zio.{ZIO, ZLayer}
import zio.test.ExecutionEvent

trait ResultPrinter {
  def print[E](event: ExecutionEvent.Test[E]): ZIO[Any, Nothing, Unit]
}

object ResultPrinter {
  val json: ZLayer[Any, Nothing, ResultPrinter] = ResultPrinterJson.live
}
