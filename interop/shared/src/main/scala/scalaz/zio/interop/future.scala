package scalaz.zio
package interop

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.implicitConversions
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

  implicit class FiberObjOps(private val fiberObj: Fiber.type) extends AnyVal {
    def fromFuture[A](_ftr: => Future[A])(ec: ExecutionContext): Fiber[Throwable, A] =
      new Fiber[Throwable, A] {
        private lazy val ftr = _ftr
        def observe: IO[Nothing, ExitResult[Throwable, A]] = IO.async {
          cb: Callback[Nothing, ExitResult[Throwable, A]] =>
            ftr.onComplete {
              case Success(a) => cb(ExitResult.Completed(ExitResult.Completed(a)))
              case Failure(t) => cb(ExitResult.Completed(ExitResult.Failed(t)))
            }(ec)
        }
        def tryObserve: IO[Nothing, Option[ExitResult[Throwable, A]]] = IO.sync {
          ftr.value map {
            case Success(a) => ExitResult.Completed(a)
            case Failure(t) => ExitResult.Failed(t)
          }
        }
        def interrupt0(ts: List[Throwable]): IO[Nothing, Unit] = join.attempt.void
      }
  }

  implicit class IOThrowableOps[A](private val io: IO[Throwable, A]) extends AnyVal {
    def toFuture: IO[Nothing, Future[A]] =
      io.redeemPure(Future.failed, Future.successful)
  }

  implicit class IOOps[E, A](private val io: IO[E, A]) extends AnyVal {
    def toFutureE(f: E => Throwable): IO[Nothing, Future[A]] = io.leftMap(f).toFuture
  }

  class FutureOps[A](private val delayedFuture: () => Future[A]) extends AnyVal {
    def unsafeToIO(implicit ec: ExecutionContext): IO[Throwable, A] = IOObjOps(IO).fromFuture(delayedFuture)(ec)
    def toFiber(implicit ec: ExecutionContext): Fiber[Throwable, A] = FiberObjOps(Fiber).fromFuture(delayedFuture())(ec)
  }

  implicit def FutureToFutureOps[A](fa: => Future[A]): FutureOps[A] = new FutureOps(() => fa)

}
