package zio.test.sbt

import sbt.testing._
import zio.test.{DefaultTestReporter, ExecutedSpec, TestAnnotation, TestAnnotationRenderer, TestFailure, TestSuccess}

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
  ): Seq[ZTestEvent] = {

    def loop(executedSpec: ExecutedSpec[E], label: Option[String]): Seq[ZTestEvent] =
      executedSpec.caseValue match {
        case ExecutedSpec.LabeledCase(label, spec) => loop(spec, Some(label))
        case ExecutedSpec.MultipleCase(specs)      => specs.flatMap(loop(_, label))
        case ExecutedSpec.TestCase(result, annotations) =>
          Seq(
            ZTestEvent(
              fullyQualifiedName,
              new TestSelector(label.getOrElse("")),
              toStatus(result),
              toThrowable(executedSpec, label, result),
              annotations.get(TestAnnotation.timing).toMillis,
              fingerprint
            )
          )
      }

    loop(executedSpec, None)
  }

  private def toStatus[E](result: Either[TestFailure[E], TestSuccess]) = result match {
    case Left(_)                         => Status.Failure
    case Right(TestSuccess.Succeeded(_)) => Status.Success
    case Right(TestSuccess.Ignored)      => Status.Ignored
  }

  private def toThrowable[E](
    spec: ExecutedSpec[E],
    label: Option[String],
    result: Either[TestFailure[E], TestSuccess]
  ) = result.left.toOption.map {
    case TestFailure.Assertion(_) => new AssertionError(render(spec, label))
    case TestFailure.Runtime(_)   => new RuntimeException(render(spec, label))
  }

  private def render[E](spec: ExecutedSpec[E], label: Option[String]) = DefaultTestReporter
    .render(label.fold(spec)(ExecutedSpec.labeled(_, spec)), TestAnnotationRenderer.default, includeCause = true)
    .flatMap(_.rendered)
    .mkString("\n")
    .replaceAll("\u001B\\[[;\\d]*m", "")
}
