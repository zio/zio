package zio.test.sbt

import sbt.testing.{EventHandler, Status, TaskDef}
import zio.{UIO, ZIO}
import zio.test.{ExecutionEvent, TestAnnotation, TestFailure, ZTestEventHandler}

class ZTestEventHandlerSbt(eventHandler: EventHandler, taskDef: TaskDef) extends ZTestEventHandler {
  def handle(event: ExecutionEvent): UIO[Unit] =
    event match {
      case evt @ ExecutionEvent.Test(_, _, _, _, _, _) =>
        ZIO.succeed(eventHandler.handle(ZTestEvent.convertEvent(evt, taskDef)))
      case ExecutionEvent.SectionStart(_, _, _)      => ZIO.unit
      case ExecutionEvent.SectionEnd(_, _, _)        => ZIO.unit
      case ExecutionEvent.TopLevelFlush(_)           => ZIO.unit
      case ExecutionEvent.RuntimeFailure(id, labelsReversed, failure, ancestors) =>
        val (e, annotations) = failure match {
          case TestFailure.Assertion(result, annotations) => ???
          case TestFailure.Runtime(cause, annotations) => (cause.dieOption, annotations)
        }


        val zTestEvent = ZTestEvent(
          fullyQualifiedName = taskDef.fullyQualifiedName(),
          selector = taskDef.selectors().head,
          status = Status.Failure,
          maybeThrowable = e,
          duration = annotations.get(TestAnnotation.timing).toMillis,
          fingerprint = ZioSpecFingerprint
        )
        ZIO.succeed(eventHandler.handle(zTestEvent))
    }
}
