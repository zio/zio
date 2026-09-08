package zio.testapps

import zio._

object TestAppMultipleFinalizers extends ZIOAppDefault {
  def run = ZIO.scoped {
    for {
      _ <- ZIO.acquireRelease(Console.printLine("ACQUIRED_1"))(_ => Console.printLine("FINALIZER_1_RAN").orDie)
      _ <- ZIO.acquireRelease(Console.printLine("ACQUIRED_2"))(_ => Console.printLine("FINALIZER_2_RAN").orDie)
      _ <- Console.printLine("APP_STARTED")
      _ <- ZIO.never
    } yield ()
  }
}
