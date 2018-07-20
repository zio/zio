package scalaz.zio
package interop

import cats.effect
import cats.effect.Effect
import cats.syntax.all._

object catz extends RTS {

  case class FromToThrowable[E](to: E => Throwable, from: Throwable => Option[E])

  object FromToThrowable {
    implicit val IdFromToThrowable: FromToThrowable[Throwable] = FromToThrowable(identity, Some.apply)
  }

  implicit def catsEffectInstance[E](implicit ftt: FromToThrowable[E]): Effect[IO[E, ?]] = new Effect[IO[E, ?]] {
    def runAsync[A](
      fa: IO[E, A]
    )(cb: Either[Throwable, A] => effect.IO[Unit]): effect.IO[Unit] = {
      val cbZ2C: ExitResult[E, A] => Either[Throwable, A] = {
        case ExitResult.Completed(a)  => Right(a)
        case ExitResult.Failed(e)     => Left(ftt.to(e))
        case ExitResult.Terminated(t) => Left(t)
      }
      effect.IO {
        unsafeRunAsync(fa) {
          cb.compose(cbZ2C).andThen(_.unsafeRunAsync(_ => ()))
        }
      }.attempt.void
    }

    def async[A](k: (Either[Throwable, A] => Unit) => Unit): IO[E, A] = {
      val kk = k.compose[ExitResult[E, A] => Unit] {
        _.compose[Either[Throwable, A]] {
          case Left(t) =>
            ftt.from(t).fold[ExitResult[E, A]](ExitResult.Terminated(t))(ExitResult.Failed(_))
          case Right(r) =>
            ExitResult.Completed(r)
        }
      }

      IO.async(kk)
    }

    def suspend[A](thunk: => IO[E, A]): IO[E, A] = IO.suspend(
      try {
        thunk
      } catch {
        case t: Throwable if nonFatal(t) =>
          ftt.from(t).fold[IO[E, A]](IO.terminate(t))(IO.fail(_))
      }
    )

    def raiseError[A](t: Throwable): IO[E, A] =
      ftt.from(t).fold[IO[E, A]](IO.terminate(t))(IO.fail(_))

    def handleErrorWith[A](fa: IO[E, A])(f: Throwable => IO[E, A]): IO[E, A] =
      fa.catchAll(f.compose(ftt.to))

    def pure[A](x: A): IO[E, A] = IO.now(x)

    def flatMap[A, B](fa: IO[E, A])(f: A => IO[E, B]): IO[E, B] = fa.flatMap(f)

    //LOL monad "law"
    def tailRecM[A, B](a: A)(f: A => IO[E, Either[A, B]]): IO[E, B] =
      f(a).flatMap {
        case Left(l)  => tailRecM(l)(f)
        case Right(r) => IO.now(r)
      }
  }

}
