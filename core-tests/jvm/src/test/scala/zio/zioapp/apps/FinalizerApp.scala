package zio.zioapp.apps

import zio._

object FinalizerApp extends ZIOAppDefault {
  def run =
    ZIO.scoped {
      ZIO
        .acquireRelease(Console.printLine("APP_READY"))(_ => Console.printLine("FINALIZER_RAN").orDie) *>
        ZIO.never
    }
}
