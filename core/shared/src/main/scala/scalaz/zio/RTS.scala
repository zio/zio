package scalaz.zio

import scalaz.zio.internal.Env
import scalaz.zio.ExitResult.Cause

trait RTS {
  lazy val env = Env.newDefaultEnv()

  val reportError: Cause[Any] => IO[Nothing, _] =
    cause => IO.sync(println(cause.toString))

  final def unsafeRun[E, A](io: IO[E, A]): A =
    env.unsafeRun(reportError, io)

  final def unsafeRunSync[E, A](io: IO[E, A]): ExitResult[E, A] =
    env.unsafeRunSync(reportError, io)

  final def unsafeRunAsync[E, A](io: IO[E, A])(k: ExitResult[E, A] => Unit): Unit =
    env.unsafeRunAsync(reportError, io, k)
}
