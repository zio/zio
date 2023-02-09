package zio.test

object ExecutionEvent {

  final case class TestStarted(
    labelsReversed: List[String],
    annotations: TestAnnotationMap,
    ancestors: List[SuiteId],
    id: SuiteId,
    fullyQualifiedName: String
  ) extends ExecutionEvent {
    val labels: List[String] = labelsReversed.reverse
  }

  final case class Test[+E](
    labelsReversed: List[String],
    test: Either[TestFailure[E], TestSuccess],
    annotations: TestAnnotationMap,
    ancestors: List[SuiteId],
    duration: Long,
    id: SuiteId,
    fullyQualifiedName: String
  ) extends ExecutionEvent {
    val labels: List[String] = labelsReversed.reverse
  }

  final case class SectionStart(
    labelsReversed: List[String],
    id: SuiteId,
    ancestors: List[SuiteId]
  ) extends ExecutionEvent {
    val labels: List[String] = labelsReversed.reverse
  }

  final case class SectionEnd(
    labelsReversed: List[String],
    id: SuiteId,
    ancestors: List[SuiteId]
  ) extends ExecutionEvent {
    val labels: List[String] = labelsReversed.reverse
  }

  final case class TopLevelFlush(id: SuiteId) extends ExecutionEvent {
    val labels: List[String]     = List.empty
    val ancestors: List[SuiteId] = List.empty
  }

  final case class RuntimeFailure[+E](
    id: SuiteId,
    labelsReversed: List[String],
    failure: TestFailure[E],
    ancestors: List[SuiteId]
  ) extends ExecutionEvent {
    val labels: List[String] = labelsReversed.reverse
  }

}

sealed trait ExecutionEvent {
  val id: SuiteId
  val ancestors: List[SuiteId]
  val labels: List[String]
}
