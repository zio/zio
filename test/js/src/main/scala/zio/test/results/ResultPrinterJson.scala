package zio.test.results

import zio._
import zio.test._

private[test] object ResultPrinterJson {
  val live: ZLayer[Any, Nothing, ResultPrinter] =
    ZLayer.make[ResultPrinter](
      ResultSerializer.live,
      ResultFileOps.live,
      ZLayer.fromFunction(
        LiveImpl(_, _)
      )
    )

  private case class LiveImpl(serializer: ResultSerializer, resultFileOps: ResultFileOps) extends ResultPrinter {
    override def print[E](event: ExecutionEvent.Test[E]): ZIO[Any, Nothing, Unit] =
      resultFileOps.write(serializer.render(event), append = true).orDie
  }
}
