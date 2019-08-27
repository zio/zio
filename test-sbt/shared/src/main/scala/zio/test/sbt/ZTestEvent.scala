package zio.test.sbt

import sbt.testing._
import zio.test.{ AssertResult, ExecutedSpec, Spec, TestResult }

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
  def from[L](executedSpec: ExecutedSpec[L], fullyQualifiedName: String, fingerprint: Fingerprint): Seq[ZTestEvent] = {
    def loop(executedSpec: ExecutedSpec[String]): Seq[ZTestEvent] =
      executedSpec.caseValue match {
        case Spec.SuiteCase(_, executedSpecs, _) => executedSpecs.flatMap(loop)
        case Spec.TestCase(label, result) =>
          Seq(ZTestEvent(fullyQualifiedName, new TestSelector(label), toStatus(result), None, 0, fingerprint))
      }
    loop(executedSpec.mapLabel(_.toString))
  }

  private def toStatus[L](result: TestResult) =
    result match {
      case AssertResult.Success    => Status.Success
      case AssertResult.Failure(_) => Status.Failure
      case AssertResult.Ignore     => Status.Ignored
    }
}
