package scalaz.zio

import java.util.{ Timer, TimerTask }

import org.specs2.Specification
import org.specs2.execute.AsResult
import org.specs2.specification.core.{ AsExecution, Execution }

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scalaz.zio.internal.PlatformLive

abstract class TestRuntimeJS extends Specification with DefaultRuntime {

  override val Platform = PlatformLive.makeDefault().withReportFailure(_ => ())

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  val DefaultTimeout = 1.seconds
  val timer          = new Timer()

  implicit def zioAsExecution[
    A: AsResult,
    R >: scalaz.zio.clock.Clock with scalaz.zio.console.Console with scalaz.zio.system.System with scalaz.zio.random.Random,
    E
  ]: AsExecution[ZIO[R, E, A]] =
    a => {
      val p = scala.concurrent.Promise[A]()
      val timeout = new TimerTask {
        override def run(): Unit =
          try {
            p.failure(new Exception("TIMEOUT: " + DefaultTimeout))
          } catch {
            case _: Throwable => ()
          }
      }
      timer.schedule(timeout, DefaultTimeout.toMillis)
      unsafeRunToFuture(a.mapError(a => new Exception(a.toString))).map(a => p.success(a))

      Execution.withEnvAsync(_ => p.future)
    }
}
