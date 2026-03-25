package zio.zioapp.apps

import zio._

object SlowFinalizerApp extends ZIOAppDefault {
  def run =
    ZIO.scoped {
      ZIO
        .acquireRelease(Console.printLine("APP_READY"))(_ =>
          ZIO.sleep(10.seconds) *> Console.printLine("SLOW_FINALIZER_DONE").orDie
        ) *>
        ZIO.never
    }
}
