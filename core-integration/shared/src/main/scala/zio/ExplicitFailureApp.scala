package zio

import zio.Console._

object ExplicitFailureApp extends ZIOAppDefault {
  override def run =
    printLine("Hello, World!") *> ZIO.fail(new RuntimeException("Explicit Failure"))
}
