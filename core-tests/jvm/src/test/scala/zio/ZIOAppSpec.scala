package zio

import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

import java.io.{BufferedReader, InputStreamReader}
import java.util.concurrent.TimeUnit
import scala.collection.mutable.ListBuffer

/**
 * Test suite that verifies the correct behaviour of ZIOApp.
 *
 * Tests cover:
 *  1. Correct exit code is emitted on success, failure, and defect
 *  2. Application finalizers are run on normal completion and SIGINT
 *  3. Shutdown sequence doesn't hang
 *  4. `gracefulShutdownTimeout` is respected
 *  5. Regression cases from historical issues:
 *     - #9901: ZIOApp hangs on SIGINT when using blocking operations
 *     - #9807: Finalizers not run on SIGINT
 *     - #9240: gracefulShutdownTimeout not respected
 */
object ZIOAppSpec extends ZIOSpecDefault {

  /** Runs a helper app in a subprocess and returns (exitCode, stdoutLines, stderrLines). */
  private def runApp(
    mainClass: String,
    args: List[String] = Nil,
    sendSigintAfterMs: Option[Long] = None,
    waitForOutputContaining: Option[String] = None,
    timeoutSeconds: Int = 30
  ): Task[(Int, List[String], List[String])] =
    ZIO.attemptBlockingIO {
      val cp        = System.getProperty("java.class.path")
      val javaHome  = System.getProperty("java.home")
      val javaExe   = s"$javaHome/bin/java"
      val cmdTokens = List(javaExe, "-cp", cp, mainClass) ++ args

      val pb      = new ProcessBuilder(cmdTokens: _*)
      pb.redirectErrorStream(false)
      val process = pb.start()

      val stdoutLines = ListBuffer.empty[String]
      val stderrLines = ListBuffer.empty[String]

      // Stream stdout in a background thread
      val stdoutThread = new Thread(() => {
        val reader = new BufferedReader(new InputStreamReader(process.getInputStream))
        var line   = reader.readLine()
        while (line != null) {
          stdoutLines.synchronized(stdoutLines += line)
          line = reader.readLine()
        }
      }, "stdout-reader")
      stdoutThread.setDaemon(true)
      stdoutThread.start()

      // Stream stderr in a background thread
      val stderrThread = new Thread(() => {
        val reader = new BufferedReader(new InputStreamReader(process.getErrorStream))
        var line   = reader.readLine()
        while (line != null) {
          stderrLines.synchronized(stderrLines += line)
          line = reader.readLine()
        }
      }, "stderr-reader")
      stderrThread.setDaemon(true)
      stderrThread.start()

      // If we need to wait for specific output before sending signal
      waitForOutputContaining.foreach { marker =>
        val deadline = System.currentTimeMillis() + timeoutSeconds * 1000L
        var found    = false
        while (!found && System.currentTimeMillis() < deadline) {
          Thread.sleep(50)
          found = stdoutLines.synchronized(stdoutLines.exists(_.contains(marker))) ||
            stderrLines.synchronized(stderrLines.exists(_.contains(marker)))
        }
      }

      // Send SIGINT if requested
      sendSigintAfterMs.foreach { delayMs =>
        Thread.sleep(delayMs)
        // On JVM we can send SIGINT to the process
        process.toHandle.descendants().forEach(_.destroy())
        process.toHandle.destroy() // SIGTERM on Unix maps to graceful shutdown; use destroyForcibly for SIGKILL
        // Actually send SIGINT via Runtime.exec on Unix
        try {
          val pid       = process.pid()
          val sigintCmd = Array("kill", "-INT", pid.toString)
          Runtime.getRuntime.exec(sigintCmd).waitFor()
        } catch {
          case _: Exception => process.destroy()
        }
      }

      val finished = process.waitFor(timeoutSeconds.toLong, TimeUnit.SECONDS)
      if (!finished) {
        process.destroyForcibly()
        stdoutThread.interrupt()
        stderrThread.interrupt()
      }

      stdoutThread.join(2000)
      stderrThread.join(2000)

      val exitCode = if (finished) process.exitValue() else -1
      (exitCode, stdoutLines.synchronized(stdoutLines.toList), stderrLines.synchronized(stderrLines.toList))
    }

  // ---------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------

  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ZIOAppSpec")(
      suite("exit codes")(
        test("exits with code 0 on successful completion") {
          for {
            (exitCode, _, _) <- runApp("zio.ZIOAppSpecHelpers$SuccessApp")
          } yield assert(exitCode)(equalTo(0))
        },
        test("exits with code 1 on ZIO failure") {
          for {
            (exitCode, _, _) <- runApp("zio.ZIOAppSpecHelpers$FailureApp")
          } yield assert(exitCode)(equalTo(1))
        },
        test("exits with code 1 on defect / unexpected exception") {
          for {
            (exitCode, _, _) <- runApp("zio.ZIOAppSpecHelpers$DefectApp")
          } yield assert(exitCode)(equalTo(1))
        }
      ),
      suite("finalizers on normal completion")(
        test("finalizers run when app succeeds") {
          for {
            (exitCode, stdout, _) <- runApp("zio.ZIOAppSpecHelpers$FinalizerOnSuccessApp")
          } yield assert(exitCode)(equalTo(0)) &&
            assert(stdout)(contains("finalizer-ran"))
        },
        test("finalizers run when app fails") {
          for {
            (exitCode, stdout, _) <- runApp("zio.ZIOAppSpecHelpers$FinalizerOnFailureApp")
          } yield assert(exitCode)(equalTo(1)) &&
            assert(stdout)(contains("finalizer-ran"))
        },
        test("finalizers run when app throws a defect") {
          for {
            (exitCode, stdout, _) <- runApp("zio.ZIOAppSpecHelpers$FinalizerOnDefectApp")
          } yield assert(exitCode)(equalTo(1)) &&
            assert(stdout)(contains("finalizer-ran"))
        }
      ),
      suite("finalizers on external signal (SIGINT)")(
        // Regression: #9807 – finalizers not called on SIGINT
        test("finalizers run when SIGINT is received (#9807)") {
          for {
            (exitCode, stdout, _) <- runApp(
                                       "zio.ZIOAppSpecHelpers$LongRunningApp",
                                       sendSigintAfterMs = Some(500),
                                       waitForOutputContaining = Some("app-started")
                                     )
          } yield assert(stdout)(contains("finalizer-ran")) &&
            // Interrupted by signal so exit code should be non-zero (130 on most Unix)
            assert(exitCode)(not(equalTo(0)))
        },
        test("nested scoped resources are released on SIGINT") {
          for {
            (_, stdout, _) <- runApp(
                                "zio.ZIOAppSpecHelpers$ScopedResourceApp",
                                sendSigintAfterMs = Some(500),
                                waitForOutputContaining = Some("resource-acquired")
                              )
          } yield assert(stdout)(contains("resource-released"))
        }
      ),
      suite("shutdown does not hang")(
        // Regression: #9901 – ZIOApp hangs on SIGINT with blocking ops
        test("app terminates within timeout when SIGINT is sent during blocking operation (#9901)") {
          for {
            start            <- Clock.currentTime(TimeUnit.MILLISECONDS)
            (exitCode, _, _) <- runApp(
                                  "zio.ZIOAppSpecHelpers$BlockingOpApp",
                                  sendSigintAfterMs = Some(300),
                                  waitForOutputContaining = Some("blocking-started"),
                                  timeoutSeconds = 15
                                )
            end              <- Clock.currentTime(TimeUnit.MILLISECONDS)
            elapsed           = end - start
          } yield assert(elapsed)(isLessThan(14000L)) &&
            assert(exitCode)(not(equalTo(-1))) // -1 means we had to kill it
        },
        test("app does not hang when it completes normally") {
          for {
            start            <- Clock.currentTime(TimeUnit.MILLISECONDS)
            (exitCode, _, _) <- runApp("zio.ZIOAppSpecHelpers$QuickApp", timeoutSeconds = 10)
            end              <- Clock.currentTime(TimeUnit.MILLISECONDS)
            elapsed           = end - start
          } yield assert(elapsed)(isLessThan(9000L)) &&
            assert(exitCode)(equalTo(0))
        }
      ),
      suite("gracefulShutdownTimeout")(
        // Regression: #9240 – gracefulShutdownTimeout not respected
        test("gracefulShutdownTimeout is respected when finalizer takes too long (#9240)") {
          for {
            start            <- Clock.currentTime(TimeUnit.MILLISECONDS)
            (exitCode, stdout, _) <- runApp(
                                       "zio.ZIOAppSpecHelpers$SlowFinalizerApp",
                                       sendSigintAfterMs = Some(300),
                                       waitForOutputContaining = Some("app-started"),
                                       timeoutSeconds = 15
                                     )
            end              <- Clock.currentTime(TimeUnit.MILLISECONDS)
            elapsed           = end - start
          } yield
            // The slow finalizer sleeps 60s but gracefulShutdownTimeout is 2s,
            // so total wall-clock should be well under 10s
            assert(elapsed)(isLessThan(10000L)) &&
              assert(stdout)(contains("slow-finalizer-started"))
        },
        test("gracefulShutdownTimeout allows fast finalizers to complete") {
          for {
            (_, stdout, _) <- runApp(
                                "zio.ZIOAppSpecHelpers$FastFinalizerWithTimeoutApp",
                                sendSigintAfterMs = Some(300),
                                waitForOutputContaining = Some("app-started")
                              )
          } yield assert(stdout)(contains("fast-finalizer-ran"))
        }
      ),
      suite("custom exit codes")(
        test("app can exit with a custom exit code via ZIOAppDefault.exitCode") {
          for {
            (exitCode, _, _) <- runApp("zio.ZIOAppSpecHelpers$CustomExitCodeApp")
          } yield assert(exitCode)(equalTo(42))
        }
      ),
      suite("ZIOApp composition")(
        test("composed apps both run their finalizers") {
          for {
            (exitCode, stdout, _) <- runApp("zio.ZIOAppSpecHelpers$ComposedAppsApp")
          } yield assert(exitCode)(equalTo(0)) &&
            assert(stdout)(contains("app1-finalizer-ran")) &&
            assert(stdout)(contains("app2-finalizer-ran"))
        }
      )
    ) @@ TestAspect.timeout(Duration.fromSeconds(120)) @@ TestAspect.sequential
}
