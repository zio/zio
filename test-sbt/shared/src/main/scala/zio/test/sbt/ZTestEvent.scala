package zio.test.sbt

import sbt.testing._
import zio.test.{ExecutionEvent, TestSuccess}

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
  def convertEvent(executionEvent: ExecutionEvent.Test[_], taskDef: TaskDef): Event = {
    taskDef.selectors().foreach(println(_))
    ZTestEvent(
      fullyQualifiedName = taskDef.fullyQualifiedName(),
      // taskDef.selectors() is "one to many" so we can expect nonEmpty here
      selector = taskDef.selectors().head,
      status = statusFrom(executionEvent),
      maybeThrowable = None,
      duration = 0L,
      fingerprint = ZioSpecFingerprint
    )
  }

  def statusFrom(test: ExecutionEvent.Test[_]) =
    test.test match {
      case Left(value) => Status.Failure
      case Right(value) => value match {
        case TestSuccess.Succeeded(result, annotations) => Status.Success
        case TestSuccess.Ignored(annotations) => Status.Ignored
      }
    }
}
