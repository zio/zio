package zio

import zio.Console._

object FinalizerExceedsTheTimeoutApp extends ZIOAppDefault {
  // Wait at most 5 seconds for finalizers to complete on SIGINT
  override def gracefulShutdownTimeout: Duration = 5.seconds

  val run: ZIO[ZIOAppArgs with Scope, Any, Any] =
    ZIO.acquireReleaseWith(
      acquire = printLine("Acquiring resource...").as("MyResource")
    )(release =
      _ =>
        printLine("Releasing resource (20s) ...").orDie *> ZIO.sleep(20.seconds) *>
          printLine("Cleanup done").orDie
    ) { resource =>
      printLine(s"Running with $resource, press Ctrl+C to interrupt") *> ZIO.never
    }
}
