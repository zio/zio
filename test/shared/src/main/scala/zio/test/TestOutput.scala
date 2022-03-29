package zio.test

import zio.{Chunk, Ref, ZIO, ZLayer}

trait TestOutput {

  // TODO I think these can just be one method that takes an `ExecutionEvent`
  def printOrFlush(
      end: ExecutionEvent.SectionEnd
  ): ZIO[ExecutionEventSink with TestLogger, Nothing, Unit]

  def printOrQueue(
    reporterEvent: ExecutionEvent
  ): ZIO[ExecutionEventSink with TestLogger, Nothing, Unit]

}

object TestOutput {
  val live: ZLayer[Any, Nothing, TestOutput] =
    ZLayer.fromZIO(
      TestOutputLive.make
    )

  def printOrFlush(
      end: ExecutionEvent.SectionEnd
  ): ZIO[TestOutput with ExecutionEventSink with TestLogger, Nothing, Unit] =
    ZIO.serviceWithZIO[TestOutput](_.printOrFlush(end))

  def printOrQueue(
    reporterEvent: ExecutionEvent
  ): ZIO[TestOutput with ExecutionEventSink with TestLogger, Nothing, Unit] =
    ZIO.serviceWithZIO[TestOutput](_.printOrQueue(reporterEvent))

  case class TestOutputLive(
    output: Ref[Map[SuiteId, Chunk[ExecutionEvent]]],
    reporters: TestReporters
  ) extends TestOutput {

    private def getAndRemoveSectionOutput(id: SuiteId) =
      output
        .getAndUpdate(initial => updatedWith(initial, id)(_ => None))
        .map(_.getOrElse(id, Chunk.empty))

    // TODO This parameter indicates that everything should just be passed into a single method
    def printOrFlush(
      end: ExecutionEvent.SectionEnd
    ): ZIO[ExecutionEventSink with TestLogger, Nothing, Unit] =
      for {
        suiteIsPrinting <- reporters.attemptToGetPrintingControl(end.id, end.ancestors)
        sectionOutput   <- getAndRemoveSectionOutput(end.id)
        _ <-
          if (suiteIsPrinting)
            ZIO.foreachDiscard(sectionOutput) { subLine =>
              TestLogger.logLine(
                ReporterEventRenderer.render(subLine).mkString("\n")
              )
            }
          else {
            end.ancestors.headOption match {
              case Some(parentId) =>
                appendToSectionContents(parentId, sectionOutput)
              case None =>
                ZIO.dieMessage("Suite tried to send its output to a nonexistent parent")
            }
          }

        _ <- reporters.relinquishPrintingControl(end.id)
      } yield ()

    def printOrQueue(
      reporterEvent: ExecutionEvent
    ): ZIO[ExecutionEventSink with TestLogger, Nothing, Unit] =
      for {
        _               <- appendToSectionContents(reporterEvent.id, Chunk(reporterEvent))
        suiteIsPrinting <- reporters.attemptToGetPrintingControl(reporterEvent.id, reporterEvent.ancestors)
        _ <- ZIO.when(suiteIsPrinting)(
               for {
                 currentOutput <- getAndRemoveSectionOutput(reporterEvent.id)
                 _ <- ZIO.foreachDiscard(currentOutput) { event =>
                        TestLogger.logLine(
                          ReporterEventRenderer.render(event).mkString("\n")
                        )
                      }
               } yield ()
             )
      } yield ()

    private def appendToSectionContents(id: SuiteId, content: Chunk[ExecutionEvent]) =
      output.update { outputNow =>
        updatedWith(outputNow, id)(previousSectionOutput =>
          Some(previousSectionOutput.map(old => old ++ content).getOrElse(content))
        )
      }

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
