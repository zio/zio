package zio.app

import zio._

object InterruptionBugExample extends ZIOAppDefault {

  /**
   * This hardcoded timeout of 1 second will be used, ignoring any
   * `-Dzio.app.shutdown.timeout` property passed on the command line.
   */
  override def gracefulShutdownTimeout: Duration = 1.second

  /**
   * When this app is interrupted externally (e.g., via Ctrl+C), the
   * `exitCode` logic in `ZIOAppPlatformSpecific` incorrectly treats the
   * interruption as a failure, returning exit code 1.
   *
   * Additionally, the `gracefulShutdownTimeout` above (1 second) will be
   * used, causing the 3-second finalizer to be interrupted prematurely.
   * The test expects "Finalizer finished" to be printed, which will not happen.
   */
  override val run =
    ZIO.acquireReleaseWith(
      // acquire
      Console.printLine("Resource acquired. Application is running, press Ctrl+C to interrupt.").orDie
    )(
      // release
      _ =>
        Console.printLine("Finalizer started, will take 3 seconds to complete...").orDie *>
          ZIO.sleep(3.seconds) *>
          Console.printLine("Finalizer finished.").orDie
    )(
      // use
      _ => ZIO.never // App runs forever until interrupted
    )
}