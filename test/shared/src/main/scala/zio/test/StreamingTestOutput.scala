package zio.test

import zio.{Chunk, Ref, ZIO, ZLayer}

trait StreamingTestOutput {

  def printOrFlush(
    id: TestSectionId,
    ancestors: List[TestSectionId],
  ): ZIO[ExecutionEventSink with TestLogger, Nothing, Unit]

  def printOrQueue(
    id: TestSectionId,
    ancestors: List[TestSectionId],
    reporterEvent: ReporterEvent
  ): ZIO[ExecutionEventSink with TestLogger, Nothing, Unit]

  def relinquishPrintingControl(sectionId: TestSectionId): ZIO[Any, Nothing, Unit]
  def attemptToGetPrintingControl(sectionId: TestSectionId, ancestors: List[TestSectionId]): ZIO[Any, Nothing, Unit]
}

// Used as a baseline to compare against
// TODO Move to test dir
case class BrokenStreamer() extends StreamingTestOutput {
  override def printOrFlush(
    id: TestSectionId,
    ancestors: List[TestSectionId],
  ): ZIO[ExecutionEventSink with TestLogger, Nothing, Unit] =
    ZIO.unit

  override def printOrQueue(
    id: TestSectionId,
    ancestors: List[TestSectionId],
    reporterEvent: ReporterEvent
  ): ZIO[ExecutionEventSink with TestLogger, Nothing, Unit] =
    ZIO.debug(
      ReporterEventRenderer.render(reporterEvent)
    )

  def relinquishPrintingControl(sectionId: TestSectionId): ZIO[Any, Nothing, Unit] = ZIO.unit

  def attemptToGetPrintingControl(sectionId: TestSectionId, ancestors: List[TestSectionId]): ZIO[Any, Nothing, Unit] = ZIO.unit
}

object StreamingTestOutput {
  val live: ZLayer[Any, Nothing, StreamingTestOutput] =
    TestOutputTree.make.toLayer >>> ZLayer.fromZIO(
      TestOutputTree.make
    )

  def printOrFlush(
    id: TestSectionId,
    ancestors: List[TestSectionId],
  ): ZIO[StreamingTestOutput with ExecutionEventSink with TestLogger, Nothing, Unit] =
    ZIO.serviceWithZIO[StreamingTestOutput](_.printOrFlush(id, ancestors))

  def printOrQueue(
    id: TestSectionId,
    ancestors: List[TestSectionId],
    reporterEvent: ReporterEvent
  ): ZIO[StreamingTestOutput with ExecutionEventSink with TestLogger, Nothing, Unit] =
    ZIO.serviceWithZIO[StreamingTestOutput](_.printOrQueue(id, ancestors, reporterEvent))

  def relinquishPrintingControl(
                    id: TestSectionId,
                  ): ZIO[StreamingTestOutput with ExecutionEventSink with TestLogger, Nothing, Unit] =
    ZIO.serviceWithZIO[StreamingTestOutput](_.relinquishPrintingControl(id))

  def attemptToGetPrintingControl(
                                 id: TestSectionId,
                                 ancestors: List[TestSectionId],
                               ): ZIO[StreamingTestOutput with ExecutionEventSink with TestLogger, Nothing, Unit] =
    ZIO.serviceWithZIO[StreamingTestOutput](_.attemptToGetPrintingControl(id, ancestors))
}

// TODO Move to separate file
case class TestOutputTree(
  output: Ref[Map[TestSectionId, Chunk[ReporterEvent]]],
  talkers: TestReporters
) extends StreamingTestOutput {

  private def getAndRemoveSectionOutput(id: TestSectionId) =
    output
      .getAndUpdate(initial => updatedWith(initial, id)(_ => None))
      .map(_.getOrElse(id, Chunk.empty))

  def printOrFlush(
    id: TestSectionId,
    ancestors: List[TestSectionId],
  ): ZIO[ExecutionEventSink with TestLogger, Nothing, Unit] =
    for {
      sectionOutput <- getAndRemoveSectionOutput(id)
      _ <-
        talkers.printOrElse(
          id,
          print = ZIO.foreachDiscard(sectionOutput) { subLine =>
            TestLogger.logLine(
              ReporterEventRenderer.render(subLine).mkString("\n")
            )
          },
          fallback =
            if (sectionOutput.nonEmpty)
              appendToSectionContents(ancestors.head, sectionOutput)
            else ZIO.unit
        )
    } yield ()

  private def appendToSectionContents(id: TestSectionId, content: Chunk[ReporterEvent]) =
    output.update { outputNow =>
      updatedWith(outputNow, id)(previousSectionOutput =>
        Some(previousSectionOutput.map(old => old ++ content).getOrElse(content))
      )
    }

  def printOrQueue(
    id: TestSectionId,
    ancestors: List[TestSectionId],
    reporterEvent: ReporterEvent
  ): ZIO[ExecutionEventSink with TestLogger, Nothing, Unit] =
    for {
      _ <- appendToSectionContents(id, Chunk(reporterEvent))
      _ <-
        talkers.printOrElse(
          id,
          for {
            currentOutput <- getAndRemoveSectionOutput(id)
            _ <- ZIO.foreachDiscard(currentOutput) { line =>
                   TestLogger.logLine(
                     ReporterEventRenderer.render(line).mkString("\n") // TODO might need to shuffle this
                   )
                 }
          } yield (),
          ZIO.unit
        )
    } yield ()

  // We need this helper to run on Scala 2.11
  private def updatedWith[TestSectionId](initial: Map[TestSectionId, Chunk[ReporterEvent]], key: TestSectionId)(
    remappingFunction: Option[Chunk[ReporterEvent]] => Option[Chunk[ReporterEvent]]
  ): Map[TestSectionId, Chunk[ReporterEvent]] = {
    val previousValue = initial.get(key)
    val nextValue     = remappingFunction(previousValue)
    (previousValue, nextValue) match {
      case (None, None)    => initial
      case (Some(_), None) => initial - key
      case (_, Some(v))    => initial.updated(key, v)
    }
  }


  def relinquishPrintingControl(sectionId: TestSectionId): ZIO[Any, Nothing, Unit] =
    talkers.relinquishPrintingControl(sectionId)


  def attemptToGetPrintingControl(sectionId: TestSectionId, ancestors: List[TestSectionId]): ZIO[Any, Nothing, Unit] =
    talkers.attemptToGetPrintingControl(sectionId, ancestors)
}

object TestOutputTree {

  def make: ZIO[Any, Nothing, StreamingTestOutput] = for {
    talkers <- TestReporters.make // TODO Use this everywhere
    output <- Ref.make[Map[TestSectionId, Chunk[ReporterEvent]]](Map.empty)
  } yield TestOutputTree(output, talkers)

}
