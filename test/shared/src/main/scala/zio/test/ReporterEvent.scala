package zio.test

import zio.Chunk

import java.util.UUID

/**
 * These types contain all the information needed to print test results
 */
sealed trait ReporterEvent {
  val id: TestSectionId
}
case class SectionHeader(
  labelsReversed: List[String],
  id: TestSectionId
) extends ReporterEvent

case class SectionState(results: Chunk[ExecutionEvent.Test[_]], id: TestSectionId) extends ReporterEvent

case class RuntimeFailure[E](
  labelsReversed: List[String],
  failure: TestFailure[E],
  ancestors: List[UUID],
  id: TestSectionId
) extends ReporterEvent
