package zio.test

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Chunk, Trace}
import zio.test.render.{ConsoleRenderer, IntelliJRenderer}

trait ReporterEventRenderer {
  def render(event: ExecutionEvent)(implicit trace: Trace): Chunk[String]
}
object ReporterEventRenderer {
  object ConsoleEventRenderer extends ReporterEventRenderer {
    override def render(executionEvent: ExecutionEvent)(implicit trace: Trace): Chunk[String] =
      Chunk.fromIterable(
        ConsoleRenderer
          .render(executionEvent, includeCause = true)
      )
  }

  object IntelliJEventRenderer extends ReporterEventRenderer {
    override def render(executionEvent: ExecutionEvent)(implicit trace: Trace): Chunk[String] =
      Chunk.fromIterable(
        IntelliJRenderer
          .render(executionEvent, includeCause = true)
      )
  }
}
