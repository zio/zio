package scalaz.zio.interop
import scalaz.zio.{ Callback, ExitResult, Fiber, IO }
import scalaz.zio.internal.Executor

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

object future {

  implicit class IOObjOps(private val ioObj: IO.type) extends AnyVal {
    private def unsafeFutureToIO[A](f: Future[A], ec: ExecutionContext): IO[Throwable, A] =
      f.value.fold(
        IO.async { (cb: ExitResult[Throwable, A] => Unit) =>
          f.onComplete {
            case Success(a) => cb(ExitResult.succeeded(a))
            case Failure(t) => cb(ExitResult.checked(t))
          }(ec)
        }
      )(IO.fromTry(_))

    def fromFuture[A](ftr: () => Future[A])(ec: ExecutionContext): IO[Throwable, A] =
      IO.suspend {
        unsafeFutureToIO(ftr(), ec)
      }

    def fromFutureAction[A](ftr: ExecutionContext => Future[A]): IO[Throwable, A] =
      IO.flatten {
        IO.sync0 { env =>
          val ec = env.executor(Executor.Type.Asynchronous).asExecutionContext

          unsafeFutureToIO(ftr(ec), ec)
        }
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
