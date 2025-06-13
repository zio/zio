package zio.app

import zio._
import zio.test._
import zio.app.ProcessTestUtils._
import java.time.temporal.ChronoUnit

/**
 * Tests for ZIOApp that require launching external processes.
 * These tests verify the behavior of ZIOApp when running as a standalone application.
 */
object ZIOAppProcessSpec extends ZIOBaseSpec {
  def spec = suite("ZIOAppProcessSpec")(
    // Normal completion tests
    test("app completes successfully") {
      for {
        process <- runApp("zio.app.TestApps$SuccessApp")
        _       <- process.waitForOutput("Starting SuccessApp")
        exitCode <- process.waitForExit()
      } yield assertTrue(exitCode == 0)
    },
    
    test("app fails with non-zero exit code on error") {
      for {
        process <- runApp("zio.app.TestApps$FailureApp")
        _       <- process.waitForOutput("Starting FailureApp")
        exitCode <- process.waitForExit()
      } yield assertTrue(exitCode != 0)
    },
    
    test("app crashes with exception gives non-zero exit code") {
      for {
        process <- runApp("zio.app.TestApps$CrashingApp")
        _       <- process.waitForOutput("Starting CrashingApp")
        exitCode <- process.waitForExit()
      } yield assertTrue(exitCode != 0)
    },
    
    // Finalizer tests
    test("finalizers run on normal completion") {
      for {
        process <- runApp("zio.app.TestApps$ResourceApp")
        _       <- process.waitForOutput("Starting ResourceApp")
        _       <- process.waitForOutput("Resource acquired")
        output  <- process.waitForOutput("Resource released").as(true).timeout(5.seconds).map(_.getOrElse(false))
        exitCode <- process.waitForExit()
      } yield assertTrue(output) && assertTrue(exitCode == 0)
    },
    
    test("finalizers run on signal interruption") {
      for {
        process <- runApp("zio.app.TestApps$ResourceWithNeverApp")
        _       <- process.waitForOutput("Starting ResourceWithNeverApp")
        _       <- process.waitForOutput("Resource acquired")
        _       <- ZIO.sleep(1.second)
        _       <- process.sendSignal("INT") // Send SIGINT (Ctrl+C)
        output  <- process.waitForOutput("Resource released").as(true).timeout(5.seconds).map(_.getOrElse(false))
        exitCode <- process.waitForExit()
      } yield assertTrue(output)
    },
    
    test("nested finalizers run in the correct order") {
      for {
        process <- runApp("zio.app.TestApps$NestedFinalizersApp")
        _       <- process.waitForOutput("Starting NestedFinalizersApp")
        _       <- process.waitForOutput("Outer resource acquired")
        _       <- process.waitForOutput("Inner resource acquired")
        _       <- ZIO.sleep(1.second)
        _       <- process.sendSignal("INT")
        output  <- process.outputString.delay(2.seconds)
      } yield {
        // Inner resources should be released before outer resources
        val lineSeparator = System.lineSeparator()
        val lines = output.split(lineSeparator).toList
        val innerReleaseIndex = lines.indexWhere(_.contains("Inner resource released"))
        
        assertTrue(innerReleaseIndex >= 0) &&
        assertTrue(lines.exists(_.contains("Outer resource released"))) &&
        assertTrue(lines.indexWhere(_.contains("Inner resource released")) < lines.indexWhere(_.contains("Outer resource released")))
      }
    },
    
    // Signal handling tests
    test("SIGINT (Ctrl+C) triggers graceful shutdown") {
      for {
        process <- runApp("zio.app.TestApps$ResourceWithNeverApp")
        _       <- process.waitForOutput("Starting ResourceWithNeverApp")
        _       <- process.waitForOutput("Resource acquired")
        _       <- ZIO.sleep(1.second)
        _       <- process.sendSignal("INT") // Send SIGINT (Ctrl+C)
        released <- process.waitForOutput("Resource released").as(true).timeout(5.seconds).map(_.getOrElse(false))
      } yield assertTrue(released)
    },
    
    test("SIGTERM triggers graceful shutdown") {
      for {
        process <- runApp("zio.app.TestApps$ResourceWithNeverApp")
        _       <- process.waitForOutput("Starting ResourceWithNeverApp")
        _       <- process.waitForOutput("Resource acquired")
        _       <- ZIO.sleep(1.second)
        _       <- process.sendSignal("TERM") // Send SIGTERM
        released <- process.waitForOutput("Resource released").as(true).timeout(5.seconds).map(_.getOrElse(false))
      } yield assertTrue(released)
    },
    
    // Timeout tests
    test("gracefulShutdownTimeout configuration works") {
      for {
        process <- runApp("zio.app.TestApps$TimeoutApp")
        _       <- process.waitForOutput("Starting TimeoutApp")
        output  <- process.waitForOutput("Graceful shutdown timeout: 500ms").as(true).timeout(5.seconds).map(_.getOrElse(false))
      } yield assertTrue(output)
    },
    
    test("slow finalizers are cut off after timeout") {
      for {
        process <- runApp("zio.app.TestApps$SlowFinalizerApp")
        _       <- process.waitForOutput("Starting SlowFinalizerApp")
        _       <- process.waitForOutput("Resource acquired")
        _       <- ZIO.sleep(1.second)
        startTime <- Clock.currentTime(ChronoUnit.MILLIS)
        _       <- process.sendSignal("INT")
        exitCode <- process.waitForExit(3.seconds)
        endTime <- Clock.currentTime(ChronoUnit.MILLIS)
        output  <- process.outputString
      } yield {
        val duration = endTime - startTime
        val startedFinalizer = output.contains("Starting slow finalizer")
        val completedFinalizer = output.contains("Resource released")
        
        // Since the finalizer takes 2 seconds but timeout is 1 second,
        // we expect the finalizer to have started but not completed
        assertTrue(startedFinalizer) &&
        assertTrue(!completedFinalizer) &&
        assertTrue(duration < 2000) // Should not wait the full 2 seconds
      }
    },
    
    // Race condition tests (issue #9807)
    test("no race conditions with JVM shutdown hooks") {
      for {
        process <- runApp("zio.app.TestApps$FinalizerAndHooksApp")
        _       <- process.waitForOutput("Starting FinalizerAndHooksApp")
        _       <- process.waitForOutput("Resource acquired")
        _       <- ZIO.sleep(1.second)
        _       <- process.sendSignal("INT")
        exitCode <- process.waitForExit()
        output  <- process.outputString
      } yield {
        // Check if the output contains any stack traces or exceptions
        val hasException = output.contains("Exception") || output.contains("Error") || 
                          output.contains("Throwable") || output.contains("at ")
                          
        assertTrue(!hasException) && 
        assertTrue(output.contains("Resource released")) &&
        assertTrue(output.contains("JVM shutdown hook executed"))
      }
    },
    
    // Shutdown hook tests
    test("shutdown hooks run during application shutdown") {
      for {
        process <- runApp("zio.app.TestApps$ShutdownHookApp")
        _       <- process.waitForOutput("Starting ShutdownHookApp")
        _       <- ZIO.sleep(1.second)
        _       <- process.sendSignal("INT")
        _       <- ZIO.sleep(1.second) // Give the process time to handle signal
        output  <- process.outputString
      } yield assertTrue(output.contains("JVM shutdown hook executed"))
    }
  ) @@ TestAspect.sequential @@ TestAspect.jvmOnly
} 