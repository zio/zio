package zio

import zio.test._
import zio.testapps.ProcessTestHelper

object ZIOAppProcessSpec extends ZIOBaseSpec {

  private val defaultTimeout = java.time.Duration.ofSeconds(30)

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
      test("defect (die) emits exit code 1") {
        ZIO.attemptBlockingInterrupt {
          ProcessTestHelper.runApp("zio.testapps.TestAppDie", defaultTimeout)
        }.map { result =>
          assertTrue(result.exitCode == 1)
        }
      }
    ),
    suite("signal handling")(
      test("finalizers run when app receives SIGTERM") {
        ZIO.attemptBlockingInterrupt {
          ProcessTestHelper.runAppAndSignal("zio.testapps.TestAppFinalizer", "APP_STARTED", defaultTimeout)
        }.map { result =>
          assertTrue(result.stdout.contains("FINALIZER_RAN")) &&
          assertTrue(result.exitCode != -1)
        }
      },
      test("multiple finalizers all run on signal (regression #9901)") {
        ZIO.attemptBlockingInterrupt {
          ProcessTestHelper.runAppAndSignal("zio.testapps.TestAppMultipleFinalizers", "APP_STARTED", defaultTimeout)
        }.map { result =>
          assertTrue(result.stdout.contains("FINALIZER_1_RAN")) &&
          assertTrue(result.stdout.contains("FINALIZER_2_RAN"))
        }
      },
      test("shutdown doesn't hang") {
        ZIO.attemptBlockingInterrupt {
          val start = java.lang.System.currentTimeMillis()
          val result =
            ProcessTestHelper.runAppAndSignal(
              "zio.testapps.TestAppFinalizer",
              "APP_STARTED",
              java.time.Duration.ofSeconds(15)
            )
          val elapsed = java.lang.System.currentTimeMillis() - start
          (result, elapsed)
        }.map { case (result, elapsed) =>
          assertTrue(result.exitCode != -1) &&
          assertTrue(elapsed < 10000L)
        }
      },
      test("gracefulShutdownTimeout is respected") {
        ZIO.attemptBlockingInterrupt {
          val start = java.lang.System.currentTimeMillis()
          val result = ProcessTestHelper.runAppAndSignal(
            "zio.testapps.TestAppSlowFinalizer",
            "APP_STARTED",
            java.time.Duration.ofSeconds(15)
          )
          val elapsed = java.lang.System.currentTimeMillis() - start
          (result, elapsed)
        }.map { case (result, elapsed) =>
          assertTrue(result.exitCode != -1) &&
          assertTrue(elapsed < 10000L)
        }
      }
    ),
    suite("regressions")(
      test("no uncaught exception on stderr during shutdown (regression #9807)") {
        ZIO.attemptBlockingInterrupt {
          ProcessTestHelper.runAppAndSignal("zio.testapps.TestAppCleanShutdown", "APP_STARTED", defaultTimeout)
        }.map { result =>
          assertTrue(result.stderr.contains("SHUTDOWN_HOOK_RAN")) &&
          assertTrue(!result.stderr.contains("Exception in thread")) &&
          assertTrue(!result.stderr.contains("FiberFailure"))
        }
      },
      test("signal handler works via reflection without NoClassDefFoundError (regression #9240)") {
        ZIO.attemptBlockingInterrupt {
          ProcessTestHelper.runApp("zio.testapps.TestAppSuccess", defaultTimeout)
        }.map { result =>
          assertTrue(!result.stderr.contains("NoClassDefFoundError")) &&
          assertTrue(!result.stderr.contains("sun/misc/SignalHandler")) &&
          assertTrue(result.exitCode == 0)
        }
      }
    )
  ) @@ TestAspect.timeout(60.seconds) @@ TestAspect.sequential
}
