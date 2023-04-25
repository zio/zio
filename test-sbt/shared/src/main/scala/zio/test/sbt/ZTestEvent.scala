package zio.test.sbt

import sbt.testing._
import zio.test.render.TestRenderer
import zio.test.{ExecutionEvent, TestSuccess}

final case class ZTestEvent(
  fullyQualifiedName0: String,
  selector0: Selector,
  status0: Status,
  maybeThrowable: Option[Throwable],
  duration0: Long,
  fingerprint0: Fingerprint
) extends Event {
  def duration(): Long               = duration0
  def fingerprint(): Fingerprint     = fingerprint0
  def fullyQualifiedName(): String   = fullyQualifiedName0
  def selector(): Selector           = selector0
  def status(): Status               = status0
  def throwable(): OptionalThrowable = maybeThrowable.fold(new OptionalThrowable())(new OptionalThrowable(_))
}

object ZTestEvent {

  def convertEvent(test: ExecutionEvent.Test[_], taskDef: TaskDef, renderer: TestRenderer): Event = {
    val status = statusFrom(test)
    val maybeThrowable = status match {
      case Status.Failure =>
        // Includes ansii colors
        val failureMsg =
          renderer
            .render(test, true)
            .mkString("\n")
        Some(new Exception(failureMsg))
      case _ => None
    }

    ZTestEvent(
      taskDef.fullyQualifiedName(),
      new TestSelector(test.labels.mkString(" - ")),
      status,
      maybeThrowable,
      test.duration,
      ZioSpecFingerprint
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
