package zio.zioapp.apps

import zio._

/**
 * Acquires three resources in order A -> B -> C, then blocks forever.
 * On SIGINT, finalizers must ALL run and in reverse order: C, B, A.
 * Covers regression #9901.
 */
object MultiFinalizerOrderApp extends ZIOAppDefault {
  val run: ZIO[Scope, Nothing, Nothing] =
    for {
      _ <- ZIO.acquireRelease(ZIO.unit)(_ => Console.printLine("FIN_A").orDie)
      _ <- ZIO.acquireRelease(ZIO.unit)(_ => Console.printLine("FIN_B").orDie)
      _ <- ZIO.acquireRelease(ZIO.unit)(_ => Console.printLine("FIN_C").orDie)
      _ <- Console.printLine("APP_READY").orDie
      n <- ZIO.never
    } yield n
}
