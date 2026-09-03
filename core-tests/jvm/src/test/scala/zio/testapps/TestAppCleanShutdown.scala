package zio.testapps

import zio._

object TestAppCleanShutdown extends ZIOAppDefault {
  def run = ZIO.succeed {
    java.lang.Runtime.getRuntime.addShutdownHook(new Thread(() => {
      java.lang.System.err.println("SHUTDOWN_HOOK_RAN")
      Thread.sleep(1000)
    }))
  } *> Console.printLine("APP_STARTED") *> ZIO.never
}
