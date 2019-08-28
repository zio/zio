package zio.test.sbt

import sbt.testing._
import zio.test.{ ExecutedSpec, Spec, TestFailure, TestStatus }

case class ZTestEvent(
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
  def from[L, E, S](
    executedSpec: ExecutedSpec[L, E, S],
    fullyQualifiedName: String,
    fingerprint: Fingerprint
  ): Seq[ZTestEvent] = {
    def loop(executedSpec: ExecutedSpec[String, E, S]): Seq[ZTestEvent] =
      executedSpec.caseValue match {
        case Spec.SuiteCase(_, executedSpecs, _) => executedSpecs.flatMap(loop)
        case Spec.TestCase(label, result) =>
          Seq(ZTestEvent(fullyQualifiedName, new TestSelector(label), toStatus(result), None, 0, fingerprint))
      }
    loop(executedSpec.mapLabel(_.toString))
  }

  private def toStatus[L, E, S](result: Either[TestFailure[E], TestStatus[S]]) = result match {
    case Left(_)                       => Status.Failure
    case Right(TestStatus.Executed(_)) => Status.Success
    case Right(TestStatus.Ignored)     => Status.Ignored
  }
}
