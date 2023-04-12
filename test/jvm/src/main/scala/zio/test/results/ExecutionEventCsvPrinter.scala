package zio.test.results

import zio.test._
import zio.{ZIO, ZLayer}

// TODO Use Csv classes instead
private[test] object ExecutionEventCsvPrinter {
  private val lively: ZLayer[ResultSerializer with ResultFileOps, Nothing, TestResultPrinter] =
    ZLayer.fromFunction(
      LiveImpl(_, _)
    )

  val live: ZLayer[Any, Nothing, TestResultPrinter] =
    ZLayer.make[TestResultPrinter](
      ResultSerializer.csv,
      ResultFileOpsCsv.live,
      lively
    )

  private case class LiveImpl(serializer: ResultSerializer, resultFileOps: ResultFileOps)
      extends TestResultPrinter {
    override def print[E](event: ExecutionEvent.Test[E]): ZIO[Any, Nothing, Unit] =
      resultFileOps.write(serializer.render(event), append = true).orDie
  }
}
