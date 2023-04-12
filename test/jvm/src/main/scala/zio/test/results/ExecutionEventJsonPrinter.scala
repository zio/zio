package zio.test.results

import zio.{ZIO, ZLayer}
import zio.test._

private[test] object ExecutionEventJsonPrinter {
  private val lively: ZLayer[ResultSerializer with ResultFileOps, Nothing, TestResultPrinter] =
    ZLayer.fromFunction(
      LiveImpl(_, _)
    )

  val live: ZLayer[Any, Nothing, TestResultPrinter] =
    ZLayer.make[TestResultPrinter](
      ResultSerializer.live,
//      ResultSerializer.csv,
      ResultFileOps.live,
      lively
    )

  private case class LiveImpl(serializer: ResultSerializer, resultFileOps: ResultFileOps)
      extends TestResultPrinter {
    override def print[E](event: ExecutionEvent.Test[E]): ZIO[Any, Nothing, Unit] =
      resultFileOps.write(serializer.render(event), append = true).orDie
  }
}
