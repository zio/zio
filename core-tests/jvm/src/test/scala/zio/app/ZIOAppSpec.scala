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
          _ <- ZIO.logInfo("[TEST DEBUG] Starting 'successful exit code' test")
          _ <- ZIO.unit // Test will be implemented based on platform
          _ <- ZIO.logInfo("[TEST DEBUG] Completed 'successful exit code' test")
        } yield assertCompletes
      }
    ),

    // JVM-specific tests that require process management
    suite("ZIOApp JVM process tests")(
      test("successful app returns exit code 0") {
        for {
          _ <- ZIO.logInfo("[TEST DEBUG] Starting 'successful app returns exit code 0' test")
          process  <- ProcessTestUtils.runApp("zio.app.SuccessApp")
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process started with PID ${process.process.pid()}")
          exitCode <- process.waitForExit()
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process exited with code: $exitCode")
          _        <- process.destroy
          _ <- ZIO.logInfo("[TEST DEBUG] Process destroyed, test completed")
        } yield assert(exitCode)(equalTo(0)) // Normal exit code is 0
      },
      test("successful app with explicit exit code 0 returns 0") {
        for {
          _ <- ZIO.logInfo("[TEST DEBUG] Starting 'successful app with explicit exit code 0 returns 0' test")
          process  <- ProcessTestUtils.runApp("zio.app.SuccessAppWithCode")
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process started with PID ${process.process.pid()}")
          exitCode <- process.waitForExit()
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process exited with code: $exitCode")
          _        <- process.destroy
          _ <- ZIO.logInfo("[TEST DEBUG] Process destroyed, test completed")
        } yield assert(exitCode)(equalTo(0)) // Normal exit code is 0
      },
      test("pure successful app returns exit code 0") {
        for {
          _ <- ZIO.logInfo("[TEST DEBUG] Starting 'pure successful app returns exit code 0' test")
          process  <- ProcessTestUtils.runApp("zio.app.PureSuccessApp")
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process started with PID ${process.process.pid()}")
          exitCode <- process.waitForExit()
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process exited with code: $exitCode")
          _        <- process.destroy
          _ <- ZIO.logInfo("[TEST DEBUG] Process destroyed, test completed")
        } yield assert(exitCode)(equalTo(0)) // Normal exit code is 0
      },
      test("failing app returns exit code 1") {
        for {
          _ <- ZIO.logInfo("[TEST DEBUG] Starting 'failing app returns exit code 1' test")
          process  <- ProcessTestUtils.runApp("zio.app.FailureApp")
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process started with PID ${process.process.pid()}")
          exitCode <- process.waitForExit()
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process exited with code: $exitCode")
          _        <- process.destroy
          _ <- ZIO.logInfo("[TEST DEBUG] Process destroyed, test completed")
        } yield assert(exitCode)(equalTo(1)) // Error exit code is 1
      },
      test("app with unhandled error returns exit code 1") {
        for {
          _ <- ZIO.logInfo("[TEST DEBUG] Starting 'app with unhandled error returns exit code 1' test")
          process  <- ProcessTestUtils.runApp("zio.app.CrashingApp")
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process started with PID ${process.process.pid()}")
          exitCode <- process.waitForExit()
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process exited with code: $exitCode")
          _        <- process.destroy
          _ <- ZIO.logInfo("[TEST DEBUG] Process destroyed, test completed")
        } yield assert(exitCode)(equalTo(1)) // Error exit code is 1
      },
      test("finalizers run on normal completion") {
        for {
          _ <- ZIO.logInfo("[TEST DEBUG] Starting 'finalizers run on normal completion' test")
          process <- ProcessTestUtils.runApp("zio.app.ResourceApp")
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process started with PID ${process.process.pid()}")
          // Wait for app to start
          _ <- process.waitForOutput("Starting ResourceApp")
          _ <- ZIO.logInfo("[TEST DEBUG] Detected 'Starting ResourceApp' in output")
          // Wait for resource acquisition to complete
          _ <- process.waitForOutput("Resource acquired")
          _ <- ZIO.logInfo("[TEST DEBUG] Detected 'Resource acquired' in output")
          exitCode <- process.waitForExit()
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process exited with code: $exitCode")
          output   <- process.outputString
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process output: ${output.replace("\n", " | ")}")
          _        <- process.destroy
          _ <- ZIO.logInfo("[TEST DEBUG] Process destroyed, test completed")
        } yield {
          val result = assert(output)(containsString("Resource released")) && assert(exitCode)(equalTo(0))
          ZIO.logInfo(s"[TEST DEBUG] Test result: ${result.toString()}").as(result)
        }
      },
      test("finalizers run when interrupted by signal") {
        for {
          _ <- ZIO.logInfo("[TEST DEBUG] Starting 'finalizers run when interrupted by signal' test")
          process <- ProcessTestUtils.runApp("zio.app.ResourceWithNeverApp")
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process started with PID ${process.process.pid()}")
          // Wait for app to start
          _ <- process.waitForOutput("Starting ResourceWithNeverApp")
          _ <- ZIO.logInfo("[TEST DEBUG] Detected 'Starting ResourceWithNeverApp' in output")
          // Wait for resource acquisition to complete
          _ <- process.waitForOutput("Resource acquired")
          _ <- ZIO.logInfo("[TEST DEBUG] Detected 'Resource acquired' in output")
          // Give the app a moment to stabilize
          _ <- ZIO.logInfo("[TEST DEBUG] Sleeping for 1 second to allow app to stabilize")
          _ <- ZIO.sleep(1.second)
          // Send interrupt signal
          _ <- ZIO.logInfo("[TEST DEBUG] Sending INT signal to process")
          _ <- process.sendSignal("INT")
          // Explicitly wait for finalizer to run before checking exit code
          _ <- ZIO.logInfo("[TEST DEBUG] Waiting for finalizer to run (Resource released)")
          released <- process.waitForOutput("Resource released").as(true).timeout(5.seconds).map(_.getOrElse(false))
          _ <- ZIO.logInfo(s"[TEST DEBUG] Finalizer detection result: $released")
          // Wait for process to exit
          _ <- ZIO.logInfo("[TEST DEBUG] Waiting for process to exit")
          exitCode <- process.waitForExit()
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process exited with code: $exitCode")
          _        <- process.destroy
          _ <- ZIO.logInfo("[TEST DEBUG] Process destroyed, test completed")
        } yield {
          val result = assert(released)(isTrue) && assert(exitCode)(equalTo(130))
          ZIO.logInfo(s"[TEST DEBUG] Test result: ${result.toString()}").as(result)
        }
      },
      test("graceful shutdown timeout is respected") {
        for {
          _ <- ZIO.logInfo("[TEST DEBUG] Starting 'graceful shutdown timeout is respected' test")
          // Run with a short timeout
          process <- ProcessTestUtils.runApp(
                       "zio.app.SlowFinalizerApp",
                       Some(Duration.fromMillis(500))
                     )
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process started with PID ${process.process.pid()} and 500ms timeout")
          // Wait for app to start
          _ <- process.waitForOutput("Starting SlowFinalizerApp")
          _ <- ZIO.logInfo("[TEST DEBUG] Detected 'Starting SlowFinalizerApp' in output")
          // Wait for resource acquisition to complete
          _ <- process.waitForOutput("Resource acquired")
          _ <- ZIO.logInfo("[TEST DEBUG] Detected 'Resource acquired' in output")
          // Give the app a moment to stabilize
          _ <- ZIO.logInfo("[TEST DEBUG] Sleeping for 1 second to allow app to stabilize")
          _ <- ZIO.sleep(1.second)
          // Send interrupt signal
          _ <- ZIO.logInfo("[TEST DEBUG] Sending INT signal to process")
          _ <- process.sendSignal("INT")
          // Wait for process to exit
          _ <- ZIO.logInfo("[TEST DEBUG] Starting timer and waiting for process to exit")
          startTime <- Clock.currentTime(ChronoUnit.MILLIS)
          _ <- ZIO.logInfo(s"[TEST DEBUG] Start time: $startTime ms")
          exitCode  <- process.waitForExit()
          endTime   <- Clock.currentTime(ChronoUnit.MILLIS)
          _ <- ZIO.logInfo(s"[TEST DEBUG] End time: $endTime ms")
          output    <- process.outputString
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process output: ${output.replace("\n", " | ")}")
          _        <- process.destroy
          _ <- ZIO.logInfo("[TEST DEBUG] Process destroyed")
          duration   = Duration.fromMillis(endTime - startTime)
          _ <- ZIO.logInfo(s"[TEST DEBUG] Duration: ${duration.toMillis} ms")
        } yield {
          val slowFinStarted = output.contains("Starting slow finalizer")
          val resourceReleased = output.contains("Resource released")
          val underTwoSec = duration.toMillis < 2000L
          val exitCodeCheck = exitCode == 130
          
          _ <- ZIO.logInfo(s"[TEST DEBUG] Slow finalizer started: $slowFinStarted")
          _ <- ZIO.logInfo(s"[TEST DEBUG] Resource released message found: $resourceReleased")
          _ <- ZIO.logInfo(s"[TEST DEBUG] Duration under 2 seconds: $underTwoSec")
          _ <- ZIO.logInfo(s"[TEST DEBUG] Exit code is 130: $exitCodeCheck")
          
          val result = assert(output)(containsString("Starting slow finalizer")) &&
            assert(output)(not(containsString("Resource released"))) &&
            assert(duration.toMillis)(isLessThan(2000L)) &&
            assert(exitCode)(equalTo(130))
            
          ZIO.logInfo(s"[TEST DEBUG] Test completed with result: ${result.toString()}").as(result)
        }
      },
      test("custom graceful shutdown timeout allows longer finalizers") {
        for {
          _ <- ZIO.logInfo("[TEST DEBUG] Starting 'custom graceful shutdown timeout allows longer finalizers' test")
          // Run with a longer timeout
          process <- ProcessTestUtils.runApp(
                       "zio.app.SlowFinalizerApp",
                       Some(Duration.fromMillis(3000))
                     )
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process started with PID ${process.process.pid()} and 3000ms timeout")
          // Wait for app to start
          _ <- process.waitForOutput("Starting SlowFinalizerApp")
          _ <- ZIO.logInfo("[TEST DEBUG] Detected 'Starting SlowFinalizerApp' in output")
          // Wait for resource acquisition to complete
          _ <- process.waitForOutput("Resource acquired")
          _ <- ZIO.logInfo("[TEST DEBUG] Detected 'Resource acquired' in output")
          // Give the app a moment to stabilize
          _ <- ZIO.logInfo("[TEST DEBUG] Sleeping for 1 second to allow app to stabilize")
          _ <- ZIO.sleep(1.second)
          // Send interrupt signal
          _ <- ZIO.logInfo("[TEST DEBUG] Sending INT signal to process")
          _ <- process.sendSignal("INT")
          // Wait for process to exit
          _ <- ZIO.logInfo("[TEST DEBUG] Waiting for process to exit")
          exitCode  <- process.waitForExit()
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process exited with code: $exitCode")
          outputStr <- process.outputString
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process output: ${outputStr.replace("\n", " | ")}")
          _        <- process.destroy
          _ <- ZIO.logInfo("[TEST DEBUG] Process destroyed")
        } yield {
          val slowFinStarted = outputStr.contains("Starting slow finalizer")
          val resourceReleased = outputStr.contains("Resource released")
          val exitCodeCheck = exitCode == 130
          
          _ <- ZIO.logInfo(s"[TEST DEBUG] Slow finalizer started: $slowFinStarted")
          _ <- ZIO.logInfo(s"[TEST DEBUG] Resource released message found: $resourceReleased")
          _ <- ZIO.logInfo(s"[TEST DEBUG] Exit code is 130: $exitCodeCheck")
          
          val result = assert(outputStr)(containsString("Starting slow finalizer")) &&
            assert(outputStr)(containsString("Resource released")) &&
            assert(exitCode)(equalTo(130))
            
          ZIO.logInfo(s"[TEST DEBUG] Test completed with result: ${result.toString()}").as(result)
        }
      },
      test("nested finalizers execute in correct order") {
        for {
          _ <- ZIO.logInfo("[TEST DEBUG] Starting 'nested finalizers execute in correct order' test")
          process <- ProcessTestUtils.runApp("zio.app.NestedFinalizersApp")
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process started with PID ${process.process.pid()}")
          // Wait for app to start
          _ <- process.waitForOutput("Starting NestedFinalizersApp")
          _ <- ZIO.logInfo("[TEST DEBUG] Detected 'Starting NestedFinalizersApp' in output")
          // Wait for resource acquisition to complete
          _ <- process.waitForOutput("Outer resource acquired")
          _ <- ZIO.logInfo("[TEST DEBUG] Detected 'Outer resource acquired' in output")
          _ <- process.waitForOutput("Inner resource acquired")
          _ <- ZIO.logInfo("[TEST DEBUG] Detected 'Inner resource acquired' in output")
          // Give the app a moment to stabilize
          _ <- ZIO.logInfo("[TEST DEBUG] Sleeping for 1 second to allow app to stabilize")
          _ <- ZIO.sleep(1.second)
          // Send interrupt signal
          _ <- ZIO.logInfo("[TEST DEBUG] Sending INT signal to process")
          _ <- process.sendSignal("INT")
          // Wait for process to exit
          _ <- ZIO.logInfo("[TEST DEBUG] Waiting for process to exit")
          exitCode <- process.waitForExit()
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process exited with code: $exitCode")
          // Add a delay to ensure all output is captured properly
          _ <- ZIO.logInfo("[TEST DEBUG] Adding 2 second delay to ensure output is captured")
          _         <- ZIO.sleep(2.seconds)
          outputStr <- process.outputString
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process output: ${outputStr.replace("\n", " | ")}")
          lines      = outputStr.split(java.lang.System.lineSeparator()).toList
          _ <- ZIO.logInfo(s"[TEST DEBUG] Output line count: ${lines.length}")
          _         <- process.destroy
          _ <- ZIO.logInfo("[TEST DEBUG] Process destroyed")

          // Find the indices of the finalizer messages
          innerFinalizerIndex = lines.indexWhere(_.contains("Inner resource released"))
          outerFinalizerIndex = lines.indexWhere(_.contains("Outer resource released"))
          _ <- ZIO.logInfo(s"[TEST DEBUG] Inner finalizer release index: $innerFinalizerIndex")
          _ <- ZIO.logInfo(s"[TEST DEBUG] Outer finalizer release index: $outerFinalizerIndex")
        } yield {
          val innerFound = innerFinalizerIndex >= 0
          val outerFound = outerFinalizerIndex >= 0
          val orderCorrect = outerFinalizerIndex < innerFinalizerIndex
          val exitCodeCorrect = exitCode == 130
          
          _ <- ZIO.logInfo(s"[TEST DEBUG] Inner finalizer found: $innerFound")
          _ <- ZIO.logInfo(s"[TEST DEBUG] Outer finalizer found: $outerFound")
          _ <- ZIO.logInfo(s"[TEST DEBUG] Order correct (outer before inner): $orderCorrect")
          _ <- ZIO.logInfo(s"[TEST DEBUG] Exit code is 130: $exitCodeCorrect")
          
          val result = assert(innerFinalizerIndex)(isGreaterThanEqualTo(0)) &&
            assert(outerFinalizerIndex)(isGreaterThanEqualTo(0)) &&
            assert(outerFinalizerIndex)(isLessThan(innerFinalizerIndex)) &&
            assert(exitCode)(equalTo(130))
            
          ZIO.logInfo(s"[TEST DEBUG] Test completed with result: ${result.toString()}").as(result)
        }
      },
      test("SIGTERM triggers graceful shutdown with exit code 143") {
        for {
          _ <- ZIO.logInfo("[TEST DEBUG] Starting 'SIGTERM triggers graceful shutdown with exit code 143' test")
          process <- ProcessTestUtils.runApp("zio.app.ResourceWithNeverApp")
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process started with PID ${process.process.pid()}")
          // Wait for app to start
          _ <- process.waitForOutput("Starting ResourceWithNeverApp")
          _ <- ZIO.logInfo("[TEST DEBUG] Detected 'Starting ResourceWithNeverApp' in output")
          // Wait for resource acquisition to complete
          _ <- process.waitForOutput("Resource acquired")
          _ <- ZIO.logInfo("[TEST DEBUG] Detected 'Resource acquired' in output")
          // Give the app a moment to stabilize
          _ <- ZIO.logInfo("[TEST DEBUG] Sleeping for 1 second to allow app to stabilize")
          _ <- ZIO.sleep(1.second)
          // Use process.destroy directly instead of sendSignal("TERM")
          // This is more reliable across platforms
          _ <- ZIO.logInfo("[TEST DEBUG] Sending TERM signal via process.destroy()")
          _ <- ZIO.attempt(process.process.destroy())
          // Wait for process to exit
          _ <- ZIO.logInfo("[TEST DEBUG] Waiting for process to exit")
          exitCode <- process.waitForExit()
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process exited with code: $exitCode")
          output   <- process.outputString
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process output: ${output.replace("\n", " | ")}")
          _        <- process.destroy
          _ <- ZIO.logInfo("[TEST DEBUG] Process destroyed")
        } yield {
          val resourceReleased = output.contains("Resource released")
          val exitCodeCorrect = exitCode == 143
          
          _ <- ZIO.logInfo(s"[TEST DEBUG] Resource released message found: $resourceReleased")
          _ <- ZIO.logInfo(s"[TEST DEBUG] Exit code is 143: $exitCodeCorrect")
          
          val result = assert(output)(containsString("Resource released")) &&
            assert(exitCode)(equalTo(143))
            
          ZIO.logInfo(s"[TEST DEBUG] Test completed with result: ${result.toString()}").as(result)
        }
      },
      test("SIGKILL results in exit code 137") {
        for {
          _ <- ZIO.logInfo("[TEST DEBUG] Starting 'SIGKILL results in exit code 137' test")
          process <- ProcessTestUtils.runApp("zio.app.ResourceWithNeverApp")
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process started with PID ${process.process.pid()}")
          // Wait for app to start
          _ <- process.waitForOutput("Starting ResourceWithNeverApp")
          _ <- ZIO.logInfo("[TEST DEBUG] Detected 'Starting ResourceWithNeverApp' in output")
          // Wait for resource acquisition to complete
          _ <- process.waitForOutput("Resource acquired")
          _ <- ZIO.logInfo("[TEST DEBUG] Detected 'Resource acquired' in output")
          // Give the app a moment to stabilize
          _ <- ZIO.logInfo("[TEST DEBUG] Sleeping for 1 second to allow app to stabilize")
          _ <- ZIO.sleep(1.second)
          // Use process.destroyForcibly directly instead of sendSignal("KILL")
          // This is more reliable across platforms
          _ <- ZIO.logInfo("[TEST DEBUG] Sending KILL signal via process.destroyForcibly()")
          _ <- ZIO.attempt(process.process.destroyForcibly())
          // Wait for process to exit
          _ <- ZIO.logInfo("[TEST DEBUG] Waiting for process to exit")
          exitCode <- process.waitForExit()
          _ <- ZIO.logInfo(s"[TEST DEBUG] Process exited with code: $exitCode")
          // Note: We don't expect finalizers to run with SIGKILL
          _ <- ZIO.logInfo("[TEST DEBUG] Note: Finalizers not expected to run with SIGKILL")
          _ <- process.destroy
          _ <- ZIO.logInfo("[TEST DEBUG] Process destroyed")
        } yield {
          val exitCodeCorrect = exitCode == 137
          _ <- ZIO.logInfo(s"[TEST DEBUG] Exit code is 137: $exitCodeCorrect")
          
          val result = assert(exitCode)(equalTo(137))
          ZIO.logInfo(s"[TEST DEBUG] Test completed with result: ${result.toString()}").as(result)
        }
      },

      // New tests using SpecialExitCodeApp for consistent exit code testing
      suite("Exit code consistency suite")(
        test("SpecialExitCodeApp responds to signals with correct exit codes") {
          for {
            _ <- ZIO.logInfo("[TEST DEBUG] Starting 'SpecialExitCodeApp responds to signals' test")
            process <- ProcessTestUtils.runApp("zio.app.SpecialExitCodeApp")
            _ <- ZIO.logInfo(s"[TEST DEBUG] Process started with PID ${process.process.pid()}")
            // Wait for app to start and signal handler to be installed
            _ <- process.waitForOutput("Signal handler installed")
            _ <- ZIO.logInfo("[TEST DEBUG] Detected 'Signal handler installed' in output")
            // Send INT signal
            _ <- ZIO.logInfo("[TEST DEBUG] Sending INT signal to process")
            _ <- process.sendSignal("INT")
            // Wait for process to exit
            _ <- ZIO.logInfo("[TEST DEBUG] Waiting for process to exit")
            exitCode <- process.waitForExit()
            _ <- ZIO.logInfo(s"[TEST DEBUG] Process exited with code: $exitCode")
            _        <- process.outputString
            _        <- process.destroy
            _ <- ZIO.logInfo("[TEST DEBUG] Process destroyed")
          } yield {
            val exitCodeCorrect = exitCode == 130
            _ <- ZIO.logInfo(s"[TEST DEBUG] Exit code is 130: $exitCodeCorrect")
            
            val result = assert(exitCode)(equalTo(130))
            ZIO.logInfo(s"[TEST DEBUG] Test completed with result: ${result.toString()}").as(result)
          }
        },
        test("SIGTERM produces exit code 143 via SpecialExitCodeApp") {
          for {
            _ <- ZIO.logInfo("[TEST DEBUG] Starting 'SIGTERM produces exit code 143 via SpecialExitCodeApp' test")
            process <- ProcessTestUtils.runApp("zio.app.SpecialExitCodeApp")
            _ <- ZIO.logInfo(s"[TEST DEBUG] Process started with PID ${process.process.pid()}")
            // Wait for app to start and signal handler to be installed
            _ <- process.waitForOutput("Signal handler installed")
            _ <- ZIO.logInfo("[TEST DEBUG] Detected 'Signal handler installed' in output")
            // Use process.destroy directly instead of sendSignal("TERM")
            // This is more reliable across platforms
            _ <- ZIO.logInfo("[TEST DEBUG] Sending TERM signal via process.destroy()")
            _ <- ZIO.attempt(process.process.destroy())
            // Wait for process to exit
            _ <- ZIO.logInfo("[TEST DEBUG] Waiting for process to exit")
            exitCode <- process.waitForExit()
            _ <- ZIO.logInfo(s"[TEST DEBUG] Process exited with code: $exitCode")
            output   <- process.outputString
            _ <- ZIO.logInfo(s"[TEST DEBUG] Process output: ${output.replace("\n", " | ")}")
            _        <- process.destroy
            _ <- ZIO.logInfo("[TEST DEBUG] Process destroyed")
          } yield {
            val signalDetected = output.contains("ZIO-SIGNAL: TERM")
            val exitCodeCorrect = exitCode == 143
            _ <- ZIO.logInfo(s"[TEST DEBUG] ZIO-SIGNAL: TERM detected: $signalDetected")
            _ <- ZIO.logInfo(s"[TEST DEBUG] Exit code is 143: $exitCodeCorrect")
            
            val result = assert(output.contains("ZIO-SIGNAL: TERM") || exitCode == 143)(isTrue) &&
              assert(exitCode)(equalTo(143))
              
            ZIO.logInfo(s"[TEST DEBUG] Test completed with result: ${result.toString()}").as(result)
          }
        },
        test("SIGKILL produces exit code 137 via SpecialExitCodeApp") {
          for {
            _ <- ZIO.logInfo("[TEST DEBUG] Starting 'SIGKILL produces exit code 137 via SpecialExitCodeApp' test")
            process <- ProcessTestUtils.runApp("zio.app.SpecialExitCodeApp")
            _ <- ZIO.logInfo(s"[TEST DEBUG] Process started with PID ${process.process.pid()}")
            // Wait for app to start and signal handler to be installed
            _ <- process.waitForOutput("Signal handler installed")
            _ <- ZIO.logInfo("[TEST DEBUG] Detected 'Signal handler installed' in output")
            // Use process.destroyForcibly directly instead of sendSignal("KILL")
            // This is more reliable across platforms
            _ <- ZIO.logInfo("[TEST DEBUG] Sending KILL signal via process.destroyForcibly()")
            _ <- ZIO.attempt(process.process.destroyForcibly())
            // Wait for process to exit
            _ <- ZIO.logInfo("[TEST DEBUG] Waiting for process to exit")
            exitCode <- process.waitForExit()
            _ <- ZIO.logInfo(s"[TEST DEBUG] Process exited with code: $exitCode")
            _        <- process.destroy
            _ <- ZIO.logInfo("[TEST DEBUG] Process destroyed")
          } yield {
            val exitCodeCorrect = exitCode == 137
            _ <- ZIO.logInfo(s"[TEST DEBUG] Exit code is 137: $exitCodeCorrect")
            
            val result = assert(exitCode)(equalTo(137))
            ZIO.logInfo(s"[TEST DEBUG] Test completed with result: ${result.toString()}").as(result)
          }
        }
      )
    ) @@ jvmOnly @@ withLiveClock @@ sequential
  ) @@ sequential
}
