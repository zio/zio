package scalaz.zio
package interop

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }
import scalaz.zio.duration.Duration

object Task {
  type Par[A] = Par.T[Throwable, A]

  final def apply[A](effect: => A): Task[A] = IO.syncThrowable(effect)

  final def succeed[A](effect: A): Task[A]                                  = IO.succeed(effect)
  final def succeedLazy[A](effect: => A): Task[A]                           = IO.succeedLazy(effect)
  final def sync[A](effect: => A): Task[A]                                  = IO.sync(effect)
  final def async[A](register: (IO[Throwable, A] => Unit) => Unit): Task[A] = IO.async(register)

  final def fail[A](error: Throwable): Task[A] = IO.fail(error)

  final def unit: Task[Unit]                      = IO.unit
  final def sleep(duration: Duration): Task[Unit] = IO.sleep(duration)

  final def fromFuture[E, A](ec: ExecutionContext)(io: Task[Future[A]]): Task[A] =
    io.attempt.flatMap { tf =>
      tf.fold(
        t => IO.fail(t),
        f =>
          f.value.fold(
            IO.async { (cb: IO[Throwable, A] => Unit) =>
              f.onComplete {
                case Success(a) => cb(IO.succeed(a))
                case Failure(t) => cb(IO.fail(t))
              }(ec)
            }
          )(IO.fromTry(_))
      )
    }
}
