package zio.test.results

import zio.test._
import zio.{ZIO, ZLayer}

private[test] object TestResultPrinterJson {
  val live: ZLayer[ResultSerializer with ResultFileOps, Nothing, TestResultPrinter] =
    ZLayer.fromFunction(
      LiveImpl(_, _)
    )

  private case class LiveImpl(serializer: ResultSerializer, resultFileOps: ResultFileOps) extends TestResultPrinter {
    override def print[E](event: ExecutionEvent.Test[E]): ZIO[Any, Nothing, Unit] =
      resultFileOps.write(serializer.render(event), append = true).orDie
  }
}
