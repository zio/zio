package zio.zioapp.apps

import zio._

/**
 * Forks a daemon fiber that ticks forever, then blocks.
 * On SIGINT the process must still exit cleanly (daemon fibers interrupted).
 */
object DaemonFiberCleanupApp extends ZIOAppDefault {
  val run: ZIO[Any, Nothing, Nothing] =
    for {
      _ <- (ZIO.sleep(50.millis) *> Console.printLine("TICK").orDie).forever.forkDaemon
      _ <- Console.printLine("APP_READY").orDie
      n <- ZIO.never
    } yield n
}
