package zio.test.sbt

import sbt.testing._
import zio.test.{ExecutedSpec, TestAnnotation, TestFailure, TestSuccess}

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

  def from[E](
    executedSpec: ExecutedSpec[E],
    fullyQualifiedName: String,
    fingerprint: Fingerprint
  ): Seq[ZTestEvent] =
    executedSpec.fold[Seq[ZTestEvent]] { c =>
      (c: @unchecked) match {
        case ExecutedSpec.SuiteCase(_, results) => results.flatten
        case ExecutedSpec.TestCase(label, result, annotations) =>
          Seq(
            ZTestEvent(
              fullyQualifiedName,
              new TestSelector(label),
              toStatus(result),
              None,
              annotations.get(TestAnnotation.timing).toMillis,
              fingerprint
            )
          )
      }
    }

  private def toStatus[E](result: Either[TestFailure[E], TestSuccess]) = result match {
    case Left(_)                         => Status.Failure
    case Right(TestSuccess.Succeeded(_)) => Status.Success
    case Right(TestSuccess.Ignored)      => Status.Ignored
  }
}
