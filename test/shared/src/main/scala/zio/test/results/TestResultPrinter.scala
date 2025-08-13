package zio.test.results

import zio.{ZIO, ZLayer}
import zio.test.{ExecutionEvent, TestArgs}

trait ResultPrinter {
  def print[E](event: ExecutionEvent.Test[E]): ZIO[Any, Nothing, Unit]
}

object ResultPrinter {
  val json: ZLayer[Any, Nothing, ResultPrinter] = json(TestArgs.reportsParentDefault)

  def json(reportsParent: String): ZLayer[Any, Nothing, ResultPrinter] =
    ResultPrinterJson.live(s"$reportsParent/${TestArgs.jsonResultPath}")
}
