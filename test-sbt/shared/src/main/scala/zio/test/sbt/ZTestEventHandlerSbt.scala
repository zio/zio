package zio.test.sbt

import sbt.testing.{EventHandler, TaskDef}
import zio.{UIO, ZIO}
import zio.test.{ExecutionEvent, ZTestEventHandler}

class ZTestEventHandlerSbt(eventHandler: EventHandler, taskDef: TaskDef) extends ZTestEventHandler {
  def handle(event: ExecutionEvent): UIO[Unit] =
    event match {
      case evt @ ExecutionEvent.Test(_, _, _, _, _, _) =>
        ZIO.succeed(eventHandler.handle(ZTestEvent.convertEvent(evt, taskDef)))
      case ExecutionEvent.SectionStart(_, _, _)      => ZIO.unit
      case ExecutionEvent.SectionEnd(_, _, _)        => ZIO.unit
      case ExecutionEvent.TopLevelFlush(_)           => ZIO.unit
      case ExecutionEvent.RuntimeFailure(_, _, _, _) => ZIO.unit
    }
}
