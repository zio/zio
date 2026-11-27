package zio.test.results

import zio.{ZIO, ZLayer}
import zio.test.{ExecutionEvent, TestArgs}

/**
 * Receives the result of each finished test of a run, for example to write it to a results file. The test runner passes each [[zio.test.ExecutionEvent.Test]] event of the run to it.
 */
trait ResultPrinter {

  /** Handles the finished test `event`. */
  def print[E](event: ExecutionEvent.Test[E]): ZIO[Any, Nothing, Unit]
}

/** Layers that provide a [[ResultPrinter]]. */
object ResultPrinter {

  /**
   * The same as `json(TestArgs.reportsParentDefault)`, with the default reports directory `target`.
   */
  val json: ZLayer[Any, Nothing, ResultPrinter] = json(TestArgs.reportsParentDefault)

  /**
   * A layer whose printer turns each event into text with [[ResultSerializer.Json]] for the JSON results file. The file path is `reportsParent`, then `/`, then `TestArgs.jsonResultPath` (`test-reports-zio/output.json`). The test runner passes the directory of the `-reports` test argument, or `TestArgs.reportsParentDefault` (`target`) when there is none.
   *
   * On the JVM and Scala Native, the printer keeps the entries in memory and writes the file when the layer is released, creating missing parent directories. On Scala.js, it writes no file.
   */
  def json(reportsParent: String): ZLayer[Any, Nothing, ResultPrinter] =
    ResultPrinterJson.live(s"$reportsParent/${TestArgs.jsonResultPath}")
}
