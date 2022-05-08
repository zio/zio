package zio.test.sbt

import sbt.testing.{EventHandler, TaskDef}
import zio.{UIO, ZIO}
import zio.test.{ExecutionEvent, ZTestEventHandler, test}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths, StandardOpenOption}

class ZTestEventHandlerSbt(eventHandler: EventHandler, taskDef: TaskDef) extends ZTestEventHandler {
  val htmlDir = Paths.get(s"target/test-reports-html")
  val html = htmlDir.resolve(s"${taskDef.fullyQualifiedName()}.html")
  def handle(event: ExecutionEvent.Test[_]): UIO[Unit] =
    ZIO.succeed(eventHandler.handle(ZTestEvent.convertEvent(event, taskDef))) *>
      ZIO.succeed {
        Files.write(html,
          TestResultsHtml.render(event)
          .getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND)
      }.ignore
}
