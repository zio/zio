package zio.test.results

import zio.test._
import zio._

object ExecutionEventJsonPrinter {
  val live: ZLayer[ResultSerializer with ResultFileOpsJson, Nothing, Live] =
      ZLayer.fromFunction(ExecutionEventJsonPrinter.Live.apply _)

  case class Live(serializer: ResultSerializer, resultFileOps: ResultFileOpsJson) extends ExecutionEventPrinter {
    override def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit] =
      resultFileOps.write(serializer.render(event), append = true).orDie

  }
}




