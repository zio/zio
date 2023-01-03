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
        case ExecutionEvent.Test(labelsReversed, test, annotations, ancestors, duration, id, fullyQualifiedName) =>
          s"""
             |    {
             |       "name" : "$fullyQualifiedName/${labelsReversed.reverse
            .map(s => s.replace("/", "\\/"))
            .mkString("/")}",
             |       "status" : "${jsonify(test)}",
             |       "durationMillis" : "$duration",
             |       "annotations" : "${jsonify(annotations)}",
             |       "fullyQualifiedClassName" : "$fullyQualifiedName",
             |       "labels" : ["${labelsReversed.reverse.map(s => s.replace("/", "\\/")).mkString("\", \"")}"]
             |    },""".stripMargin
      }

    private def jsonify[E](test: Either[TestFailure[E], TestSuccess]): String =
      test match {
        case Left(value) =>
          "Failure"
        case Right(value) =>
          "Success"
      }

    private[results] def jsonify(testAnnotationMap: TestAnnotationMap): String =
      TestAnnotationRenderer.default
        .run(List.empty, testAnnotationMap)
        .map(s => s.replace("\"", "\\\""))
        .mkString(" : ")

  }
}
