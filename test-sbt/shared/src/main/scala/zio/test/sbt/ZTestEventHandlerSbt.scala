package zio.test.sbt

import sbt.testing.{EventHandler, TaskDef}
import zio.{UIO, ZIO}
import zio.test.{ExecutionEvent, ZTestEventHandler}

class ZTestEventHandlerSbt(eventHandler: EventHandler, taskDef: TaskDef) extends ZTestEventHandler {
  def handle(event: ExecutionEvent.Test[_]): UIO[Unit] =
    ZIO.succeed(eventHandler.handle(ZTestEvent.convertEvent(event, taskDef)))
}
