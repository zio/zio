package zio.test.sbt

import sbt.testing._

import zio.test.{ ExecutedSpec, Spec, TestFailure, TestSuccess }
import zio.UIO

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
  def from[E, L, S](
    executedSpec: ExecutedSpec[E, L, S],
    fullyQualifiedName: String,
    fingerprint: Fingerprint
  ): UIO[Seq[ZTestEvent]] =
    executedSpec.mapLabel(_.toString).fold[UIO[Seq[ZTestEvent]]] {
      case Spec.SuiteCase(_, results, _) =>
        results.flatMap(UIO.collectAll(_).map(_.flatten))
      case zio.test.Spec.TestCase(label, result) =>
        result.map { result =>
          Seq(ZTestEvent(fullyQualifiedName, new TestSelector(label), toStatus(result._1), None, 0, fingerprint))
        }
    }

  private def toStatus[E, L, S](result: Either[TestFailure[E], TestSuccess[S]]) = result match {
    case Left(_)                         => Status.Failure
    case Right(TestSuccess.Succeeded(_)) => Status.Success
    case Right(TestSuccess.Ignored)      => Status.Ignored
  }
}
