package zio.zioapp.apps

import zio._

object MultipleFinalizersApp extends ZIOAppDefault {
  def run =
    ZIO.scoped {
      ZIO.acquireRelease(ZIO.unit)(_ => Console.printLine("FINALIZER_OUTER").orDie) *>
        ZIO.scoped {
          ZIO.acquireRelease(Console.printLine("APP_READY"))(_ => Console.printLine("FINALIZER_INNER").orDie) *>
            ZIO.never
        }
    }
}
