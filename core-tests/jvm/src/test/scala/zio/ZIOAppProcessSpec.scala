package zio

import zio.test._
import zio.testapps.ProcessTestHelper

object ZIOAppProcessSpec extends ZIOBaseSpec {
  private val defaultTimeout = java.time.Duration.ofSeconds(30)
  private val sigintExitCode = 130

  def spec = suite("ZIOAppProcessSpec")(
    suite("natural completion")(
      test("successful app emits exit code 0") {
        ZIO.attemptBlockingInterrupt {
          ProcessTestHelper.runApp("zio.testapps.TestAppSuccess", defaultTimeout)
        }.map { result =>
          assertTrue(result.exitCode == 0) &&
          assertTrue(result.stdout.contains("APP_SUCCESS"))
        }
      },
      test("failed app emits exit code 1") {
        ZIO.attemptBlockingInterrupt {
          ProcessTestHelper.runApp("zio.testapps.TestAppFailure", defaultTimeout)
        }.map { result =>
          assertTrue(result.exitCode == 1)
        }
      },
      test("defect app emits exit code 1") {
        ZIO.attemptBlockingInterrupt {
          ProcessTestHelper.runApp("zio.testapps.TestAppDie", defaultTimeout)
        }.map { result =>
          assertTrue(result.exitCode == 1)
        }
      }
    ),
    suite("signal shutdown")(
      test("finalizers run when app receives SIGINT") {
        ZIO.attemptBlockingInterrupt {
          ProcessTestHelper.runAppAndSignal("zio.testapps.TestAppFinalizer", "APP_STARTED", "INT", defaultTimeout)
        }.map { result =>
          assertTrue(result.stdout.contains("FINALIZER_RAN")) &&
          assertTrue(result.exitCode == sigintExitCode)
        }
      },
      test("multiple finalizers run on SIGINT (regression #9901)") {
        ZIO.attemptBlockingInterrupt {
          ProcessTestHelper.runAppAndSignal(
            "zio.testapps.TestAppMultipleFinalizers",
            "APP_STARTED",
            "INT",
            defaultTimeout
          )
        }.map { result =>
          assertTrue(result.stdout.contains("FINALIZER_1_RAN")) &&
          assertTrue(result.stdout.contains("FINALIZER_2_RAN")) &&
          assertTrue(result.exitCode == sigintExitCode)
        }
      },
      test("shutdown sequence does not hang") {
        ZIO.attemptBlockingInterrupt {
          val start = java.lang.System.currentTimeMillis()
          val result = ProcessTestHelper.runAppAndSignal(
            "zio.testapps.TestAppFinalizer",
            "APP_STARTED",
            "INT",
            java.time.Duration.ofSeconds(15)
          )
          val elapsed = java.lang.System.currentTimeMillis() - start
          (result, elapsed)
        }.map { case (result, elapsed) =>
          assertTrue(result.exitCode == sigintExitCode) &&
          assertTrue(elapsed < 10000L)
        }
      },
      test("gracefulShutdownTimeout is respected") {
        ZIO.attemptBlockingInterrupt {
          val start = java.lang.System.currentTimeMillis()
          val result = ProcessTestHelper.runAppAndSignal(
            "zio.testapps.TestAppSlowFinalizer",
            "APP_STARTED",
            "INT",
            java.time.Duration.ofSeconds(20)
          )
          val elapsed = java.lang.System.currentTimeMillis() - start
          (result, elapsed)
        }.map { case (result, elapsed) =>
          assertTrue(result.stdout.contains("SLOW_FINALIZER_STARTED")) &&
          assertTrue(result.stdout.contains("Timed out waiting for ZIO application to shut down")) &&
          assertTrue(result.exitCode == sigintExitCode) &&
          assertTrue(elapsed > 1000L) &&
          assertTrue(elapsed < 8000L)
        }
      }
    ),
    suite("regressions")(
      test("shutdown hook and finalizers complete without uncaught shutdown exceptions (#9807)") {
        ZIO.attemptBlockingInterrupt {
          ProcessTestHelper.runAppAndSignal("zio.testapps.TestAppCleanShutdown", "APP_STARTED", "INT", defaultTimeout)
        }.map { result =>
          assertTrue(result.stderr.contains("SHUTDOWN_HOOK_RAN")) &&
          assertTrue(result.stdout.contains("FINALIZER_RAN")) &&
          assertTrue(!result.stderr.contains("Exception in thread")) &&
          assertTrue(!result.stderr.contains("FiberFailure"))
        }
      },
      test("signal handling startup does not fail with SignalHandler linkage errors (#9240)") {
        ZIO.attemptBlockingInterrupt {
          ProcessTestHelper.runApp("zio.testapps.TestAppSuccess", defaultTimeout)
        }.map { result =>
          assertTrue(result.exitCode == 0) &&
          assertTrue(!result.stderr.contains("NoClassDefFoundError")) &&
          assertTrue(!result.stderr.contains("sun/misc/SignalHandler"))
        }
      }
    )
  ) @@ TestAspect.timeout(90.seconds) @@ TestAspect.sequential
}
