package scalaz.zio
package interop

import scalaz.zio.ExitResult.Cause

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

object future {

  implicit class IOObjOps(private val ioObj: IO.type) extends AnyVal {
    def fromFuture[A](ftr: () => Future[A])(ec: ExecutionContext): IO[Throwable, A] =
      IO.suspend {
        val f = ftr()
        f.value.fold(
          IO.async { (cb: ExitResult[Throwable, A] => Unit) =>
            f.onComplete {
              case Success(a) => cb(ExitResult.succeeded(a))
              case Failure(t) => cb(ExitResult.checked(t))
            }(ec)
          }
        )(_.fold(t => IO.fail0(Cause.checked(t)), IO.point(_)))
      }
  }

  implicit class FiberObjOps(private val fiberObj: Fiber.type) extends AnyVal {
    def fromFuture[A](_ftr: () => Future[A])(ec: ExecutionContext): Fiber[Throwable, A] =
      new Fiber[Throwable, A] {
        private lazy val ftr = _ftr()
        def observe: IO[Nothing, ExitResult[Throwable, A]] =
          IO.suspend {
            ftr.value.fold(
              IO.async { cb: Callback[Nothing, ExitResult[Throwable, A]] =>
                ftr.onComplete {
                  case Success(a) => cb(ExitResult.succeeded(ExitResult.succeeded(a)))
                  case Failure(t) => cb(ExitResult.succeeded(ExitResult.checked(t)))
                }(ec)
              }
            )(t => IO.point(ExitResult.fromTry(t)))
          }

        def poll: IO[Nothing, Option[ExitResult[Throwable, A]]] =
          IO.sync(ftr.value.map(ExitResult.fromTry))

        def interrupt: IO[Nothing, ExitResult[Throwable, A]] =
          join.redeemPure(ExitResult.checked(_), ExitResult.succeeded(_))
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
