package zio.test.results

import zio._
import zio.test._

private[test] object ResultPrinterJson {
  def live(jsonResultPath: String): ZLayer[Any, Nothing, ResultPrinter] =
    ZLayer.make[ResultPrinter](
      ResultSerializer.live,
      ResultFileOps.live(jsonResultPath),
      ZLayer.fromFunction(
        LiveImpl(_, _)
      )
    )

  private case class LiveImpl(serializer: ResultSerializer, resultFileOps: ResultFileOps) extends ResultPrinter {
    override def print[E](event: ExecutionEvent.Test[E]): ZIO[Any, Nothing, Unit] =
      resultFileOps.write(serializer.render(event), append = true).orDie
  }
}
