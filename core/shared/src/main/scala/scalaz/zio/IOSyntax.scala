package scalaz.zio

import scala.language.implicitConversions

object syntax extends IOSyntax

trait IOSyntax {
  implicit final def ioEagerSyntax[A](a: A): IOEagerSyntax[A]  = new IOEagerSyntax[A](a)
  implicit final def ioLazySyntax[A](a: => A): IOLazySyntax[A] = new IOLazySyntax[A](() => a)
}

final class IOEagerSyntax[A](val a: A) extends AnyVal {
  def now: IO[Nothing, A]                        = IO.now(a)
  def fail: IO[A, Nothing]                       = IO.fail(a)
  def ensure[AA]: IO[A, Option[AA]] => IO[A, AA] = IO.require(a)
}

final class IOLazySyntax[A](val a: () => A) extends AnyVal {
  def point: IO[Nothing, A]                                   = IO.point(a())
  def sync: IO[Nothing, A]                                    = IO.sync(a())
  def syncException: IO[Exception, A]                         = IO.syncException(a())
  def syncThrowable: IO[Throwable, A]                         = IO.syncThrowable(a())
  def syncCatch[E]: PartialFunction[Throwable, E] => IO[E, A] = IO.syncCatch(a())
}
