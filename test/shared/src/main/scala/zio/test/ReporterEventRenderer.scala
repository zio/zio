package zio.test

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Chunk, Trace}
import zio.test.render.{ConsoleRenderer, IntelliJRenderer}

/**
 * Renders an [[ExecutionEvent]] into the text that the test runner prints. The
 * `-renderer` command-line argument selects the renderer.
 */
trait ReporterEventRenderer {

  /**
   * Returns the text for `event`. An empty chunk means that the event prints
   * nothing.
   */
  def render(event: ExecutionEvent)(implicit trace: Trace): Chunk[String]
}

/**
 * The predefined [[ReporterEventRenderer]] values.
 */
object ReporterEventRenderer {

  /**
   * The renderer for the console, used unless `-renderer intellij` is given.
   */
  object ConsoleEventRenderer extends ReporterEventRenderer {
    override def render(executionEvent: ExecutionEvent)(implicit trace: Trace): Chunk[String] =
      Chunk.fromIterable(
        ConsoleRenderer
          .render(executionEvent, includeCause = true)
      )
  }

  /**
   * The renderer for IntelliJ IDEA's test runner, selected with `-renderer
   * intellij`.
   */
  object IntelliJEventRenderer extends ReporterEventRenderer {
    override def render(executionEvent: ExecutionEvent)(implicit trace: Trace): Chunk[String] =
      Chunk.fromIterable(
        IntelliJRenderer
          .render(executionEvent, includeCause = true)
      )
  }
}
