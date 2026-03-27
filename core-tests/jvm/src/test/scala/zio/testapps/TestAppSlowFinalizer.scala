package zio.testapps

import zio._

object TestAppSlowFinalizer extends ZIOAppDefault {
  override def gracefulShutdownTimeout: Duration = 2.seconds

  def run =
    (Console.printLine("APP_STARTED") *> ZIO.never)
      .ensuring(Console.printLine("SLOW_FINALIZER_STARTED").orDie *> ZIO.sleep(60.seconds))
}