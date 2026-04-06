package zio.zioapp

import zio._
import zio.test._
import zio.test.TestAspect._

/**
 * Process-level integration tests for ZIOApp lifecycle behaviour.
 *
 * These tests spawn real JVM child processes so the full `main()` code path
 * is exercised, including JVM shutdown hooks, `System.exit`, signal delivery,
 * and `gracefulShutdownTimeout`.
 *
 * The existing `ZIOAppSpec` uses `invoke()` which is great for unit-level
 * verification but bypasses all JVM-level shutdown machinery. These tests
 * complement it by covering the gaps that only process-level testing can reach.
 *
 * Each test app is a minimal `ZIOAppDefault` in the `apps` sub-package. They
 * print sentinel markers (e.g. "APP_READY", "FINALIZER_RAN") to stdout so
 * the test harness can verify behaviour by inspecting captured output.
 *
 * Signal tests are gated behind `os(o => !o.isWindows)` and use `nonFlaky(3)` for stability.
 *
 * @see [[https://github.com/zio/zio/issues/9909 #9909]]
 */
object ZIOAppIntegrationSpec extends ZIOBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("ZIOAppIntegrationSpec")(
    exitCodeSuite,
    finalizerSuite,
    signalHandlingSuite,
    regressionSuite,
    argsSuite
  ) @@ os(o => !o.isWindows) @@ sequential @@ timeout(120.seconds)

  // ---------------------------------------------------------------------------
  // Exit codes
  // ---------------------------------------------------------------------------

  private val exitCodeSuite = suite("exit codes")(
    test("successful app returns exit code 0") {
      for {
        result <- ZIO.attemptBlockingInterrupt(
                    JvmProcessRunner.runToCompletion("zio.zioapp.apps.SuccessExitApp")
                  )
      } yield assertTrue(result.exitCode == 0) &&
        assertTrue(result.stdout.contains("APP_READY"))
    },
    test("failed app returns exit code 1") {
      for {
        result <- ZIO.attemptBlockingInterrupt(
                    JvmProcessRunner.runToCompletion("zio.zioapp.apps.FailureExitApp")
                  )
      } yield assertTrue(result.exitCode == 1)
    },
    test("defect (die) returns exit code 1") {
      for {
        result <- ZIO.attemptBlockingInterrupt(
                    JvmProcessRunner.runToCompletion("zio.zioapp.apps.DefectExitApp")
                  )
      } yield assertTrue(result.exitCode == 1)
    }
  )

  // ---------------------------------------------------------------------------
  // Finalizers on natural completion
  // ---------------------------------------------------------------------------

  private val finalizerSuite = suite("finalizers")(
    test("finalizer runs on successful completion") {
      for {
        result <- ZIO.attemptBlockingInterrupt(
                    JvmProcessRunner.runToCompletion("zio.zioapp.apps.FinalizerOnExitApp")
                  )
      } yield assertTrue(result.stdout.contains("FINALIZER_RAN"))
    },
    test("finalizer runs on failure") {
      for {
        result <- ZIO.attemptBlockingInterrupt(
                    JvmProcessRunner.runToCompletion("zio.zioapp.apps.FinalizerOnFailureApp")
                  )
      } yield assertTrue(result.exitCode == 1) &&
        assertTrue(result.stdout.contains("FINALIZER_RAN"))
    }
  )

  // ---------------------------------------------------------------------------
  // Signal handling (SIGINT)
  // ---------------------------------------------------------------------------

  private val signalHandlingSuite = suite("signal handling")(
    test("finalizer runs when SIGINT is received (#9901)") {
      for {
        result <- ZIO.attemptBlockingInterrupt(
                    JvmProcessRunner.runAndInterrupt("zio.zioapp.apps.FinalizerOnSignalApp")
                  )
      } yield assertTrue(result.stdout.contains("FINALIZER_RAN"))
    } @@ nonFlaky(3),

    test("multiple finalizers all run in reverse order on SIGINT (#9901)") {
      for {
        result <- ZIO.attemptBlockingInterrupt(
                    JvmProcessRunner.runAndInterrupt("zio.zioapp.apps.MultiFinalizerOrderApp")
                  )
      } yield {
        val out = result.stdout
        assertTrue(out.contains("FIN_A")) &&
        assertTrue(out.contains("FIN_B")) &&
        assertTrue(out.contains("FIN_C")) &&
        // Acquired A, B, C in order; released C, B, A
        assertTrue(out.indexOf("FIN_C") < out.indexOf("FIN_B")) &&
        assertTrue(out.indexOf("FIN_B") < out.indexOf("FIN_A"))
      }
    } @@ nonFlaky(3),

    test("shutdown completes without hanging") {
      for {
        result <- ZIO.attemptBlockingInterrupt(
                    JvmProcessRunner.runAndInterrupt("zio.zioapp.apps.FinalizerOnSignalApp", timeoutMs = 15000)
                  )
      } yield assertTrue(result.exitCode != -1) // -1 means we timed out
    } @@ nonFlaky(3),

    test("gracefulShutdownTimeout cuts off slow finalizer") {
      for {
        result <- ZIO.attemptBlockingInterrupt(
                    JvmProcessRunner.runAndInterrupt("zio.zioapp.apps.SlowFinalizerTimeoutApp", timeoutMs = 15000)
                  )
      } yield {
        // The slow finalizer sleeps 30s but timeout is 1s.
        // SLOW_FIN_DONE must NOT appear.
        assertTrue(!result.stdout.contains("SLOW_FIN_DONE"))
      }
    } @@ nonFlaky(3),

    test("bootstrap layer finalizer runs on SIGINT") {
      for {
        result <- ZIO.attemptBlockingInterrupt(
                    JvmProcessRunner.runAndInterrupt("zio.zioapp.apps.BootstrapFinalizerApp")
                  )
      } yield assertTrue(result.stdout.contains("BOOTSTRAP_RELEASED"))
    } @@ nonFlaky(3)
  )

  // ---------------------------------------------------------------------------
  // Regressions
  // ---------------------------------------------------------------------------

  private val regressionSuite = suite("regressions")(
    test("no uncaught exception on stderr during clean shutdown (#9807)") {
      for {
        result <- ZIO.attemptBlockingInterrupt(
                    JvmProcessRunner.runAndInterrupt("zio.zioapp.apps.CleanStderrApp")
                  )
      } yield {
        val stderr = result.stderr
        assertTrue(!stderr.contains("Exception in thread")) &&
        assertTrue(!stderr.contains("FiberFailure"))
      }
    } @@ nonFlaky(3),

    test("daemon fibers are cleaned up on shutdown") {
      for {
        result <- ZIO.attemptBlockingInterrupt(
                    JvmProcessRunner.runAndInterrupt("zio.zioapp.apps.DaemonFiberCleanupApp")
                  )
      } yield assertTrue(result.exitCode != -1)
    } @@ nonFlaky(3),

    test("failing finalizer does not prevent other finalizers from running") {
      for {
        result <- ZIO.attemptBlockingInterrupt(
                    JvmProcessRunner.runAndInterrupt("zio.zioapp.apps.FailingFinalizerApp")
                  )
      } yield {
        val out = result.stdout
        assertTrue(out.contains("BEFORE_CRASH_FIN")) &&
        assertTrue(out.contains("SAFE_FIN"))
      }
    } @@ nonFlaky(3)
  )

  // ---------------------------------------------------------------------------
  // Args passing
  // ---------------------------------------------------------------------------

  private val argsSuite = suite("args passing")(
    test("args are correctly forwarded to the app") {
      for {
        result <- ZIO.attemptBlockingInterrupt(
                    JvmProcessRunner.runToCompletion("zio.zioapp.apps.ArgsEchoApp", args = List("hello", "world"))
                  )
      } yield assertTrue(result.stdout.contains("ARGS:hello,world"))
    }
  )
}
