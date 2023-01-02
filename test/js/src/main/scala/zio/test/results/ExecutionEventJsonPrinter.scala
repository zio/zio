package zio.test.results

import zio._
import zio.test._

/**
 * Determines test results are written for later analysis. TODO Figure out what
 * happens when we are cross building. I'm worried the artifacts get
 * overwritten.
 */
private[test] object ExecutionEventJsonPrinter {
  val live: ZLayer[ResultSerializer with ResultFileOpsJson, Nothing, Live] = {
    ZLayer.debug("Using ScalaJS, so we will not write results to file") >>>
      ZLayer.fromFunction(
        LiveImpl(_, _)
      )
  }

  object NoOp extends Live {
    override def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit] = ZIO.unit
  }

  trait Live extends ExecutionEventPrinter

  case class LiveImpl(serializer: ResultSerializer, resultFileOps: ResultFileOpsJson) extends Live {
    override def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit] =
      ZIO.unit
  }
}
