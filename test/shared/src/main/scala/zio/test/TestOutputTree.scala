package zio.test

import zio.test.render.ConsoleRenderer
import zio.{Chunk, Ref, ZIO, ZLayer}

trait StreamingTestOutput {

  def printOrSendOutputToParent(
    id: TestSectionId,
    ancestors: List[TestSectionId],
    talkers: TestReporters
  ): ZIO[ExecutionEventSink with TestLogger, Nothing, Unit]

  def printOrQueue(
    id: TestSectionId,
    ancestors: List[TestSectionId],
    talkers: TestReporters,
    reporterEvent: ReporterEvent
  ): ZIO[ExecutionEventSink with TestLogger, Nothing, Unit]

}

// Used as a baseline to compare against
case class DumbStreamer() extends StreamingTestOutput {
  override def printOrSendOutputToParent(
    id: TestSectionId,
    ancestors: List[TestSectionId],
    talkers: TestReporters
  ): ZIO[ExecutionEventSink with TestLogger, Nothing, Unit] =
    ZIO.unit

  override def printOrQueue(
    id: TestSectionId,
    ancestors: List[TestSectionId],
    talkers: TestReporters,
    reporterEvent: ReporterEvent
  ): ZIO[ExecutionEventSink with TestLogger, Nothing, Unit] =
    ZIO.debug(
      ReporterEventRenderer.render(id, reporterEvent)
    )

}

object StreamingTestOutput {
  val live: ZLayer[Any, Nothing, StreamingTestOutput] =
    TestOutputTree.make.toLayer >>> ZLayer.fromZIO(
      TestOutputTree.make
    )

  def printOrSendOutputToParent(
    id: TestSectionId,
    ancestors: List[TestSectionId],
    talkers: TestReporters
  ): ZIO[StreamingTestOutput with ExecutionEventSink with TestLogger, Nothing, Unit] =
    ZIO.serviceWithZIO[StreamingTestOutput](_.printOrSendOutputToParent(id, ancestors, talkers))

  def printOrQueue(
    id: TestSectionId,
    ancestors: List[TestSectionId],
    talkers: TestReporters,
    reporterEvent: ReporterEvent
  ): ZIO[StreamingTestOutput with ExecutionEventSink with TestLogger, Nothing, Unit] =
    ZIO.serviceWithZIO[StreamingTestOutput](_.printOrQueue(id, ancestors, talkers, reporterEvent))

}

case class TestOutputTree(
  output: Ref[Map[TestSectionId, Chunk[String]]]
) extends StreamingTestOutput {

  private def getAndRemoveSectionOutput(id: TestSectionId) =
    output
      .getAndUpdate(initial => updatedWith(initial, id)(_ => None))
      .map(_.getOrElse(id, Chunk.empty))

  // TODO Scrutinize this method
  def printOrSendOutputToParent(
    id: TestSectionId,
    ancestors: List[TestSectionId],
    talkers: TestReporters
  ): ZIO[ExecutionEventSink with TestLogger, Nothing, Unit] =
    for {
      sectionOutput <- getAndRemoveSectionOutput(id)
      _ <-
        talkers.useTalkingStickIAmTheHolder(
          id,
          behaviorIfAvailable = ZIO.foreachDiscard(sectionOutput) { subLine =>
            TestLogger.logLine(subLine)
          },
          fallback =
            if (sectionOutput.nonEmpty)
              appendToSectionContents(ancestors.head, sectionOutput)
            else ZIO.unit
        )
    } yield ()

  private def appendToSectionContents(id: TestSectionId, content: Chunk[String]) =
    output.update { outputNow =>
      updatedWith(outputNow, id)(previousSectionOutput =>
        Some(previousSectionOutput.map(old => old ++ content).getOrElse(content))
      )
    }

  def printOrQueue(
    id: TestSectionId,
    ancestors: List[TestSectionId],
    talkers: TestReporters,
    reporterEvent: ReporterEvent
  ): ZIO[ExecutionEventSink with TestLogger, Nothing, Unit] = {
    // TODO Rendering the reporterEvent should happen *before* it comes into this class
    val content =
      ReporterEventRenderer.render(id, reporterEvent)

    for {
      _ <- appendToSectionContents(id, content)
      _ <-
        talkers.useTalkingStickIAmTheHolder(
          id,
          for {
            currentOutput <- getAndRemoveSectionOutput(id)
            _ <- ZIO.foreachDiscard(currentOutput) { line =>
                   TestLogger.logLine(line)
                 }
          } yield (),
          ZIO.unit
        )
    } yield ()
  }

  // We need this helper to run on Scala 2.11
  private def updatedWith[TestSectionId](initial: Map[TestSectionId, Chunk[String]], key: TestSectionId)(
    remappingFunction: Option[Chunk[String]] => Option[Chunk[String]]
  ): Map[TestSectionId, Chunk[String]] = {
    val previousValue = initial.get(key)
    val nextValue     = remappingFunction(previousValue)
    (previousValue, nextValue) match {
      case (None, None)    => initial
      case (Some(_), None) => initial - key
      case (_, Some(v))    => initial.updated(key, v)
    }
  }
}

object TestOutputTree {

  def make: ZIO[Any, Nothing, StreamingTestOutput] = for {
    output <- Ref.make[Map[TestSectionId, Chunk[String]]](Map.empty)
  } yield TestOutputTree(output)

}
