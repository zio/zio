package zio.test.sbt

import sbt.testing._

import zio.UIO
import zio.test.{ ExecutedSpec, Spec, TestFailure, TestSuccess }

final case class ZTestEvent(
  fullyQualifiedName: String,
  selector: Selector,
  status: Status,
  maybeThrowable: Option[Throwable],
  duration: Long,
  fingerprint: Fingerprint
) extends Event {
  def throwable: OptionalThrowable = maybeThrowable.fold(new OptionalThrowable())(new OptionalThrowable(_))
}

object ZTestEvent {
  def from[E](
    executedSpec: ExecutedSpec[E],
    fullyQualifiedName: String,
    fingerprint: Fingerprint
  ): UIO[Seq[ZTestEvent]] =
    executedSpec.fold[UIO[Seq[ZTestEvent]]] {
      case Spec.SuiteCase(_, results, _) =>
        results.use(UIO.collectAll(_).map(_.flatten))
      case Spec.TestCase(label, result, _) =>
        result.map { result =>
          Seq(ZTestEvent(fullyQualifiedName, new TestSelector(label), toStatus(result), None, 0, fingerprint))
        }
    }

  private def toStatus[E](result: Either[TestFailure[E], TestSuccess]) = result match {
    case Left(_)                         => Status.Failure
    case Right(TestSuccess.Succeeded(_)) => Status.Success
    case Right(TestSuccess.Ignored)      => Status.Ignored
  }
}
