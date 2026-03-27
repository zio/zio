package zio.testapps

import zio._

object TestAppMultipleFinalizers extends ZIOAppDefault {
  def run =
    (Console.printLine("APP_STARTED") *> ZIO.never)
      .ensuring(Console.printLine("FINALIZER_1_RAN").orDie)
      .ensuring(Console.printLine("FINALIZER_2_RAN").orDie)
}
