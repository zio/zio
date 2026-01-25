package zio

import zio.Console._

object HelloWorldFinalizersAfterInterruptApp extends ZIOAppDefault {
  override def run =
    for {
      _ <- printLine("Hello, World! Press Ctrl+C to interrupt...").ensuring(printLine("Executing finalizer...").orDie)
      _ <- ZIO.never
    } yield ()
}
