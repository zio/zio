package zio.test.results

import zio._
import zio.test._

trait ResultSerializer {
  def render[E](executionEvent: ExecutionEvent.Test[E]): String
}

object ResultSerializer {
  val live: ULayer[ResultSerializer] = ZLayer.succeed(Json)

  object Json extends ResultSerializer {
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
