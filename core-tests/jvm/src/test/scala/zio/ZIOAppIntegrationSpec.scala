/*
 * Copyright 2021-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
 * and the [[ZIOApp.gracefulShutdownTimeout]] mechanism.
 *
 * == How it works ==
 *
 *  1. Each test launches a fixture object from [[ZIOAppTestApps]] via
 *     [[ProcessBuilder]], reusing the current test classpath.
 *  2. For ''normal completion'' tests the parent simply waits for the child
 *     to exit and asserts on its exit code / stdout markers.
 *  3. For ''signal'' tests the parent waits for the `READY` marker, sends
 *     `kill -INT` or `kill -TERM` (Unix), and then asserts on finalizer
 *     markers and timing.
 *
 * == Platform notes ==
 *
 * Signal-based suites are gated behind [[unixOnly]] because
 * [[Process#destroy]] on Windows terminates the process without invoking
 * JVM shutdown hooks.  CI runs on Linux, so all suites are exercised there.
 *
 * == Coverage mapping ==
 *
 * | Requirement (issue #9909)                        | Suite / test                                         |
 * |--------------------------------------------------|------------------------------------------------------|
 * | Correct exit code on success / failure / defect  | ''app completion''                                   |
 * | Finalizers run on normal completion              | ''finalizers on normal completion''                  |
 * | Finalizers run on SIGINT                         | ''external signal handling''                         |
 * | Finalizers run on SIGTERM                        | ''external signal handling — SIGTERM''               |
 * | ZLayer resources released on shutdown            | ''ZLayer resource management''                       |
 * | gracefulShutdownTimeout = Infinity respected     | ''gracefulShutdownTimeout''                          |
 * | gracefulShutdownTimeout = 1s cuts slow finalizer | ''gracefulShutdownTimeout''                          |
 * | gracefulShutdownTimeout = 0 exits fast           | ''zero gracefulShutdownTimeout''                     |
 * | Shutdown sequence does not hang                  | ''gracefulShutdownTimeout / no-hang''                |
 * | #9901 – finalizer waited on with Infinity        | ''slow finalizer completes …''                       |
 * | #9807 – shutdown-hook race                       | all signal tests (atomic `shuttingDown` flag path)   |
 * | #9240 – sun.misc.SignalHandler CNFE              | every test app starts without `NoClassDefFoundError` |
 */
object ZIOAppIntegrationSpec extends ZIOBaseSpec {

  // -- helpers ----------------------------------------------------------------

  private val javaHome  = java.lang.System.getProperty("java.home")
  private val javaBin   = s"$javaHome/bin/java"
  private val classpath = java.lang.System.getProperty("java.class.path")
  private val isWindows = java.lang.System.getProperty("os.name").toLowerCase.contains("win")

  /** Skips the annotated suite on Windows (no JVM shutdown hooks via SIGINT). */
  private val unixOnly: TestAspectPoly =
    if (isWindows) TestAspect.ignore else TestAspect.identity

  /** Starts a child JVM running `mainClass` with the current classpath. */
  private def startProcess(mainClass: String): java.lang.Process = {
    val builder = new ProcessBuilder(javaBin, "-cp", classpath, mainClass)
    builder.redirectErrorStream(true)
    builder.start()
  }

  /** Drains the remaining stdout of a finished process into a [[String]]. */
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
   * Reads stdout line-by-line until `marker` appears or `timeoutMs` elapses.
   * Returns all output read so far (including the marker line).
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

  /** Sends SIGINT (Unix) or [[Process#destroy]] (Windows) to a process. */
  private def sendInterrupt(process: java.lang.Process): Unit =
    sendSignal(process, "INT")

  /** Sends SIGTERM (Unix) or [[Process#destroy]] (Windows) to a process. */
  private def sendTerm(process: java.lang.Process): Unit =
    sendSignal(process, "TERM")

  private def sendSignal(process: java.lang.Process, signal: String): Unit = {
    val pid = process.pid()
    if (isWindows) {
      val _ = process.toHandle.destroy()
    } else {
      val _ = java.lang.Runtime.getRuntime.exec(Array("kill", s"-$signal", pid.toString))
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
        throw new RuntimeException(s"$mainClass did not exit within 30 s.\nOutput: $output")
      }
      (process.exitValue(), output)
    }

  /**
   * Runs a long-running app, waits for `READY`, sends the specified signal,
   * then collects remaining output.  Returns `(preSignal, postSignal)`.
   */
  private def runAppWithSignal(
    mainClass: String,
    signal: java.lang.Process => Unit = sendInterrupt
  ): ZIO[Any, Throwable, (String, String)] =
    ZIO.attemptBlocking {
      val process = startProcess(mainClass)
      val pre     = waitForMarker(process, "READY", 30000)
      if (!pre.contains("READY")) {
        process.destroyForcibly()
        throw new RuntimeException(s"$mainClass never printed READY within 30 s.\nOutput so far: $pre")
      }
      signal(process)
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

    // ---- 1. Normal completion ------------------------------------------------
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

    // ---- 3. SIGINT-driven shutdown -------------------------------------------
    suite("external signal handling — SIGINT")(
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

    // ---- 4. SIGTERM-driven shutdown ------------------------------------------
    // On the JVM, SIGTERM triggers shutdown hooks just like SIGINT.
    suite("external signal handling — SIGTERM")(
      test("app shuts down and runs finalizers on SIGTERM") {
        runAppWithSignal("zio.AppHangsUntilInterrupted", sendTerm).map { case (pre, post) =>
          assertTrue((pre + post).contains("FINALIZED"))
        }
      },
      test("slow finalizer completes on SIGTERM when gracefulShutdownTimeout is Infinity") {
        runAppWithSignal("zio.AppSlowFinalizer", sendTerm).map { case (pre, post) =>
          val all = pre + post
          assertTrue(all.contains("FINALIZING"), all.contains("FINALIZED"))
        }
      }
    ) @@ unixOnly,

    // ---- 5. ZLayer resource management --------------------------------------
    // Verifies that resources acquired via ZLayer are properly released on
    // both normal completion and signal-driven shutdown.
    suite("ZLayer resource management")(
      test("ZLayer resources released on normal app exit") {
        runApp("zio.AppZLayerNormalExit").map { case (code, out) =>
          assertTrue(code == 0, out.contains("LAYER-ACQUIRED"), out.contains("LAYER-RELEASED"))
        }
      },
      test("ZLayer resources released on SIGINT") {
        runAppWithSignal("zio.AppZLayerSIGINT").map { case (pre, post) =>
          val all = pre + post
          assertTrue(all.contains("LAYER-ACQUIRED"), all.contains("LAYER-RELEASED"))
        }
      }
    ) @@ unixOnly,

    // ---- 6. gracefulShutdownTimeout -----------------------------------------
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
          // Timeout is 1 s; finalizer wants 10 s → process exits in well under 10 s
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

    // ---- 7. Zero gracefulShutdownTimeout ------------------------------------
    // With Duration.Zero the shutdown hook does NOT wait for finalizers.
    // The process should exit quickly (under 3 s).
    suite("zero gracefulShutdownTimeout")(
      test("process exits quickly when gracefulShutdownTimeout is Duration.Zero") {
        ZIO.attemptBlocking {
          val process = startProcess("zio.AppZeroTimeout")
          val pre     = waitForMarker(process, "READY", 30000)
          if (!pre.contains("READY")) {
            process.destroyForcibly()
            throw new RuntimeException("AppZeroTimeout never printed READY")
          }
          val t0   = java.lang.System.currentTimeMillis()
          sendInterrupt(process)
          val done = process.waitFor(10, TimeUnit.SECONDS)
          val dt   = java.lang.System.currentTimeMillis() - t0
          if (!done) process.destroyForcibly()
          // With Duration.Zero the hook returns immediately; process exits fast
          assertTrue(done, dt < 3000L)
        }
      }
    ) @@ unixOnly,

    // ---- 8. Background fiber cleanup ----------------------------------------
    suite("background fiber cleanup")(
      test("daemon fibers are interrupted on shutdown") {
        runAppWithSignal("zio.AppBackgroundFiber").map { case (pre, post) =>
          assertTrue((pre + post).contains("BG-FINALIZED"))
        }
      }
    ) @@ unixOnly

  ) @@ TestAspect.sequential @@ TestAspect.timeout(120.seconds) @@ TestAspect.jvmOnly
}
