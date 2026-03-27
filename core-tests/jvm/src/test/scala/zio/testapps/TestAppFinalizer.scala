package zio.testapps

import zio._

object TestAppFinalizer extends ZIOAppDefault {
  def run =
    (Console.printLine("APP_STARTED") *> ZIO.never)
      .ensuring(Console.printLine("FINALIZER_RAN").orDie)
}
