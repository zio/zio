package zio.zioapp.apps

import zio._

// Multiple finalizers should ALL run in reverse order on SIGINT
object MultiFinalizerOrderApp extends ZIOAppDefault {
  val run = ZIO.scoped {
    for {
      _ <- ZIO.acquireRelease(ZIO.unit)(_ => ZIO.succeed(println("FIN_A")))
      _ <- ZIO.acquireRelease(ZIO.unit)(_ => ZIO.succeed(println("FIN_B")))
      _ <- ZIO.acquireRelease(ZIO.unit)(_ => ZIO.succeed(println("FIN_C")))
      _ <- ZIO.succeed(println("APP_READY"))
      _ <- ZIO.never
    } yield ()
  }
}
