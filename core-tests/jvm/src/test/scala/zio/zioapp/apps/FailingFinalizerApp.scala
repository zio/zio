package zio.zioapp.apps

import zio._

/**
 * Three scoped resources: inner one's finalizer dies. The other two finalizers
 * must still run. Tests that a defect in one finalizer does not prevent others.
 */
object FailingFinalizerApp extends ZIOAppDefault {
  val run: ZIO[Scope, Nothing, Nothing] =
    for {
      _ <- ZIO.acquireRelease(ZIO.unit)(_ => Console.printLine("SAFE_FIN").orDie)
      _ <- ZIO.acquireRelease(ZIO.unit)(_ => ZIO.die(new RuntimeException("finalizer exploded")))
      _ <- ZIO.acquireRelease(ZIO.unit)(_ => Console.printLine("BEFORE_CRASH_FIN").orDie)
      _ <- Console.printLine("APP_READY").orDie
      n <- ZIO.never
    } yield n
}
