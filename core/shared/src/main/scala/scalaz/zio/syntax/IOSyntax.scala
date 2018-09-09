package scalaz.zio.syntax
import scalaz.zio.IO

object IOSyntax {
  final class IOCreationEagerSyntax[A](val a: A) extends AnyVal {
    def now: IO[Nothing, A]                        = IO.now(a)
    def fail: IO[A, Nothing]                       = IO.fail(a)
    def ensure[AA]: IO[A, Option[AA]] => IO[A, AA] = IO.require(a)
  }

  final class IOCreationLazySyntax[A](val a: () => A) extends AnyVal {
    def point: IO[Nothing, A]                                   = IO.point(a())
    def sync: IO[Nothing, A]                                    = IO.sync(a())
    def syncException: IO[Exception, A]                         = IO.syncException(a())
    def syncThrowable: IO[Throwable, A]                         = IO.syncThrowable(a())
    def syncCatch[E]: PartialFunction[Throwable, E] => IO[E, A] = IO.syncCatch(a())
  }
}
