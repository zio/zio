package zio.test

import zio.{Chunk, Ref, ZIO, ZLayer}

trait ExecutionEventPrinter {
  def print(event: ExecutionEvent): ZIO[TestLogger, Nothing, Unit]
}

object ExecutionEventPrinter {
  def live: ZLayer[Any, Nothing, ExecutionEventPrinter] =
    ZLayer.succeed(Live)

  def print(event: ExecutionEvent): ZIO[ExecutionEventPrinter with TestLogger, Nothing, Unit] = {
    ZIO.serviceWithZIO[ExecutionEventPrinter](_.print(event))
  }

  object Live extends ExecutionEventPrinter {
    override def print(event: ExecutionEvent): ZIO[TestLogger, Nothing, Unit] = {
      TestLogger.logLine(
        ReporterEventRenderer.render(event).mkString("\n")
      )
    }
  }
}

trait TestOutput {

  /**
   * Does not necessarily print immediately. Might queue for later, sensible
   * output.
   */
  def print(
    executionEvent: ExecutionEvent
  ): ZIO[ExecutionEventSink with ExecutionEventPrinter with TestLogger, Nothing, Unit]
}

object TestOutput {
  val live: ZLayer[Any, Nothing, TestOutput] =
    ZLayer.fromZIO(
      TestOutputLive.make
    )

  /**
   * Guarantees:
   * - Everything at or below a specific suite level will be printed contiguously
   * - Everything will be printed, as long as required SectionEnd events have been passed in
   *
   * Not guaranteed:
   * - Ordering within a suite
   *
   */
  def print(
    executionEvent: ExecutionEvent
  ): ZIO[TestOutput with ExecutionEventSink with ExecutionEventPrinter with TestLogger, Nothing, Unit] =
    ZIO.serviceWithZIO[TestOutput](_.print(executionEvent))

  case class TestOutputLive(
    output: Ref[Map[SuiteId, Chunk[ExecutionEvent]]],
    reporters: TestReporters
  ) extends TestOutput {

    private def getAndRemoveSectionOutput(id: SuiteId) =
      output
        .getAndUpdate(initial => updatedWith(initial, id)(_ => None))
        .map(_.getOrElse(id, Chunk.empty))

    def print(
      executionEvent: ExecutionEvent
    ): ZIO[ExecutionEventSink with ExecutionEventPrinter with TestLogger, Nothing, Unit] =
      executionEvent match {
        case end: ExecutionEvent.SectionEnd =>
          printOrFlush(end)
        case other =>
          printOrQueue(other)
      }

    private def printOrFlush(
      end: ExecutionEvent.SectionEnd
    ): ZIO[ExecutionEventSink with ExecutionEventPrinter with TestLogger, Nothing, Unit] =
      for {
        suiteIsPrinting <- reporters.attemptToGetPrintingControl(end.id, end.ancestors)
        _               <- appendToSectionContents(end.id, Chunk(end))
        sectionOutput   <- getAndRemoveSectionOutput(end.id)
        _ <-
          if (suiteIsPrinting)
            printToConsole(sectionOutput)
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

    private def printOrQueue(
      reporterEvent: ExecutionEvent
    ): ZIO[ExecutionEventSink with ExecutionEventPrinter with TestLogger, Nothing, Unit] =
      for {
//        _ <- ZIO.debug("printOrQueue.reporterEvent: " + reporterEvent)
        _               <- appendToSectionContents(reporterEvent.id, Chunk(reporterEvent))
        suiteIsPrinting <- reporters.attemptToGetPrintingControl(reporterEvent.id, reporterEvent.ancestors)
        _ <- ZIO.when(suiteIsPrinting)(
               for {
                 currentOutput <- getAndRemoveSectionOutput(reporterEvent.id)
                 _             <- printToConsole(currentOutput)
               } yield ()
             )
      } yield ()

    private def printToConsole(events: Chunk[ExecutionEvent]) =
      ZIO.foreachDiscard(events) { event =>
        ExecutionEventPrinter.print(event)
      }

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
