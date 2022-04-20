package zio.test

import zio.{Chunk, Ref, ZIO, ZLayer}

trait TestOutput {

  /**
   * Does not necessarily print immediately. Might queue for later, sensible
   * output.
   */
  def print(
    executionEvent: ExecutionEvent
  ): ZIO[Any, Nothing, Unit]
}

object TestOutput {
  val live: ZLayer[ExecutionEventPrinter, Nothing, TestOutput] =
    ZLayer.fromZIO(
      for {
        _ <- ZIO.debug("Creating new TestOutput. Should only see this once.")
        executionEventPrinter <- ZIO.service[ExecutionEventPrinter].debug("ExecutionEventPrinter")
        outputLive            <- TestOutputLive.make(executionEventPrinter)
      } yield outputLive
    )

  /**
   * Guarantees:
   *   - Everything at or below a specific suite level will be printed
   *     contiguously
   *   - Everything will be printed, as long as required SectionEnd events have
   *     been passed in
   *
   * Not guaranteed:
   *   - Ordering within a suite
   */
  def print(
    executionEvent: ExecutionEvent
  ): ZIO[TestOutput, Nothing, Unit] =
    ZIO.serviceWithZIO[TestOutput](_.print(executionEvent))

  case class TestOutputLive(
    output: Ref[Map[SuiteId, Chunk[ExecutionEvent]]],
    reporters: TestReporters,
    executionEventPrinter: ExecutionEventPrinter
  ) extends TestOutput {

    private def getAndRemoveSectionOutput(id: SuiteId) =
      output
        .getAndUpdate(initial => updatedWith(initial, id)(_ => None))
        .map(_.getOrElse(id, Chunk.empty))

    def print(
      executionEvent: ExecutionEvent
    ): ZIO[Any, Nothing, Unit] = {
//      ZIO.debug("Printer in play: " + executionEventPrinter) *>
        (executionEvent match {
        case end: ExecutionEvent.SectionEnd =>
          printOrFlush(end)

        case flush: ExecutionEvent.TopLevelFlush =>
          printOrFlushZ2(flush)
        case other =>
          printOrQueue(other)
      })
    }

    private def printOrFlush(
      end: ExecutionEvent.SectionEnd
    ): ZIO[Any, Nothing, Unit] =
      for {
        suiteIsPrinting <- reporters.attemptToGetPrintingControl(end.id, end.ancestors)//.debug("printOrFlush id: " + end.id)
        sectionOutput   <- getAndRemoveSectionOutput(end.id).map(_ :+ end)
        _ <-
          if (suiteIsPrinting)
            printToConsole(sectionOutput)
          else {
            end.ancestors.headOption match {
              case Some(parentId) =>
//                ZIO.debug(s"${end.id} is sending its output to $parentId") *>
                appendToSectionContents(parentId, sectionOutput)
              case None =>
                // TODO If we can't find cause of failure in CI, unsafely print to console instead of failing
//                appendToSectionContents(SuiteId.global, sectionOutput)
                ZIO.dieMessage("Suite tried to send its output to a nonexistent parent. ExecutionEvent: " + end)
            }
          }

        _ <- reporters.relinquishPrintingControl(end.id)
      } yield ()


    private def printOrFlushZ2(
                                end: ExecutionEvent.TopLevelFlush
                            ): ZIO[Any, Nothing, Unit] =
      for {
        sectionOutput <- getAndRemoveSectionOutput(end.id)
        _ <- appendToSectionContents(SuiteId.global, sectionOutput)
        suiteIsPrinting <- reporters.attemptToGetPrintingControl(SuiteId.global, List.empty)//.debug("printOrFlushZ2 id: " + end.id)
        _ <-
          if (suiteIsPrinting) {
            for {
              globalOutput <- getAndRemoveSectionOutput(SuiteId.global)
              _ <- printToConsole(globalOutput)
            } yield ()

          } else {
            ZIO.unit
          }

//        _ <- reporters.relinquishPrintingControl(end.id)
      } yield ()

    private def printOrQueue(
      reporterEvent: ExecutionEvent
    ): ZIO[Any, Nothing, Unit] =
      for {
        _               <- appendToSectionContents(reporterEvent.id, Chunk(reporterEvent))
        suiteIsPrinting <- reporters.attemptToGetPrintingControl(reporterEvent.id, reporterEvent.ancestors)//.debug("printOrQueue id: " + reporterEvent.id)
        _ <- ZIO.when(suiteIsPrinting)(
               for {
                 currentOutput <- getAndRemoveSectionOutput(reporterEvent.id)
                 _             <- printToConsole(currentOutput)
               } yield ()
             )
      } yield ()

    private def printToConsole(events: Chunk[ExecutionEvent]) =
      ZIO.foreachDiscard(events) { event =>
//        ZIO.debug("Sending to print: " + event) *>
        executionEventPrinter.print(event)
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

    def make(executionEventPrinter: ExecutionEventPrinter): ZIO[Any, Nothing, TestOutput] = for {
      talkers <- TestReporters.make
      output  <- Ref.make[Map[SuiteId, Chunk[ExecutionEvent]]](Map.empty)
    } yield TestOutputLive(output, talkers, executionEventPrinter)

  }
}
