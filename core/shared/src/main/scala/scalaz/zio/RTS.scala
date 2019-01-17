package scalaz.zio

import scalaz.zio.internal.impls.Env

trait RTS {
  lazy val env =
    Env.newDefaultEnv {
      case cause if cause.interrupted => IO.unit // do not log interruptions
      case cause                      => IO.sync(println(cause.toString))
    }

  /**
   * Awaits for the result of the fiber to be computed.
   * In Javascript, this operation will not, in general, succeed because it is not possible to block for the result.
   * However, it may succeed in some cases if the IO is purely synchronous.
   */
  final def unsafeRun[E, A](io: IO[E, A]): A =
    env.unsafeRun(io)

  /**
   * Awaits for the result of the fiber to be computed.
   * In Javascript, this operation will not, in general, succeed because it is not possible to block for the result.
   * However, it may succeed in some cases if the IO is purely synchronous.
   */
  final def unsafeRunSync[E, A](io: IO[E, A]): Exit[E, A] =
    env.unsafeRunSync(io)

  /**
   * Runs the `io` asynchronously.
   */
  final def unsafeRunAsync[E, A](io: IO[E, A])(k: Exit[E, A] => Unit): Unit =
    env.unsafeRunAsync(io, k)

  final def shutdown(): Unit = env.shutdown()
}
