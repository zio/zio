package zio.testapps

import zio._

object TestAppCleanShutdown extends ZIOAppDefault {
  def run =
    ZIO.attempt {
      java.lang.Runtime.getRuntime.addShutdownHook(new Thread(() => {
        java.lang.System.err.println("SHUTDOWN_HOOK_RAN")
        Thread.sleep(100L)
      }))
    }.orDie *> (Console.printLine("APP_STARTED") *> ZIO.never)
      .ensuring(Console.printLine("FINALIZER_RAN").orDie)
}
