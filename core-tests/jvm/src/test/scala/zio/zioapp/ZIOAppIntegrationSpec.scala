package zio.zioapp

import zio._
import zio.test._
import zio.test.TestAspect._

// Process-level integration tests for ZIOApp lifecycle.
// Spawns real JVM subprocesses so we can test exit codes, SIGINT handling,
// gracefulShutdownTimeout, and the various shutdown regressions (#9807, #9901).
// Each test uses a tiny ZIOAppDefault from the `apps` package.
object ZIOAppIntegrationSpec extends ZIOBaseSpec {

  def spec = suite("ZIOAppIntegrationSpec")(
    suite("exit codes")(
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
    ),
    suite("finalizers on natural completion")(
      test("finalizer runs when app completes normally") {
        for {
          result <- ZIO.attemptBlockingInterrupt(
                      JvmProcessRunner.runToCompletion("zio.zioapp.apps.FinalizerOnExitApp")
                    )
        } yield assertTrue(result.stdout.contains("FINALIZER_RAN"))
      }
    ),
    suite("signal handling")(
      test("finalizer runs when SIGINT is received") {
        for {
          result <- ZIO.attemptBlockingInterrupt(
                      JvmProcessRunner.runAndInterrupt("zio.zioapp.apps.FinalizerOnSignalApp")
                    )
        } yield assertTrue(result.stdout.contains("FINALIZER_RAN"))
      } @@ nonFlaky(3),
      test("multiple finalizers all run on SIGINT in reverse order") {
        for {
          result <- ZIO.attemptBlockingInterrupt(
                      JvmProcessRunner.runAndInterrupt("zio.zioapp.apps.MultiFinalizerOrderApp")
                    )
        } yield {
          val stdout = result.stdout
          // all three should be there
          assertTrue(stdout.contains("FIN_A")) &&
          assertTrue(stdout.contains("FIN_B")) &&
          assertTrue(stdout.contains("FIN_C")) &&
          // C acquired last -> finalized first, then B, then A
          assertTrue(stdout.indexOf("FIN_C") < stdout.indexOf("FIN_B")) &&
          assertTrue(stdout.indexOf("FIN_B") < stdout.indexOf("FIN_A"))
        }
      } @@ nonFlaky(3),
      test("shutdown does not hang indefinitely") {
        for {
          result <- ZIO.attemptBlockingInterrupt(
                      JvmProcessRunner.runAndInterrupt("zio.zioapp.apps.FinalizerOnSignalApp", timeoutMs = 15000)
                    )
        } yield assertTrue(result.exitCode != -1) // -1 means timed out
      } @@ nonFlaky(3),
      test("gracefulShutdownTimeout cuts off slow finalizer") {
        for {
          result <- ZIO.attemptBlockingInterrupt(
                      JvmProcessRunner.runAndInterrupt("zio.zioapp.apps.SlowFinalizerTimeoutApp", timeoutMs = 15000)
                    )
        } yield {
          // The slow finalizer tries to sleep 30s but timeout is 1s,
          // so we should NOT see the done marker
          assertTrue(!result.stdout.contains("SLOW_FIN_DONE"))
        }
      } @@ nonFlaky(3)
    ),
    suite("regressions")(
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
        } yield {
          // The process should exit cleanly - no hang
          assertTrue(result.exitCode != -1)
        }
      } @@ nonFlaky(3),
      test("failing finalizer does not prevent other finalizers from running") {
        for {
          result <- ZIO.attemptBlockingInterrupt(
                      JvmProcessRunner.runAndInterrupt("zio.zioapp.apps.FailingFinalizerApp")
                    )
        } yield {
          val stdout = result.stdout
          assertTrue(stdout.contains("BEFORE_CRASH_FIN")) &&
          assertTrue(stdout.contains("SAFE_FIN"))
        }
      } @@ nonFlaky(3)
    ),
    suite("args passing")(
      test("args are correctly forwarded to the app") {
        for {
          result <- ZIO.attemptBlockingInterrupt(
                      JvmProcessRunner.runToCompletion("zio.zioapp.apps.ArgsEchoApp", args = List("hello", "world"))
                    )
        } yield assertTrue(result.stdout.contains("ARGS:hello,world"))
      }
    )
  ) @@ os(o => o.isUnix || o.isMac) @@ sequential @@ timeout(120.seconds)
}
