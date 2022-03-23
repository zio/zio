package zio.test

object ExecutionEvent {

  // TODO Do we need to do something else for ancestor annotations?
  final case class Test[+E](
    labelsReversed: List[String],
    test: Either[TestFailure[E], TestSuccess],
    annotations: TestAnnotationMap,
    ancestors: List[TestSectionId],
    // TODO Calculate duration in appropriate location
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
    id: TestSectionId,
    labelsReversed: List[String],
    failure: TestFailure[E],
    ancestors: List[TestSectionId]
  ) extends ExecutionEvent {
    def labels: List[String] = labelsReversed.reverse
  }

}

sealed trait ExecutionEvent
