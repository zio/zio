package scalaz.zio
package interop

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.Duration
import scala.util.{ Failure, Success }

object Task {
  type Par[A] = Par.T[Throwable, A]

  final def apply[A](effect: => A): Task[A] = IO.syncThrowable(effect)

  final def now[A](effect: A): Task[A]                                              = IO.now(effect)
  final def point[A](effect: => A): Task[A]                                         = IO.point(effect)
  final def sync[A](effect: => A): Task[A]                                          = IO.sync(effect)
  final def async[A](register: (ExitResult[Throwable, A] => Unit) => Unit): Task[A] = IO.async(register)

  final def fail[A](error: Throwable): Task[A] = IO.fail(error)

  final def unit: Task[Unit]                      = IO.unit
  final def sleep(duration: Duration): Task[Unit] = IO.sleep(duration)

  final def fromFuture[E, A](io: Task[Future[A]])(ec: ExecutionContext): Task[A] =
    io.attempt.flatMap { f =>
      IO.async { (cb: ExitResult[Throwable, A] => Unit) =>
        f.fold(
          t => cb(ExitResult.Failed(t)),
          _.onComplete {
            case Success(a) => cb(ExitResult.Completed(a))
            case Failure(t) => cb(ExitResult.Failed(t))
          }(ec)
        )
      }
    }
}
