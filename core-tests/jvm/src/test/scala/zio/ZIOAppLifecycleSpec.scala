package zio

import zio.test._

import java.io.File
import java.util.concurrent.TimeUnit

object ZIOAppLifecycleSpec extends ZIOSpecDefault {

  // ---------------------------------------------------------------------------
  // Helpers for launching sub-processes
  // ---------------------------------------------------------------------------

  private val javaExecutable: String = {
    val javaHome = java.lang.System.getProperty("java.home")
    val sep      = File.separator
    s"$javaHome${sep}bin${sep}java"
  }

  private val testClasspath: String =
    java.lang.System.getProperty("java.class.path")

  private def launchApp(mainClass: String, extraArgs: List[String] = Nil): ProcessBuilder = {
    val cmd = List(javaExecutable, "-cp", testClasspath, mainClass) ++ extraArgs
    val pb  = new ProcessBuilder(cmd: _*)
    pb.redirectErrorStream(false)
    pb
  }

  /** Collect all bytes from a stream within a time window. */
  private def readOutput(process: Process): (String, String) = {
    val stdout = new String(process.getInputStream.readAllBytes())
    val stderr = new String(process.getErrorStream.readAllBytes())
    (stdout, stderr)
  }

  /**
   * Run a sub-process to completion (or kill after timeoutSeconds) and return
   * (exitCode, stdout, stderr).
   */
  private def runToCompletion(
    pb: ProcessBuilder,
    timeoutSeconds: Int = 30
  ): ZIO[Any, Throwable, (Int, String, String)] =
    ZIO.attemptBlocking {
      val process  = pb.start()
      val finished = process.waitFor(timeoutSeconds.toLong, TimeUnit.SECONDS)
      if (!finished) {
        process.destroyForcibly()
        process.waitFor(5L, TimeUnit.SECONDS)
      }
      val (stdout, stderr) = readOutput(process)
      (process.exitValue(), stdout, stderr)
    }

  /**
   * Start a sub-process, wait briefly, send SIGINT (graceful), wait for
   * completion, return (exitCode, stdout, stderr).
   */
  private def runWithSigint(
    pb: ProcessBuilder,
    delayBeforeSignalMs: Long = 500L,
    timeoutSeconds: Int = 30
  ): ZIO[Any, Throwable, (Int, String, String)] =
    ZIO.attemptBlocking {
      val process = pb.start()
      // Let the app start up
      Thread.sleep(delayBeforeSignalMs)
      // SIGINT via kill -INT <pid>  (Java 9+ ProcessHandle)
      val pid = process.pid()
      java.lang.Runtime.getRuntime.exec(Array("/bin/kill", "-INT", pid.toString)).waitFor()
      val finished = process.waitFor(timeoutSeconds.toLong, TimeUnit.SECONDS)
      if (!finished) {
        process.destroyForcibly()
        process.waitFor(5L, TimeUnit.SECONDS)
      }
      val (stdout, stderr) = readOutput(process)
      (process.exitValue(), stdout, stderr)
    }

  // ---------------------------------------------------------------------------
  // Inner ZIOApp programs (launched as sub-processes)
  // ---------------------------------------------------------------------------

  /** app: exits successfully -> exit code 0 */
  object SuccessApp extends ZIOAppDefault {
    def run = Console.printLine("success")
  }

  /** app: fails with a non-zero exit code -> exit code 1 */
  object FailureApp extends ZIOAppDefault {
    def run = ZIO.fail("boom")
  }

  /** app: calls exit(42) explicitly */
  object ExplicitExitApp extends ZIOAppDefault {
    def run = exit(ExitCode(42))
  }

  /**
   * app: runs forever, registers a finalizer that prints "finalizer ran". On
   * SIGINT the finalizer should be invoked.
   */
  object ForeverWithFinalizerApp extends ZIOAppDefault {
    def run =
      ZIO
        .acquireReleaseWith(
          ZIO.succeed(())
        )(_ => Console.printLine("finalizer ran").orDie)(_ => ZIO.never)
  }

  /**
   * app: gracefulShutdownTimeout is 2 seconds; finalizer sleeps for 10 seconds.
   * Shutdown must complete within the timeout and exit with a non-zero code.
   */
  object SlowFinalizerApp extends ZIOAppDefault {
    override def gracefulShutdownTimeout: Duration = 2.seconds

    def run =
      ZIO
        .acquireReleaseWith(
          Console.printLine("started").orDie
        )(_ => ZIO.sleep(10.seconds) *> Console.printLine("slow finalizer done").orDie)(_ => ZIO.never)
  }

  /**
   * Regression #9901: ZIOApp should not hang when the effect fails immediately
   * and finalizers are present.
   */
  object Issue9901App extends ZIOAppDefault {
    def run =
      ZIO
        .acquireReleaseWith(
          ZIO.succeed(())
        )(_ => Console.printLine("9901 finalizer").orDie)(_ => ZIO.fail("9901 failure"))
  }

  /**
   * Regression #9807: ZIOApp should emit exit code 1 when the main effect dies
   * (defect).
   */
  object Issue9807App extends ZIOAppDefault {
    def run = ZIO.die(new RuntimeException("9807 defect"))
  }

  /**
   * Regression #9240: Exit code should be non-zero when the app is interrupted
   * externally without completing its work.
   */
  object Issue9240App extends ZIOAppDefault {
    def run =
      Console.printLine("9240 started").orDie *> ZIO.never
  }

  // ---------------------------------------------------------------------------
  // Fully-qualified class names used to spawn sub-processes
  // ---------------------------------------------------------------------------

  private val pkg = "zio.ZIOAppLifecycleSpec"

  private val successAppClass       = s"$pkg$$SuccessApp"
  private val failureAppClass       = s"$pkg$$FailureApp"
  private val explicitExitAppClass  = s"$pkg$$ExplicitExitApp"
  private val foreverFinalizerClass = s"$pkg$$ForeverWithFinalizerApp"
  private val slowFinalizerClass    = s"$pkg$$SlowFinalizerApp"
  private val issue9901Class        = s"$pkg$$Issue9901App"
  private val issue9807Class        = s"$pkg$$Issue9807App"
  private val issue9240Class        = s"$pkg$$Issue9240App"

  // ---------------------------------------------------------------------------
  // Spec
  // ---------------------------------------------------------------------------

  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ZIOAppLifecycleSpec")(
      test("success app exits with code 0") {
        for {
          result        <- runToCompletion(launchApp(successAppClass))
          (code, out, _) = result
        } yield assertTrue(code == 0) && assertTrue(out.contains("success"))
      },
      test("failure app exits with non-zero code") {
        for {
          result        <- runToCompletion(launchApp(failureAppClass))
          (code, _, _)   = result
        } yield assertTrue(code != 0)
      },
      test("explicit exit(42) produces exit code 42") {
        for {
          result        <- runToCompletion(launchApp(explicitExitAppClass))
          (code, _, _)   = result
        } yield assertTrue(code == 42)
      },
      test("finalizer runs on SIGINT") {
        for {
          result        <- runWithSigint(launchApp(foreverFinalizerClass))
          (_, out, _)    = result
        } yield assertTrue(out.contains("finalizer ran"))
      },
      test("slow finalizer is interrupted after gracefulShutdownTimeout") {
        for {
          result              <- runWithSigint(launchApp(slowFinalizerClass), delayBeforeSignalMs = 1000L)
          (code, out, _)       = result
        } yield assertTrue(code != 0) && assertTrue(!out.contains("slow finalizer done"))
      },
      test("regression #9901: app with failing effect and finalizer exits cleanly") {
        for {
          result        <- runToCompletion(launchApp(issue9901Class))
          (code, out, _) = result
        } yield assertTrue(code != 0) && assertTrue(out.contains("9901 finalizer"))
      },
      test("regression #9807: app that dies exits with non-zero code") {
        for {
          result        <- runToCompletion(launchApp(issue9807Class))
          (code, _, _)   = result
        } yield assertTrue(code != 0)
      },
      test("regression #9240: interrupted app exits with non-zero code") {
        for {
          result        <- runWithSigint(launchApp(issue9240Class))
          (code, _, _)   = result
        } yield assertTrue(code != 0)
      }
    ) @@ TestAspect.timeout(120.seconds) @@ TestAspect.sequential
}
