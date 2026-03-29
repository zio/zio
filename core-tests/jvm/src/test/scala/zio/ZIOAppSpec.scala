package zio

import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

import java.lang.ProcessBuilder.Redirect
import scala.jdk.CollectionConverters._

/**
 * Integration-level test suite for [[ZIOApp]] / [[ZIOAppDefault]].
 *
 * Each test launches a small helper app (defined in [[ZIOAppSpecHelper]]) in a
 * separate JVM process, then inspects the exit code and/or stdout.  This lets
 * us validate process-level behavior (exit codes, OS signal handling) without
 * polluting the test-runner process.
 *
 * Tested scenarios
 * ================
 *  1. Correct exit code is emitted (0 on success, 1 on failure/die, 130 on SIGINT)
 *  2. Application finalizers are run (success, failure, die, SIGINT)
 *  3. Shutdown sequence doesn't hang
 *  4. `gracefulShutdownTimeout` is respected
 *  5. Regression tests for past issues:
 *       - #9901 finalizer not called on SIGINT
 *       - #9807 shutdown hangs when finalizer contains ZIO effects
 *       - #9240 exit code 0 even on failure
 */
object ZIOAppSpec extends ZIOSpecDefault {

  // -----------------------------------------------------------------------
  // Process-running infrastructure
  // -----------------------------------------------------------------------

  private val javaExe: String =
    java.nio.file.Paths
      .get(System.getProperty("java.home"), "bin", "java")
      .toAbsolutePath
      .toString

  private val classpath: String =
    System.getProperty("java.class.path")

  private val isWindows: Boolean =
    System.getProperty("os.name", "").toLowerCase.contains("win")

  /**
   * Run a helper app in a child process.
   *
   * @param mainClass          Fully-qualified Scala object name (module class).
   * @param timeoutSeconds     Hard wall-clock timeout; process is killed if exceeded.
   * @param sendSigintAfterMs  If set, SIGINT (Unix) / TerminateProcess (Windows)
   *                           is sent to the child after the given delay.
   * @return                   (exitCode, stdout, stderr)
   */
  private def runApp(
    mainClass: String,
    timeoutSeconds: Int = 20,
    sendSigintAfterMs: Option[Long] = None
  ): ZIO[Any, Throwable, (Int, String, String)] =
    ZIO.scoped {
      for {
        process <- ZIO.acquireRelease(
                     ZIO.attempt {
                       val pb = new java.lang.ProcessBuilder(
                         (List(javaExe, "-cp", classpath, mainClass) ++ Nil).asJava
                       )
                       pb.redirectErrorStream(false)
                       pb.start()
                     }
                   )(p => ZIO.succeed(if (p.isAlive) p.destroyForcibly() else p))

        // Optionally schedule a SIGINT / destroy after a delay.
        _ <- sendSigintAfterMs match {
               case None => ZIO.unit
               case Some(delayMs) =>
                 (ZIO.sleep(Duration.fromMillis(delayMs)) *> ZIO.attempt {
                   if (isWindows) {
                     process.destroy()
                   } else {
                     new java.lang.ProcessBuilder(
                       "kill",
                       "-INT",
                       process.pid().toString
                     ).start()
                   }
                 }.orDie).forkDaemon
             }

        // Wait for the process to finish (or time out).
        finished <- ZIO.attempt(
                      process.waitFor(timeoutSeconds.toLong, java.util.concurrent.TimeUnit.SECONDS)
                    )

        _ <- ZIO.when(!finished)(
               ZIO.attempt(process.destroyForcibly()) *>
                 ZIO.fail(
                   new RuntimeException(
                     s"Process $mainClass timed out after ${timeoutSeconds}s"
                   )
                 )
             )

        stdout <- ZIO.attempt(new String(process.getInputStream.readAllBytes()))
        stderr <- ZIO.attempt(new String(process.getErrorStream.readAllBytes()))
        code   <- ZIO.attempt(process.exitValue())
      } yield (code, stdout, stderr)
    }

  // -----------------------------------------------------------------------
  // Convenience
  // -----------------------------------------------------------------------

  /** Helper name → fully-qualified class name for the module class. */
  private def helperClass(name: String): String =
    s"zio.ZIOAppSpecHelper$$$name"

  // -----------------------------------------------------------------------
  // Spec
  // -----------------------------------------------------------------------

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ZIOAppSpec")(

      // ====================================================================
      // 1. Exit codes
      // ====================================================================
      suite("exit codes")(
        test("exits 0 on successful completion") {
          runApp(helperClass("SuccessApp")).map { case (code, _, _) =>
            assertTrue(code == 0)
          }
        },
        test("exits 1 on ZIO failure") {
          runApp(helperClass("FailureApp")).map { case (code, _, _) =>
            assertTrue(code == 1)
          }
        },
        test("exits 1 on defect (ZIO.die)") {
          runApp(helperClass("DieApp")).map { case (code, _, _) =>
            assertTrue(code == 1)
          }
        },
        test("exits non-zero on SIGINT") {
          runApp(
            helperClass("LongRunningApp"),
            timeoutSeconds = 15,
            sendSigintAfterMs = Some(400L)
          ).map { case (code, _, _) =>
            // Unix: 130 (128+2). Windows destroy: 1. Some JVMs: 143 (128+15).
            assertTrue(code != 0)
          }
        }
      ),

      // ====================================================================
      // 2. Finalizers are run
      // ====================================================================
      suite("finalizers")(
        test("finalizer runs on success") {
          runApp(helperClass("FinalizerOnSuccessApp")).map { case (code, out, _) =>
            assertTrue(code == 0) && assertTrue(out.contains("finalizer-ran"))
          }
        },
        test("finalizer runs on failure") {
          runApp(helperClass("FinalizerOnFailureApp")).map { case (code, out, _) =>
            assertTrue(code == 1) && assertTrue(out.contains("finalizer-ran"))
          }
        },
        test("finalizer runs on die") {
          runApp(helperClass("FinalizerOnDieApp")).map { case (code, out, _) =>
            assertTrue(code == 1) && assertTrue(out.contains("finalizer-ran"))
          }
        },
        test("finalizer runs on SIGINT") {
          runApp(
            helperClass("FinalizerOnSigintApp"),
            timeoutSeconds = 15,
            sendSigintAfterMs = Some(400L)
          ).map { case (_, out, _) =>
            assertTrue(out.contains("finalizer-ran"))
          }
        },
        test("layer finalizer runs on success") {
          runApp(helperClass("LayerFinalizerApp")).map { case (code, out, _) =>
            assertTrue(code == 0) && assertTrue(out.contains("layer-finalizer-ran"))
          }
        },
        test("layer finalizer runs on SIGINT") {
          runApp(
            helperClass("LayerFinalizerOnSigintApp"),
            timeoutSeconds = 15,
            sendSigintAfterMs = Some(400L)
          ).map { case (_, out, _) =>
            assertTrue(out.contains("layer-finalizer-ran"))
          }
        }
      ),

      // ====================================================================
      // 3. Shutdown does not hang
      // ====================================================================
      suite("shutdown does not hang")(
        test("successful app terminates promptly") {
          for {
            t0           <- zio.Clock.nanoTime
            (code, _, _) <- runApp(helperClass("SuccessApp"), timeoutSeconds = 10)
            t1           <- zio.Clock.nanoTime
            ms            = (t1 - t0) / 1_000_000L
          } yield assertTrue(code == 0) && assertTrue(ms < 9_000L)
        },
        test("failed app terminates promptly") {
          for {
            t0           <- zio.Clock.nanoTime
            (code, _, _) <- runApp(helperClass("FailureApp"), timeoutSeconds = 10)
            t1           <- zio.Clock.nanoTime
            ms            = (t1 - t0) / 1_000_000L
          } yield assertTrue(code == 1) && assertTrue(ms < 9_000L)
        },
        test("SIGINT triggers shutdown without hanging (issue #9807)") {
          for {
            t0    <- zio.Clock.nanoTime
            _     <- runApp(
                       helperClass("LongRunningApp"),
                       timeoutSeconds = 15,
                       sendSigintAfterMs = Some(400L)
                     )
            t1    <- zio.Clock.nanoTime
            ms     = (t1 - t0) / 1_000_000L
          } yield assertTrue(ms < 14_000L)
        },
        test("ZIO.never app terminates after SIGINT without hanging") {
          for {
            t0 <- zio.Clock.nanoTime
            _  <- runApp(
                    helperClass("NeverApp"),
                    timeoutSeconds = 15,
                    sendSigintAfterMs = Some(400L)
                  )
            t1 <- zio.Clock.nanoTime
            ms  = (t1 - t0) / 1_000_000L
          } yield assertTrue(ms < 14_000L)
        }
      ),

      // ====================================================================
      // 4. gracefulShutdownTimeout is respected
      // ====================================================================
      suite("gracefulShutdownTimeout")(
        test("process exits before slow finalizer completes") {
          // SlowFinalizerApp installs a 2-second daemon guard thread and has a
          // 10-second finalizer.  The process should exit in ~2s, not 10s.
          for {
            t0    <- zio.Clock.nanoTime
            _     <- runApp(
                       helperClass("SlowFinalizerApp"),
                       timeoutSeconds = 15,
                       sendSigintAfterMs = Some(400L)
                     )
            t1    <- zio.Clock.nanoTime
            ms     = (t1 - t0) / 1_000_000L
          } yield assertTrue(ms < 7_000L)
        },
        test("fast finalizer completes within gracefulShutdownTimeout") {
          for {
            (_, out, _) <- runApp(
                             helperClass("FastFinalizerApp"),
                             timeoutSeconds = 15,
                             sendSigintAfterMs = Some(400L)
                           )
          } yield assertTrue(out.contains("finalizer-ran"))
        }
      ),

      // ====================================================================
      // 5. Issue-specific regression tests
      // ====================================================================
      suite("regression")(
        // ------------------------------------------------------------------
        // #9901 – finalizer not called when ZIOApp receives SIGINT
        // ------------------------------------------------------------------
        test("#9901 – finalizer is called on SIGINT") {
          runApp(
            helperClass("Issue9901App"),
            timeoutSeconds = 15,
            sendSigintAfterMs = Some(400L)
          ).map { case (_, out, _) =>
            assertTrue(out.contains("finalizer-ran"))
          }
        },

        // ------------------------------------------------------------------
        // #9807 – shutdown hangs when finalizer contains ZIO operations
        // ------------------------------------------------------------------
        test("#9807 – shutdown with ZIO finalizer effects does not hang") {
          for {
            t0    <- zio.Clock.nanoTime
            (_, out, _) <- runApp(
                             helperClass("Issue9807App"),
                             timeoutSeconds = 15,
                             sendSigintAfterMs = Some(400L)
                           )
            t1    <- zio.Clock.nanoTime
            ms     = (t1 - t0) / 1_000_000L
          } yield assertTrue(out.contains("finalizer-ran")) && assertTrue(ms < 12_000L)
        },

        // ------------------------------------------------------------------
        // #9240 – exit code is 0 even when app fails
        // ------------------------------------------------------------------
        test("#9240 – exit code is non-zero when ZIOApp fails") {
          runApp(helperClass("Issue9240App")).map { case (code, _, _) =>
            assertTrue(code != 0)
          }
        },

        // ------------------------------------------------------------------
        // General: multiple SIGINT signals do not cause deadlock / hang
        // ------------------------------------------------------------------
        test("multiple SIGINT signals do not cause deadlock") {
          ZIO.scoped {
            for {
              process <- ZIO.acquireRelease(
                           ZIO.attempt {
                             val pb = new java.lang.ProcessBuilder(
                               List(javaExe, "-cp", classpath, helperClass("LongRunningApp")).asJava
                             )
                             pb.redirectErrorStream(false)
                             pb.start()
                           }
                         )(p => ZIO.succeed(if (p.isAlive) p.destroyForcibly() else p))

              _ <- ZIO.sleep(400.millis)

              _ <- ZIO.attempt {
                     if (isWindows) {
                       process.destroy()
                     } else {
                       new java.lang.ProcessBuilder("kill", "-INT", process.pid().toString).start()
                     }
                   }.orDie

              _ <- ZIO.sleep(100.millis)

              _ <- ZIO.attempt {
                     if (isWindows) {
                       process.destroy()
                     } else {
                       new java.lang.ProcessBuilder("kill", "-INT", process.pid().toString).start()
                     }
                   }.orDie

              finished <- ZIO.attempt(
                            process.waitFor(10L, java.util.concurrent.TimeUnit.SECONDS)
                          )
            } yield assertTrue(finished)
          }
        }
      )
    ) @@ sequential @@ withLiveClock @@ TestAspect.tag("integration")
}
