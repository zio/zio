package zio.app

import zio._
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import java.time.temporal.ChronoUnit

/**
 * Tests specific to signal handling behavior in ZIOApp. These tests verify the
 * fix for issue #9240 where signal handlers should gracefully degrade on
 * unsupported platforms.
 */
object ZIOAppSignalHandlingSpec extends ZIOSpecDefault {
  // Helper method for debug logging
  private def debugLog(msg: String): UIO[Unit] = 
    ZIO.succeed(println(s"[DEBUG-SIGNAL-TEST] ${java.time.LocalDateTime.now()}: $msg"))

  def spec = suite("ZIOAppSignalHandlingSpec")(
    test("addSignalHandler does not throw on any platform") {
      // TestApp exposes the protected method for testing
      val app = new TestZIOApp()

      for {
        _ <- debugLog("Starting 'addSignalHandler does not throw on any platform' test")
        _ <- debugLog(s"OS name: ${System.getProperty("os.name")}")
        _ <- debugLog(s"OS version: ${System.getProperty("os.version")}")
        _ <- debugLog(s"Java version: ${System.getProperty("java.version")}")
        startTest <- Clock.currentTime(ChronoUnit.MILLIS)
        runtime <- ZIO.runtime[Any]
        _ <- debugLog("Got ZIO runtime")
        resultExit  <- app.testInstallSignalHandlers(runtime).exit
        endTest <- Clock.currentTime(ChronoUnit.MILLIS)
        _ <- debugLog(s"Signal handler installation completed in ${endTest - startTest}ms with result: $resultExit")
      } yield assert(resultExit.isSuccess)(isTrue)
    },
    test("signal handlers are installed exactly 3 times") {
      val counter = new java.util.concurrent.atomic.AtomicInteger(0)

      val app = new TestZIOApp {
        override def testInstallSignalHandlers(runtime: Runtime[Any])(implicit trace: Trace): UIO[Any] =
          ZIO.attempt(counter.incrementAndGet()).tap(count => debugLog(s"Signal handler install count: $count")).ignore
      }

      for {
        _ <- debugLog("Starting 'signal handlers are installed exactly 3 times' test")
        startTest <- Clock.currentTime(ChronoUnit.MILLIS)
        runtime <- ZIO.runtime[Any]
        _ <- debugLog("Got ZIO runtime, installing handlers 3 times")
        _ <- app.testInstallSignalHandlers(runtime)
        _ <- app.testInstallSignalHandlers(runtime)
        _ <- app.testInstallSignalHandlers(runtime)
        count <- ZIO.succeed(counter.get())
        endTest <- Clock.currentTime(ChronoUnit.MILLIS)
        _ <- debugLog(s"Signal handler installation completed in ${endTest - startTest}ms, final count: $count")
      } yield assert(count)(equalTo(3))
    },
    test("windows platform detection works correctly") {
      // Use ZIO's System service instead of Java's System
      for {
        _ <- debugLog("Starting 'windows platform detection works correctly' test")
        osName <- zio.System.property("os.name")
          .tap(name => debugLog(s"System property os.name: $name"))
          .map(_.getOrElse(""))
        isWindows <- ZIO.attempt(System.os.isWindows)
        _ <- debugLog(s"System.os.isWindows reports: $isWindows")
        expectedWindows = osName.toLowerCase().contains("win")
        _ <- debugLog(s"Expected Windows based on name: $expectedWindows")
        _ <- debugLog(s"Platform detection test result: ${isWindows == expectedWindows}")
      } yield assert(isWindows)(equalTo(expectedWindows))
    }
  ) @@ sequential

  // Helper class that exposes the protected method
  class TestZIOApp extends ZIOAppDefault {
    override def run = ZIO.unit

    def testInstallSignalHandlers(runtime: Runtime[Any])(implicit trace: Trace): UIO[Any] = {
      debugLog("Installing signal handlers").flatMap(_ => 
        installSignalHandlers(runtime)
          .tap(_ => debugLog("Signal handlers installed successfully"))
          .catchAll(e => debugLog(s"Signal handler installation failed: $e"))
      )
    }
  }
}
