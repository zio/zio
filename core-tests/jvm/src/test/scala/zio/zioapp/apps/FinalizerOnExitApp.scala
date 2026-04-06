package zio.zioapp.apps

import zio._

/**
 * Registers a scoped finalizer and then exits naturally. The finalizer must run
 * on normal completion.
 */
object FinalizerOnExitApp extends ZIOAppDefault {
  val run: ZIO[Scope, Nothing, Unit] =
    ZIO.acquireRelease(Console.printLine("APP_READY").orDie)(_ => Console.printLine("FINALIZER_RAN").orDie)
}
