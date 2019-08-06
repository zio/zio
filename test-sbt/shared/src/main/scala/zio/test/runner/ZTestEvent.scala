package zio.test.runner

import sbt.testing._
import zio.test.RenderedResult

case class ZTestEvent(fullyQualifiedName: String,
                      selector: Selector,
                      status: Status,
                      maybeThrowable: Option[Throwable],
                      duration: Long,
                      fingerprint: Fingerprint
                     ) extends Event {
  def throwable: OptionalThrowable = maybeThrowable.fold(new OptionalThrowable())(new OptionalThrowable(_))
}

object ZTestEvent {

  def from(renderedResult: RenderedResult, fullyQualifiedName: String, fingerprint: Fingerprint) = {
    ZTestEvent(fullyQualifiedName, new TestSelector(renderedResult.label), toStatus(renderedResult), None, 0, fingerprint)
  }

  private def toStatus(testResult: RenderedResult) = testResult.status match {
    case RenderedResult.Status.Failed  => Status.Failure
    case RenderedResult.Status.Passed  => Status.Success
    case RenderedResult.Status.Ignored => Status.Ignored
  }
}

