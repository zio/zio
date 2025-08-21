package zio.runtime.fixtures

import zio._

object HangingApp extends ZIOAppDefault {
  override def run = ZIO.never
}

object FinalizingApp extends ZIOAppDefault {
  override def run =
    ZIO.acquireRelease(ZIO.succeed("resource"))(_ =>
      ZIO.logInfo("Finalizer ran") *> ZIO.sleep(1.second)
    ) *> ZIO.sleep(5.seconds)
}

object LoggingApp extends ZIOAppDefault {
  override def run =
    ZIO.logInfo("App started") *> ZIO.sleep(2.seconds) *> ZIO.logInfo("App finished")
}

object TimeoutApp extends ZIOAppDefault {
  override def run =
    ZIO.sleep(10.seconds).timeoutFail(new RuntimeException("Timed out"))(3.seconds)
}
