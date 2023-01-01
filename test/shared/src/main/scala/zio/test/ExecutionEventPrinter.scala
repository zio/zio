package zio.test

import zio.test.results.ExecutionEventJsonPrinter
import zio.{ZEnvironment, ZIO, ZLayer}

trait ExecutionEventPrinter {
  def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit]
}
object ExecutionEventPrinter {
  def live(renderer: ReporterEventRenderer): ZLayer[ExecutionEventJsonPrinter.Live, Nothing, ExecutionEventPrinter] = {
    for {
      jsonPrinter <- ZLayer.service[ExecutionEventJsonPrinter.Live]
    } yield ZEnvironment[ExecutionEventPrinter](jsonPrinter.get)
  }

  // TODO Where to put environment variable check?
  case class Composite(console: ExecutionEventPrinter.Live, file: ExecutionEventJsonPrinter.Live) extends ExecutionEventPrinter {
    override def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit] =
      console.print(event) *> file.print(event)
  }

  object Composite {
    val live: ZLayer[ExecutionEventPrinter.Live with ExecutionEventJsonPrinter.Live, Nothing, Composite] =
      ZLayer.fromFunction(Composite.apply _)
  }

  def liveOg(renderer: ReporterEventRenderer): ZLayer[TestLogger, Nothing, ExecutionEventPrinter.Live] =
    ZLayer {
      for {
        testLogger <- ZIO.service[TestLogger]
      } yield Live(testLogger, renderer)
    }

  def print(event: ExecutionEvent): ZIO[ExecutionEventPrinter, Nothing, Unit] =
    ZIO.serviceWithZIO(_.print(event))

  case class Live(logger: TestLogger, eventRenderer: ReporterEventRenderer) extends ExecutionEventPrinter {
    override def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit] = {
      val rendered = eventRenderer.render(event)
      ZIO
        .when(rendered.nonEmpty)(
          logger.logLine(
            rendered.mkString("\n")
          )
        )
        .unit
    }
  }

}
