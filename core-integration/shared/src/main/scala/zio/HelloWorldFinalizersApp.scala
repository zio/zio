package zio

import zio.Console._

object HelloWorldFinalizersApp extends ZIOAppDefault {
  override def run =
    printLine("Hello, World!").ensuring(printLine("Executing finalizer...").orDie)
}
