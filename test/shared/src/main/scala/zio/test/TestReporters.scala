package zio.test

import zio.{Ref, ZIO, ZTraceElement}

object TestReporters {
  val make: ZIO[Any, Nothing, TestReporters] =
    Ref.make(List.empty[TestSectionId]).map(TestReporters(_))
}

case class TestReporters(testIds: Ref[List[TestSectionId]]) {

  def attemptToGetTalkingStickZ(sectionId: TestSectionId, ancestors: List[TestSectionId]) =
    testIds.updateSome {
      case Nil =>
        List(sectionId)

      case writers if ancestors.nonEmpty && writers.head == ancestors.head =>
        sectionId :: writers
    }

  // TODO Consider more domain-specific version of `UUID`
  // TODO Consider returning non-printed `List[String]` as the result
  def useTalkingStickIAmTheHolder(
    id: TestSectionId,
    behaviorIfAvailable: ZIO[ExecutionEventSink with TestLogger, Nothing, Unit],
    fallback: ZIO[ExecutionEventSink with TestLogger, Nothing, Unit]
  )(implicit
    trace: ZTraceElement
  ): ZIO[ExecutionEventSink with TestLogger, Nothing, Unit] =
    for {
      initialTalker <- testIds.get.map(_.head)
      _ <-
        if (initialTalker == id)
          behaviorIfAvailable
        else
          fallback
    } yield ()

  def relinquishTalkingStick(sectionId: TestSectionId) =
    testIds.updateSome {
      case head :: tail if head == sectionId =>
        tail
    }

}
