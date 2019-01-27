package scalaz.zio.interop

import scalaz.zio.Task

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

object Util {
  type Par[A] = Par.T[Throwable, A]

  final def fromFuture[E, A](ec: ExecutionContext)(io: Task[Future[A]]): Task[A] =
    io.attempt.flatMap { tf =>
      tf.fold(
        t => Task.fail(t),
        f =>
          f.value.fold(
            Task.async { (cb: Task[A] => Unit) =>
              f.onComplete {
                case Success(a) => cb(Task.succeed(a))
                case Failure(t) => cb(Task.fail(t))
              }(ec)
            }
          )(Task.fromTry(_))
      )
    }
}
