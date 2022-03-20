package zio.test

import zio.Chunk
import zio.test.render.ConsoleRenderer

object ReporterEventRenderer {
  def render(reporterEvent: ReporterEvent): Chunk[String] =
    Chunk.fromIterable(
      ConsoleRenderer
        .render(DefaultTestReporter.render(reporterEvent, false), TestAnnotationRenderer.timed)
        .map(line => s"${reporterEvent.id.id.toString.take(4)} $line")
    )
}
