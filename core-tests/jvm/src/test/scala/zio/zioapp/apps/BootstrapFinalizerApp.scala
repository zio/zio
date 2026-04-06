package zio.zioapp.apps

import zio._

/**
 * Bootstrap layer acquires a resource. On SIGINT, the bootstrap layer's
 * finalizer must also run (not just the run-level finalizers).
 */
object BootstrapFinalizerApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Nothing, Any] =
    ZLayer.scoped(
      ZIO.acquireRelease(Console.printLine("BOOTSTRAP_ACQUIRED").orDie)(_ =>
        Console.printLine("BOOTSTRAP_RELEASED").orDie
      )
    )

  val run: ZIO[Any, Nothing, Nothing] =
    Console.printLine("APP_READY").orDie *> ZIO.never
}
