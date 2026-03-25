package zio.zioapp.apps

import zio._

object DaemonFiberApp extends ZIOAppDefault {
  def run =
    ZIO.scoped {
      (Console.printLine("DAEMON_RUNNING") *> ZIO.yieldNow).forever.forkDaemon *>
        Console.printLine("APP_READY") *>
        ZIO.never
    }
}
