package zio.test

import zio.Chunk
import zio.test.render.ConsoleRenderer

object ReporterEventRenderer {
  def render(reporterEvent: ReporterEvent): Chunk[String] =
    Chunk.fromIterable(
      ConsoleRenderer
        .render(DefaultTestReporter.render(reporterEvent, false), TestAnnotationRenderer.timed)
      // TODO decide whether to keep this available somewhere for debugging
//        .map(line => s"${reporterEvent.id.id.toString.take(4)} $line")
    )

  def render(executionEvent: ExecutionEvent): Chunk[String] =
    Chunk.fromIterable(
      ConsoleRenderer
        .render(DefaultTestReporter.render(executionEvent, false), TestAnnotationRenderer.timed)
      // TODO decide whether to keep this available somewhere for debugging
      //        .map(line => s"${reporterEvent.id.id.toString.take(4)} $line")
    )
}
