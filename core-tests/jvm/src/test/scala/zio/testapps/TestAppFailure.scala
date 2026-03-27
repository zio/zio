package zio.testapps

import zio._

object TestAppFailure extends ZIOAppDefault {
  def run = Console.printLine("APP_STARTED") *> ZIO.fail("intentional failure")
}