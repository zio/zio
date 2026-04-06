package zio.zioapp.apps

import zio._

// Tests that a failing finalizer doesn't prevent other finalizers from running
object FailingFinalizerApp extends ZIOAppDefault {
  val run = ZIO.scoped {
    for {
      _ <- ZIO.acquireRelease(ZIO.unit)(_ => ZIO.succeed(println("SAFE_FIN")))
      _ <- ZIO.acquireRelease(ZIO.unit)(_ => ZIO.die(new RuntimeException("fin exploded")))
      _ <- ZIO.acquireRelease(ZIO.unit)(_ => ZIO.succeed(println("BEFORE_CRASH_FIN")))
      _ <- ZIO.succeed(println("APP_READY"))
      _ <- ZIO.never
    } yield ()
  }
}
