package zio.app

import zio._
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.app.ProcessTestUtils._
import java.time.temporal.ChronoUnit
import zio.test.TestAspect

/**
 * Tests for ZIOApp that require launching external processes. These tests
 * verify the behavior of ZIOApp when running as a standalone application.
 */
object ZIOAppProcessSpec extends ZIOSpecDefault {
  // Helper method for debug logging
  private def debugLog(msg: String): UIO[Unit] = 
    ZIO.succeed(println(s"[DEBUG-PROCESS-TEST] ${java.time.LocalDateTime.now()}: $msg"))

  def spec = suite("ZIOAppProcessSpec")(
    // Normal completion tests
    test("app completes successfully") {
      for {
        _ <- debugLog("Starting 'normal exit' test")
        process  <- runApp("zio.app.SuccessApp")
        _        <- debugLog(s"Process started with PID: ${process.process.pid()}")
        startExit <- Clock.currentTime(ChronoUnit.MILLIS)
        exitCode  <- process.waitForExit()
        endExit   <- Clock.currentTime(ChronoUnit.MILLIS)
        _         <- debugLog(s"Process exited with code: $exitCode in ${endExit - startExit}ms")
        output    <- process.outputString
        _         <- debugLog(s"Process output: ${output.replace("\n", "\\n")}")
        _         <- process.destroy
        _         <- debugLog("Process destroyed, test complete")
      } yield assert(exitCode)(equalTo(0))
    },
    test("app fails with exit code 1 on error") {
      for {
        process  <- runApp("zio.app.FailureApp")
        _        <- process.waitForOutput("Starting FailureApp")
        exitCode <- process.waitForExit()
      } yield assertTrue(exitCode == 1) // Error exit code is 1
    },
    test("app crashes with exception gives exit code 1") {
      for {
        process  <- runApp("zio.app.CrashingApp")
        _        <- process.waitForOutput("Starting CrashingApp")
        exitCode <- process.waitForExit()
      } yield assertTrue(exitCode == 1) // Exception exit code is 1
    },

    // Finalizer tests
    test("finalizers run on normal completion") {
      for {
        _ <- debugLog("Starting 'finalizers run on normal completion' test")
        process  <- runApp("zio.app.ResourceApp")
        _        <- debugLog(s"Process started with PID: ${process.process.pid()}")
        _        <- debugLog("App started message detected")
        _        <- process.waitForOutput("Starting ResourceApp")
        _        <- debugLog("Resource acquisition detected")
        startExit <- Clock.currentTime(ChronoUnit.MILLIS)
        exitCode  <- process.waitForExit()
        endExit   <- Clock.currentTime(ChronoUnit.MILLIS)
        _         <- debugLog(s"Process exited with code: $exitCode in ${endExit - startExit}ms")
        output    <- process.outputString
        _         <- debugLog(s"Process output: ${output.replace("\n", "\\n")}")
        _         <- debugLog(s"Output contains 'Resource released': ${output.contains("Resource released")}")
        _         <- process.destroy
        _         <- debugLog("Process destroyed, test complete")
      } yield assert(output)(containsString("Resource released"))
    },
    test("finalizers run on signal interruption") {
      for {
        _ <- debugLog("Starting 'finalizers run on signal interruption' test")
        process <- runApp("zio.app.ResourceWithNeverApp")
        _       <- debugLog(s"Process started with PID: ${process.process.pid()}")
        _       <- process.waitForOutput("Starting ResourceWithNeverApp")
        _       <- debugLog("App started message detected")
        _       <- process.waitForOutput("Resource acquired")
        _       <- debugLog("Resource acquisition detected")
        startWait <- Clock.currentTime(ChronoUnit.MILLIS)
        _         <- ZIO.sleep(1.second)
        endWait   <- Clock.currentTime(ChronoUnit.MILLIS)
        _         <- debugLog(s"Stabilization wait completed in ${endWait - startWait}ms")
        _         <- process.sendSignal("INT")
        _         <- debugLog("INT signal sent")
        startExit <- Clock.currentTime(ChronoUnit.MILLIS)
        exitCode  <- process.waitForExit()
        endExit   <- Clock.currentTime(ChronoUnit.MILLIS)
        _         <- debugLog(s"Process exited with code: $exitCode in ${endExit - startExit}ms")
        output    <- process.outputString
        _         <- debugLog(s"Process output: ${output.replace("\n", "\\n")}")
        _         <- debugLog(s"Output contains 'Resource released': ${output.contains("Resource released")}")
        _         <- process.destroy
        _         <- debugLog("Process destroyed, test complete")
      } yield assert(exitCode)(equalTo(130)) &&
             assert(output)(containsString("Resource released"))
    },
    test("nested finalizers run in the correct order") {
      for {
        _ <- debugLog("Starting 'nested finalizers run in the correct order' test")
        process <- runApp("zio.app.NestedFinalizersApp")
        _       <- debugLog(s"Process started with PID: ${process.process.pid()}")
        _       <- process.waitForOutput("Starting NestedFinalizersApp")
        _       <- debugLog("App started message detected")
        _       <- process.waitForOutput("Outer resource acquired")
        _       <- debugLog("Outer resource acquisition detected")
        _       <- process.waitForOutput("Inner resource acquired")
        _       <- debugLog("Inner resource acquisition detected")
        startWait <- Clock.currentTime(ChronoUnit.MILLIS)
        _         <- ZIO.sleep(1.second)
        endWait   <- Clock.currentTime(ChronoUnit.MILLIS)
        _         <- debugLog(s"Stabilization wait completed in ${endWait - startWait}ms")
        _         <- process.sendSignal("INT")
        _         <- debugLog("INT signal sent")
        startExit <- Clock.currentTime(ChronoUnit.MILLIS)
        exitCode  <- process.waitForExit()
        endExit   <- Clock.currentTime(ChronoUnit.MILLIS)
        _         <- debugLog(s"Process exited with code: $exitCode in ${endExit - startExit}ms")
        output    <- process.outputString
        _         <- debugLog(s"Process output: ${output.replace("\n", "\\n")}")
        lines     = output.split(java.lang.System.lineSeparator()).toList
        _         <- debugLog(s"Output lines: ${lines.size}")
        
        innerFinalizerIndex = lines.indexWhere(_.contains("Inner resource released"))
        outerFinalizerIndex = lines.indexWhere(_.contains("Outer resource released"))
        _         <- debugLog(s"Inner finalizer index: $innerFinalizerIndex, Outer finalizer index: $outerFinalizerIndex")
        _         <- process.destroy
        _         <- debugLog("Process destroyed, test complete")
      } yield assert(innerFinalizerIndex)(isGreaterThanEqualTo(0)) &&
             assert(outerFinalizerIndex)(isGreaterThanEqualTo(0)) &&
             assert(outerFinalizerIndex)(isGreaterThan(innerFinalizerIndex))
    },

    // Signal handling tests
    test("SIGINT (Ctrl+C) triggers graceful shutdown with exit code 130") {
      for {
        process  <- runApp("zio.app.ResourceWithNeverApp")
        _        <- process.waitForOutput("Starting ResourceWithNeverApp")
        _        <- process.waitForOutput("Resource acquired")
        _        <- ZIO.sleep(1.second)
        _        <- process.sendSignal("INT") // Send SIGINT (Ctrl+C)
        released <- process.waitForOutput("Resource released").as(true).timeout(5.seconds).map(_.getOrElse(false))
        exitCode <- process.waitForExit()
      } yield assertTrue(released) && assertTrue(exitCode == 130) // SIGINT exit code is 130
    },
    test("SIGTERM triggers graceful shutdown with exit code 143") {
      for {
        process  <- runApp("zio.app.ResourceWithNeverApp")
        _        <- process.waitForOutput("Starting ResourceWithNeverApp")
        _        <- process.waitForOutput("Resource acquired")
        _        <- ZIO.sleep(1.second)
        _        <- ZIO.attempt(process.process.destroy())
        released <- process.waitForOutput("Resource released").as(true).timeout(5.seconds).map(_.getOrElse(false))
        exitCode <- process.waitForExit()
      } yield assertTrue(released) && assertTrue(exitCode == 143) // SIGTERM exit code is 143
    },
    test("SIGKILL gives exit code 137") {
      for {
        process  <- runApp("zio.app.ResourceWithNeverApp")
        _        <- process.waitForOutput("Starting ResourceWithNeverApp")
        _        <- process.waitForOutput("Resource acquired")
        _        <- ZIO.sleep(1.second)
        _        <- ZIO.attempt(process.process.destroyForcibly())
        exitCode <- process.waitForExit()
      } yield
      // SIGKILL should give exit code 137 as per maintainer requirements
      assertTrue(exitCode == 137)
    },

    // Timeout tests
    test("gracefulShutdownTimeout configuration works") {
      for {
        // Pass an explicit timeout of 3000ms (3 seconds)
        process <- runApp("zio.app.TimeoutApp", Some(Duration.fromMillis(3000)))
        _       <- process.waitForOutput("Starting TimeoutApp")
        output <- process
                    .waitForOutput("Using overridden graceful shutdown timeout: 3000ms")
                    .as(true)
                    .timeout(5.seconds)
                    .map(_.getOrElse(false))
      } yield assertTrue(output)
    },
    test("slow finalizers are cut off after timeout") {
      for {
        process   <- runApp("zio.app.SlowFinalizerApp")
        _         <- process.waitForOutput("Starting SlowFinalizerApp")
        _         <- process.waitForOutput("Resource acquired")
        _         <- ZIO.sleep(1.second)
        startTime <- Clock.currentTime(ChronoUnit.MILLIS)
        _         <- process.sendSignal("INT")
        exitCode  <- process.waitForExit(3.seconds)
        endTime   <- Clock.currentTime(ChronoUnit.MILLIS)
        output    <- process.outputString
      } yield {
        val duration           = endTime - startTime
        val startedFinalizer   = output.contains("Starting slow finalizer")
        val completedFinalizer = output.contains("Resource released")

        // Since the finalizer takes 2 seconds but timeout is 1 second,
        // we expect the finalizer to have started but not completed
        assertTrue(startedFinalizer) &&
        assertTrue(!completedFinalizer) &&
        assertTrue(duration < 2000) && // Should not wait the full 2 seconds
        assertTrue(exitCode == 130)    // SIGINT exit code is 130
      }
    },

    // Race condition tests (issue #9807)
    test("no race conditions with JVM shutdown hooks") {
      for {
        process  <- runApp("zio.app.FinalizerAndHooksApp")
        _        <- process.waitForOutput("Starting FinalizerAndHooksApp")
        _        <- process.waitForOutput("Resource acquired")
        _        <- ZIO.sleep(1.second)
        _        <- process.sendSignal("INT")
        exitCode <- process.waitForExit()
        output   <- process.outputString
      } yield {
        // Check if the output contains any stack traces or exceptions
        val hasException = output.contains("Exception") || output.contains("Error") ||
          output.contains("Throwable") || output.contains("at ")

        assertTrue(!hasException) &&
        assertTrue(output.contains("Resource released")) &&
        assertTrue(output.contains("JVM shutdown hook executed")) &&
        assertTrue(exitCode == 130) // SIGINT exit code is 130
      }
    },

    // Shutdown hook tests
    test("shutdown hooks run during application shutdown") {
      for {
        process  <- runApp("zio.app.ShutdownHookApp")
        _        <- process.waitForOutput("Starting ShutdownHookApp")
        _        <- ZIO.sleep(1.second)
        _        <- process.sendSignal("INT")
        exitCode <- process.waitForExit()
        output   <- process.outputString
      } yield assertTrue(output.contains("JVM shutdown hook executed")) && assertTrue(
        exitCode == 130
      ) // SIGINT exit code is 130
    },

    // Cross-platform consistent exit code tests using SpecialExitCodeApp
    suite("Cross-platform exit code tests")(
      test("SpecialExitCodeApp consistently returns exit code 130 for SIGINT") {
        for {
          process  <- runApp("zio.app.SpecialExitCodeApp")
          _        <- process.waitForOutput("Signal handler installed")
          _        <- process.sendSignal("INT")
          exitCode <- process.waitForExit()
          _        <- process.outputString
        } yield assertTrue(exitCode == 130) // Only check exit code, don't require specific output
      },
      test("SpecialExitCodeApp consistently returns exit code 143 for SIGTERM") {
        for {
          process  <- runApp("zio.app.SpecialExitCodeApp")
          _        <- process.waitForOutput("Signal handler installed")
          _        <- ZIO.attempt(process.process.destroy())
          exitCode <- process.waitForExit()
          output   <- process.outputString
        } yield assertTrue(output.contains("ZIO-SIGNAL: TERM") || exitCode == 143) && assertTrue(exitCode == 143)
      },
      test("SpecialExitCodeApp consistently returns exit code 137 for SIGKILL") {
        for {
          process  <- runApp("zio.app.SpecialExitCodeApp")
          _        <- process.waitForOutput("Signal handler installed")
          _        <- ZIO.attempt(process.process.destroyForcibly())
          exitCode <- process.waitForExit()
        } yield assertTrue(exitCode == 137) // Maintainer-specified exit code for SIGKILL
      }
    )
  ) @@ TestAspect.sequential @@ TestAspect.jvmOnly @@ TestAspect.withLiveClock
}
