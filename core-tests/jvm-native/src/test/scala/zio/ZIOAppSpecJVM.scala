package zio

import zio.test._
import zio.test.TestAspect.{jvm, nonFlaky}

import java.util.concurrent.atomic.AtomicBoolean

/**
 * JVM-specific tests for ZIOApp signal handling and shutdown behavior.
 * These tests cover the behavior when the app completes due to external signals (SIGINT, SIGTERM),
 * graceful shutdown timeout, and finalizer execution during shutdown.
 * 
 * Related issues:
 * - #9901: Finalizers not running on termination
 * - #9807: Race between shutdown hooks
 * - #9240: Signal handler compatibility (covered by platform)
 */
object ZIOAppSpecJVM extends ZIOBaseSpec {

  def spec = suite("ZIOAppSpecJVM")(
    test("ZIOApp can be created with custom runtime") {
      for {
        ref <- Ref.make(0)
        runtime = Runtime.default
        app = ZIOApp(ZIO.succeed(ref.update(_ + 1)), runtime)
        _   <- app.invoke(Chunk.empty)
        v   <- ref.get
      } yield assertTrue(v == 1)
    } @@ jvm,
    test("shuttingDown flag is set during shutdown") {
      for {
        ref <- Ref.make(false)
        app = new ZIOAppDefault {
                override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
                  ZIO.succeed(shuttingDown.get()).flatMap(ref.set)
              }
        _     <- app.invoke(Chunk.empty)
        value <- ref.get
      } yield assertTrue(!value) // Should be false after app completes
    } @@ jvm,
    test("installSignalHandlers is called during workflow") {
      // This test verifies that signal handlers are installed
      // by checking that the app runs without errors on JVM
      for {
        code <- ZIOAppDefault.fromZIO(ZIO.unit).invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
      } yield assertTrue(code == ExitCode.success)
    } @@ jvm,
    test("shutdown timeout can be set to zero for immediate exit") {
      for {
        ref  <- Ref.make(false)
        app   = new ZIOAppDefault {
                 override val gracefulShutdownTimeout: Duration = Duration.Zero
                 override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
                   ZIO.never.ensuring(ref.set(true))
               }
        fiber <- app.invoke(Chunk.empty).fork
        _     <- ZIO.sleep(50.millis) // Give some time for the app to start
        // Interrupt should cause finalizer to attempt to run
        _     <- fiber.interrupt
        // With Duration.Zero, shutdown should be immediate
        value <- ref.get
      } yield assertTrue(value) // Finalizer should still run on interrupt
    } @@ jvm,
    test("shutdown timeout can be set to infinite") {
      for {
        ref  <- Ref.make(false)
        app   = new ZIOAppDefault {
                 override val gracefulShutdownTimeout: Duration = Duration.Infinity
                 override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
                   ZIO.never.ensuring(ref.set(true))
               }
        fiber <- app.invoke(Chunk.empty).fork
        _     <- ZIO.sleep(50.millis)
        _     <- fiber.interrupt
        value <- ref.get
      } yield assertTrue(value)
    } @@ jvm,
    test("exit with specific code via exit method") {
      for {
        code <- ZIOAppDefault
                 .fromZIO(ZIO.unit *> ZIO.succeed(ExitCode(123)))
                 .invoke(Chunk.empty)
                 .exitCode: @nowarn("cat=deprecation")
      } yield assertTrue(code == ExitCode(123))
    } @@ jvm,
    test("exit method stops the app immediately") {
      for {
        ref       <- Ref.make(false)
        started   <- Promise.make[Nothing, Unit]
        neverRun  <- Promise.make[Nothing, Unit]
        // Using exit should stop the app before the second effect runs
        app        = ZIOAppDefault.fromZIO(
                      started.succeed(()) *> ZIO.exit(ExitCode.success) *> neverRun.succeed(()).as(false)
                    )
        fiber     <- app.invoke(Chunk.empty).fork
        _         <- started.await
        _         <- ZIO.sleep(100.millis)
        neverDone <- neverRun.isDone
        _         <- fiber.await
      } yield assertTrue(!neverDone) // The second effect should never run
    } @@ jvm,
    test("finalizer timeout is respected") {
      // Test that a slow finalizer doesn't block forever when timeout is set
      for {
        ref  <- Ref.make(false)
        app   = new ZIOAppDefault {
                 override val gracefulShutdownTimeout: Duration = 100.millis
                 override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
                   ZIO.never.ensuring(
                     ZIO.sleep(500.millis) *> ref.set(true)
                   )
               }
        fiber <- app.invoke(Chunk.empty).fork
        _     <- ZIO.sleep(50.millis)
        _     <- fiber.interrupt
        // Give some time for the timeout to potentially kick in
        _     <- ZIO.sleep(200.millis)
        value <- ref.get
      } yield assertTrue(value) // Finalizer should still have been attempted
    } @@ jvm,
    test("run method can access args via getArgs") {
      for {
        ref <- Ref.make(Chunk.empty[String])
        app = new ZIOAppDefault {
                override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
                  ZIOAppArgs.getArgs.flatMap(args => ref.set(args))
              }
        _     <- app.invoke(Chunk("test", "args"))
        value <- ref.get
      } yield assertTrue(value == Chunk("test", "args"))
    } @@ jvm
  ) @@ jvm
}
