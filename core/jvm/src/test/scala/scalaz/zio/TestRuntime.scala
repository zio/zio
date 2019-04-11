package scalaz.zio

import scala.concurrent.duration._
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.{ AsResult, Failure, Result, Skipped }
import org.specs2.matcher.Expectations
import org.specs2.matcher.TerminationMatchers.terminate
import org.specs2.specification.{ Around, AroundEach, AroundTimeout }
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

  override final def aroundTimeout(to: Duration)(implicit ee: ExecutionEnv): Around =
    new Around {
      def around[T: AsResult](t: => T): Result = {
        lazy val result = t
        val termination = terminate(retries = 1000, sleep = (to.toMicros / 1000).micros)
          .orSkip(_ => "TIMEOUT: " + to)(Expectations.createExpectable(result))

        if (!termination.toResult.isSkipped) AsResult(result)
        else termination.toResult
      }
    }
}
