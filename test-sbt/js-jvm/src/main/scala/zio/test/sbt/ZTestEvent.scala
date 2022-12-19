package zio.test.sbt

import sbt.testing._
import zio.test.render.{ConsoleRenderer, IntelliJRenderer, TestRenderer}
import zio.test.render.LogLine.{Line, Message}
import zio.test.{ExecutionEvent, TestAnnotation, TestFailure, TestSuccess}

final case class ZTestEvent(
  fullyQualifiedName: String,
  selector: Selector,
  status: Status,
  maybeThrowable: Option[Throwable],
  duration: Long,
  fingerprint: Fingerprint
) extends Event {
  def throwable(): OptionalThrowable = maybeThrowable.fold(new OptionalThrowable())(new OptionalThrowable(_))
}

object ZTestEvent {
  def convertEvent(test: ExecutionEvent.Test[_], taskDef: TaskDef, renderer: TestRenderer): Event = {
    val status = statusFrom(test)
    val maybeThrowable = status match {
      case Status.Failure =>
        val failureMsg =
          renderer
            .render(test, true)
            .mkString("\n")
        Some(new Exception(failureMsg))
      case _ => None
    }

    ZTestEvent(
      fullyQualifiedName = taskDef.fullyQualifiedName(),
      selector = new TestSelector(test.labels.mkString(" - ")),
      status = status,
      maybeThrowable = maybeThrowable,
      // Should we just be using test.duration here?
      duration = test.duration,
      fingerprint = ZioSpecFingerprint
    )
  }

  private def statusFrom(test: ExecutionEvent.Test[_]): Status =
    test.test match {
      case Left(_) => Status.Failure
      case Right(value) =>
        value match {
          case TestSuccess.Succeeded(_) => Status.Success
          case TestSuccess.Ignored(_)   => Status.Ignored
        }
    }
}
