package zio.zioapp.apps

import zio._

object SignalHandlerApp extends ZIOAppDefault {
  def run =
    ZIO.scoped {
      Console.printLine("APP_READY") *> ZIO.never
    }
}
