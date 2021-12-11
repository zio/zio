package zio.test

import zio.Chunk
import zio.test.render.ConsoleRenderer

object ReporterEventRenderer {
  def render(sectionId: TestSectionId, reporterEvent: ReporterEvent) =
    Chunk.fromIterable(
      ConsoleRenderer
        .render(DefaultTestReporter.render(reporterEvent, false), TestAnnotationRenderer.timed)
        .map(line => s"${sectionId.id.toString.take(4)} $line")
    )
}
