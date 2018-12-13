package scalaz.zio

import scalaz.zio.internal.Env

trait RTS {
  lazy val env =
    Env.newDefaultEnv(cause => IO.sync(println(cause.toString)))

  final def unsafeRun[E, A](io: IO[E, A], timeout: Long = Long.MaxValue): A =
    env.unsafeRun(io, timeout)

  final def unsafeRunSync[E, A](io: IO[E, A], timeout: Long = Long.MaxValue): ExitResult[E, A] =
    env.unsafeRunSync(io, timeout)

  final def unsafeRunAsync[E, A](io: IO[E, A])(k: ExitResult[E, A] => Unit): Unit =
    env.unsafeRunAsync(io, k)
}
