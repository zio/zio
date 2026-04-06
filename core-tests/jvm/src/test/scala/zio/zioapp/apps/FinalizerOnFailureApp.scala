package zio.zioapp.apps

import zio._

/**
 * Registers a scoped finalizer then fails. The finalizer must run even when the
 * app fails.
 */
object FinalizerOnFailureApp extends ZIOAppDefault {
  val run: ZIO[Scope, String, Nothing] =
    ZIO.acquireRelease(Console.printLine("APP_READY").orDie)(_ => Console.printLine("FINALIZER_RAN").orDie) *> ZIO.fail(
      "app failed"
    )
}
