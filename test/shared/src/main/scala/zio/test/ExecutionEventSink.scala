package zio.test

import zio.{Chunk, ZLayer, Ref, UIO, ZIO, ZTraceElement}

import java.util.UUID
import zio.ULayer

trait ExecutionEventSink {
  def process(event: ExecutionEvent): UIO[Unit]
}

object ExecutionEventSink {
  private def minimal = new ExecutionEventSink {
    override def process(event: ExecutionEvent): UIO[Unit] = 
      ZIO.debug("Should actually do stuff with this ExecutionEvent: " + event)
  }
  val minimalLayer: ULayer[ExecutionEventSink] =
    ZLayer.succeed(minimal)

  def make[R](stateReporter: ReporterEvent => ZIO[R, Nothing, Any], summary: Ref[Summary], hasFailures: Ref[Boolean])(implicit
                                                                                                                      trace: ZTraceElement
  ): ZIO[R, Nothing, ExecutionEventSink] = {
    for {
      sectionState <- Ref.make(Map.empty[UUID, SectionState])

      //      final case class Summary(success: Int, fail: Int, ignore: Int, summary: String) {
      env <- ZIO.environment[R]
    } yield new ExecutionEventSink {
      // TODO When to collect results VS when to report
      // TODO Fail parent suite when inner suite fails
      override def process(event: ExecutionEvent): UIO[Unit] = {
        event match {
          case testEvent@ExecutionEvent.Test(labelsReversed, test, annotations, ancestors) =>
            ancestors match {
              case Nil =>
                stateReporter(SectionState(Chunk(testEvent))).provideEnvironment(env).unit
              case head :: _ =>
                sectionState.update(curState =>
                  curState.get(head) match {
                    case Some(SectionState(results)) =>
                      val newResults = results :+ testEvent
                      curState.updated(head, SectionState(newResults))
                    case None =>
                      curState.updated(head, SectionState(Chunk(testEvent)))
                  }
                )
            }
          case ExecutionEvent.SectionStart(labelsReversed, id, ancestors) =>
            sectionState.update(curState => curState.updated(id, SectionState(Chunk.empty)))
          case ExecutionEvent.SectionEnd(labelsReversed, id, ancestors) =>
            sectionState.modify(curState =>
              curState.get(id) match {
                case Some(sectionState) =>
                  (sectionState, curState - id)
                case None =>
                  (SectionState(Chunk.empty), curState)
              }
            ).flatMap(finalSectionState =>
              stateReporter(finalSectionState).provideEnvironment(env).unit
            )
          case ExecutionEvent.Failure(labelsReversed, failure, ancestors) =>
            stateReporter(Failure(labelsReversed, failure, ancestors)).provideEnvironment(env).unit
        }

      }
    }
  }

}

/**
 * A `TestExecutor[R, E]` is capable of executing specs that require an
 * environment `R` and may fail with an `E`.
 */
//abstract class TestExecutor[+R, E] {
//  def run(spec: ZSpec[R, E], defExec: ExecutionStrategy)(implicit trace: ZTraceElement): UIO[ExecutedSpec[E]]
//  def environment: Layer[Nothing, R]
//}
