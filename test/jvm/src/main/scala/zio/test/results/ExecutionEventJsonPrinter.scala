package zio.test.results

import zio.{ZIO, ZLayer}
import zio.test._

/**
 * Determines test results are written for later analysis. TODO Figure out what
 * happens when we are cross building. I'm worried the artifacts get
 * overwritten.
 */
private[test] object ExecutionEventJsonPrinter {
  val live: ZLayer[ResultSerializer with ResultFileOpsJson, Nothing, Live] =
    ZLayer.fromFunction(
      LiveImpl(_, _)
    )

  trait Live extends ExecutionEventPrinter

  case class LiveImpl(serializer: ResultSerializer, resultFileOps: ResultFileOpsJson) extends Live {
    override def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit] =
      resultFileOps.write(serializer.render(event), append = true).orDie
  }
}
