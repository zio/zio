package zio.test

import zio.Chunk

import java.util.UUID

sealed trait ReporterEvent {
  val id: TestSectionId
}
case class SectionHeader(
  labelsReversed: List[String],
  id: TestSectionId
) extends ReporterEvent

case class SectionState(results: Chunk[ExecutionEvent.Test[_]], id: TestSectionId) extends ReporterEvent {
  def containsFailures: Boolean =
    results.exists(_.test.isLeft)
}

case class RuntimeFailure[E](
  labelsReversed: List[String],
  failure: TestFailure[E],
  ancestors: List[UUID],
  id: TestSectionId
) extends ReporterEvent
