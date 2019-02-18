package scalaz.zio

import scala.concurrent.duration._
import org.specs2.Specification
import org.specs2.specification.{ AroundEach, AroundTimeout }
import org.specs2.execute.{ AsResult, Failure, Result, Skipped }

import scalaz.zio.internal.{ Platform, PlatformLive }

abstract class AbstractRTSSpec(implicit ee: org.specs2.concurrent.ExecutionEnv)
    extends Specification
    with RTS
    with AroundEach
    with AroundTimeout {

  override val Platform = new Platform {
    def executor = PlatformLive.executor

    def nonFatal(t: Throwable): Boolean = PlatformLive.nonFatal(t)

    def reportFailure(cause: Exit.Cause[_]): Unit = ()

    def newWeakHashMap[A, B]() = PlatformLive.newWeakHashMap()
  }

  val DefaultTimeout = 60.seconds

  override final def around[R: AsResult](r: => R): Result =
    AsResult.safely(upTo(DefaultTimeout)(r)) match {
      case Skipped(m, e) if m contains "TIMEOUT" => Failure(m, e)
      case other                                 => other
    }
}
