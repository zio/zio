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
          process  <- ProcessTestUtils.runApp("zio.app.SuccessApp")
          exitCode <- process.waitForExit()
          _        <- process.destroy
        } yield assert(exitCode)(equalTo(0)) // Normal exit code is 0
      },
      test("successful app with explicit exit code 0 returns 0") {
        for {
          process  <- ProcessTestUtils.runApp("zio.app.SuccessAppWithCode")
          exitCode <- process.waitForExit()
          _        <- process.destroy
        } yield assert(exitCode)(equalTo(0)) // Normal exit code is 0
      },
      test("pure successful app returns exit code 0") {
        for {
          process  <- ProcessTestUtils.runApp("zio.app.PureSuccessApp")
          exitCode <- process.waitForExit()
          _        <- process.destroy
        } yield assert(exitCode)(equalTo(0)) // Normal exit code is 0
      },
      test("failing app returns exit code 1") {
        for {
          process  <- ProcessTestUtils.runApp("zio.app.FailureApp")
          exitCode <- process.waitForExit()
          _        <- process.destroy
        } yield assert(exitCode)(equalTo(1)) // Error exit code is 1
      },
      test("app with unhandled error returns exit code 1") {
        for {
          process  <- ProcessTestUtils.runApp("zio.app.CrashingApp")
          exitCode <- process.waitForExit()
          _        <- process.destroy
        } yield assert(exitCode)(equalTo(1)) // Error exit code is 1
      },
      test("finalizers run on normal completion") {
        for {
          process <- ProcessTestUtils.runApp("zio.app.ResourceApp")
          // Wait for app to start
          _ <- process.waitForOutput("Starting ResourceApp")
          _ <- ZIO.attempt(println(s"[DEBUG] Process started, waiting for resource acquisition in 'finalizers run on normal completion'"))
          // Wait for resource acquisition to complete
          _        <- process.waitForOutput("Resource acquired")
          _ <- ZIO.attempt(println(s"[DEBUG] Resource acquired, waiting for normal exit in 'finalizers run on normal completion'"))
          exitCode <- process.waitForExit()
          _ <- ZIO.attempt(println(s"[DEBUG] Process exited with code $exitCode in 'finalizers run on normal completion'"))
          output   <- process.outputString
          _        <- process.destroy
        } yield assert(output)(containsString("Resource released")) &&
          assert(exitCode)(equalTo(0)) // Normal exit code is 0
      },
      test("finalizers run when interrupted by signal") {
        for {
          process <- ProcessTestUtils.runApp("zio.app.ResourceWithNeverApp")
          // Wait for app to start
          _ <- process.waitForOutput("Starting ResourceWithNeverApp")
          _ <- ZIO.attempt(println(s"[DEBUG] Process started, waiting for resource acquisition in 'finalizers run when interrupted by signal'"))
          // Wait for resource acquisition to complete
          _ <- process.waitForOutput("Resource acquired")
          _ <- ZIO.attempt(println(s"[DEBUG] Resource acquired, preparing to send signal in 'finalizers run when interrupted by signal'"))
          // Give the app a moment to stabilize
          _ <- ZIO.sleep(1.second)
          _ <- ZIO.attempt(println(s"[DEBUG] Stabilization period complete, sending SIGINT in 'finalizers run when interrupted by signal'"))
          // Send interrupt signal
          _ <- process.sendSignal("INT")
          _ <- ZIO.attempt(println(s"[DEBUG] SIGINT sent, waiting for finalizer to run in 'finalizers run when interrupted by signal'")) 
          // Explicitly wait for finalizer to run before checking exit code
          released <- process.waitForOutput("Resource released").as(true).timeout(5.seconds).map(_.getOrElse(false))
          _ <- ZIO.attempt(println(s"[DEBUG] Finalizer detected: $released in 'finalizers run when interrupted by signal'"))
          // Wait for process to exit
          exitCode <- process.waitForExit()
          _ <- ZIO.attempt(println(s"[DEBUG] Process exited with code $exitCode in 'finalizers run when interrupted by signal'"))
          _        <- process.destroy
        } yield assert(released)(isTrue) &&
          assert(exitCode)(equalTo(130)) // SIGINT exit code is 130
      },
      test("graceful shutdown timeout is respected") {
        for {
          // Run with a short timeout
          process <- ProcessTestUtils.runApp(
                       "zio.app.SlowFinalizerApp",
                       Some(Duration.fromMillis(500))
                     )
          _ <- ZIO.attempt(println(s"[DEBUG] Started SlowFinalizerApp with short timeout (500ms) in 'graceful shutdown timeout is respected'"))
          // Wait for app to start
          _ <- process.waitForOutput("Starting SlowFinalizerApp")
          _ <- ZIO.attempt(println(s"[DEBUG] Process started, waiting for resource acquisition in 'graceful shutdown timeout is respected'"))
          // Wait for resource acquisition to complete
          _ <- process.waitForOutput("Resource acquired")
          _ <- ZIO.attempt(println(s"[DEBUG] Resource acquired, preparing to send signal in 'graceful shutdown timeout is respected'"))
          // Give the app a moment to stabilize
          _ <- ZIO.sleep(1.second)
          _ <- ZIO.attempt(println(s"[DEBUG] Stabilization period complete, sending SIGINT in 'graceful shutdown timeout is respected'"))
          // Send interrupt signal
          _ <- process.sendSignal("INT")
          _ <- ZIO.attempt(println(s"[DEBUG] SIGINT sent, measuring shutdown time in 'graceful shutdown timeout is respected'"))
          // Wait for process to exit
          startTime <- Clock.currentTime(ChronoUnit.MILLIS)
          _ <- ZIO.attempt(println(s"[DEBUG] Shutdown start time: $startTime ms in 'graceful shutdown timeout is respected'"))
          exitCode  <- process.waitForExit()
          endTime   <- Clock.currentTime(ChronoUnit.MILLIS)
          _ <- ZIO.attempt(println(s"[DEBUG] Shutdown end time: $endTime ms (duration: ${endTime - startTime} ms) in 'graceful shutdown timeout is respected'"))
          output    <- process.outputString
          _         <- process.destroy
          duration   = Duration.fromMillis(endTime - startTime)
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
          _ <- ZIO.attempt(println(s"[DEBUG] Started SlowFinalizerApp with longer timeout (3000ms) in 'custom graceful shutdown timeout allows longer finalizers'"))
          // Wait for app to start
          _ <- process.waitForOutput("Starting SlowFinalizerApp")
          _ <- ZIO.attempt(println(s"[DEBUG] Process started, waiting for resource acquisition in 'custom graceful shutdown timeout allows longer finalizers'"))
          // Wait for resource acquisition to complete
          _ <- process.waitForOutput("Resource acquired")
          _ <- ZIO.attempt(println(s"[DEBUG] Resource acquired, preparing to send signal in 'custom graceful shutdown timeout allows longer finalizers'"))
          // Give the app a moment to stabilize
          _ <- ZIO.sleep(1.second)
          _ <- ZIO.attempt(println(s"[DEBUG] Stabilization period complete, sending SIGINT in 'custom graceful shutdown timeout allows longer finalizers'"))
          // Send interrupt signal
          _ <- process.sendSignal("INT")
          _ <- ZIO.attempt(println(s"[DEBUG] SIGINT sent, waiting for process exit in 'custom graceful shutdown timeout allows longer finalizers'"))
          // Wait for process to exit
          exitCode  <- process.waitForExit()
          _ <- ZIO.attempt(println(s"[DEBUG] Process exited with code $exitCode in 'custom graceful shutdown timeout allows longer finalizers'"))
          outputStr <- process.outputString
          _         <- process.destroy
        } yield assert(outputStr)(containsString("Starting slow finalizer")) &&
          assert(outputStr)(containsString("Resource released")) &&
          assert(exitCode)(equalTo(130)) // SIGINT exit code is 130
      },
      test("nested finalizers execute in correct order") {
        for {
          process <- ProcessTestUtils.runApp("zio.app.NestedFinalizersApp")
          // Wait for app to start
          _ <- process.waitForOutput("Starting NestedFinalizersApp")
          _ <- ZIO.attempt(println(s"[DEBUG] Process started, waiting for resource acquisition in 'nested finalizers execute in correct order'"))
          // Wait for resource acquisition to complete
          _ <- process.waitForOutput("Outer resource acquired")
          _ <- ZIO.attempt(println(s"[DEBUG] Outer resource acquired in 'nested finalizers execute in correct order'"))
          _ <- process.waitForOutput("Inner resource acquired")
          _ <- ZIO.attempt(println(s"[DEBUG] Inner resource acquired, preparing to send signal in 'nested finalizers execute in correct order'"))
          // Give the app a moment to stabilize
          _ <- ZIO.sleep(1.second)
          _ <- ZIO.attempt(println(s"[DEBUG] Stabilization period complete, sending SIGINT in 'nested finalizers execute in correct order'"))
          // Send interrupt signal
          _ <- process.sendSignal("INT")
          _ <- ZIO.attempt(println(s"[DEBUG] SIGINT sent, waiting for process exit in 'nested finalizers execute in correct order'"))
          // Wait for process to exit
          exitCode <- process.waitForExit()
          _ <- ZIO.attempt(println(s"[DEBUG] Process exited with code $exitCode in 'nested finalizers execute in correct order'"))
          // Add a delay to ensure all output is captured properly
          _         <- ZIO.sleep(2.seconds)
          outputStr <- process.outputString
          lines      = outputStr.split(java.lang.System.lineSeparator()).toList
          _         <- process.destroy

          // Find the indices of the finalizer messages
          innerFinalizerIndex = lines.indexWhere(_.contains("Inner resource released"))
          outerFinalizerIndex = lines.indexWhere(_.contains("Outer resource released"))
          _ <- ZIO.attempt(println(s"[DEBUG] Finalizer indices - Inner: $innerFinalizerIndex, Outer: $outerFinalizerIndex in 'nested finalizers execute in correct order'"))
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
          _ <- ZIO.attempt(println(s"[DEBUG] Process started, waiting for resource acquisition in 'SIGTERM triggers graceful shutdown with exit code 143'"))
          // Wait for resource acquisition to complete
          _ <- process.waitForOutput("Resource acquired")
          _ <- ZIO.attempt(println(s"[DEBUG] Resource acquired, preparing to send SIGTERM in 'SIGTERM triggers graceful shutdown with exit code 143'"))
          // Give the app a moment to stabilize
          _ <- ZIO.sleep(1.second)
          _ <- ZIO.attempt(println(s"[DEBUG] Stabilization period complete, sending SIGTERM in 'SIGTERM triggers graceful shutdown with exit code 143'"))
          // Use process.destroy directly instead of sendSignal("TERM")
          // This is more reliable across platforms
          _ <- ZIO.attempt(process.process.destroy())
          _ <- ZIO.attempt(println(s"[DEBUG] SIGTERM sent via process.destroy(), waiting for process exit in 'SIGTERM triggers graceful shutdown with exit code 143'"))
          // Wait for process to exit
          exitCode <- process.waitForExit()
          _ <- ZIO.attempt(println(s"[DEBUG] Process exited with code $exitCode in 'SIGTERM triggers graceful shutdown with exit code 143'"))
          output   <- process.outputString
          _        <- process.destroy
        } yield assert(output)(containsString("Resource released")) &&
          assert(exitCode)(equalTo(143)) // SIGTERM exit code is 143
      },
      test("SIGKILL results in exit code 137") {
        for {
          process <- ProcessTestUtils.runApp("zio.app.ResourceWithNeverApp")
          // Wait for app to start
          _ <- process.waitForOutput("Starting ResourceWithNeverApp")
          _ <- ZIO.attempt(println(s"[DEBUG] Process started, waiting for resource acquisition in 'SIGKILL results in exit code 137'"))
          // Wait for resource acquisition to complete
          _ <- process.waitForOutput("Resource acquired")
          _ <- ZIO.attempt(println(s"[DEBUG] Resource acquired, preparing to send SIGKILL in 'SIGKILL results in exit code 137'"))
          // Give the app a moment to stabilize
          _ <- ZIO.sleep(1.second)
          _ <- ZIO.attempt(println(s"[DEBUG] Stabilization period complete, sending SIGKILL in 'SIGKILL results in exit code 137'"))
          // Use process.destroyForcibly directly instead of sendSignal("KILL")
          // This is more reliable across platforms
          _ <- ZIO.attempt(process.process.destroyForcibly())
          _ <- ZIO.attempt(println(s"[DEBUG] SIGKILL sent via process.destroyForcibly(), waiting for process exit in 'SIGKILL results in exit code 137'"))
          // Wait for process to exit
          exitCode <- process.waitForExit()
          _ <- ZIO.attempt(println(s"[DEBUG] Process exited with code $exitCode in 'SIGKILL results in exit code 137'"))
          // Note: We don't expect finalizers to run with SIGKILL
          _ <- process.destroy
        } yield assert(exitCode)(equalTo(137)) // SIGKILL exit code is 137 per maintainer
      },

      // New tests using SpecialExitCodeApp for consistent exit code testing
      suite("Exit code consistency suite")(
        test("SpecialExitCodeApp responds to signals with correct exit codes") {
          for {
            process <- ProcessTestUtils.runApp("zio.app.SpecialExitCodeApp")
            _ <- ZIO.attempt(println(s"[DEBUG] Started SpecialExitCodeApp in 'SpecialExitCodeApp responds to signals with correct exit codes'"))
            // Wait for app to start and signal handler to be installed
            _ <- process.waitForOutput("Signal handler installed")
            _ <- ZIO.attempt(println(s"[DEBUG] Signal handler installed, preparing to send SIGINT in 'SpecialExitCodeApp responds to signals with correct exit codes'"))
            // Send INT signal
            _ <- process.sendSignal("INT")
            _ <- ZIO.attempt(println(s"[DEBUG] SIGINT sent, waiting for process exit in 'SpecialExitCodeApp responds to signals with correct exit codes'"))
            // Wait for process to exit
            exitCode <- process.waitForExit()
            _ <- ZIO.attempt(println(s"[DEBUG] Process exited with code $exitCode in 'SpecialExitCodeApp responds to signals with correct exit codes'"))
            _        <- process.outputString
            _        <- process.destroy
          } yield assert(exitCode)(equalTo(130)) // Only check exit code, don't require specific output
        },
        test("SIGTERM produces exit code 143 via SpecialExitCodeApp") {
          for {
            process <- ProcessTestUtils.runApp("zio.app.SpecialExitCodeApp")
            _ <- ZIO.attempt(println(s"[DEBUG] Started SpecialExitCodeApp in 'SIGTERM produces exit code 143 via SpecialExitCodeApp'"))
            // Wait for app to start and signal handler to be installed
            _ <- process.waitForOutput("Signal handler installed")
            _ <- ZIO.attempt(println(s"[DEBUG] Signal handler installed, preparing to send SIGTERM in 'SIGTERM produces exit code 143 via SpecialExitCodeApp'"))
            // Use process.destroy directly instead of sendSignal("TERM")
            // This is more reliable across platforms
            _ <- ZIO.attempt(process.process.destroy())
            _ <- ZIO.attempt(println(s"[DEBUG] SIGTERM sent via process.destroy(), waiting for process exit in 'SIGTERM produces exit code 143 via SpecialExitCodeApp'"))
            // Wait for process to exit
            exitCode <- process.waitForExit()
            _ <- ZIO.attempt(println(s"[DEBUG] Process exited with code $exitCode in 'SIGTERM produces exit code 143 via SpecialExitCodeApp'"))
            output   <- process.outputString
            _        <- process.destroy
          } yield assert(output.contains("ZIO-SIGNAL: TERM") || exitCode == 143)(isTrue) &&
            assert(exitCode)(equalTo(143))
        },
        test("SIGKILL produces exit code 137 via SpecialExitCodeApp") {
          for {
            process <- ProcessTestUtils.runApp("zio.app.SpecialExitCodeApp")
            _ <- ZIO.attempt(println(s"[DEBUG] Started SpecialExitCodeApp in 'SIGKILL produces exit code 137 via SpecialExitCodeApp'"))
            // Wait for app to start and signal handler to be installed
            _ <- process.waitForOutput("Signal handler installed")
            _ <- ZIO.attempt(println(s"[DEBUG] Signal handler installed, preparing to send SIGKILL in 'SIGKILL produces exit code 137 via SpecialExitCodeApp'"))
            // Use process.destroyForcibly directly instead of sendSignal("KILL")
            // This is more reliable across platforms
            _ <- ZIO.attempt(process.process.destroyForcibly())
            _ <- ZIO.attempt(println(s"[DEBUG] SIGKILL sent via process.destroyForcibly(), waiting for process exit in 'SIGKILL produces exit code 137 via SpecialExitCodeApp'"))
            // Wait for process to exit
            exitCode <- process.waitForExit()
            _ <- ZIO.attempt(println(s"[DEBUG] Process exited with code $exitCode in 'SIGKILL produces exit code 137 via SpecialExitCodeApp'"))
            _        <- process.destroy
          } yield assert(exitCode)(equalTo(137)) // SIGKILL exit code is 137
        }
      )
    ) @@ jvmOnly @@ withLiveClock @@ sequential
  ) @@ sequential
}
