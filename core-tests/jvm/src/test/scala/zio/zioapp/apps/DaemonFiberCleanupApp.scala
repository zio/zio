package zio.zioapp.apps

import zio._

// Tests that daemon fibers are interrupted during shutdown
object DaemonFiberCleanupApp extends ZIOAppDefault {
  val run = for {
    _ <- ZIO.succeed(println("APP_READY"))
    _ <- (ZIO.sleep(100.millis) *> ZIO.succeed(println("DAEMON_TICK"))).forever.forkDaemon
    _ <- ZIO.never
  } yield ()
}
