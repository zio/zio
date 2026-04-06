package zio.zioapp.apps

import zio._

/**
 * Overrides gracefulShutdownTimeout to 1 second. The finalizer tries to sleep
 * for 30 seconds before printing a marker. Since the timeout is 1 second, the
 * marker must NOT appear — the finalizer should be cut off.
 */
object SlowFinalizerTimeoutApp extends ZIOAppDefault {

  override def gracefulShutdownTimeout: Duration = 1.second

  val run: ZIO[Scope, Nothing, Nothing] =
    ZIO.acquireRelease(Console.printLine("APP_READY").orDie)(_ =>
      ZIO.sleep(30.seconds) *> Console.printLine("SLOW_FIN_DONE").orDie
    ) *> ZIO.never
}
