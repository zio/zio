package scalaz.zio
package interop

import cats.effect.{ Effect, ExitCase }
import cats.syntax.functor._
import cats.{ effect, _ }

import scala.util.control.NonFatal

object catz extends CatsInstances

abstract class CatsInstances extends CatsInstances1 {
  implicit val taskEffectInstances: Effect[Task] with SemigroupK[Task] =
    new CatsEffect
}

sealed abstract class CatsInstances1 extends CatsInstances2 {
  implicit def ioMonoidInstances[E: Monoid]: MonadError[IO[E, ?], E] with Bifunctor[IO] with Alternative[IO[E, ?]] =
    new CatsAlternative[E] with CatsBifunctor
}

sealed abstract class CatsInstances2 {
  implicit def ioInstances[E]: MonadError[IO[E, ?], E] with Bifunctor[IO] with SemigroupK[IO[E, ?]] =
    new CatsMonadError[E] with CatsSemigroupK[E] with CatsBifunctor
}

private class CatsEffect extends CatsMonadError[Throwable] with Effect[Task] with CatsSemigroupK[Throwable] with RTS {
  protected def exitResultToEither[A]: ExitResult[Throwable, A] => Either[Throwable, A] = {
    case ExitResult.Completed(a)       => Right(a)
    case ExitResult.Failed(t, _)       => Left(t)
    case ExitResult.Terminated(Nil)    => Left(Errors.TerminatedFiber)
    case ExitResult.Terminated(t :: _) => Left(t)
  }

  protected def eitherToExitResult[A]: Either[Throwable, A] => ExitResult[Throwable, A] = {
    case Left(t)  => ExitResult.Failed(t)
    case Right(r) => ExitResult.Completed(r)
  }

  override def never[A]: Task[A] =
    IO.never

  override def runAsync[A](
    fa: Task[A]
  )(cb: Either[Throwable, A] => effect.IO[Unit]): effect.SyncIO[Unit] =
    effect.SyncIO {
      unsafeRunAsync(fa) { exit =>
        cb(exitResultToEither(exit)).unsafeRunAsync(_ => ())
      }
    }.void

  override def async[A](k: (Either[Throwable, A] => Unit) => Unit): Task[A] =
    IO.async { kk: Callback[Throwable, A] =>
      k(eitherToExitResult andThen kk)
    }

  override def asyncF[A](k: (Either[Throwable, A] => Unit) => Task[Unit]): Task[A] =
    IO.asyncPure { kk: Callback[Throwable, A] =>
      k(eitherToExitResult andThen kk)
    }

  override def suspend[A](thunk: => Task[A]): Task[A] =
    IO.suspend(
      try {
        thunk
      } catch {
        case NonFatal(e) => IO.fail(e)
      }
    )

  override def bracket[A, B](acquire: Task[A])(use: A => Task[B])(
    release: A => Task[Unit]
  ): Task[B] = IO.bracket(acquire)(release(_).catchAll(IO.terminate(_)))(use)

  override def bracketCase[A, B](
    acquire: Task[A]
  )(use: A => Task[B])(release: (A, ExitCase[Throwable]) => Task[Unit]): Task[B] =
    acquire.bracket0[Throwable, B] { (a, exitResult) =>
      val exitCase = exitResult match {
        case ExitResult.Completed(_)           => ExitCase.Completed
        case ExitResult.Failed(error, defects) => ExitCase.Error(Errors.UnhandledError(error, defects))
        case ExitResult.Terminated(Nil)        => ExitCase.Error(Errors.TerminatedFiber)
        case ExitResult.Terminated(t :: _)     => ExitCase.Error(t)
      }
      release(a, exitCase)
        .catchAll(IO.terminate(_))
    }(use)
}

private class CatsMonad[E] extends Monad[IO[E, ?]] {
  override def pure[A](a: A): IO[E, A]                                 = IO.now(a)
  override def map[A, B](fa: IO[E, A])(f: A => B): IO[E, B]            = fa.map(f)
  override def flatMap[A, B](fa: IO[E, A])(f: A => IO[E, B]): IO[E, B] = fa.flatMap(f)
  override def tailRecM[A, B](a: A)(f: A => IO[E, Either[A, B]]): IO[E, B] =
    f(a).flatMap {
      case Left(l)  => tailRecM(l)(f)
      case Right(r) => IO.now(r)
    }
}

private class CatsMonadError[E] extends CatsMonad[E] with MonadError[IO[E, ?], E] {
  override def handleErrorWith[A](fa: IO[E, A])(f: E => IO[E, A]): IO[E, A] = fa.catchAll(f)
  override def raiseError[A](e: E): IO[E, A]                                = IO.fail(e)
}

// lossy, throws away errors using the "first success" interpretation of SemigroupK
private trait CatsSemigroupK[E] extends SemigroupK[IO[E, ?]] {
  override def combineK[A](a: IO[E, A], b: IO[E, A]): IO[E, A] = a.orElse(b)
}

private class CatsAlternative[E: Monoid] extends CatsMonadError[E] with Alternative[IO[E, ?]] {
  override def combineK[A](a: IO[E, A], b: IO[E, A]): IO[E, A] =
    a.catchAll { e1 =>
      b.catchAll { e2 =>
        IO.fail(Monoid[E].combine(e1, e2))
      }
    }
  override def empty[A]: IO[E, A] = raiseError(Monoid[E].empty)
}

trait CatsBifunctor extends Bifunctor[IO] {
  override def bimap[A, B, C, D](fab: IO[A, B])(f: A => C, g: B => D): IO[C, D] =
    fab.bimap(f, g)
}
