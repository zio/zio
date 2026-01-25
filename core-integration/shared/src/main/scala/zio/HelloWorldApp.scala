package zio

import zio.Console._

object HelloWorldApp extends ZIOAppDefault {

  override def run = printLine("Hello, World!")
}
