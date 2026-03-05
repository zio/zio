package zio

import zio.test._

import java.io.{BufferedReader, InputStreamReader}
import java.util.concurrent.TimeUnit

/**
 * Process-based integration tests for [[ZIOApp]].
 *
 * Unlike the in-process [[ZIOAppSpec]] (which uses [[ZIOApp.invoke]]), these
 * tests spawn each fixture app in a **separate JVM** so that the full
 * lifecycle is exercised — JVM shutdown hooks, signal delivery, exit codes,
 * and the [[gracefulShutdownTimeout]] mechanism.
 *
 * == How it works ==
 *
 *  1. Each test launches a fixture object from [[ZIOAppTestApps]] via
 *     [[ProcessBuilder]], reusing the current test classpath.
 *  2. For ''normal completion'' tests the parent simply waits for the child
 *     to exit and asserts on its exit code / stdout markers.
 *  3. For ''signal'' tests the parent waits for the `READY` marker, sends
 *     `kill -INT` (Unix) or calls `ProcessHandle#destroy` (Windows),
 *     and then asserts on finalizer markers and timing.
 *
 * == Platform notes ==
 *
 * Signal-based suites are gated behind [[unixOnly]] because
 * [[ProcessHandle#destroy]] on Windows terminates the process without
 * invoking JVM shutdown hooks.  CI runs on Linux, so all suites are
 * exercised there.
 *
 * == Coverage mapping ==
 *
 * | Requirement (issue #9909)                   | Suite / test                                        |
 * |---------------------------------------------|-----------------------------------------------------|
 * | Correct exit code on success / failure       | ''app completion''                                  |
 * | Finalizers run (normal)                      | ''finalizers on normal completion''                 |
 * | Finalizers run (signal)                      | ''external signal handling''                        |
 * | gracefulShutdownTimeout respected            | ''gracefulShutdownTimeout''                         |
 * | Shutdown does not hang                       | ''gracefulShutdownTimeout / no-hang''               |
 * | #9901 – finalizers not waited on             | ''slow finalizer completes …''                      |
 * | #9807 – shutdown-hook race                   | all signal tests (atomic `shuttingDown` flag)       |
 * | #9240 – sun.misc.SignalHandler CNFE          | every test app starts without `NoClassDefFoundError` |
 */
object ZIOAppIntegrationSpec extends ZIOBaseSpec {

  // -- helpers ----------------------------------------------------------------

  private val javaHome  = java.lang.System.getProperty("java.home")
  private val javaBin   = s"$javaHome/bin/java"
  private val classpath = java.lang.System.getProperty("java.class.path")
  private val isWindows = java.lang.System.getProperty("os.name").toLowerCase.contains("win")

  /**
   * Skips the annotated suite when running on Windows.
   * Signal delivery on Windows does not trigger JVM shutdown hooks, so
   * signal-based tests are meaningless there.
   */
  private val unixOnly: TestAspectPoly =
    if (isWindows) TestAspect.ignore else TestAspect.identity

  /** Starts a child JVM running `mainClass` with the current classpath. */
  private def startProcess(mainClass: String): java.lang.Process = {
    val builder = new ProcessBuilder(javaBin, "-cp", classpath, mainClass)
    builder.redirectErrorStream(true)
    builder.start()
  }

  /** Drains the remaining stdout of a (finished) process into a String. */
  private def readOutput(process: java.lang.Process): String = {
    val reader = new BufferedReader(new InputStreamReader(process.getInputStream))
    val sb     = new StringBuilder
    var line   = reader.readLine()
    while (line != null) {
      sb.append(line).append("\n")
      line = reader.readLine()
    }
    sb.toString()
  }

  /**
   * Reads stdout line-by-line until `marker` appears or `timeoutMs`
   * elapses.  Returns everything that was read.
   */
  private def waitForMarker(process: java.lang.Process, marker: String, timeoutMs: Long): String = {
    val reader   = new BufferedReader(new InputStreamReader(process.getInputStream))
    val sb       = new StringBuilder
    val deadline = java.lang.System.currentTimeMillis() + timeoutMs
    while (java.lang.System.currentTimeMillis() < deadline) {
      if (reader.ready()) {
        val line = reader.readLine()
        if (line != null) {
          sb.append(line).append("\n")
          if (line.contains(marker)) return sb.toString()
        }
      } else {
        Thread.sleep(50)
      }
    }
    sb.toString()
  }

  /** Sends SIGINT (Unix) or a destroy signal (Windows) to a process. */
  private def sendInterrupt(process: java.lang.Process): Unit = {
    val pid = process.pid()
    if (isWindows) {
      val _ = process.toHandle.destroy()
    } else {
      val _ = java.lang.Runtime.getRuntime.exec(Array("kill", "-INT", pid.toString))
    }
  }

  // -- higher-level combinators -----------------------------------------------

  /** Runs a self-completing app and returns `(exitCode, stdout)`. */
  private def runApp(mainClass: String): ZIO[Any, Throwable, (Int, String)] =
    ZIO.attemptBlocking {
      val process = startProcess(mainClass)
      val exited  = process.waitFor(30, TimeUnit.SECONDS)
      val output  = readOutput(process)
      if (!exited) {
        process.destroyForcibly()
        throw new RuntimeException(s"$mainClass did not exit within 30 s")
      }
      (process.exitValue(), output)
    }

  /**
   * Runs a long-running app, waits for `READY`, sends SIGINT, then
   * collects the remaining output.  Returns `(preSignal, postSignal)`.
   */
  private def runAppWithSignal(mainClass: String): ZIO[Any, Throwable, (String, String)] =
    ZIO.attemptBlocking {
      val process = startProcess(mainClass)
      val pre     = waitForMarker(process, "READY", 30000)
      if (!pre.contains("READY")) {
        process.destroyForcibly()
        throw new RuntimeException(s"$mainClass never printed READY within 30 s")
      }
      sendInterrupt(process)
      val exited = process.waitFor(30, TimeUnit.SECONDS)
      val post   = readOutput(process)
      if (!exited) {
        process.destroyForcibly()
        throw new RuntimeException(s"$mainClass did not exit after signal within 30 s")
      }
      (pre, post)
    }

  // -- spec -------------------------------------------------------------------

  def spec = suite("ZIOAppIntegrationSpec")(
    // ---- 1. Normal completion -----------------------------------------------
    suite("app completion")(
      test("successful app exits with code 0") {
        runApp("zio.AppSuccess").map { case (code, out) =>
          assertTrue(code == 0, out.contains("SUCCESS"))
        }
      },
      test("failed app exits with code 1") {
        runApp("zio.AppFailure").map { case (code, _) =>
          assertTrue(code == 1)
        }
      },
      test("defect exits with code 1") {
        runApp("zio.AppDie").map { case (code, _) =>
          assertTrue(code == 1)
        }
      },
      test("app completes work before exiting") {
        runApp("zio.AppExitAfterWork").map { case (code, out) =>
          assertTrue(code == 0, out.contains("WORKING"), out.contains("DONE"))
        }
      }
    ),

    // ---- 2. Finalizers on normal completion ----------------------------------
    suite("finalizers on normal completion")(
      test("finalizers run on success") {
        runApp("zio.AppFinalizerOnSuccess").map { case (_, out) =>
          assertTrue(out.contains("ACQUIRED"), out.contains("FINALIZED"))
        }
      },
      test("finalizers run on failure") {
        runApp("zio.AppFinalizerOnFailure").map { case (code, out) =>
          assertTrue(code == 1, out.contains("ACQUIRED"), out.contains("FINALIZED"))
        }
      }
    ),

    // ---- 3. Signal-driven shutdown ------------------------------------------
    suite("external signal handling")(
      test("app shuts down and runs finalizers on SIGINT") {
        runAppWithSignal("zio.AppHangsUntilInterrupted").map { case (pre, post) =>
          assertTrue((pre + post).contains("FINALIZED"))
        }
      },
      test("slow finalizer completes when gracefulShutdownTimeout is Infinity (issue #9901)") {
        runAppWithSignal("zio.AppSlowFinalizer").map { case (pre, post) =>
          val all = pre + post
          assertTrue(all.contains("FINALIZING"), all.contains("FINALIZED"))
        }
      },
      test("multiple finalizers run in LIFO order") {
        runAppWithSignal("zio.AppMultipleFinalizers").map { case (pre, post) =>
          val all = pre + post
          assertTrue(all.contains("FINAL-1"), all.contains("FINAL-2"), all.contains("FINAL-3")) && {
            val i1 = all.indexOf("FINAL-1")
            val i2 = all.indexOf("FINAL-2")
            val i3 = all.indexOf("FINAL-3")
            assertTrue(i3 < i2, i2 < i1) // LIFO: 3 → 2 → 1
          }
        }
      },
      test("bootstrap layer finalizers run on SIGINT") {
        runAppWithSignal("zio.AppBootstrapFinalizer").map { case (pre, post) =>
          val all = pre + post
          assertTrue(all.contains("BOOTSTRAP-ACQUIRED"), all.contains("BOOTSTRAP-FINALIZED"))
        }
      }
    ) @@ unixOnly,

    // ---- 4. gracefulShutdownTimeout -----------------------------------------
    suite("gracefulShutdownTimeout")(
      test("shutdown respects timeout — slow finalizer is cut short") {
        ZIO.attemptBlocking {
          val process = startProcess("zio.AppShutdownTimeout")
          val pre     = waitForMarker(process, "READY", 30000)
          if (!pre.contains("READY")) {
            process.destroyForcibly()
            throw new RuntimeException("AppShutdownTimeout never printed READY")
          }
          val t0   = java.lang.System.currentTimeMillis()
          sendInterrupt(process)
          val done = process.waitFor(15, TimeUnit.SECONDS)
          val dt   = java.lang.System.currentTimeMillis() - t0
          val post = readOutput(process)
          if (!done) process.destroyForcibly()
          val all = pre + post
          // Timeout is 1 s, finalizer wants 10 s → process exits in well under 10 s
          assertTrue(done, dt < 8000L, all.contains("FINALIZING"), !all.contains("FINALIZED"))
        }
      },
      test("well-behaved app does not hang on shutdown") {
        ZIO.attemptBlocking {
          val process = startProcess("zio.AppHangsUntilInterrupted")
          val pre     = waitForMarker(process, "READY", 30000)
          if (!pre.contains("READY")) {
            process.destroyForcibly()
            throw new RuntimeException("Process never printed READY")
          }
          val t0   = java.lang.System.currentTimeMillis()
          sendInterrupt(process)
          val done = process.waitFor(15, TimeUnit.SECONDS)
          val dt   = java.lang.System.currentTimeMillis() - t0
          if (!done) process.destroyForcibly()
          assertTrue(done, dt < 10000L)
        }
      }
    ) @@ unixOnly,

    // ---- 5. Background fibers -----------------------------------------------
    suite("background fiber cleanup")(
      test("daemon fibers are interrupted on shutdown") {
        runAppWithSignal("zio.AppBackgroundFiber").map { case (pre, post) =>
          assertTrue((pre + post).contains("BG-FINALIZED"))
        }
      }
    ) @@ unixOnly

  ) @@ TestAspect.sequential @@ TestAspect.timeout(120.seconds) @@ TestAspect.jvmOnly
}
