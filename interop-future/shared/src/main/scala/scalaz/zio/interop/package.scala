package scalaz.zio.interop

import scalaz.zio.{ Exit, Fiber, IO }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

package object future extends FuturePlatformSpecific {

  implicit class IOObjOps(private val ioObj: IO.type) extends AnyVal {
    private def unsafeFromFuture[A](ec: ExecutionContext, f: Future[A]): IO[Throwable, A] =
      f.value.fold(
        IO.async { (cb: IO[Throwable, A] => Unit) =>
          f.onComplete {
            case Success(a) => cb(IO.succeed(a))
            case Failure(t) => cb(IO.fail(t))
          }(ec)
        }
      )(IO.fromTry(_))

    def fromFuture[A](ec: ExecutionContext)(ftr: () => Future[A]): IO[Throwable, A] =
      IO.suspend {
        unsafeFromFuture(ec, ftr())
      }

    def fromFutureIO[A, E >: Throwable](ec: ExecutionContext)(ftrio: IO[E, Future[A]]): IO[E, A] =
      ftrio.flatMap(unsafeFromFuture(ec, _))

    def fromFutureAction[A](ftr: ExecutionContext => Future[A]): IO[Throwable, A] =
      for {
        d  <- IO.descriptor
        ec = d.executor.asEC
        a  <- unsafeFromFuture(ec, ftr(ec))
      } yield a
  }

  implicit class FiberObjOps(private val fiberObj: Fiber.type) extends AnyVal {

    def fromFuture[A](ec: ExecutionContext)(_ftr: () => Future[A]): Fiber[Throwable, A] = {

      lazy val ftr = _ftr()

      new Fiber[Throwable, A] {

        def await: IO[Nothing, Exit[Throwable, A]] =
          IO.suspend {
            ftr.value.fold(
              IO.async { (cb: IO[Nothing, Exit[Throwable, A]] => Unit) =>
                ftr.onComplete {
                  case Success(a) => cb(IO.succeed(Exit.succeed(a)))
                  case Failure(t) => cb(IO.succeed(Exit.fail(t)))
                }(ec)
              }
            )(t => IO.succeedLazy(Exit.fromTry(t)))
          }

        def poll: IO[Nothing, Option[Exit[Throwable, A]]] =
          IO.sync(ftr.value.map(Exit.fromTry))

        def interrupt: IO[Nothing, Exit[Throwable, A]] =
          join.fold(Exit.fail, Exit.succeed)
      }
    }
  }

  implicit class IOThrowableOps[A](private val io: IO[Throwable, A]) extends AnyVal {
    def toFuture: IO[Nothing, Future[A]] =
      io.fold(Future.failed, Future.successful)
  }

  implicit class IOOps[E, A](private val io: IO[E, A]) extends AnyVal {
    def toFutureE(f: E => Throwable): IO[Nothing, Future[A]] = io.mapError(f).toFuture
  }

}
