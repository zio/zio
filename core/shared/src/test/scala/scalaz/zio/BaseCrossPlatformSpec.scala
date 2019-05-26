package scalaz.zio

import java.util.{ Timer, TimerTask }

import org.specs2.Specification
import org.specs2.execute.AsResult
import org.specs2.matcher.MatchResult
import org.specs2.matcher.describe.Diffable
import org.specs2.specification.core.{ AsExecution, Execution }
import scalaz.zio.Exit.Cause
import scalaz.zio.internal.PlatformLive
import scalaz.zio.clock.Clock
import scalaz.zio.console.Console
import scalaz.zio.random.Random
import scalaz.zio.system.System

import scala.concurrent.duration.{ Duration, _ }
import scala.concurrent.{ ExecutionContext, Future }

abstract class BaseCrossPlatformSpec extends Specification with DefaultRuntime with TestUtils {

  override val Platform = PlatformLive.makeDefault().withReportFailure(_ => ())

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  val DefaultTimeout: Duration = 60.seconds
  val timer                    = new Timer()

  implicit def zioAsExecution[A: AsResult, R >: Clock with Console with System with Random, E]
    : AsExecution[ZIO[R, E, A]] =
    io => Execution.withEnvAsync(_ => runToFutureWithTimeout(io, DefaultTimeout))

  protected def runToFutureWithTimeout[E, R >: Clock with Console with System with Random, A: AsResult](
    io: ZIO[R, E, A],
    timeout: Duration
  ): Future[A] = {
    val p = scala.concurrent.Promise[A]()
    val task = new TimerTask {
      override def run(): Unit =
        try {
          p.failure(new Exception("TIMEOUT: " + timeout))
          ()
        } catch {
          case _: Throwable => ()
        }
    }
    timer.schedule(task, timeout.toMillis)

    unsafeRunToFuture(io.sandbox.mapError(FiberFailure(_))).map(p.success)
    p.future
  }

  implicit class ZIOMustExpectable[R, E, A](zio: ZIO[R, E, A]) {

    def must_===(other: => A)(implicit di: Diffable[A]): ZIO[R, E, MatchResult[A]] =
      zio.map(a => a must_=== other)

    def mustFailBecauseOf(cause: Cause[E]): ZIO[R, A, MatchResult[Cause[E]]] =
      zio.sandbox.flip.map(error => error must_=== cause)

  }
}
