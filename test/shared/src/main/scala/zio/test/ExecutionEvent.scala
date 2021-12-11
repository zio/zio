package zio.test

object ExecutionEvent {

  // TODO store ancestor annotations
  // TODO Calculate duration in appropriate location
  final case class Test[+E](
    labelsReversed: List[String],
    test: Either[TestFailure[E], TestSuccess],
    annotations: TestAnnotationMap,
    ancestors: List[TestSectionId],
    duration: Long = 0L,
    id: TestSectionId
  ) extends ExecutionEvent {
    def labels: List[String] = labelsReversed.reverse
  }

  final case class SectionStart(
    labelsReversed: List[String],
    id: TestSectionId,
    ancestors: List[TestSectionId]
  ) extends ExecutionEvent {
    def labels: List[String] = labelsReversed.reverse
  }

  final case class SectionEnd(
    labelsReversed: List[String],
    id: TestSectionId,
    ancestors: List[TestSectionId]
  ) extends ExecutionEvent {
    def labels: List[String] = labelsReversed.reverse
  }

  final case class RuntimeFailure[+E](
    labelsReversed: List[String],
    failure: TestFailure[E],
    ancestors: List[TestSectionId]
  ) extends ExecutionEvent {
    def labels: List[String] = labelsReversed.reverse
  }

}

sealed trait ExecutionEvent
