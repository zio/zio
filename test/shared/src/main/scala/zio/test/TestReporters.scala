package zio.test

import zio.{Ref, ZIO, ZTraceElement}

object TestReporters {
  val make: ZIO[Any, Nothing, TestReporters] =
    Ref.make(List.empty[TestSectionId]).map(TestReporters(_))
}

case class TestReporters(testIds: Ref[List[TestSectionId]]) {

  def attemptToGetPrintingControl(sectionId: TestSectionId, ancestors: List[TestSectionId]): ZIO[Any, Nothing, Unit] =
    testIds.updateSome {
      case Nil =>
        List(sectionId)

      case writers if ancestors.nonEmpty && writers.head == ancestors.head =>
        sectionId :: writers
    }

  def printOrElse(
    id: TestSectionId,
    print: ZIO[ExecutionEventSink with TestLogger, Nothing, Unit],
    fallback: ZIO[ExecutionEventSink with TestLogger, Nothing, Unit]
  )(implicit
    trace: ZTraceElement
  ): ZIO[ExecutionEventSink with TestLogger, Nothing, Unit] =
    for {
      initialTalker <- testIds.get.map(_.head)
      _ <-
        if (initialTalker == id)
          print
        else
          fallback
    } yield ()

  def relinquishPrintingControl(sectionId: TestSectionId) =
    testIds.updateSome {
      case head :: tail if head == sectionId =>
        tail
    }

}
