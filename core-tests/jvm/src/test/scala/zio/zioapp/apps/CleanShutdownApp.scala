package zio.zioapp.apps

import zio._

object CleanShutdownApp extends ZIOAppDefault {
  def run =
    ZIO.scoped {
      ZIO.succeed {
        java.lang.Runtime.getRuntime.addShutdownHook(
          new Thread(() => println("JVM_HOOK_RAN"))
        )
      } *>
        ZIO
          .acquireRelease(Console.printLine("APP_READY"))(_ => ZIO.unit) *>
        ZIO.never
    }
}
