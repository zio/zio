package scalaz.zio

import scala.concurrent.duration._
import org.specs2.Specification
import org.specs2.specification.{ AroundEach, AroundTimeout }
import org.specs2.execute.{ AsResult, Failure, Result, Skipped }

import scalaz.zio.internal.PlatformLive

abstract class TestRuntime(implicit ee: org.specs2.concurrent.ExecutionEnv)
    extends Specification
    with DefaultRuntime
    with AroundEach
    with AroundTimeout {

  override val Platform = PlatformLive.makeDefault().withReportFailure(_ => ())

  val DefaultTimeout = 60.seconds

  override final def around[R: AsResult](r: => R): Result =
    AsResult.safely(upTo(DefaultTimeout)(r)) match {
      case Skipped(m, e) if m contains "TIMEOUT" => Failure(m, e)
      case other                                 => other
    }
}
