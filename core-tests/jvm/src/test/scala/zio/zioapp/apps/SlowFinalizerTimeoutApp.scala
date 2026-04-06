package zio.zioapp.apps

import zio._

// A slow finalizer that should be cut off by gracefulShutdownTimeout.
// Timeout set to 1 second while finalizer tries to sleep for 30 seconds.
object SlowFinalizerTimeoutApp extends ZIOAppDefault {

  override def gracefulShutdownTimeout: Duration = 1.second

  val run = ZIO.scoped {
    ZIO.acquireRelease(ZIO.succeed(println("APP_READY")))(_ =>
      ZIO.sleep(30.seconds) *> ZIO.succeed(println("SLOW_FIN_DONE"))
    ) *> ZIO.never
  }
}
