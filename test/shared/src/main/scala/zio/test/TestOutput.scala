package zio.test

import zio.{Chunk, Ref, ZIO, ZLayer}

trait TestOutput {

  def printOrFlush(
    id: SuiteId,
    ancestors: List[SuiteId]
  ): ZIO[ExecutionEventSink with TestLogger, Nothing, Unit]

  def printOrQueue(
    id: SuiteId,
    ancestors: List[SuiteId],
    reporterEvent: ExecutionEvent
  ): ZIO[ExecutionEventSink with TestLogger, Nothing, Unit]

}

object TestOutput {
  val live: ZLayer[Any, Nothing, TestOutput] =
    ZLayer.fromZIO(
      TestOutputLive.make
    )

  def printOrFlush(
    id: SuiteId,
    ancestors: List[SuiteId]
  ): ZIO[TestOutput with ExecutionEventSink with TestLogger, Nothing, Unit] =
    ZIO.serviceWithZIO[TestOutput](_.printOrFlush(id, ancestors))

  def printOrQueue(
    id: SuiteId,
    ancestors: List[SuiteId],
    reporterEvent: ExecutionEvent
  ): ZIO[TestOutput with ExecutionEventSink with TestLogger, Nothing, Unit] =
    ZIO.serviceWithZIO[TestOutput](_.printOrQueue(id, ancestors, reporterEvent))

  case class TestOutputLive(
    output: Ref[Map[SuiteId, Chunk[ExecutionEvent]]],
    reporters: TestReporters
  ) extends TestOutput {

    private def getAndRemoveSectionOutput(id: SuiteId) =
      output
        .getAndUpdate(initial => updatedWith(initial, id)(_ => None))
        .map(_.getOrElse(id, Chunk.empty))

    def printOrFlush(
      id: SuiteId,
      ancestors: List[SuiteId]
    ): ZIO[ExecutionEventSink with TestLogger, Nothing, Unit] =
      for {
        suiteIsPrinting <- reporters.attemptToGetPrintingControl(id, ancestors)
        sectionOutput   <- getAndRemoveSectionOutput(id)
        _ <-
          if (suiteIsPrinting)
            ZIO.foreachDiscard(sectionOutput) { subLine =>
              TestLogger.logLine(
                ReporterEventRenderer.render(subLine).mkString("\n")
              )
            }
          else {
            ancestors.headOption match {
              case Some(parentId) =>
                appendToSectionContents(parentId, sectionOutput)
              case None =>
                ZIO.dieMessage("Suite tried to send its output to a nonexistent parent")
            }
          }

        _ <- reporters.relinquishPrintingControl(id)
      } yield ()

    private def appendToSectionContents(id: SuiteId, content: Chunk[ExecutionEvent]) =
      output.update { outputNow =>
        updatedWith(outputNow, id)(previousSectionOutput =>
          Some(previousSectionOutput.map(old => old ++ content).getOrElse(content))
        )
      }

    def printOrQueue(
      id: SuiteId,
      ancestors: List[SuiteId],
      reporterEvent: ExecutionEvent
    ): ZIO[ExecutionEventSink with TestLogger, Nothing, Unit] =
      for {
        _               <- appendToSectionContents(id, Chunk(reporterEvent))
        suiteIsPrinting <- reporters.attemptToGetPrintingControl(id, ancestors)
        _ <- ZIO.when(suiteIsPrinting)(
               for {
                 currentOutput <- getAndRemoveSectionOutput(id)
                 _ <- ZIO.foreachDiscard(currentOutput) { line =>
                        TestLogger.logLine(
                          ReporterEventRenderer.render(line).mkString("\n")
                        )
                      }
               } yield ()
             )
      } yield ()

    // We need this helper to run on Scala 2.11
    private def updatedWith(initial: Map[SuiteId, Chunk[ExecutionEvent]], key: SuiteId)(
      remappingFunction: Option[Chunk[ExecutionEvent]] => Option[Chunk[ExecutionEvent]]
    ): Map[SuiteId, Chunk[ExecutionEvent]] = {
      val previousValue = initial.get(key)
      val nextValue     = remappingFunction(previousValue)
      (previousValue, nextValue) match {
        case (None, None)    => initial
        case (Some(_), None) => initial - key
        case (_, Some(v))    => initial.updated(key, v)
      }
    }

  }

  object TestOutputLive {

    def make: ZIO[Any, Nothing, TestOutput] = for {
      talkers <- TestReporters.make
      output  <- Ref.make[Map[SuiteId, Chunk[ExecutionEvent]]](Map.empty)
    } yield TestOutputLive(output, talkers)

  }
}
