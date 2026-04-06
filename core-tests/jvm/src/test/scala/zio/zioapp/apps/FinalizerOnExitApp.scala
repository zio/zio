package zio.zioapp.apps

import zio._

// Tests that a finalizer on a scoped resource runs to completion on natural exit
object FinalizerOnExitApp extends ZIOAppDefault {
  val run = ZIO.scoped {
    ZIO.acquireRelease(ZIO.succeed(println("APP_READY")))(_ => ZIO.succeed(println("FINALIZER_RAN")))
  }
}
