package scalaz.zio

import scalaz.zio.clock.Clock
import scalaz.zio.console.Console
import scalaz.zio.internal.Env

trait RTS {
  lazy val env =
    Env.newDefaultEnv {
      case cause if cause.interrupted => IO.unit // do not log interruptions
      case cause                      => IO.sync(println(cause.toString))
    }

  final def unsafeRun[E, A](io: ZIO[Clock with Console, E, A]): A =
    env.unsafeRun(io.provide(new Clock.Live with Console.Live))

  final def unsafeRunSync[E, A](io: IO[E, A]): Exit[E, A] =
    env.unsafeRunSync(io)

  final def unsafeRunAsync[E, A](io: IO[E, A])(k: Exit[E, A] => Unit): Unit =
    env.unsafeRunAsync(io, k)

  final def shutdown(): Unit = env.shutdown()
}
