package zio.test.results

import zio.{ZIO, ZLayer}
import zio.test._

private[test] object ExecutionEventJsonPrinter {
  val live: ZLayer[ResultSerializer with ResultFileOpsJson, Nothing, TestResultPrinter] =
    ZLayer.fromFunction(
      LiveImpl(_, _)
    )

  private case class LiveImpl(serializer: ResultSerializer, resultFileOps: ResultFileOpsJson)
      extends TestResultPrinter {
    override def print[E](event: ExecutionEvent.Test[E]): ZIO[Any, Nothing, Unit] =
      resultFileOps.write(serializer.render(event), append = true).orDie
  }
}
