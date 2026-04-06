package zio.zioapp.apps

import zio._

/**
 * Runs forever after acquiring a resource. When SIGINT arrives, the finalizer
 * must still run. Covers regression #9901 where finalizers stopped executing
 * on Ctrl+C in ZIO 2.1.18.
 */
object FinalizerOnSignalApp extends ZIOAppDefault {
  val run: ZIO[Scope, Nothing, Nothing] =
    ZIO.acquireRelease(Console.printLine("APP_READY").orDie)(_ =>
      Console.printLine("FINALIZER_RAN").orDie
    ) *> ZIO.never
}
