package zio.test

import zio.{Chunk, Ref, ZIO, ZLayer}

import scala.io.Source

/**
 * Collects the execution events of a test run and passes them to the printer
 * one suite at a time.
 */
private[test] trait TestOutput {

  /**
   * Passes `executionEvent` to the printer when its suite holds or can take
   * printing control, and otherwise queues it. When a suite ends without
   * printing control, its queued events move to its parent suite's queue. A
   * `TopLevelFlush` event is not printed itself; it adds the events queued
   * under its id to the queue of `SuiteId.global`, and prints that queue when
   * `SuiteId.global` can take printing control.
   */
  def print(executionEvent: ExecutionEvent): ZIO[Any, Nothing, Unit]
}

private[test] object TestOutput {

  /**
   * Builds a `TestOutput` that prints through the `ExecutionEventPrinter` in
   * the environment.
   */
  val live: ZLayer[ExecutionEventPrinter, Nothing, TestOutput] =
    ZLayer.fromZIO(
      for {
        executionEventPrinter <- ZIO.service[ExecutionEventPrinter]
        // If you need to enable the debug output to diagnose flakiness, set this to true
        outputLive <- TestOutputLive.make(executionEventPrinter, debug = false)
      } yield outputLive
    )

  /**
   * Guarantees:
   *   - Everything at or below a specific suite level will be printed
   *     contiguously
   *
   * Not guaranteed:
   *   - Ordering within a suite
   */
  def print(executionEvent: ExecutionEvent): ZIO[TestOutput, Nothing, Unit] =
    ZIO.serviceWithZIO[TestOutput](_.print(executionEvent))

  /**
   * The `TestOutput` that queues the events of each suite and prints them when
   * the suite holds printing control in its `TestReporters`.
   */
  case class TestOutputLive(
    output: Ref[Map[SuiteId, Chunk[ExecutionEvent]]],
    reporters: TestReporters,
    executionEventPrinter: ExecutionEventPrinter,
    lock: TestDebugFileLock,
    debug: Boolean
  ) extends TestOutput {

    private def getAndRemoveSectionOutput(id: SuiteId) =
      output
        .getAndUpdate(initial => updatedWith(initial, id)(_ => None))
        .map(_.getOrElse(id, Chunk.empty))

    def print(executionEvent: ExecutionEvent): ZIO[Any, Nothing, Unit] =
      executionEvent match {
        case end: ExecutionEvent.SectionEnd      => printOrFlush(end)
        case flush: ExecutionEvent.TopLevelFlush => flushGlobalOutputIfPossible(flush)
        case other                               => printOrQueue(other)
      }

    private def printOrFlush(end: ExecutionEvent.SectionEnd): ZIO[Any, Nothing, Unit] =
      for {
        suiteIsPrinting <- reporters.attemptToGetPrintingControl(end.id, end.ancestors)
        sectionOutput   <- getAndRemoveSectionOutput(end.id).map(_ :+ end)
        _ <-
          if (suiteIsPrinting) print(sectionOutput)
          else {
            end.ancestors match {
              case parentId :: _ =>
                appendToSectionContents(parentId, sectionOutput)
              case Nil =>
                // TODO If we can't find cause of failure in CI, unsafely print to console instead of failing
                ZIO.dieMessage("Suite tried to send its output to a nonexistent parent. ExecutionEvent: " + end)
            }
          }
        _ <- reporters.relinquishPrintingControl(end.id)
      } yield ()

    private def flushGlobalOutputIfPossible(end: ExecutionEvent.TopLevelFlush): ZIO[Any, Nothing, Unit] =
      for {
        sectionOutput   <- getAndRemoveSectionOutput(end.id)
        _               <- appendToSectionContents(SuiteId.global, sectionOutput)
        suiteIsPrinting <- reporters.attemptToGetPrintingControl(SuiteId.global, List.empty)
        _               <- ZIO.whenDiscard(suiteIsPrinting)(getAndRemoveSectionOutput(SuiteId.global).flatMap(print))

      } yield ()

    private def printOrQueue(
      reporterEvent: ExecutionEvent
    ): ZIO[Any, Nothing, Unit] =
      for {
        _               <- ZIO.whenDiscard(debug)(TestDebug.print(reporterEvent, lock))
        _               <- appendToSectionContents(reporterEvent.id, Chunk(reporterEvent))
        suiteIsPrinting <- reporters.attemptToGetPrintingControl(reporterEvent.id, reporterEvent.ancestors)
        _               <- ZIO.whenDiscard(suiteIsPrinting)(getAndRemoveSectionOutput(reporterEvent.id).flatMap(print))
      } yield ()

    private def print(events: Chunk[ExecutionEvent]) =
      ZIO.foreachDiscard(events)(executionEventPrinter.print)

    private def appendToSectionContents(id: SuiteId, content: Chunk[ExecutionEvent]) =
      output.update { outputNow =>
        updatedWith(outputNow, id)(previousSectionOutput =>
          Some(previousSectionOutput.map(old => old ++ content).getOrElse(content))
        )
      }

    // We need this helper to run on Scala 2.12
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

    /**
     * Creates a `TestOutputLive` with empty queues. With `debug` set, it also
     * records started and finished tests in a debug file on the JVM and Scala
     * Native.
     */
    def make(executionEventPrinter: ExecutionEventPrinter, debug: Boolean): ZIO[Any, Nothing, TestOutput] =
      for {
        talkers <- TestReporters.make
        lock    <- TestDebugFileLock.make
        output  <- Ref.make[Map[SuiteId, Chunk[ExecutionEvent]]](Map.empty)
      } yield TestOutputLive(output, talkers, executionEventPrinter, lock, debug)

  }
}
