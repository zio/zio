package zio.test.sbt

import sbt.testing.{Event, Fingerprint, OptionalThrowable, Selector, Status, TaskDef, TestSelector}
import zio.test.render.TestRenderer
import zio.test.{ExecutionEvent, TestAnnotation, TestFailure, TestSuccess}

/**
 * An sbt event that reports the result of one ZIO test, or a runtime failure
 * outside a test.
 *
 * @param fullyQualifiedName0
 *   the name of the spec, taken from its `TaskDef`
 * @param selector0
 *   the selector that identifies the test
 * @param status0
 *   the result of the test
 * @param maybeThrowable
 *   the error that the event carries, if any
 * @param duration0
 *   the duration in milliseconds
 */
final case class ZTestEvent(
  fullyQualifiedName0: String,
  selector0: Selector,
  status0: Status,
  maybeThrowable: Option[Throwable],
  duration0: Long
) extends Event {

  /** Returns [[ZioSpecFingerprint]]. */
  def fingerprint(): Fingerprint = ZioSpecFingerprint

  /**
   * Returns the duration in milliseconds.
   */
  def duration(): Long = duration0

  /** Returns the name of the spec. */
  def fullyQualifiedName(): String = fullyQualifiedName0

  /** Returns the selector that identifies the test. */
  def selector(): Selector = selector0

  /** Returns the result of the test. */
  def status(): Status = status0

  /**
   * Returns `maybeThrowable` as an sbt `OptionalThrowable`, which is empty when
   * `maybeThrowable` is `None`.
   */
  def throwable(): OptionalThrowable = maybeThrowable.fold(new OptionalThrowable())(new OptionalThrowable(_))
}

/**
 * Converts ZIO Test execution events to sbt events.
 */
object ZTestEvent {

  /**
   * Converts the result of one test to an sbt event for the spec that `taskDef`
   * names: `Status.Success` for a passed test, `Status.Ignored` for an ignored
   * test and `Status.Failure` for a failed test. The selector is a
   * `TestSelector` whose name is the labels of the test, outermost first,
   * joined with `" - "` (for example `outer - inner - my test`), and the
   * duration is the run time recorded in `test`. The event of a failed test
   * carries an `Exception` whose message is the result rendered by `renderer`,
   * including the cause of the failure; the `Exception` itself has no cause.
   */
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

  /**
   * Converts a runtime failure that isn't the result of one test (for example,
   * a suite whose effect dies) to an sbt event with `Status.Failure`, the first
   * selector of `taskDef`, and the `TestAnnotation.timing` annotation of
   * `failure` in milliseconds as its duration. The event carries the defect
   * when the cause of `failure` contains one (`Cause.dieOption`), and no
   * throwable otherwise. Throws a `java.util.NoSuchElementException` when
   * `taskDef` has no selectors.
   */
  def convertRuntimeFailure(failure: TestFailure.Runtime[_], taskDef: TaskDef) =
    ZTestEvent(
      taskDef.fullyQualifiedName(),
      taskDef.selectors().head,
      Status.Failure,
      failure.cause.dieOption,
      failure.annotations.get(TestAnnotation.timing).toMillis
    )
}
