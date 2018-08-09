package scalaz.zio
package interop

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

object future {

  implicit class IOObjOps(private val ioObj: IO.type) extends AnyVal {
    def fromFuture[A](ftr: () => Future[A])(ec: ExecutionContext): IO[Throwable, A] =
      IO.async { (cb: ExitResult[Throwable, A] => Unit) =>
        ftr().onComplete {
          case Success(a) => cb(ExitResult.Completed(a))
          case Failure(t) => cb(ExitResult.Failed(t))
        }(ec)
      }
  }

  implicit class IOThrowableOps[A](private val io: IO[Throwable, A]) extends AnyVal {
    def toFuture: IO[Nothing, Future[A]] =
      io.redeemPure(Future.failed, Future.successful)
  }

  implicit class IOOps[E, A](private val io: IO[E, A]) extends AnyVal {
    def toFutureE(f: E => Throwable): IO[Nothing, Future[A]] = io.leftMap(f).toFuture
  }

}
