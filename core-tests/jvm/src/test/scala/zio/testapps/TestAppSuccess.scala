package zio.testapps

import zio._

object TestAppSuccess extends ZIOAppDefault {
  def run = Console.printLine("APP_SUCCESS")
}
