package zio.test.sbt

import sbt.testing._
import zio.test.render.{ConsoleRenderer, IntelliJRenderer, TestRenderer}
import zio.test.render.LogLine.{Line, Message}
import zio.test.{ExecutionEvent, TestAnnotation, TestSuccess}

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
  // TODO Test this method directly
  def convertEvent(test: ExecutionEvent.Test[_], taskDef: TaskDef, renderer: TestRenderer): Event = {
    println("Hi convert")
    val status = statusFrom(test)
    val maybeThrowable = status match {
      case Status.Failure =>
        val failureMsg =
          renderer match {
            case c: ConsoleRenderer =>
              // TODO Test this funky dance. Seems like what I'm doing in the Intellij branch should also work here
              println("rendering to console")
              "BORK" + c
                .renderToStringLines(Message(ConsoleRenderer.render(test, true).map(Line.fromString(_))))
                .mkString("\n")
            case i: IntelliJRenderer =>
              println("Hitting my new renderer!")
                i.render(test, includeCause = true) // TODO Should we actually includeCause here?
                  .mkString("\n")

            case r =>
              throw new IllegalArgumentException("Unrecognized renderer: " + r)
          }
        Some(new Exception(failureMsg))
      case _ => None
    }

    ZTestEvent(
      fullyQualifiedName = taskDef.fullyQualifiedName(),
      selector = new TestSelector(test.labels.mkString(" - ")),
      status = status,
      maybeThrowable = maybeThrowable,
      duration = test.annotations.get(TestAnnotation.timing).toMillis,
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
