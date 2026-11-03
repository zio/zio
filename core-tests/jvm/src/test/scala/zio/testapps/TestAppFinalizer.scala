package zio.testapps

import zio._

object TestAppFinalizer extends ZIOAppDefault {
  def run = ZIO.scoped {
    ZIO.acquireRelease(Console.printLine("APP_STARTED"))(_ => Console.printLine("FINALIZER_RAN").orDie) *> ZIO.never
  }
}
