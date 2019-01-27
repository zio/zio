package scalaz.zio

import scalaz.zio.clock.Clock
import scalaz.zio.console.Console
import scalaz.zio.internal.Env

trait RTS {
  type BuiltIn = Clock with Console
  val BuiltIn = new Clock.Live with Console.Live

  lazy val env =
    Env.newDefaultEnv {
      case cause if cause.interrupted => IO.unit // do not log interruptions
      case cause                      => IO.sync(println(cause.toString))
    }

  final def unsafeRun[E, A](io: ZIO[BuiltIn, E, A]): A =
    env.unsafeRun(io.provide(BuiltIn))

  final def unsafeRunSync[E, A](io: ZIO[BuiltIn, E, A]): Exit[E, A] =
    env.unsafeRunSync(io.provide(BuiltIn))

  final def unsafeRunAsync[E, A](io: ZIO[BuiltIn, E, A])(k: Exit[E, A] => Unit): Unit =
    env.unsafeRunAsync(io.provide(BuiltIn), k)

  final def shutdown(): Unit = env.shutdown()
}
