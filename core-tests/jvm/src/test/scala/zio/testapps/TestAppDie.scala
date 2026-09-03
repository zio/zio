package zio.testapps

import zio._

object TestAppDie extends ZIOAppDefault {
  def run = Console.printLine("APP_STARTED") *> ZIO.die(new RuntimeException("boom"))
}
