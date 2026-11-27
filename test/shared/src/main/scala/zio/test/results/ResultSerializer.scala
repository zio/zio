package zio.test.results

import zio._
import zio.test._

/**
 * Turns the result of a finished test into the text for the test results file, which is written on the JVM and Scala Native. `ResultPrinter.json` uses [[ResultSerializer.Json]].
 */
trait ResultSerializer {

  /** Returns the text for the finished test `executionEvent`. */
  def render[E](executionEvent: ExecutionEvent.Test[E]): String
}

/** The [[ResultSerializer]] implementations. */
object ResultSerializer {

  /** A layer that provides [[ResultSerializer.Json]]. */
  val live: ULayer[ResultSerializer] = ZLayer.succeed(Json)

  /** The [[ResultSerializer]] for the JSON results file. */
  object Json extends ResultSerializer {

    /**
     * Turns one finished test event into the text written to the JSON results
     * file, used by `ResultPrinter.json`.
     */
    def render[E](executionEvent: ExecutionEvent.Test[E]): String =
      executionEvent match {
        case ExecutionEvent.Test(labelsReversed, test, annotations, _, duration, _, fullyQualifiedName) =>
          s"""
             |    {
             |       "name" : "$fullyQualifiedName/${labelsReversed.reverse
              .map(s => s.replace("/", "\\/"))
              .mkString("/")}",
             |       "status" : "${render(test)}",
             |       "durationMillis" : "$duration",
             |       "annotations" : "${render(annotations)}",
             |       "fullyQualifiedClassName" : "$fullyQualifiedName",
             |       "labels" : ["${labelsReversed.reverse.map(s => s.replace("/", "\\/")).mkString("\", \"")}"]
             |    },""".stripMargin
      }

    private def render[E](test: Either[TestFailure[E], TestSuccess]): String =
      if (test.isRight) "Success" else "Failure"

    private[results] def render(testAnnotationMap: TestAnnotationMap): String =
      TestAnnotationRenderer.default
        .run(List.empty, testAnnotationMap)
        .map(s => s.replace("\"", "\\\""))
        .mkString(" : ")
  }

}
