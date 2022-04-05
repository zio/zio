package zio.test

import zio.Chunk
import zio.test.render.ConsoleRenderer

object ReporterEventRenderer {
  def render(executionEvent: ExecutionEvent): Chunk[String] =
    Chunk.fromIterable(
      ConsoleRenderer
        .render(DefaultTestReporter.render(executionEvent, true), TestAnnotationRenderer.timed)
    )
}
