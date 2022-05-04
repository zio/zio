package zio.test

import zio.Chunk
import zio.test.render.{ConsoleRenderer, IntelliJRenderer}

trait ReporterEventRenderer {
  def render(event: ExecutionEvent): Chunk[String]
}
object ReporterEventRenderer {
  object ConsoleEventRenderer extends ReporterEventRenderer {
    override def render(executionEvent: ExecutionEvent): Chunk[String] =
      Chunk.fromIterable(
        ConsoleRenderer
          .render(DefaultTestReporter.render(executionEvent, includeCause = true), TestAnnotationRenderer.timed)
      )
  }

  object IntelliJEventRenderer extends ReporterEventRenderer {
    override def render(event: ExecutionEvent): Chunk[String] =
      Chunk.fromIterable(
        IntelliJRenderer
          .render(event, includeCause = false)
      )
  }
}
