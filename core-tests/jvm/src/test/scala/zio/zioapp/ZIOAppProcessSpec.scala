package zio.zioapp

import zio._
import zio.test._
import zio.test.TestAspect._

object ZIOAppProcessSpec extends ZIOBaseSpec {

  private val defaultTimeout: Duration = 30.seconds

  private def runBlocking[A](thunk: => A): ZIO[Any, Throwable, A] =
    ZIO.attemptBlockingInterrupt(thunk)

  private def runApp(mainClass: String, timeout: Duration = defaultTimeout) =
    runBlocking(ProcessTestSupport.runApp(mainClass, timeout))

  private def runAppAndSendSIGINT(mainClass: String, readyMarker: String, timeout: Duration = defaultTimeout) =
    runBlocking(ProcessTestSupport.runAppAndSendSIGINT(mainClass, readyMarker, timeout))

  private val unixOnly: TestAspectPoly =
    if (ProcessTestSupport.isUnix) TestAspect.identity else TestAspect.ignore

  def spec =
    suite("ZIOAppProcessSpec")(
      suite("natural completion")(
        test("successful app emits exit code 0") {
          for {
            result <- runApp("zio.zioapp.apps.SuccessApp")
          } yield assertTrue(result.exitCode == 0)
        },
        test("failed app emits exit code 1") {
          for {
            result <- runApp("zio.zioapp.apps.FailureApp")
          } yield assertTrue(result.exitCode == 1)
        },
        test("defect (die) emits exit code 1") {
          for {
            result <- runApp("zio.zioapp.apps.DieApp")
          } yield assertTrue(result.exitCode == 1)
        }
      ),
      suite("finalizers on natural completion")(
        test("finalizer runs on successful completion") {
          for {
            result <- runApp("zio.zioapp.apps.FinalizerOnSuccessApp")
          } yield assertTrue(result.stdout.contains("FINALIZER_RAN"))
        }
      ),
      suite("signal handling")(
        test("finalizer runs when app receives SIGINT") {
          for {
            result <- runAppAndSendSIGINT("zio.zioapp.apps.FinalizerApp", "APP_READY")
          } yield assertTrue(result.stdout.contains("FINALIZER_RAN"))
        },
        test("multiple finalizers all run on SIGINT (regression #9901)") {
          for {
            result <- runAppAndSendSIGINT("zio.zioapp.apps.MultipleFinalizersApp", "APP_READY")
          } yield assertTrue(result.stdout.contains("FINALIZER_INNER")) &&
            assertTrue(result.stdout.contains("FINALIZER_OUTER"))
        },
        test("shutdown does not hang") {
          for {
            result <- runAppAndSendSIGINT("zio.zioapp.apps.FinalizerApp", "APP_READY")
          } yield assertTrue(result.exitCode != -1)
        },
        test("slow finalizer completes when gracefulShutdownTimeout is Infinity") {
          for {
            result <- runAppAndSendSIGINT("zio.zioapp.apps.SlowFinalizerApp", "APP_READY", 60.seconds)
          } yield assertTrue(result.stdout.contains("SLOW_FINALIZER_DONE"))
        },
        test("gracefulShutdownTimeout cuts off slow finalizer") {
          for {
            result <- runAppAndSendSIGINT("zio.zioapp.apps.ShutdownTimeoutApp", "APP_READY")
          } yield assertTrue(!result.stdout.contains("SLOW_FINALIZER_DONE"))
        }
      ) @@ unixOnly @@ nonFlaky(3),
      suite("regressions")(
        test("no uncaught exception on stderr during shutdown (regression #9807)") {
          for {
            result <- runAppAndSendSIGINT("zio.zioapp.apps.CleanShutdownApp", "APP_READY")
          } yield assertTrue(!result.stderr.contains("Exception")) &&
            assertTrue(result.stdout.contains("JVM_HOOK_RAN"))
        },
        test("signal handler works without NoClassDefFoundError (regression #9240)") {
          for {
            result <- runAppAndSendSIGINT("zio.zioapp.apps.SignalHandlerApp", "APP_READY")
          } yield assertTrue(!result.stderr.contains("NoClassDefFoundError"))
        }
      ) @@ unixOnly @@ nonFlaky(3),
      suite("background fiber cleanup")(
        test("daemon fibers are interrupted on shutdown") {
          for {
            result <- runAppAndSendSIGINT("zio.zioapp.apps.DaemonFiberApp", "APP_READY")
          } yield assertTrue(result.exitCode != -1)
        },
        test("exit code is correct after SIGINT") {
          for {
            result <- runAppAndSendSIGINT("zio.zioapp.apps.FinalizerApp", "APP_READY")
          } yield assertTrue(result.exitCode != -1) && assertTrue(result.stdout.contains("FINALIZER_RAN"))
        }
      ) @@ unixOnly @@ nonFlaky(3)
    ) @@ timeout(120.seconds) @@ sequential
}
