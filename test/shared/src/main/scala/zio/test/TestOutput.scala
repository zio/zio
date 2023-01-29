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
        executionEventPrinter <- ZIO.service[ExecutionEventPrinter]
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
    executionEventPrinter: ExecutionEventPrinter,
    lock: Ref.Synchronized[Unit] // TODO Use a more specific type
  ) extends TestOutput {

    private def getAndRemoveSectionOutput(id: SuiteId) =
      output
        .getAndUpdate(initial => updatedWith(initial, id)(_ => None))
        .map(_.getOrElse(id, Chunk.empty))

    def print(
      executionEvent: ExecutionEvent
    ): ZIO[Any, Nothing, Unit] =
      executionEvent match {
        case end: ExecutionEvent.SectionEnd =>
          printOrFlush(end)

        case flush: ExecutionEvent.TopLevelFlush =>
          flushGlobalOutputIfPossible(flush)
        case other =>
          printOrQueue(other)
      }

    private def printOrFlush(
      end: ExecutionEvent.SectionEnd
    ): ZIO[Any, Nothing, Unit] =
      for {
        suiteIsPrinting <-
          reporters.attemptToGetPrintingControl(end.id, end.ancestors)
        sectionOutput <- getAndRemoveSectionOutput(end.id).map(_ :+ end)
        _ <-
          if (suiteIsPrinting)
            print(sectionOutput)
          else {
            end.ancestors.headOption match {
              case Some(parentId) =>
                appendToSectionContents(parentId, sectionOutput)
              case None =>
                // TODO If we can't find cause of failure in CI, unsafely print to console instead of failing
                ZIO.dieMessage("Suite tried to send its output to a nonexistent parent. ExecutionEvent: " + end)
            }
          }

        _ <- reporters.relinquishPrintingControl(end.id)
      } yield ()

    private def flushGlobalOutputIfPossible(
      end: ExecutionEvent.TopLevelFlush
    ): ZIO[Any, Nothing, Unit] =
      for {
        sectionOutput <- getAndRemoveSectionOutput(end.id)
        _             <- appendToSectionContents(SuiteId.global, sectionOutput)
        suiteIsPrinting <-
          reporters.attemptToGetPrintingControl(SuiteId.global, List.empty)
        _ <-
          if (suiteIsPrinting) {
            for {
              globalOutput <- getAndRemoveSectionOutput(SuiteId.global)
              _            <- print(globalOutput)
            } yield ()

          } else {
            ZIO.unit
          }
      } yield ()


    // TODO Dedup this with the same method in JVM/Native ResultFileOpsJson?
    def write(content: => String, append: Boolean): ZIO[Any, Nothing, Unit] =
      lock.updateZIO(_ =>
        ZIO
          .acquireReleaseWith(ZIO.attemptBlockingIO(new java.io.FileWriter("target/test-reports-zio/last_executing.txt", append)))(f =>
            ZIO.attemptBlocking(f.close()).orDie
          ) { f =>
            ZIO.attemptBlockingIO(f.append(content))
          }
          .ignore
      )

    private def removeLine(searchString: String) = ZIO.succeed{
      import scala.io.Source
      import java.io._

      val fileName = "target/test-reports-zio/last_executing.txt"

      val lines = Source.fromFile(fileName).getLines.filterNot(_.contains(searchString)).toList
      val pw = new PrintWriter(fileName)
      pw.write(lines.mkString("\n"))
      pw.close()
    }

    private def printEmergency(executionEvent: ExecutionEvent) =
      executionEvent match {
        // TODO Should we have a TestStarted? Is that more generally useful, than just for this debug mode?
        case t @ ExecutionEvent.TestStarted(

        labelsReversed,
        annotations,
        ancestors,
        id,
        fullyQualifiedName
          ) =>

          write(s"${t.labels.mkString(" - ")} STARTED\n", true)

        case t@ExecutionEvent.Test(labelsReversed, test, annotations, ancestors, duration, id, fullyQualifiedName) =>
            removeLine(t.labels.mkString(" - ") + " STARTED")

        case ExecutionEvent.SectionStart(labelsReversed, id, ancestors) => ZIO.unit
        case ExecutionEvent.SectionEnd(labelsReversed, id, ancestors) => ZIO.unit
        case ExecutionEvent.TopLevelFlush(id) => ZIO.unit
        case ExecutionEvent.RuntimeFailure(id, labelsReversed, failure, ancestors) => ZIO.unit
      }

    private def printOrQueue(
      reporterEvent: ExecutionEvent
    ): ZIO[Any, Nothing, Unit] =
      for {
        // TODO Figure out why I can't set a true ENV variable. Should default to false once I figure that out
        debugPrint <- zio.System.envOrElse("DEBUG_TEST", "true").orDie
        _ <- ZIO.when(debugPrint == "true")(printEmergency(reporterEvent))
//        _ <- printEmergency(reporterEvent)
        _ <- appendToSectionContents(reporterEvent.id, Chunk(reporterEvent))
        suiteIsPrinting <- reporters.attemptToGetPrintingControl(
                             reporterEvent.id,
                             reporterEvent.ancestors
                           )
        _ <- ZIO.when(suiteIsPrinting)(
               for {
                 currentOutput <- getAndRemoveSectionOutput(reporterEvent.id)
                 _             <- print(currentOutput)
               } yield ()
             )
      } yield ()

    private def print(events: Chunk[ExecutionEvent]) =
      ZIO.foreachDiscard(events) { event =>
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
      lock <- Ref.Synchronized.make[Unit](())
      output  <- Ref.make[Map[SuiteId, Chunk[ExecutionEvent]]](Map.empty)
    } yield TestOutputLive(output, talkers, executionEventPrinter, lock)

  }
}
