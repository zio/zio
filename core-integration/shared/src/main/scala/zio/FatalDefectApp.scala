package zio

import zio.Console._

object FatalDefectApp extends ZIOAppDefault {
  override def run =
    printLine("Hello, World!") *> ZIO.dieMessage("Fatal Defect")
}
