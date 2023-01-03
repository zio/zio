package zio.test.results

import zio.test._
import zio.{ZIO, ZLayer}

/**
 * Determines test results are written for later analysis. TODO Figure out what
 * happens when we are cross building. I'm worried the artifacts get
 * overwritten.
 */
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
