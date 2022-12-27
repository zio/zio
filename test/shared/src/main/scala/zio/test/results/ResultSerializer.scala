package zio.test.results
import zio.{ULayer, ZLayer}
import zio.test.ExecutionEvent
import zio.test.{ExecutionEvent, TestAnnotationMap, TestAnnotationRenderer, TestFailure, TestSuccess}

trait ResultSerializer {
  def render(executionEvent: ExecutionEvent): String
}

object ResultSerializer {
  val live: ULayer[ResultSerializer] = ZLayer.succeed(Json)

  object Json extends ResultSerializer {
    def render(executionEvent: ExecutionEvent): String = executionEvent match {
      case ExecutionEvent.Test(labelsReversed, test, annotations, ancestors, duration, id, fullyQualifiedName) =>
        s"""
           |    {
           |       "name" : "$fullyQualifiedName/${labelsReversed.reverse.mkString("/")}",
           |       "status" : "${jsonify(test)}",
           |       "durationMillis" : "${duration}",
           |       "annotations" : "${jsonify(annotations)}",
           |       "fullyQualifiedClassName" : "$fullyQualifiedName",
           |       "labels" : ["${labelsReversed.reverse.mkString("\", \"")}"],
           |    },""".stripMargin
      case ExecutionEvent.SectionStart(labelsReversed, id, ancestors) =>
        ""
      case ExecutionEvent.SectionEnd(labelsReversed, id, ancestors) =>
        // TODO Deal with trailing commas
        ""
      case ExecutionEvent.TopLevelFlush(id) => "TODO TopLevelFlush"
      case ExecutionEvent.RuntimeFailure(id, labelsReversed, failure, ancestors) =>
        "TODO RuntimeFailure"
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
