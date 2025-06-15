package zio.app

import zio._

import java.util.concurrent.TimeUnit

object InterruptionBugExample extends ZIOAppDefault {

  // This finalizer simulates a slow cleanup task that takes 3 seconds.
  private val slowFinalizer =
    ZIO.acquireReleaseWith(
      acquire = Console.printLine("Resource acquired. Application is running, press Ctrl+C to interrupt.").orDie
    )(
      release =
        Console.printLine("Finalizer started, will take 3 seconds to complete...").orDie *>
          ZIO.sleep(3.seconds) *>
          Console.printLine("Finalizer finished.").orDie
    )(
      use = _ => ZIO.never // App runs forever until interrupted
    )

  /**
   * This hardcoded timeout of 1 second will be used, ignoring any
   * `-Dzio.app.shutdown.timeout` property passed on the command line.
   */
  override def gracefulShutdownTimeout: Duration = 1.second

  /**
   * When this app is interrupted externally (e.g., via Ctrl+C), the
   * `exitCode` logic in `ZIOAppPlatformSpecific` incorrectly treats the
   * interruption as a failure, causing the app to return exit code 1
   * instead of 0.
   *
   * Additionally, the 1-second `gracefulShutdownTimeout` defined above
   * will cause the shutdown to time out before the 3-second finalizer can
   * complete, preventing "Finalizer finished." from being printed.
   */
  override def run = slowFinalizer
} 