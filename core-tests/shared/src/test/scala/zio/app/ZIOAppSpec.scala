package zio.app

import zio._
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

import java.nio.file.Path
import java.time.temporal.ChronoUnit
/**
 * Test suite for ZIOApp, focusing on:
 * 1. Normal completion behavior
 * 2. Error handling behavior
 * 3. Finalizer execution during shutdown
 * 4. Signal handling and graceful shutdown
 * 5. Timeout behavior
 */
object ZIOAppSpec extends ZIOSpecDefault {

  def spec = suite("ZIOAppSpec")(
    // Platform-independent tests
    suite("ZIOApp behavior")(
      test("successful exit code") {
        for {
          _ <- ZIO.unit // Test will be implemented based on platform
        } yield assertCompletes
      }
    ),

    // JVM-specific tests that require process management
    suite("ZIOApp JVM process tests")(
      test("successful app returns exit code 0") {
        for {
          process <- ProcessTestUtils.runApp("zio.app.TestApps$SuccessApp")
          exitCode <- process.waitForExit()
          _ <- process.destroy
        } yield assert(exitCode)(equalTo(0))
      },

      test("failing app returns non-zero exit code") {
        for {
          process <- ProcessTestUtils.runApp("zio.app.TestApps$FailureApp")
          exitCode <- process.waitForExit()
          _ <- process.destroy
        } yield assert(exitCode)(equalTo(42))
      },

      test("app with unhandled error returns exit code 1") {
        for {
          process <- ProcessTestUtils.runApp("zio.app.TestApps$CrashingApp")
          exitCode <- process.waitForExit()
          _ <- process.destroy
        } yield assert(exitCode)(equalTo(1))
      },

      test("finalizers run on normal completion") {
        for {
          process <- ProcessTestUtils.runApp("zio.app.TestApps$ResourceApp")
          _ <- process.waitForExit()
          output <- process.outputString
          _ <- process.destroy
        } yield assert(output)(containsString("Resource released"))
      },

      test("finalizers run when interrupted by signal") {
        for {
          process <- ProcessTestUtils.runApp("zio.app.TestApps$ResourceWithNeverApp")
          // Wait for app to start
          _ <- process.waitForOutput("Starting ResourceWithNeverApp")
          // Send interrupt signal
          _ <- process.sendSignal("INT")
          // Wait for process to exit
          _ <- process.waitForExit()
          output <- process.outputString
          _ <- process.destroy
        } yield assert(output)(containsString("Resource released"))
      },

      test("graceful shutdown timeout is respected") {
        for {
          // Run with a short timeout
          process <- ProcessTestUtils.runApp(
            "zio.app.TestApps$SlowFinalizerApp",
            Some(Duration.fromMillis(500))
          )
          // Wait for app to start
          _ <- process.waitForOutput("Starting SlowFinalizerApp")
          // Send interrupt signal
          _ <- process.sendSignal("INT")
          // Wait for process to exit
          startTime <- Clock.currentTime(ChronoUnit.MILLIS)
          _ <- process.waitForExit()
          endTime <- Clock.currentTime(ChronoUnit.MILLIS)
          output <- process.outputString
          _ <- process.destroy
          duration = Duration.fromMillis(endTime - startTime)
        } yield assert(output)(containsString("Starting slow finalizer")) &&
               assert(output)(not(containsString("Resource released"))) &&
               assert(duration.toMillis)(isLessThan(2000L))
      },

      test("custom graceful shutdown timeout allows longer finalizers") {
        for {
          // Run with a longer timeout
          process <- ProcessTestUtils.runApp(
            "zio.app.TestApps$SlowFinalizerApp",
            Some(Duration.fromMillis(3000))
          )
          // Wait for app to start
          _ <- process.waitForOutput("Starting SlowFinalizerApp")
          // Send interrupt signal
          _ <- process.sendSignal("INT")
          // Wait for process to exit
          _ <- process.waitForExit()
          outputStr <- process.outputString
          _ <- process.destroy
        } yield assert(outputStr)(containsString("Starting slow finalizer")) &&
               assert(outputStr)(containsString("Resource released"))
      },

      test("nested finalizers execute in correct order") {
        for {
          process <- ProcessTestUtils.runApp("zio.app.TestApps$NestedFinalizersApp")
          // Wait for app to start
          _ <- process.waitForOutput("Starting NestedFinalizersApp")
          // Send interrupt signal
          _ <- process.sendSignal("INT")
          // Wait for process to exit
          _ <- process.waitForExit()
          _ <- process.outputString
          lines <- process.output
          _ <- process.destroy
          
          // Find the indices of the finalizer messages
          innerFinalizerIndex = lines.indexWhere(_.contains("Inner resource released"))
          outerFinalizerIndex = lines.indexWhere(_.contains("Outer resource released"))
        } yield assert(innerFinalizerIndex)(isGreaterThanEqualTo(0)) &&
               assert(outerFinalizerIndex)(isGreaterThanEqualTo(0)) &&
               assert(innerFinalizerIndex)(isLessThan(outerFinalizerIndex))
      }
    ) @@ jvmOnly @@ withLiveClock
  )
} 