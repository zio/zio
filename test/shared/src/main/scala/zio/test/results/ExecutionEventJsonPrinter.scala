package zio.test.results

import zio.test._
import zio._

/**
 * Determines test results are written for later analysis. TODO Figure out what
 * happens when we are cross building. I'm worried the artifacts get
 * overwritten.
 */
private[test] object ExecutionEventJsonPrinter {
  val live: ZLayer[ResultSerializer with ResultFileOpsJson, Nothing, Live] =
    ZLayer.fromFunction(ExecutionEventJsonPrinter.Live.apply _)

  case class Live(serializer: ResultSerializer, resultFileOps: ResultFileOpsJson) extends ExecutionEventPrinter {
    override def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit] =
      resultFileOps.write(serializer.render(event), append = true).orDie

  }
}
