package scalaz.zio.interop

import scalaz.zio.{ Exit, Fiber, IO }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

object future extends FuturePlatformSpecific {

  implicit class IOObjOps(private val ioObj: IO.type) extends AnyVal {
    private def unsafeFutureToIO[A](f: Future[A], ec: ExecutionContext): IO[Throwable, A] =
      f.value.fold(
        IO.async { (cb: IO[Throwable, A] => Unit) =>
          f.onComplete {
            case Success(a) => cb(IO.succeed(a))
            case Failure(t) => cb(IO.fail(t))
          }(ec)
        }
      )(IO.fromTry(_))

    def fromFuture[A](ftr: () => Future[A])(ec: ExecutionContext): IO[Throwable, A] =
      IO.suspend {
        unsafeFutureToIO(ftr(), ec)
      }

    def fromFutureAction[A](ftr: ExecutionContext => Future[A]): IO[Throwable, A] =
      for {
        d  <- IO.descriptor
        ec = d.executor.asEC
        a  <- unsafeFutureToIO(ftr(ec), ec)
      } yield a
  }

  implicit class FiberObjOps(private val fiberObj: Fiber.type) extends AnyVal {
    def fromFuture[A](_ftr: () => Future[A])(ec: ExecutionContext): Fiber[Throwable, A] =
      new Fiber[Throwable, A] {
        private lazy val ftr = _ftr()
        def await: IO[Nothing, Exit[Throwable, A]] =
          IO.suspend {
            ftr.value.fold(
              IO.async { (cb: IO[Nothing, Exit[Throwable, A]] => Unit) =>
                ftr.onComplete {
                  case Success(a) => cb(IO.succeed(Exit.succeed(a)))
                  case Failure(t) => cb(IO.succeed(Exit.checked(t)))
                }(ec)
              }
            )(t => IO.succeedLazy(Exit.fromTry(t)))
          }

        def poll: IO[Nothing, Option[Exit[Throwable, A]]] =
          IO.sync(ftr.value.map(Exit.fromTry))

        def interrupt: IO[Nothing, Exit[Throwable, A]] =
          join.redeemPure(Exit.checked, Exit.succeed)
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
