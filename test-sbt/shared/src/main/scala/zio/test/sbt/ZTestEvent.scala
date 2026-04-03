package zio.test.sbt

import sbt.testing.{Event, Fingerprint, OptionalThrowable, Selector, Status, TaskDef, TestSelector}
import zio.test.render.TestRenderer
import zio.test.{ExecutionEvent, TestAnnotation, TestFailure, TestSuccess}

final case class ZTestEvent(
  fullyQualifiedName0: String,
  selector0: Selector,
  status0: Status,
  maybeThrowable: Option[Throwable],
  duration0: Long
) extends Event {
  def fingerprint(): Fingerprint     = ZioSpecFingerprint
  def duration(): Long               = duration0
  def fullyQualifiedName(): String   = fullyQualifiedName0
  def selector(): Selector           = selector0
  def status(): Status               = status0
  def throwable(): OptionalThrowable = maybeThrowable.fold(new OptionalThrowable())(new OptionalThrowable(_))
}

object ZTestEvent {
  def convertTestEvent(test: ExecutionEvent.Test[_], taskDef: TaskDef, renderer: TestRenderer): Event = {
    val status: Status = test.test match {
      case Left(_) => Status.Failure
      case Right(value) =>
        value match {
          case TestSuccess.Succeeded(_) => Status.Success
          case TestSuccess.Ignored(_)   => Status.Ignored
        }
    }
    val maybeThrowable: Option[Exception] = status match {
      case Status.Failure =>
        // Includes ansii colors
        val failureMsg: String =
          renderer
            .render(test, includeCause = true)
            .mkString("\n")
        Some(new Exception(failureMsg))
      case _ => None
    }

    ZTestEvent(
      taskDef.fullyQualifiedName(),
      new TestSelector(test.labels.mkString(" - ")),
      status,
      maybeThrowable,
      test.duration
    )
  }

  def convertRuntimeFailure(failure: TestFailure.Runtime[_], taskDef: TaskDef) =
    ZTestEvent(
      taskDef.fullyQualifiedName(),
      taskDef.selectors().head,
      Status.Failure,
      failure.cause.dieOption,
      failure.annotations.get(TestAnnotation.timing).toMillis
    )
}
