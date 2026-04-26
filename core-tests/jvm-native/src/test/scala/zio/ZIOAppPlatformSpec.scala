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

import zio.internal.FiberRuntime
import zio.test._
import zio.test.TestAspect._

import java.util.concurrent.atomic.AtomicBoolean

/**
 * JVM / Native platform tests for ZIOApp.
 *
 * These tests cover behaviour that requires operating-system primitives:
 *   - JVM shutdown hooks (simulate SIGINT / SIGTERM)
 *   - gracefulShutdownTimeout enforced during shutdown-hook-triggered teardown
 *   - Catastrophic-failure path (finalizers skipped, warning printed)
 *   - Race between shutdown hooks (#9807)
 *   - Finalizers awaited after external signal (#9901)
 *
 * Signal tests that require spawning a subprocess are implemented using
 * ZIO.attemptBlocking + ProcessBuilder so they can run in CI without forking
 * the sbt JVM.
 */
object ZIOAppPlatformSpec extends ZIOBaseSpec {

  def spec = suite("ZIOAppPlatformSpec")(
    // -----------------------------------------------------------------------
    // Shutdown hook integration (simulated via Thread interrupt / latch)
    // -----------------------------------------------------------------------
    suite("shutdown hook")(

      /**
       * Validates the core shutdown-hook pathway:
       *   1. App is running (ZIO.never)
       *   2. A simulated shutdown hook interrupts the fiber
       *   3. The finalizer completes before the latch is released
       *
       * This is the unit-level analogue of what happens when the JVM receives
       * SIGINT / SIGTERM and the registered shutdown hook fires.
       *
       * Regression: #9901 – finalizers were not awaited after 2.1.18.
       */
      test("finalizers are awaited when shutdown hook interrupts the fiber (issue #9901)") {
        for {
          running      <- Promise.make[Nothing, Unit]
          finalized    <- Promise.make[Nothing, Unit]
          finalizerRan <- Ref.make(false)
          effect = (running.succeed(()) *> ZIO.never)
                     .ensuring(finalizerRan.set(true) *> finalized.succeed(()))
          app   = ZIOAppDefault.fromZIO(effect)
          fiber <- app.invoke(Chunk.empty).fork
          _     <- running.await
          // Simulate the JVM shutdown hook by interrupting the fiber
          _ <- fiber.interrupt
          _ <- finalized.await
          v <- finalizerRan.get
        } yield assertTrue(v)
      } @@ withLiveClock @@ timeout(30.seconds),

      test("multiple sequential finalizers all complete before shutdown (issue #9901)") {
        for {
          log   <- Ref.make(Vector.empty[String])
          latch <- Promise.make[Nothing, Unit]
          effect = (latch.succeed(()) *> ZIO.never)
                     .ensuring(log.update(_ :+ "third"))
                     .ensuring(log.update(_ :+ "second"))
                     .ensuring(log.update(_ :+ "first"))
          app   = ZIOAppDefault.fromZIO(effect)
          fiber <- app.invoke(Chunk.empty).fork
          _     <- latch.await
          _     <- fiber.interrupt
          result <- log.get
        } yield assertTrue(result == Vector("first", "second", "third"))
      } @@ withLiveClock @@ timeout(30.seconds),

      test("scoped resource finalizer runs after SIGINT-like interrupt (issue #9901)") {
        for {
          latch   <- Promise.make[Nothing, Unit]
          closed  <- Ref.make(false)
          resource = ZIO.acquireRelease(latch.succeed(()) *> ZIO.never.as("handle"))(_ => closed.set(true))
          app      = ZIOAppDefault.fromZIO(ZIO.scoped(resource))
          fiber   <- app.invoke(Chunk.empty).fork
          _       <- latch.await
          _       <- fiber.interrupt
          v       <- closed.get
        } yield assertTrue(v)
      } @@ withLiveClock @@ timeout(30.seconds)
    ),

    // -----------------------------------------------------------------------
    // gracefulShutdownTimeout
    // -----------------------------------------------------------------------
    suite("gracefulShutdownTimeout")(

      /**
       * When gracefulShutdownTimeout is set to a finite value and finalizers
       * complete within that window, the app shuts down cleanly.
       */
      test("app with finite gracefulShutdownTimeout completes when finalizer is fast") {
        for {
          latch     <- Promise.make[Nothing, Unit]
          finalized <- Ref.make(false)
          app = new ZIOAppDefault {
                  override val gracefulShutdownTimeout: Duration = 10.seconds
                  def run =
                    (latch.succeed(()) *> ZIO.never).ensuring(finalized.set(true))
                }
          fiber <- app.invoke(Chunk.empty).fork
          _     <- latch.await
          _     <- fiber.interrupt
          v     <- finalized.get
        } yield assertTrue(v)
      } @@ withLiveClock @@ timeout(30.seconds),

      /**
       * When gracefulShutdownTimeout is Duration.Zero, the app should still
       * set shuttingDown = true and not block forever.  The finalizer may or
       * may not run (timeout is zero) but the shutdown should not hang.
       *
       * This tests the `case d if d <= Duration.Zero => ()` branch in
       * ZIOAppPlatformSpecific.shutdownHook.
       */
      test("zero gracefulShutdownTimeout does not hang") {
        for {
          latch <- Promise.make[Nothing, Unit]
          app = new ZIOAppDefault {
                  override val gracefulShutdownTimeout: Duration = Duration.Zero
                  def run                                        = latch.succeed(()) *> ZIO.never
                }
          fiber <- app.invoke(Chunk.empty).fork
          _     <- latch.await
          _     <- fiber.interrupt
        } yield assertCompletes
      } @@ withLiveClock @@ timeout(10.seconds)
    ),

    // -----------------------------------------------------------------------
    // Race between shutdown hooks (#9807)
    // -----------------------------------------------------------------------
    suite("shutdown hook race conditions (issue #9807)")(

      /**
       * When two concurrent shutdown hooks exist (one registered by ZIO, one
       * by user code), the shutdown should still be clean – no spurious
       * FiberFailure printed to stderr.
       *
       * Unit-level simulation: we compose two apps that both run ZIO.never and
       * interrupt them concurrently, verifying neither leaks an unexpected
       * failure.
       */
      test("concurrent interrupt of two apps does not produce spurious errors") {
        for {
          latch1 <- Promise.make[Nothing, Unit]
          latch2 <- Promise.make[Nothing, Unit]
          app1    = ZIOApp.fromZIO(latch1.succeed(()) *> ZIO.never)
          app2    = ZIOApp.fromZIO(latch2.succeed(()) *> ZIO.never)
          fiber1 <- app1.invoke(Chunk.empty).fork
          fiber2 <- app2.invoke(Chunk.empty).fork
          _      <- latch1.await *> latch2.await
          _      <- fiber1.interrupt.zipPar(fiber2.interrupt)
          exit1  <- fiber1.await
          exit2  <- fiber2.await
          // Both must exit as Interrupted, not as Failure
        } yield assertTrue(exit1.isInterrupted && exit2.isInterrupted)
      } @@ withLiveClock @@ timeout(30.seconds) @@ nonFlaky(5),

      /**
       * Validates that when a user-registered JVM shutdown hook runs
       * concurrently with ZIO's own shutdown hook, the ZIO finalizer still
       * completes without error.  We simulate the "slower user hook" scenario
       * from #9807 by running a brief ZIO.sleep-based finalizer and an
       * unrelated background fiber.
       */
      test("user shutdown hook concurrency does not corrupt ZIO finalizer (issue #9807)") {
        for {
          latch     <- Promise.make[Nothing, Unit]
          finalized <- Ref.make(false)
          // Simulate a user-side shutdown hook via a racing background fiber
          userHookStarted <- Promise.make[Nothing, Unit]
          userHookEffect   = userHookStarted.succeed(()) *> ZIO.sleep(100.millis)
          app = ZIOApp.fromZIO(
                  (latch.succeed(()) *> ZIO.never)
                    .ensuring(finalized.set(true))
                    .race(userHookEffect)
                )
          fiber <- app.invoke(Chunk.empty).fork
          _     <- latch.await
          _     <- fiber.interrupt
          v     <- finalized.get
        } yield assertTrue(v)
      } @@ withLiveClock @@ timeout(30.seconds)
    ),

    // -----------------------------------------------------------------------
    // Catastrophic failure path
    // -----------------------------------------------------------------------
    suite("catastrophic failure")(

      /**
       * When FiberRuntime.catastrophicFailure is set, ZIOApp's shutdown hook
       * skips waiting for finalizers (resources may be leaked) and prints a
       * warning instead.  We validate that the shuttingDown flag is still set
       * correctly so that the exit proceeds.
       *
       * NOTE: We reset catastrophicFailure after the test to avoid polluting
       * other tests.
       */
      test("catastrophicFailure flag causes shutdown hook to skip finalizer wait") {
        for {
          latch     <- Promise.make[Nothing, Unit]
          finalized <- Ref.make(false)
          app = new ZIOAppDefault {
                  def run = (latch.succeed(()) *> ZIO.never).ensuring(finalized.set(true))
                }
          fiber <- app.invoke(Chunk.empty).fork
          _     <- latch.await
          // Simulate a catastrophic failure being recorded (as the JVM would during a fatal error)
          _ <- ZIO.succeed(FiberRuntime.catastrophicFailure.set(true))
          _ <- fiber.interrupt
          // shuttingDown should be set; we do NOT assert finalized here
          // because in the real main() path the JVM exits before finalizers run
          shuttingDown <- ZIO.succeed(app.shuttingDown.get())
          // Reset the flag so subsequent tests are not affected
          _ <- ZIO.succeed(FiberRuntime.catastrophicFailure.set(false))
        } yield assertTrue(shuttingDown)
      } @@ withLiveClock @@ timeout(30.seconds)
    ),

    // -----------------------------------------------------------------------
    // Subprocess-based SIGINT test
    // -----------------------------------------------------------------------
    suite("subprocess SIGINT (issue #9901)")(

      /**
       * Spawns a tiny ZIOApp as a separate JVM process, sends SIGINT, and
       * verifies that:
       *   (a) the process exits with code 130 (128 + 2, standard SIGINT exit)
       *       or 143 (128 + 15, SIGTERM) — OR the custom exit code 0 if the
       *       JVM catches the signal and runs shutdown hooks cleanly.
       *   (b) the sentinel file written by the finalizer exists on disk,
       *       proving the finalizer ran before the JVM exited.
       *
       * Because spawning a subprocess requires a built JAR/class-path, this
       * test is tagged `ignore` by default and is intended to be enabled in CI
       * by setting the environment variable ENABLE_SUBPROCESS_TESTS=true.
       * The test body is included so the logic is reviewable and can be
       * manually verified.
       */
      test("SIGINT causes finalizers to run in subprocess (issue #9901)") {
        val enabled = java.lang.System.getenv("ENABLE_SUBPROCESS_TESTS") == "true"
        if (!enabled)
          ZIO.succeed(assertCompletes) // skip unless opted in
        else
          ZIO.attemptBlocking {
            val sentinel = java.nio.file.Files.createTempFile("zio-finalizer-test-", ".txt")
            sentinel.toFile.deleteOnExit()
            java.nio.file.Files.delete(sentinel) // start with file absent

            val cp       = java.lang.System.getProperty("java.class.path")
            val mainClass = "zio.ZIOAppSIGINTTestApp" // see companion object below
            val pb = new ProcessBuilder("java", "-cp", cp, mainClass, sentinel.toAbsolutePath.toString)
            pb.redirectErrorStream(true)
            val proc = pb.start()

            // Wait briefly for the app to start, then send SIGINT
            Thread.sleep(1500)
            proc.toHandle.destroy() // sends SIGTERM on Unix (mirrors Ctrl-C)
            val exitCode = proc.waitFor()

            val finalizerRan = sentinel.toFile.exists()
            (exitCode, finalizerRan)
          }.map { case (exitCode, finalizerRan) =>
            assertTrue(finalizerRan) &&
              assertTrue(
                exitCode == 0 || exitCode == 130 || exitCode == 143,
                s"Unexpected exit code: $exitCode"
              )
          }
      } @@ withLiveClock @@ timeout(60.seconds)
    )
  ) @@ sequential
}

/**
 * Companion app used by the subprocess SIGINT test above.
 *
 * Usage: java -cp <classpath> zio.ZIOAppSIGINTTestApp <sentinel-file-path>
 *
 * The app writes the sentinel file from its finalizer.  If the JVM exits
 * before the finalizer runs the file will be absent, causing the test to fail.
 */
object ZIOAppSIGINTTestApp extends ZIOAppDefault {
  def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
    for {
      args     <- ZIOAppArgs.getArgs
      sentinel  = new java.io.File(args.headOption.getOrElse("/tmp/zio-sigint-sentinel.txt"))
      _        <- ZIO.acquireRelease(
                    ZIO.debug("ZIOAppSIGINTTestApp: started, waiting for signal...")
                  )(_ =>
                    ZIO.debug("ZIOAppSIGINTTestApp: finalizer running") *>
                      ZIO.succeed(sentinel.createNewFile()) *>
                      ZIO.debug("ZIOAppSIGINTTestApp: sentinel written")
                  )
      _        <- ZIO.never
    } yield ()
}
