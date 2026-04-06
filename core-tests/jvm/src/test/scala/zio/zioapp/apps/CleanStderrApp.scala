package zio.zioapp.apps

import zio._

/**
 * Tests regression #9807: on clean shutdown via SIGINT, stderr should NOT
 * contain uncaught exception traces (e.g., "Exception in thread" or
 * "FiberFailure"). This app adds an extra JVM shutdown hook that sleeps
 * for 2 seconds to reproduce the race condition described in the issue.
 */
object CleanStderrApp extends ZIOAppDefault {

  // Simulate a slow non-ZIO shutdown hook that keeps the JVM alive
  // long enough for the main thread to print the FiberFailure
  java.lang.Runtime.getRuntime.addShutdownHook(new Thread("slow-hook") {
    override def run(): Unit = Thread.sleep(2000)
  })

  val run: ZIO[Any, Nothing, Nothing] =
    Console.printLine("APP_READY").orDie *> ZIO.never
}
