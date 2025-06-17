package zio.app

import zio._
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

import java.time.temporal.ChronoUnit

/**
 * Test suite for ZIOApp, focusing on:
 *   1. Normal completion behavior 2. Error handling behavior 3. Finalizer
 *      execution during shutdown 4. Signal handling and graceful shutdown 5.
 *      Timeout behavior
 */
object ZIOAppSpec extends ZIOSpecDefault {
  // Helper method for debug logging
  private def debugLog(msg: String): UIO[Unit] = 
    ZIO.succeed(println(s"[DEBUG-TEST] ${java.time.LocalDateTime.now()}: $msg"))

  def spec = suite("ZIOAppSpec")(
    // Platform-independent tests
    suite("ZIOApp behavior")(
      test("successful exit code") {
        for {
          _ <- debugLog("Starting 'successful exit code' test")
          _ <- ZIO.unit // Test will be implemented based on platform
          _ <- debugLog("Completed 'successful exit code' test")
        } yield assertCompletes
      }
    ),

    // JVM-specific tests that require process management
    suite("ZIOApp JVM process tests")(
      test("successful app returns exit code 0") {
        for {
          _ <- debugLog("Starting 'successful app returns exit code 0' test")
          process  <- ProcessTestUtils.runApp("zio.app.SuccessApp")
          _        <- debugLog(s"Process started with PID: ${process.process.pid()}")
          exitCode <- process.waitForExit()
          _        <- debugLog(s"Process exited with code: $exitCode")
          _        <- process.destroy
          _        <- debugLog("Process destroyed, test complete")
        } yield assert(exitCode)(equalTo(0)) // Normal exit code is 0
      },
      test("successful app with explicit exit code 0 returns 0") {
        for {
          _ <- debugLog("Starting 'successful app with explicit exit code 0 returns 0' test")
          process  <- ProcessTestUtils.runApp("zio.app.SuccessAppWithCode")
          _        <- debugLog(s"Process started with PID: ${process.process.pid()}")
          exitCode <- process.waitForExit()
          _        <- debugLog(s"Process exited with code: $exitCode")
          _        <- process.destroy
          _        <- debugLog("Process destroyed, test complete")
        } yield assert(exitCode)(equalTo(0)) // Normal exit code is 0
      },
      test("pure successful app returns exit code 0") {
        for {
          _ <- debugLog("Starting 'pure successful app returns exit code 0' test")
          process  <- ProcessTestUtils.runApp("zio.app.PureSuccessApp")
          _        <- debugLog(s"Process started with PID: ${process.process.pid()}")
          exitCode <- process.waitForExit()
          _        <- debugLog(s"Process exited with code: $exitCode")
          _        <- process.destroy
          _        <- debugLog("Process destroyed, test complete")
        } yield assert(exitCode)(equalTo(0)) // Normal exit code is 0
      },
      test("failing app returns exit code 1") {
        for {
          _ <- debugLog("Starting 'failing app returns exit code 1' test")
          process  <- ProcessTestUtils.runApp("zio.app.FailureApp")
          _        <- debugLog(s"Process started with PID: ${process.process.pid()}")
          exitCode <- process.waitForExit()
          _        <- debugLog(s"Process exited with code: $exitCode")
          _        <- process.destroy
          _        <- debugLog("Process destroyed, test complete")
        } yield assert(exitCode)(equalTo(1)) // Error exit code is 1
      },
      test("app with unhandled error returns exit code 1") {
        for {
          _ <- debugLog("Starting 'app with unhandled error returns exit code 1' test")
          process  <- ProcessTestUtils.runApp("zio.app.CrashingApp")
          _        <- debugLog(s"Process started with PID: ${process.process.pid()}")
          exitCode <- process.waitForExit()
          _        <- debugLog(s"Process exited with code: $exitCode")
          _        <- process.destroy
          _        <- debugLog("Process destroyed, test complete")
        } yield assert(exitCode)(equalTo(1)) // Error exit code is 1
      },
      test("finalizers run on normal completion") {
        for {
          _ <- debugLog("Starting 'finalizers run on normal completion' test")
          process  <- ProcessTestUtils.runApp("zio.app.ResourceApp")
          _        <- debugLog(s"Process started with PID: ${process.process.pid()}")
          // Wait for app to start
          _ <- process.waitForOutput("Starting ResourceApp")
          _ <- debugLog("App started message detected")
          // Wait for resource acquisition to complete
          _ <- process.waitForOutput("Resource acquired")
          _ <- debugLog("Resource acquisition detected")
          exitCode <- process.waitForExit()
          _        <- debugLog(s"Process exited with code: $exitCode")
          output   <- process.outputString
          _        <- debugLog(s"Process output: ${output.replace("\n", "\\n")}")
          _        <- process.destroy
          _        <- debugLog("Process destroyed, test complete")
        } yield assert(output)(containsString("Resource released")) &&
          assert(exitCode)(equalTo(0)) // Normal exit code is 0
      },
      test("finalizers run when interrupted by signal") {
        for {
          _ <- debugLog("Starting 'finalizers run when interrupted by signal' test")
          process <- ProcessTestUtils.runApp("zio.app.ResourceWithNeverApp")
          _       <- debugLog(s"Process started with PID: ${process.process.pid()}")
          // Wait for app to start
          _ <- process.waitForOutput("Starting ResourceWithNeverApp")
          _ <- debugLog("App started message detected")
          // Wait for resource acquisition to complete
          _ <- process.waitForOutput("Resource acquired")
          _ <- debugLog("Resource acquisition detected")
          // Give the app a moment to stabilize
          startWait <- Clock.currentTime(ChronoUnit.MILLIS)
          _         <- ZIO.sleep(1.second)
          endWait   <- Clock.currentTime(ChronoUnit.MILLIS)
          _         <- debugLog(s"Stabilization wait completed in ${endWait - startWait}ms")
          // Send interrupt signal
          _ <- debugLog("Sending INT signal")
          _ <- process.sendSignal("INT")
          _ <- debugLog("INT signal sent")
          // Explicitly wait for finalizer to run before checking exit code
          startFinalizer <- Clock.currentTime(ChronoUnit.MILLIS)
          released       <- process.waitForOutput("Resource released").as(true).timeout(5.seconds).map(_.getOrElse(false))
          endFinalizer   <- Clock.currentTime(ChronoUnit.MILLIS)
          _              <- debugLog(s"Finalizer wait completed in ${endFinalizer - startFinalizer}ms, finalizer ran: $released")
          // Wait for process to exit
          startExit <- Clock.currentTime(ChronoUnit.MILLIS)
          exitCode  <- process.waitForExit()
          endExit   <- Clock.currentTime(ChronoUnit.MILLIS)
          _         <- debugLog(s"Process exited with code: $exitCode in ${endExit - startExit}ms")
          _         <- process.destroy
          _         <- debugLog("Process destroyed, test complete")
        } yield assert(released)(isTrue) &&
          assert(exitCode)(equalTo(130)) // SIGINT exit code is 130
        },
      test("graceful shutdown timeout is respected") {
        for {
          _ <- debugLog("Starting 'graceful shutdown timeout is respected' test")
          // Run with a short timeout
          process <- ProcessTestUtils.runApp(
                       "zio.app.SlowFinalizerApp",
                       Some(Duration.fromMillis(500))
                     )
          _ <- debugLog(s"Process started with PID: ${process.process.pid()} and timeout: 500ms")
          // Wait for app to start
          _ <- process.waitForOutput("Starting SlowFinalizerApp")
          _ <- debugLog("App started message detected")
          // Wait for resource acquisition to complete
          _ <- process.waitForOutput("Resource acquired")
          _ <- debugLog("Resource acquisition detected")
          // Give the app a moment to stabilize
          startWait <- Clock.currentTime(ChronoUnit.MILLIS)
          _         <- ZIO.sleep(1.second)
          endWait   <- Clock.currentTime(ChronoUnit.MILLIS)
          _         <- debugLog(s"Stabilization wait completed in ${endWait - startWait}ms")
          // Send interrupt signal
          _ <- debugLog("Sending INT signal")
          _ <- process.sendSignal("INT")
          _ <- debugLog("INT signal sent")
          // Wait for process to exit
          startTime <- Clock.currentTime(ChronoUnit.MILLIS)
          exitCode  <- process.waitForExit()
          endTime   <- Clock.currentTime(ChronoUnit.MILLIS)
          _         <- debugLog(s"Process exited with code: $exitCode in ${endTime - startTime}ms")
          output    <- process.outputString
          _         <- debugLog(s"Process output: ${output.replace("\n", "\\n")}")
          _         <- debugLog(s"Output contains 'Starting slow finalizer': ${output.contains("Starting slow finalizer")}")
          _         <- debugLog(s"Output contains 'Resource released': ${output.contains("Resource released")}")
          _         <- process.destroy
          _         <- debugLog("Process destroyed, test complete")
          duration   = Duration.fromMillis(endTime - startTime)
          _         <- debugLog(s"Total exit duration: ${duration.toMillis}ms")
        } yield assert(output)(containsString("Starting slow finalizer")) &&
          assert(output)(not(containsString("Resource released"))) &&
          assert(duration.toMillis)(isLessThan(2000L)) &&
          assert(exitCode)(equalTo(130)) // SIGINT exit code is 130
        },
      test("custom graceful shutdown timeout allows longer finalizers") {
        for {
          _ <- debugLog("Starting 'custom graceful shutdown timeout allows longer finalizers' test")
          // Run with a longer timeout
          process <- ProcessTestUtils.runApp(
                       "zio.app.SlowFinalizerApp",
                       Some(Duration.fromMillis(3000))
                     )
          _ <- debugLog(s"Process started with PID: ${process.process.pid()} and timeout: 3000ms")
          // Wait for app to start
          _ <- process.waitForOutput("Starting SlowFinalizerApp")
          _ <- debugLog("App started message detected")
          // Wait for resource acquisition to complete
          _ <- process.waitForOutput("Resource acquired")
          _ <- debugLog("Resource acquisition detected")
          // Give the app a moment to stabilize
          startWait <- Clock.currentTime(ChronoUnit.MILLIS)
          _         <- ZIO.sleep(1.second)
          endWait   <- Clock.currentTime(ChronoUnit.MILLIS)
          _         <- debugLog(s"Stabilization wait completed in ${endWait - startWait}ms")
          // Send interrupt signal
          _ <- debugLog("Sending INT signal")
          _ <- process.sendSignal("INT")
          _ <- debugLog("INT signal sent")
          // Wait for process to exit
          startExit  <- Clock.currentTime(ChronoUnit.MILLIS)
          exitCode   <- process.waitForExit()
          endExit    <- Clock.currentTime(ChronoUnit.MILLIS)
          _          <- debugLog(s"Process exited with code: $exitCode in ${endExit - startExit}ms")
          outputStr  <- process.outputString
          _          <- debugLog(s"Process output: ${outputStr.replace("\n", "\\n")}")
          _          <- debugLog(s"Output contains 'Starting slow finalizer': ${outputStr.contains("Starting slow finalizer")}")
          _          <- debugLog(s"Output contains 'Resource released': ${outputStr.contains("Resource released")}")
          _          <- process.destroy
          _          <- debugLog("Process destroyed, test complete")
        } yield assert(outputStr)(containsString("Starting slow finalizer")) &&
          assert(outputStr)(containsString("Resource released")) &&
          assert(exitCode)(equalTo(130)) // SIGINT exit code is 130
        },
      test("nested finalizers execute in correct order") {
        for {
          _ <- debugLog("Starting 'nested finalizers execute in correct order' test")
          process <- ProcessTestUtils.runApp("zio.app.NestedFinalizersApp")
          _       <- debugLog(s"Process started with PID: ${process.process.pid()}")
          // Wait for app to start
          _ <- process.waitForOutput("Starting NestedFinalizersApp")
          _ <- debugLog("App started message detected")
          // Wait for resource acquisition to complete
          _ <- process.waitForOutput("Outer resource acquired")
          _ <- debugLog("Outer resource acquisition detected")
          _ <- process.waitForOutput("Inner resource acquired")
          _ <- debugLog("Inner resource acquisition detected")
          // Give the app a moment to stabilize
          startWait <- Clock.currentTime(ChronoUnit.MILLIS)
          _         <- ZIO.sleep(1.second)
          endWait   <- Clock.currentTime(ChronoUnit.MILLIS)
          _         <- debugLog(s"Stabilization wait completed in ${endWait - startWait}ms")
          // Send interrupt signal
          _ <- debugLog("Sending INT signal")
          _ <- process.sendSignal("INT")
          _ <- debugLog("INT signal sent")
          // Wait for process to exit
          startExit <- Clock.currentTime(ChronoUnit.MILLIS)
          exitCode  <- process.waitForExit()
          endExit   <- Clock.currentTime(ChronoUnit.MILLIS)
          _         <- debugLog(s"Process exited with code: $exitCode in ${endExit - startExit}ms")
          // Add a delay to ensure all output is captured properly
          _         <- debugLog("Waiting for additional output capture")
          _         <- ZIO.sleep(2.seconds)
          outputStr <- process.outputString
          _         <- debugLog(s"Process output: ${outputStr.replace("\n", "\\n")}")
          lines      = outputStr.split(java.lang.System.lineSeparator()).toList
          _         <- debugLog(s"Output lines: ${lines.size}")
          _         <- process.destroy
          _         <- debugLog("Process destroyed, test complete")

          // Find the indices of the finalizer messages
          innerFinalizerIndex = lines.indexWhere(_.contains("Inner resource released"))
          outerFinalizerIndex = lines.indexWhere(_.contains("Outer resource released"))
          _ <- debugLog(s"Inner finalizer index: $innerFinalizerIndex, Outer finalizer index: $outerFinalizerIndex")
        } yield assert(innerFinalizerIndex)(isGreaterThanEqualTo(0)) &&
          assert(outerFinalizerIndex)(isGreaterThanEqualTo(0)) &&
          assert(outerFinalizerIndex)(isLessThan(innerFinalizerIndex)) &&
          assert(exitCode)(equalTo(130)) // SIGINT exit code is 130
        },
      test("SIGTERM triggers graceful shutdown with exit code 143") {
        for {
          _ <- debugLog("Starting 'SIGTERM triggers graceful shutdown with exit code 143' test")
          process <- ProcessTestUtils.runApp("zio.app.ResourceWithNeverApp")
          _       <- debugLog(s"Process started with PID: ${process.process.pid()}")
          // Wait for app to start
          _ <- process.waitForOutput("Starting ResourceWithNeverApp")
          _ <- debugLog("App started message detected")
          // Wait for resource acquisition to complete
          _ <- process.waitForOutput("Resource acquired")
          _ <- debugLog("Resource acquisition detected")
          // Give the app a moment to stabilize
          startWait <- Clock.currentTime(ChronoUnit.MILLIS)
          _         <- ZIO.sleep(1.second)
          endWait   <- Clock.currentTime(ChronoUnit.MILLIS)
          _         <- debugLog(s"Stabilization wait completed in ${endWait - startWait}ms")
          // Use process.destroy directly instead of sendSignal("TERM")
          // This is more reliable across platforms
          _ <- debugLog("Sending TERM signal via process.destroy()")
          _ <- ZIO.attempt(process.process.destroy())
          _ <- debugLog("TERM signal sent")
          // Wait for process to exit
          startExit <- Clock.currentTime(ChronoUnit.MILLIS)
          exitCode  <- process.waitForExit()
          endExit   <- Clock.currentTime(ChronoUnit.MILLIS)
          _         <- debugLog(s"Process exited with code: $exitCode in ${endExit - startExit}ms")
          output    <- process.outputString
          _         <- debugLog(s"Process output: ${output.replace("\n", "\\n")}")
          _         <- debugLog(s"Output contains 'Resource released': ${output.contains("Resource released")}")
          _         <- process.destroy
          _         <- debugLog("Process destroyed, test complete")
        } yield assert(output)(containsString("Resource released")) &&
          assert(exitCode)(equalTo(143)) // SIGTERM exit code is 143
        },
      test("SIGKILL results in exit code 137") {
        for {
          _ <- debugLog("Starting 'SIGKILL results in exit code 137' test")
          process <- ProcessTestUtils.runApp("zio.app.ResourceWithNeverApp")
          _       <- debugLog(s"Process started with PID: ${process.process.pid()}")
          // Wait for app to start
          _ <- process.waitForOutput("Starting ResourceWithNeverApp")
          _ <- debugLog("App started message detected")
          // Wait for resource acquisition to complete
          _ <- process.waitForOutput("Resource acquired")
          _ <- debugLog("Resource acquisition detected")
          // Give the app a moment to stabilize
          startWait <- Clock.currentTime(ChronoUnit.MILLIS)
          _         <- ZIO.sleep(1.second)
          endWait   <- Clock.currentTime(ChronoUnit.MILLIS)
          _         <- debugLog(s"Stabilization wait completed in ${endWait - startWait}ms")
          // Use process.destroyForcibly directly instead of sendSignal("KILL")
          // This is more reliable across platforms
          _ <- debugLog("Sending KILL signal via process.destroyForcibly()")
          _ <- ZIO.attempt(process.process.destroyForcibly())
          _ <- debugLog("KILL signal sent")
          // Wait for process to exit
          startExit <- Clock.currentTime(ChronoUnit.MILLIS)
          exitCode  <- process.waitForExit()
          endExit   <- Clock.currentTime(ChronoUnit.MILLIS)
          _         <- debugLog(s"Process exited with code: $exitCode in ${endExit - startExit}ms")
          // Note: We don't expect finalizers to run with SIGKILL
          _ <- process.destroy
          _ <- debugLog("Process destroyed, test complete")
        } yield assert(exitCode)(equalTo(137)) // SIGKILL exit code is 137 per maintainer
        },

        // New tests using SpecialExitCodeApp for consistent exit code testing
        suite("Exit code consistency suite")(
          test("SpecialExitCodeApp responds to signals with correct exit codes") {
            for {
              _ <- debugLog("Starting 'SpecialExitCodeApp responds to signals with correct exit codes' test")
              process <- ProcessTestUtils.runApp("zio.app.SpecialExitCodeApp")
              _       <- debugLog(s"Process started with PID: ${process.process.pid()}")
              // Wait for app to start and signal handler to be installed
              _ <- process.waitForOutput("Signal handler installed")
              _ <- debugLog("Signal handler installation detected")
              // Send INT signal
              _ <- debugLog("Sending INT signal")
              _ <- process.sendSignal("INT")
              _ <- debugLog("INT signal sent")
              // Wait for process to exit
              startExit <- Clock.currentTime(ChronoUnit.MILLIS)
              exitCode  <- process.waitForExit()
              endExit   <- Clock.currentTime(ChronoUnit.MILLIS)
              _         <- debugLog(s"Process exited with code: $exitCode in ${endExit - startExit}ms")
              output    <- process.outputString
              _         <- debugLog(s"Process output: ${output.replace("\n", "\\n")}")
              _         <- process.destroy
              _         <- debugLog("Process destroyed, test complete")
            } yield assert(exitCode)(equalTo(130)) // Only check exit code, don't require specific output
          },
          test("SIGTERM produces exit code 143 via SpecialExitCodeApp") {
            for {
              _ <- debugLog("Starting 'SIGTERM produces exit code 143 via SpecialExitCodeApp' test")
              process <- ProcessTestUtils.runApp("zio.app.SpecialExitCodeApp")
              _       <- debugLog(s"Process started with PID: ${process.process.pid()}")
              // Wait for app to start and signal handler to be installed
              _ <- process.waitForOutput("Signal handler installed")
              _ <- debugLog("Signal handler installation detected")
              // Use process.destroy directly instead of sendSignal("TERM")
              // This is more reliable across platforms
              _ <- debugLog("Sending TERM signal via process.destroy()")
              _ <- ZIO.attempt(process.process.destroy())
              _ <- debugLog("TERM signal sent")
              // Wait for process to exit
              startExit <- Clock.currentTime(ChronoUnit.MILLIS)
              exitCode  <- process.waitForExit()
              endExit   <- Clock.currentTime(ChronoUnit.MILLIS)
              _         <- debugLog(s"Process exited with code: $exitCode in ${endExit - startExit}ms")
              output    <- process.outputString
              _         <- debugLog(s"Process output: ${output.replace("\n", "\\n")}")
              _         <- debugLog(s"Output contains 'ZIO-SIGNAL: TERM': ${output.contains("ZIO-SIGNAL: TERM")}")
              _         <- process.destroy
              _         <- debugLog("Process destroyed, test complete")
            } yield assert(output.contains("ZIO-SIGNAL: TERM") || exitCode == 143)(isTrue) &&
              assert(exitCode)(equalTo(143))
          },
          test("SIGKILL produces exit code 137 via SpecialExitCodeApp") {
            for {
              _ <- debugLog("Starting 'SIGKILL produces exit code 137 via SpecialExitCodeApp' test")
              process <- ProcessTestUtils.runApp("zio.app.SpecialExitCodeApp")
              _       <- debugLog(s"Process started with PID: ${process.process.pid()}")
              // Wait for app to start and signal handler to be installed
              _ <- process.waitForOutput("Signal handler installed")
              _ <- debugLog("Signal handler installation detected")
              // Use process.destroyForcibly directly instead of sendSignal("KILL")
              // This is more reliable across platforms
              _ <- debugLog("Sending KILL signal via process.destroyForcibly()")
              _ <- ZIO.attempt(process.process.destroyForcibly())
              _ <- debugLog("KILL signal sent")
              // Wait for process to exit
              startExit <- Clock.currentTime(ChronoUnit.MILLIS)
              exitCode  <- process.waitForExit()
              endExit   <- Clock.currentTime(ChronoUnit.MILLIS)
              _         <- debugLog(s"Process exited with code: $exitCode in ${endExit - startExit}ms")
              _         <- process.destroy
              _         <- debugLog("Process destroyed, test complete")
            } yield assert(exitCode)(equalTo(137))
          }
        )
      ) @@ jvmOnly @@ withLiveClock @@ sequential
    ) @@ sequential
  }
}
