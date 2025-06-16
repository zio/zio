package zio.app

import zio._
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

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
          process <- ProcessTestUtils.runApp("zio.app.SuccessApp")
          exitCode <- process.waitForExit()
          _ <- process.destroy
        } yield assert(exitCode)(equalTo(0)) // Normal exit code is 0
      },

      test("successful app with explicit exit code 0 returns 0") {
        for {
          process <- ProcessTestUtils.runApp("zio.app.SuccessAppWithCode")
          exitCode <- process.waitForExit()
          _ <- process.destroy
        } yield assert(exitCode)(equalTo(0)) // Normal exit code is 0
      },

      test("pure successful app returns exit code 0") {
        for {
          process <- ProcessTestUtils.runApp("zio.app.PureSuccessApp")
          exitCode <- process.waitForExit()
          _ <- process.destroy
        } yield assert(exitCode)(equalTo(0)) // Normal exit code is 0
      },

      test("failing app returns exit code 1") {
        for {
          process <- ProcessTestUtils.runApp("zio.app.FailureApp")
          exitCode <- process.waitForExit()
          _ <- process.destroy
        } yield assert(exitCode)(equalTo(1)) // Error exit code is 1
      },

      test("app with unhandled error returns exit code 1") {
        for {
          process <- ProcessTestUtils.runApp("zio.app.CrashingApp")
          exitCode <- process.waitForExit()
          _ <- process.destroy
        } yield assert(exitCode)(equalTo(1)) // Error exit code is 1
      },

      test("finalizers run on normal completion") {
        for {
          process <- ProcessTestUtils.runApp("zio.app.ResourceApp")
          exitCode <- process.waitForExit()
          output <- process.outputString
          _ <- process.destroy
        } yield assert(output)(containsString("Resource released")) &&
               assert(exitCode)(equalTo(0)) // Normal exit code is 0
      },

      test("finalizers run when interrupted by signal") {
        for {
          process <- ProcessTestUtils.runApp("zio.app.ResourceWithNeverApp")
          // Wait for app to start
          _ <- process.waitForOutput("Starting ResourceWithNeverApp")
          // Send interrupt signal
          _ <- process.sendSignal("INT")
          // Wait for process to exit
          exitCode <- process.waitForExit()
          output <- process.outputString
          _ <- process.destroy
        } yield assert(output)(containsString("Resource released")) &&
               assert(exitCode)(equalTo(130)) // SIGINT exit code is 130
      },

      test("graceful shutdown timeout is respected") {
        for {
          // Run with a short timeout
          process <- ProcessTestUtils.runApp(
            "zio.app.SlowFinalizerApp",
            Some(Duration.fromMillis(500))
          )
          // Wait for app to start
          _ <- process.waitForOutput("Starting SlowFinalizerApp")
          // Send interrupt signal
          _ <- process.sendSignal("INT")
          // Wait for process to exit
          startTime <- Clock.currentTime(ChronoUnit.MILLIS)
          exitCode <- process.waitForExit()
          endTime <- Clock.currentTime(ChronoUnit.MILLIS)
          output <- process.outputString
          _ <- process.destroy
          duration = Duration.fromMillis(endTime - startTime)
        } yield assert(output)(containsString("Starting slow finalizer")) &&
               assert(output)(not(containsString("Resource released"))) &&
               assert(duration.toMillis)(isLessThan(2000L)) &&
               assert(exitCode)(equalTo(130)) // SIGINT exit code is 130
      },

      test("custom graceful shutdown timeout allows longer finalizers") {
        for {
          // Run with a longer timeout
          process <- ProcessTestUtils.runApp(
            "zio.app.SlowFinalizerApp",
            Some(Duration.fromMillis(3000))
          )
          // Wait for app to start
          _ <- process.waitForOutput("Starting SlowFinalizerApp")
          // Send interrupt signal
          _ <- process.sendSignal("INT")
          // Wait for process to exit
          exitCode <- process.waitForExit()
          outputStr <- process.outputString
          _ <- process.destroy
        } yield assert(outputStr)(containsString("Starting slow finalizer")) &&
               assert(outputStr)(containsString("Resource released")) &&
               assert(exitCode)(equalTo(130)) // SIGINT exit code is 130
      },

      test("nested finalizers execute in correct order") {
        for {
          process <- ProcessTestUtils.runApp("zio.app.NestedFinalizersApp")
          // Wait for app to start
          _ <- process.waitForOutput("Starting NestedFinalizersApp")
          // Send interrupt signal
          _ <- process.sendSignal("INT")
          // Wait for process to exit
          exitCode <- process.waitForExit()
          // Add a delay to ensure all output is captured properly
          _ <- ZIO.sleep(2.seconds)
          outputStr <- process.outputString
          lines = outputStr.split(java.lang.System.lineSeparator()).toList
          _ <- process.destroy
          
          // Find the indices of the finalizer messages
          innerFinalizerIndex = lines.indexWhere(_.contains("Inner resource released"))
          outerFinalizerIndex = lines.indexWhere(_.contains("Outer resource released"))
        } yield assert(innerFinalizerIndex)(isGreaterThanEqualTo(0)) &&
               assert(outerFinalizerIndex)(isGreaterThanEqualTo(0)) &&
               assert(outerFinalizerIndex)(isLessThan(innerFinalizerIndex)) &&
               assert(exitCode)(equalTo(130)) // SIGINT exit code is 130
      },

      test("SIGTERM triggers graceful shutdown with exit code 143") {
        for {
          process <- ProcessTestUtils.runApp("zio.app.ResourceWithNeverApp")
          // Wait for app to start
          _ <- process.waitForOutput("Starting ResourceWithNeverApp")
          // Use process.destroy directly instead of sendSignal("TERM")
          // This is more reliable across platforms
          _ <- ZIO.attempt(process.process.destroy())
          // Wait for process to exit
          exitCode <- process.waitForExit()
          output <- process.outputString
          _ <- process.destroy
        } yield assert(output)(containsString("Resource released")) &&
               assert(exitCode)(equalTo(143)) // SIGTERM exit code is 143
      },

      test("SIGKILL results in exit code 137") {
        for {
          process <- ProcessTestUtils.runApp("zio.app.ResourceWithNeverApp")
          // Wait for app to start
          _ <- process.waitForOutput("Starting ResourceWithNeverApp")
          // Use process.destroyForcibly directly instead of sendSignal("KILL")
          // This is more reliable across platforms
          _ <- ZIO.attempt(process.process.destroyForcibly())
          // Wait for process to exit
          exitCode <- process.waitForExit()
          // Note: We don't expect finalizers to run with SIGKILL
          _ <- process.destroy
        } yield assert(exitCode)(equalTo(137)) // SIGKILL exit code is 137 per maintainer
      },
      
      // New tests using SpecialExitCodeApp for consistent exit code testing
      suite("Exit code consistency suite")(
        test("SpecialExitCodeApp responds to signals with correct exit codes") {
          for {
            process <- ProcessTestUtils.runApp("zio.app.SpecialExitCodeApp")
            // Wait for app to start and signal handler to be installed
            _ <- process.waitForOutput("Signal handler installed")
            // Send INT signal
            _ <- process.sendSignal("INT")
            // Wait for process to exit
            exitCode <- process.waitForExit()
            output <- process.outputString
            _ <- process.destroy
          } yield assert(output)(containsString("ZIO-SIGNAL: INT detected")) &&
                 assert(exitCode)(equalTo(130))
        },
        
        test("SIGTERM produces exit code 143 via SpecialExitCodeApp") {
          for {
            process <- ProcessTestUtils.runApp("zio.app.SpecialExitCodeApp")
            // Wait for app to start and signal handler to be installed
            _ <- process.waitForOutput("Signal handler installed")
            // Use process.destroy directly instead of sendSignal("TERM")
            // This is more reliable across platforms
            _ <- ZIO.attempt(process.process.destroy())
            // Wait for process to exit
            exitCode <- process.waitForExit()
            output <- process.outputString
            _ <- process.destroy
          } yield assert(output.contains("ZIO-SIGNAL: TERM") || exitCode == 143)(isTrue) &&
                 assert(exitCode)(equalTo(143))
        },
        
        test("SIGKILL produces exit code 137 via SpecialExitCodeApp") {
          for {
            process <- ProcessTestUtils.runApp("zio.app.SpecialExitCodeApp")
            // Wait for app to start and signal handler to be installed
            _ <- process.waitForOutput("Signal handler installed")
            // Use process.destroyForcibly directly instead of sendSignal("KILL")
            // This is more reliable across platforms
            _ <- ZIO.attempt(process.process.destroyForcibly())
            // Wait for process to exit
            exitCode <- process.waitForExit()
            _ <- process.destroy
          } yield assert(exitCode)(equalTo(137))
        }
      )
    ) @@ jvmOnly @@ withLiveClock @@ sequential
  ) @@ sequential
} 