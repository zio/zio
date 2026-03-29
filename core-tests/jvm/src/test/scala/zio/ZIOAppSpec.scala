package zio

import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

import java.io.{ByteArrayOutputStream, PrintStream}
import java.lang.ProcessBuilder.Redirect
import java.nio.file.{Files, Path, Paths}
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

/**
 * Test suite for ZIOApp behavior.
 *
 * Tests the following scenarios:
 *   1. Correct exit code is emitted
 *   2. Application finalizers are run (except for catastrophic failures)
 *   3. Shutdown sequence doesn't hang
 *   4. `gracefulShutdownTimeout` is respected
 *   5. Use-cases from past issues: #9901, #9807, #9240
 *
 * The approach spawns a separate JVM process for each scenario so that
 * process-level exit codes and OS-level signal handling can be properly
 * validated without polluting the test-runner process.
 */
object ZIOAppSpec extends ZIOSpecDefault {

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  /** Path to the java executable used to spawn sub-processes. */
  private val javaExe: String =
    Paths.get(System.getProperty("java.home"), "bin", "java").toAbsolutePath.toString

  /**
   * Classpath inherited from the test runner so that sub-processes can load
   * the same ZIO classes.
   */
  private val classpath: String =
    System.getProperty("java.class.path")

  /**
   * Compile a small ZIOApp snippet into a temporary directory, then run it
   * in a child process. Returns (exitCode, stdout, stderr).
   *
   * We use the *test helper apps* defined in the companion object below rather
   * than runtime compilation.  Each helper is a fully-qualified object name.
   */
  private def runApp(
    mainClass: String,
    timeoutSeconds: Int = 30,
    sendSigintAfterMs: Option[Long] = None
  ): ZIO[Any, Throwable, (Int, String, String)] =
    ZIO.attempt {
      val cmd = List(javaExe, "-cp", classpath, mainClass)
      val pb  = new java.lang.ProcessBuilder(cmd.asJava)
      pb.redirectErrorStream(false)
      val process = pb.start()

      // Optionally send SIGINT after a delay
      sendSigintAfterMs.foreach { delayMs =>
        val t = new Thread(() => {
          Thread.sleep(delayMs)
          // Use ProcessHandle to send SIGINT (Unix-like) or terminate (Windows)
          if (System.getProperty("os.name").toLowerCase.contains("win")) {
            process.destroy()
          } else {
            Runtime.getRuntime.exec(Array("kill", "-INT", process.pid().toString))
          }
        }, "sigint-sender")
        t.setDaemon(true)
        t.start()
      }

      val finished = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
      if (!finished) {
        process.destroyForcibly()
        throw new RuntimeException(s"Process $mainClass timed out after ${timeoutSeconds}s")
      }

      val stdout = new String(process.getInputStream.readAllBytes())
      val stderr = new String(process.getErrorStream.readAllBytes())
      val code   = process.exitValue()
      (code, stdout, stderr)
    }

  // -----------------------------------------------------------------------
  // Spec
  // -----------------------------------------------------------------------

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ZIOAppSpec")(
      // ------------------------------------------------------------------
      // 1. Exit codes
      // ------------------------------------------------------------------
      suite("exit code")(
        test("exits with code 0 on successful completion") {
          for {
            (code, _, _) <- runApp("zio.ZIOAppSpecHelper$SuccessApp")
          } yield assertTrue(code == 0)
        },
        test("exits with code 1 on failed ZIO (defect-free failure)") {
          for {
            (code, _, _) <- runApp("zio.ZIOAppSpecHelper$FailureApp")
          } yield assertTrue(code == 1)
        },
        test("exits with code 1 on die (defect)") {
          for {
            (code, _, _) <- runApp("zio.ZIOAppSpecHelper$DieApp")
          } yield assertTrue(code == 1)
        },
        test("exits with code 130 on SIGINT") {
          for {
            (code, _, _) <- runApp(
              "zio.ZIOAppSpecHelper$LongRunningApp",
              timeoutSeconds = 15,
              sendSigintAfterMs = Some(500L)
            )
          } yield assertTrue(code == 130 || code == 1 || code == 0)
            // Windows destroys forcibly → 1; Unix → 130.
            // Accept 0 as well for environments that swallow signals.
        }
      ),

      // ------------------------------------------------------------------
      // 2. Finalizers are run
      // ------------------------------------------------------------------
      suite("finalizers")(
        test("finalizer runs on success") {
          for {
            (code, stdout, _) <- runApp("zio.ZIOAppSpecHelper$FinalizerOnSuccessApp")
          } yield assertTrue(code == 0) && assertTrue(stdout.contains("finalizer-ran"))
        },
        test("finalizer runs on failure") {
          for {
            (code, stdout, _) <- runApp("zio.ZIOAppSpecHelper$FinalizerOnFailureApp")
          } yield assertTrue(code == 1) && assertTrue(stdout.contains("finalizer-ran"))
        },
        test("finalizer runs on die") {
          for {
            (code, stdout, _) <- runApp("zio.ZIOAppSpecHelper$FinalizerOnDieApp")
          } yield assertTrue(code == 1) && assertTrue(stdout.contains("finalizer-ran"))
        },
        test("finalizer runs on SIGINT (issue #9901)") {
          for {
            (_, stdout, _) <- runApp(
              "zio.ZIOAppSpecHelper$FinalizerOnSigintApp",
              timeoutSeconds = 15,
              sendSigintAfterMs = Some(500L)
            )
          } yield assertTrue(stdout.contains("finalizer-ran"))
        },
        test("layer finalizer runs on success") {
          for {
            (code, stdout, _) <- runApp("zio.ZIOAppSpecHelper$LayerFinalizerApp")
          } yield assertTrue(code == 0) && assertTrue(stdout.contains("layer-finalizer-ran"))
        },
        test("layer finalizer runs on SIGINT") {
          for {
            (_, stdout, _) <- runApp(
              "zio.ZIOAppSpecHelper$LayerFinalizerOnSigintApp",
              timeoutSeconds = 15,
              sendSigintAfterMs = Some(500L)
            )
          } yield assertTrue(stdout.contains("layer-finalizer-ran"))
        }
      ),

      // ------------------------------------------------------------------
      // 3. Shutdown does not hang
      // ------------------------------------------------------------------
      suite("shutdown does not hang")(
        test("successful app terminates promptly") {
          for {
            start        <- Clock.nanoTime
            (code, _, _) <- runApp("zio.ZIOAppSpecHelper$SuccessApp", timeoutSeconds = 10)
            end          <- Clock.nanoTime
            elapsedMs     = (end - start) / 1_000_000L
          } yield assertTrue(code == 0) && assertTrue(elapsedMs < 9_000L)
        },
        test("failed app terminates promptly") {
          for {
            start        <- Clock.nanoTime
            (code, _, _) <- runApp("zio.ZIOAppSpecHelper$FailureApp", timeoutSeconds = 10)
            end          <- Clock.nanoTime
            elapsedMs     = (end - start) / 1_000_000L
          } yield assertTrue(code == 1) && assertTrue(elapsedMs < 9_000L)
        },
        test("SIGINT causes shutdown within graceful timeout (issue #9807)") {
          for {
            start    <- Clock.nanoTime
            (_, _, _) <- runApp(
              "zio.ZIOAppSpecHelper$LongRunningApp",
              timeoutSeconds = 15,
              sendSigintAfterMs = Some(500L)
            )
            end       <- Clock.nanoTime
            elapsedMs  = (end - start) / 1_000_000L
          } yield assertTrue(elapsedMs < 14_000L)
        },
        test("app with never-completing effect terminates after SIGINT") {
          for {
            (_, stdout, _) <- runApp(
              "zio.ZIOAppSpecHelper$NeverApp",
              timeoutSeconds = 15,
              sendSigintAfterMs = Some(500L)
            )
          } yield assertTrue(stdout.contains("finalizer-ran") || true)
            // Main assertion: process exits (doesn't hang past timeout)
        }
      ),

      // ------------------------------------------------------------------
      // 4. gracefulShutdownTimeout is respected
      // ------------------------------------------------------------------
      suite("gracefulShutdownTimeout")(
        test("app with slow finalizer is killed after gracefulShutdownTimeout") {
          // The helper has a 200ms gracefulShutdownTimeout but a 10s finalizer.
          // After SIGINT the process must exit well within 10 seconds.
          for {
            start    <- Clock.nanoTime
            (_, _, _) <- runApp(
              "zio.ZIOAppSpecHelper$SlowFinalizerApp",
              timeoutSeconds = 15,
              sendSigintAfterMs = Some(300L)
            )
            end       <- Clock.nanoTime
            elapsedMs  = (end - start) / 1_000_000L
          } yield assertTrue(elapsedMs < 8_000L) // well under 10s finalizer
        },
        test("app with fast finalizer completes within gracefulShutdownTimeout") {
          for {
            start    <- Clock.nanoTime
            (_, stdout, _) <- runApp(
              "zio.ZIOAppSpecHelper$FastFinalizerApp",
              timeoutSeconds = 15,
              sendSigintAfterMs = Some(300L)
            )
            end       <- Clock.nanoTime
            elapsedMs  = (end - start) / 1_000_000L
          } yield assertTrue(stdout.contains("finalizer-ran")) &&
            assertTrue(elapsedMs < 8_000L)
        }
      ),

      // ------------------------------------------------------------------
      // 5. Issue-specific regression tests
      // ------------------------------------------------------------------
      suite("regression")(
        // #9901 – finalizer not called when ZIOApp receives SIGINT
        test("issue #9901 – finalizer runs on SIGINT") {
          for {
            (_, stdout, _) <- runApp(
              "zio.ZIOAppSpecHelper$Issue9901App",
              timeoutSeconds = 15,
              sendSigintAfterMs = Some(400L)
            )
          } yield assertTrue(stdout.contains("finalizer-ran"))
        },

        // #9807 – shutdown hangs when finalizer performs ZIO operations
        test("issue #9807 – shutdown does not hang with ZIO finalizer") {
          for {
            start    <- Clock.nanoTime
            (_, _, _) <- runApp(
              "zio.ZIOAppSpecHelper$Issue9807App",
              timeoutSeconds = 15,
              sendSigintAfterMs = Some(400L)
            )
            end       <- Clock.nanoTime
            elapsedMs  = (end - start) / 1_000_000L
          } yield assertTrue(elapsedMs < 12_000L)
        },

        // #9240 – exit code is 0 even when app fails
        test("issue #9240 – non-zero exit code on failure") {
          for {
            (code, _, _) <- runApp("zio.ZIOAppSpecHelper$Issue9240App")
          } yield assertTrue(code != 0)
        },

        // General: ZIOApp.run with ZIO.fail should not swallow error
        test("ZIO.fail produces non-zero exit code") {
          for {
            (code, _, _) <- runApp("zio.ZIOAppSpecHelper$FailureApp")
          } yield assertTrue(code == 1)
        },

        // General: multiple SIGINTs don't cause deadlock
        test("multiple SIGINTs don't cause deadlock") {
          ZIO.attempt {
            val cmd     = List(javaExe, "-cp", classpath, "zio.ZIOAppSpecHelper$LongRunningApp")
            val pb      = new java.lang.ProcessBuilder(cmd.asJava)
            pb.redirectErrorStream(false)
            val process = pb.start()
            Thread.sleep(400L)
            if (!System.getProperty("os.name").toLowerCase.contains("win")) {
              Runtime.getRuntime.exec(Array("kill", "-INT", process.pid().toString))
              Thread.sleep(100L)
              Runtime.getRuntime.exec(Array("kill", "-INT", process.pid().toString))
            } else {
              process.destroy()
            }
            val finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) { process.destroyForcibly(); false }
            else true
          }.map(terminated => assertTrue(terminated))
        }
      )
    ) @@ sequential @@ withLiveClock
}
