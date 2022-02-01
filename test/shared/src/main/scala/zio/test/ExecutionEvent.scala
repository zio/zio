package zio.test

import java.util.UUID

object ExecutionEvent {

  final case class Test[+E](
    labelsReversed: List[String],
    test: Either[TestFailure[E], TestSuccess],
    annotations: TestAnnotationMap,
    ancestors: List[UUID]
  ) extends ExecutionEvent {
    def labels: List[String] = labelsReversed.reverse
  }

  final case class SectionStart(
    labelsReversed: List[String],
    id: UUID,
    ancestors: List[UUID]
  ) extends ExecutionEvent {
    def labels: List[String] = labelsReversed.reverse
  }

  final case class SectionEnd(
    labelsReversed: List[String],
    id: UUID,
    ancestors: List[UUID]
  ) extends ExecutionEvent {
    def labels: List[String] = labelsReversed.reverse
  }

  final case class Failure[+E](
    labelsReversed: List[String],
    failure: TestFailure[E],
    ancestors: List[UUID]
  ) extends ExecutionEvent {
    def labels: List[String] = labelsReversed.reverse
  }

}

sealed trait ExecutionEvent
