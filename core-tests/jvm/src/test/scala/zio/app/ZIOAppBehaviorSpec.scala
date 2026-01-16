package zio.app

import zio._
import zio.test._
import zio.test.TestAspect._

/**
 * Comprehensive test suite for ZIOApp behavior.
 *
 * Tests the following requirements from issue #9909:
 *   1. Correct error code is emitted 2. Application finalizers are run (except
 *      for catastrophic failures) 3. Shutdown sequence doesn't hang 4.
 *      gracefulShutdownTimeout is respected 5. Use-cases from past issues
 *      (#9901, #9807, #9240, #10122)
 *
 * @see
 *   https://github.com/zio/zio/issues/9909
 */
object ZIOAppBehaviorSpec extends ZIOSpecDefault {

  import ProcessTestUtils._

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("ZIOAppBehaviorSpec")(
    exitCodeSuite,
    finalizerSuite,
    signalHandlingSuite,
    gracefulShutdownSuite,
    regressionSuite
  ) @@ sequential @@ timeout(120.seconds) @@ withLiveClock

  // ============================================
  // Exit Code Tests
  // ============================================

  val exitCodeSuite: Spec[Any, Throwable] = suite("Exit Codes")(
    test("successful app exits with code 0") {
      for {
        result <- runApp("zio.app.SuccessApp")
      } yield assertTrue(result.exitCode == 0)
    },
    test("failed app exits with code 1") {
      for {
        result <- runApp("zio.app.FailureApp")
      } yield assertTrue(result.exitCode == 1)
    },
    test("app with defect exits with non-zero code") {
      for {
        result <- runApp("zio.app.DefectApp")
      } yield assertTrue(result.exitCode != 0)
    },
    test("app with thrown exception exits with non-zero code") {
      for {
        result <- runApp("zio.app.ThrowingApp")
      } yield assertTrue(result.exitCode != 0)
    },
    test("app returning Unit exits with code 0") {
      for {
        result <- runApp("zio.app.SuccessExitApp")
      } yield assertTrue(result.exitCode == 0)
    }
  )

  // ============================================
  // Finalizer Tests
  // ============================================

  val finalizerSuite: Spec[Any, Throwable] = suite("Finalizers")(
    test("finalizers run on successful completion") {
      for {
        result <- runApp("zio.app.FinalizerOnSuccessApp")
      } yield assertTrue(
        result.outputContains("ACQUIRED") &&
          result.outputContains("COMPLETED") &&
          result.outputContains("FINALIZED")
      )
    },
    test("finalizers run on failure") {
      for {
        result <- runApp("zio.app.FinalizerOnFailureApp")
      } yield assertTrue(
        result.outputContains("ACQUIRED") &&
          result.outputContains("FINALIZED")
      )
    },
    test("multiple finalizers run in reverse order") {
      for {
        result <- ZIO.scoped {
                    for {
                      process <- startApp("zio.app.MultipleFinalizersApp")
                      ready   <- waitForOutput(process, "READY", 15.seconds)
                      _       <- ZIO.fail(new Exception("App did not print READY")).when(!ready)
                      _       <- sendSignal(process.pid, "SIGINT").when(supportsSignals)
                      result  <- waitForProcess(process, 10.seconds)
                    } yield result
                  }
      } yield {
        val output = result.stdout.mkString("\n")
        // Verify reverse order: 3 acquired last, should finalize first
        val idx1 = output.indexOf("FINALIZED_1")
        val idx2 = output.indexOf("FINALIZED_2")
        val idx3 = output.indexOf("FINALIZED_3")
        assertTrue(
          result.stdoutContains("ACQUIRED_1") &&
            result.stdoutContains("ACQUIRED_2") &&
            result.stdoutContains("ACQUIRED_3") &&
            idx1 >= 0 && idx2 >= 0 && idx3 >= 0 && idx3 < idx2 && idx2 < idx1 // Reverse order
        )
      }
    } @@ ifProp("os.name")(n => !n.toLowerCase.contains("win"))
  )

  // ============================================
  // Signal Handling Tests
  // ============================================

  val signalHandlingSuite: Spec[Any, Throwable] = suite("Signal Handling")(
    test("SIGINT triggers graceful shutdown") {
      for {
        result <- ZIO.scoped {
                    for {
                      process <- startApp("zio.app.SignalFinalizerApp")
                      ready   <- waitForOutput(process, "READY", 15.seconds)
                      _       <- ZIO.fail(new Exception("App did not print READY")).when(!ready)
                      _       <- sendSignal(process.pid, "SIGINT")
                      result  <- waitForProcess(process, 10.seconds)
                    } yield result
                  }
      } yield assertTrue(
        result.outputContains("READY") &&
          result.exitCode != -1 // Process terminated, not timed out
      )
    } @@ ifProp("os.name")(n => !n.toLowerCase.contains("win")),
    test("SIGTERM triggers graceful shutdown") {
      for {
        result <- ZIO.scoped {
                    for {
                      process <- startApp("zio.app.SignalFinalizerApp")
                      ready   <- waitForOutput(process, "READY", 15.seconds)
                      _       <- ZIO.fail(new Exception("App did not print READY")).when(!ready)
                      _       <- sendSignal(process.pid, "SIGTERM")
                      result  <- waitForProcess(process, 10.seconds)
                    } yield result
                  }
      } yield assertTrue(
        result.outputContains("READY") &&
          result.exitCode != -1
      )
    } @@ ifProp("os.name")(n => !n.toLowerCase.contains("win")),
    test("finalizers run on signal-induced shutdown") {
      for {
        result <- ZIO.scoped {
                    for {
                      process <- startApp("zio.app.SignalFinalizerApp")
                      ready   <- waitForOutput(process, "READY", 15.seconds)
                      _       <- ZIO.fail(new Exception("App did not print READY")).when(!ready)
                      _       <- sendSignal(process.pid, "SIGINT")
                      result  <- waitForProcess(process, 15.seconds)
                    } yield result
                  }
      } yield assertTrue(
        result.outputContains("READY") &&
          result.outputContains("FINALIZED")
      )
    } @@ ifProp("os.name")(n => !n.toLowerCase.contains("win"))
  )

  // ============================================
  // Graceful Shutdown Timeout Tests
  // ============================================

  val gracefulShutdownSuite: Spec[Any, Throwable] = suite("Graceful Shutdown Timeout")(
    test("shutdown doesn't hang when finalizers complete quickly") {
      for {
        result <- ZIO.scoped {
                    for {
                      process <- startApp("zio.app.FinalizerApp")
                      ready   <- waitForOutput(process, "READY", 15.seconds)
                      _       <- ZIO.fail(new Exception("App did not print READY")).when(!ready)
                      _       <- sendSignal(process.pid, "SIGINT").when(supportsSignals)
                      result  <- waitForProcess(process, 10.seconds)
                    } yield result
                  }
      } yield assertTrue(
        result.duration < 10.seconds && // Should complete well within timeout
          result.outputContains("FINALIZED")
      )
    } @@ ifProp("os.name")(n => !n.toLowerCase.contains("win")),
    test("SIGINT allows slow finalizers to complete") {
      for {
        result <- ZIO.scoped {
                    for {
                      process <- startApp("zio.app.CustomTimeoutApp")
                      ready   <- waitForOutput(process, "READY", 15.seconds)
                      _       <- ZIO.fail(new Exception("App did not print READY")).when(!ready)
                      _       <- sendSignal(process.pid, "SIGINT")
                      result  <- waitForProcess(process, 15.seconds)
                    } yield result
                  }
      } yield assertTrue(
        result.outputContains("FINALIZER_START") &&
          result.outputContains("FINALIZER_END") &&
          result.duration < 10.seconds // Custom timeout is 5s, finalizer takes 3s, allow some CI variance
      )
    } @@ ifProp("os.name")(n => !n.toLowerCase.contains("win")),
    test("hanging finalizers are interrupted after gracefulShutdownTimeout") {
      for {
        result <- ZIO.scoped {
                    for {
                      process <- startApp("zio.app.HangingFinalizerApp")
                      ready   <- waitForOutput(process, "READY", 15.seconds)
                      _       <- ZIO.fail(new Exception("App did not print READY")).when(!ready)
                      _       <- sendSignal(process.pid, "SIGINT").when(supportsSignals)
                      result  <- waitForProcess(process, 15.seconds)
                    } yield result
                  }
      } yield assertTrue(
        result.outputContains("FINALIZER_START") &&
          !result.outputContains("FINALIZER_END_SHOULD_NOT_APPEAR") &&
          result.exitCode != -1 // Process terminated, not timed out
      )
    } @@ ifProp("os.name")(n => !n.toLowerCase.contains("win"))
  )

  // ============================================
  // Regression Tests
  // ============================================

  val regressionSuite: Spec[Any, Throwable] = suite("Regression Tests")(
    suite("#9901 - Finalizers on signal shutdown")(
      test("finalizers should run when terminated with Ctrl+C (SIGINT)") {
        for {
          result <- ZIO.scoped {
                      for {
                        process <- startApp("zio.app.Issue9901App")
                        ready   <- waitForOutput(process, "READY", 15.seconds)
                        _       <- ZIO.fail(new Exception("App did not print READY")).when(!ready)
                        _       <- sendSignal(process.pid, "SIGINT")
                        result  <- waitForProcess(process, 15.seconds)
                      } yield result
                    }
        } yield assertTrue(
          result.outputContains("ACQUIRED") &&
            result.outputContains("READY") &&
            result.outputContains("FINALIZED")
        )
      } @@ ifProp("os.name")(n => !n.toLowerCase.contains("win"))
    ),
    suite("#10122 - FiberFailure on shutdown")(
      test("app should exit cleanly without InterruptedException message") {
        for {
          result <- ZIO.scoped {
                      for {
                        process <- startApp("zio.app.Issue10122App")
                        ready   <- waitForOutput(process, "READY", 15.seconds)
                        _       <- ZIO.fail(new Exception("App did not print READY")).when(!ready)
                        _       <- sendSignal(process.pid, "SIGINT")
                        result  <- waitForProcess(process, 10.seconds)
                      } yield result
                    }
        } yield assertTrue(
          result.outputContains("ACQUIRED") &&
            result.outputContains("READY") &&
            result.outputContains("FINALIZED") &&
            !result.stderrContains("InterruptedException") &&
            !result.stderrContains("FiberFailure")
        )
      } @@ ifProp("os.name")(n => !n.toLowerCase.contains("win"))
    ),
    suite("#9807 - Race between shutdown hooks")(
      test("multiple finalizers with different durations complete without errors") {
        for {
          result <- ZIO.scoped {
                      for {
                        process <- startApp("zio.app.Issue9807App")
                        ready   <- waitForOutput(process, "READY", 15.seconds)
                        _       <- ZIO.fail(new Exception("App did not print READY")).when(!ready)
                        _       <- sendSignal(process.pid, "SIGINT")
                        result  <- waitForProcess(process, 10.seconds)
                      } yield result
                    }
        } yield assertTrue(
          result.outputContains("READY") &&
            result.outputContains("FINALIZED_FAST") &&
            result.outputContains("FINALIZED_SLOW") &&
            !result.stderrContains("Exception in thread")
        )
      } @@ ifProp("os.name")(n => !n.toLowerCase.contains("win"))
    ),
    suite("#9240 - Signal handler compatibility")(
      test("app with finalizers exits with custom exit code") {
        for {
          result <- runApp("zio.app.Issue9240App")
        } yield assertTrue(
          result.exitCode == 42 &&
            result.outputContains("ACQUIRED") &&
            result.outputContains("FINALIZED")
        )
      }
    )
  )
}
