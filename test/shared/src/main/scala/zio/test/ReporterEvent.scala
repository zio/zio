package zio.test

import zio.Chunk

import java.util.UUID

sealed trait ReporterEvent
case class SectionState(results: Chunk[ExecutionEvent.Test[_]]) extends ReporterEvent
case class Failure[E](
                       labelsReversed: List[String],
                       failure: TestFailure[E],
                       ancestors: List[UUID]
                     ) extends ReporterEvent

