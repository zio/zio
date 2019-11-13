package zio

import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.{ AsResult, Failure, Result, Skipped }
import org.specs2.matcher.Expectations
import org.specs2.matcher.TerminationMatchers.terminate
import org.specs2.specification.{ Around, AroundEach, AroundTimeout }

import scala.concurrent.duration.{ Duration => SDuration, MICROSECONDS }

import zio.duration._

abstract class TestRuntime(implicit ee: org.specs2.concurrent.ExecutionEnv)
    extends BaseCrossPlatformSpec
    with AroundEach
    with AroundTimeout {

  override final def around[R: AsResult](r: => R): Result =
    AsResult.safely(upTo(DefaultTimeout)(r)) match {
      case Skipped(m, e) if m contains "TIMEOUT" => Failure(m, e)
      case other                                 => other
    }

  override final def aroundTimeout(to: SDuration)(implicit ee: ExecutionEnv): Around =
    new Around {
      def around[T: AsResult](t: => T): Result = {
        lazy val result = t
        val termination = terminate(retries = 1000, sleep = SDuration(to.toMicros / 1000, MICROSECONDS))
          .orSkip(_ => "TIMEOUT: " + to)(Expectations.createExpectable(result))

        if (!termination.toResult.isSkipped) AsResult(result)
        else termination.toResult
      }
    }

  final def flaky(
    v: => ZIO[ZEnv, Any, org.specs2.matcher.MatchResult[Any]]
  ): org.specs2.matcher.MatchResult[Any] =
    eventually(unsafeRun(v.timeout(1.second)).get)

  def nonFlaky(v: => ZIO[ZEnv, Any, org.specs2.matcher.MatchResult[Any]]): org.specs2.matcher.MatchResult[Any] =
    (1 to 100).foldLeft[org.specs2.matcher.MatchResult[Any]](true must_=== true) {
      case (acc, _) =>
        acc and unsafeRun(v)
    }

  def unsafeRunWith[R, E, A](r: UIO[R])(zio: ZIO[R, E, A]): A =
    unsafeRun(r.flatMap[Any, E, A](zio.provide))

  def unsafeRunWithManaged[R, E, A](r: UManaged[R])(zio: ZIO[R, E, A]): A =
    unsafeRun(zio.provideManaged(r))
}
