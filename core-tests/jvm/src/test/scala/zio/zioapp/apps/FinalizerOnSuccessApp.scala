package zio.zioapp.apps

import zio._

object FinalizerOnSuccessApp extends ZIOAppDefault {
  def run =
    ZIO.scoped {
      ZIO.acquireRelease(ZIO.unit)(_ => Console.printLine("FINALIZER_RAN").orDie) *>
        ZIO.unit
    }
}
