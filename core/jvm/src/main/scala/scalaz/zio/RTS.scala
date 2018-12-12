package scalaz.zio

import scalaz.zio.internal.{ Env, Executor }

trait RTS {
  lazy val env =
    Env.newDefaultEnv(cause => IO.sync(println(cause.toString)))

  final def unsafeRun[E, A](io: IO[E, A]): A =
    env.unsafeRun(io)

  final def unsafeRunSync[E, A](io: IO[E, A]): ExitResult[E, A] =
    env.unsafeRunSync(io)

  final def unsafeRunAsync[E, A](io: IO[E, A])(k: ExitResult[E, A] => Unit): Unit =
    env.unsafeRunAsync(io, k)

  final def shutdown(): Unit = {
    env.executor(Executor.Yielding).shutdown()
    env.executor(Executor.Unyielding).shutdown()
    env.scheduler.shutdown()
  }
}
