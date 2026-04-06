package zio.zioapp.apps

import zio._

// Runs forever, prints FINALIZER_RAN when interrupted via SIGINT
// Covers regression #9901 - finalizers should complete on shutdown
object FinalizerOnSignalApp extends ZIOAppDefault {
  val run = ZIO.scoped {
    ZIO.acquireRelease(ZIO.succeed(println("APP_READY")))(_ => ZIO.succeed(println("FINALIZER_RAN"))) *> ZIO.never
  }
}
