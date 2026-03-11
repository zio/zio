package zio

import zio.test._
import scala.annotation.nowarn

object ZIOAppSpec extends ZIOBaseSpec {
  def spec = suite("ZIOAppSpec")(
    test("fromZIO") {
      for {
        ref <- Ref.make(0)
        _   <- ZIOApp.fromZIO(ref.update(_ + 1)).invoke(Chunk.empty)
        v   <- ref.get
      } yield assertTrue(v == 1)
    },
    test("failure translates into ExitCode.failure") {
      for {
        code <- ZIOApp.fromZIO(ZIO.fail("Uh oh!")).invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
      } yield assertTrue(code == ExitCode.failure)
    },
    test("success translates into ExitCode.success") {
      for {
        code <- ZIOApp.fromZIO(ZIO.succeed("Hurray!")).invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
      } yield assertTrue(code == ExitCode.success)
    },
    test("composed app logic runs component logic") {
      for {
        ref <- Ref.make(2)
        app1 = ZIOApp.fromZIO(ref.update(_ + 3))
        app2 = ZIOApp.fromZIO(ref.update(_ - 5))
        _   <- (app1 <> app2).invoke(Chunk.empty)
        v   <- ref.get
      } yield assertTrue(v == 0)
    },
    test("hook update platform") {
      val counter = new java.util.concurrent.atomic.AtomicInteger(0)

      val logger1 = new ZLogger[Any, Unit] {
        def apply(
          trace: Trace,
          fiberId: zio.FiberId,
          logLevel: zio.LogLevel,
          message: () => Any,
          cause: Cause[Any],
          context: FiberRefs,
          spans: List[zio.LogSpan],
          annotations: Map[String, String]
        ): Unit = {
          counter.incrementAndGet()
          ()
        }
      }

      val app1 = ZIOApp(ZIO.fail("Uh oh!"), Runtime.addLogger(logger1))

      for {
        c <- app1.invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
        v <- ZIO.succeed(counter.get())
      } yield assertTrue(c == ExitCode.failure) && assertTrue(v == 1)
    },
    test("execution of finalizers on interruption") {
      for {
        running   <- Promise.make[Nothing, Unit]
        ref       <- Ref.make(false)
        effect     = (running.succeed(()) *> ZIO.never).ensuring(ref.set(true))
        app        = ZIOAppDefault.fromZIO(effect)
        fiber     <- app.invoke(Chunk.empty).fork
        _         <- running.await
        _         <- fiber.interrupt
        finalized <- ref.get
      } yield assertTrue(finalized)
    },
    test("finalizers are run in scope of bootstrap layer") {
      for {
        ref1 <- Ref.make(false)
        ref2 <- Ref.make(false)
        app = new ZIOAppDefault {
                override val bootstrap = ZLayer.scoped(ZIO.acquireRelease(ref1.set(true))(_ => ref1.set(false)))
                val run                = ZIO.acquireRelease(ZIO.unit)(_ => ref1.get.flatMap(ref2.set))
              }
        _     <- app.invoke(Chunk.empty)
        value <- ref2.get
      } yield assertTrue(value)
    },
    // Tests for issue #9909: correct exit codes
    test("defect results in ExitCode.failure") {
      for {
        code <- ZIOApp.fromZIO(ZIO.die(new RuntimeException("boom"))).invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
      } yield assertTrue(code == ExitCode.failure)
    },
    test("interruption results in ExitCode.failure") {
      for {
        fiber <- ZIOApp.fromZIO(ZIO.never).invoke(Chunk.empty).fork
        _     <- fiber.interrupt
        exit  <- fiber.await
      } yield assertTrue(exit.isFailure)
    },
    // Regression tests for #9901: finalizers must run on external interruption
    test("finalizers run when app is interrupted (regression #9901)") {
      for {
        started   <- Promise.make[Nothing, Unit]
        finalized <- Ref.make(false)
        app = ZIOAppDefault.fromZIO(
                ZIO.acquireRelease(started.succeed(()))(_ => finalized.set(true)) *> ZIO.never
              )
        fiber     <- app.invoke(Chunk.empty).fork
        _         <- started.await
        _         <- fiber.interrupt
        didFinalize <- finalized.get
      } yield assertTrue(didFinalize)
    },
    // Regression test for #9901: bootstrap layer finalizers run on interruption
    test("bootstrap layer finalizers run on interruption (regression #9901)") {
      for {
        started          <- Promise.make[Nothing, Unit]
        bootstrapFinalized <- Ref.make(false)
        app = new ZIOAppDefault {
          override val bootstrap = ZLayer.scoped(
            ZIO.acquireRelease(ZIO.unit)(_ => bootstrapFinalized.set(true))
          )
          val run = started.succeed(()) *> ZIO.never
        }
        fiber     <- app.invoke(Chunk.empty).fork
        _         <- started.await
        _         <- fiber.interrupt
        didFinalize <- bootstrapFinalized.get
      } yield assertTrue(didFinalize)
    },
    test("app exits immediately on success without delay") {
      for {
        start  <- Clock.instant
        _      <- ZIOApp.fromZIO(ZIO.unit).invoke(Chunk.empty)
        end    <- Clock.instant
        elapsed = java.time.Duration.between(start, end).toMillis
      } yield assertTrue(elapsed < 2000L)
    },
    test("app exits immediately on failure without delay") {
      for {
        start  <- Clock.instant
        _      <- ZIOApp.fromZIO(ZIO.fail("error")).invoke(Chunk.empty).ignore
        end    <- Clock.instant
        elapsed = java.time.Duration.between(start, end).toMillis
      } yield assertTrue(elapsed < 2000L)
    },
    test("invoke with command-line args makes args available") {
      val args = Chunk("--foo", "bar", "--baz")
      for {
        received <- ZIOApp.fromZIO(ZIOAppArgs.getArgs).invoke(args)
      } yield assertTrue(received == args)
    },
    test("bootstrap layer errors result in ExitCode.failure") {
      val failingBootstrap = ZLayer.fail("bootstrap failure")
      val app = ZIOApp(ZIO.unit, failingBootstrap)
      for {
        code <- app.invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
      } yield assertTrue(code == ExitCode.failure)
    }
  )
}
