package scalaz.zio
package interop

import cats.effect
import cats.effect.Effect
import cats.syntax.all._

import scala.util.control.NonFatal

object catz extends RTS {

  implicit val catsEffectInstance: Effect[Task] = new Effect[Task] {
    def runAsync[A](
      fa: Task[A]
    )(cb: Either[Throwable, A] => effect.IO[Unit]): effect.IO[Unit] = {
      val cbZ2C: ExitResult[Throwable, A] => Either[Throwable, A] = {
        case ExitResult.Completed(a)       => Right(a)
        case ExitResult.Failed(t, _)       => Left(t)
        case ExitResult.Terminated(Nil)    => Left(Errors.TerminatedFiber)
        case ExitResult.Terminated(t :: _) => Left(t)
      }
      effect.IO {
        unsafeRunAsync(fa) {
          cb.compose(cbZ2C).andThen(_.unsafeRunAsync(_ => ()))
        }
      }.attempt.void
    }

    def async[A](k: (Either[Throwable, A] => Unit) => Unit): Task[A] = {
      val kk = k.compose[ExitResult[Throwable, A] => Unit] {
        _.compose[Either[Throwable, A]] {
          case Left(t)  => ExitResult.Failed(t)
          case Right(r) => ExitResult.Completed(r)
        }
      }

      IO.async(kk)
    }

    def suspend[A](thunk: => Task[A]): Task[A] = IO.suspend(
      try {
        thunk
      } catch {
        case NonFatal(e) => IO.fail(e)
      }
    )

    def raiseError[A](e: Throwable): Task[A] = IO.fail(e)

    def handleErrorWith[A](fa: Task[A])(f: Throwable => Task[A]): Task[A] =
      fa.catchAll(f)

    def pure[A](x: A): Task[A] = IO.now(x)

    def flatMap[A, B](fa: Task[A])(f: A => Task[B]): Task[B] = fa.flatMap(f)

    //LOL monad "law"
    def tailRecM[A, B](a: A)(f: A => Task[Either[A, B]]): Task[B] =
      f(a).flatMap {
        case Left(l)  => tailRecM(l)(f)
        case Right(r) => IO.now(r)
      }
  }

}
