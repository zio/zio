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
    test("die translates into ExitCode.failure") {
      for {
        code <- ZIOApp.fromZIO(ZIO.die(new RuntimeException("died!"))).invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
      } yield assertTrue(code == ExitCode.failure)
    },
    test("interruption translates into ExitCode.failure") {
      for {
        ref       <- Ref.make(false)
        effect     = ZIO.never.ensuring(ref.set(true))
        app        = ZIOAppDefault.fromZIO(effect)
        fiber     <- app.invoke(Chunk.empty).fork
        _         <- ZIO.sleep(10.millis) // Let the app start
        _         <- fiber.interrupt
        finalized <- ref.get
        code      <- fiber.await
      } yield assertTrue(finalized) && assertTrue(code.isFailure)
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

      val app1 = new ZIOAppDefault {
        override def runtime: Runtime[Any] = Runtime.default.addLogger(logger1)
      }

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
    test("multiple finalizers run in reverse order") {
      for {
        ref  <- Ref.make(List.empty[Int])
        app   = ZIOAppDefault.fromZIO(
                 ZIO
                   .acquireRelease(ref.update(1 :: _))(ref.update(2 :: _))
                   .acquireRelease(ref.update(3 :: _))(ref.update(4 :: _))
                   .acquireRelease(ref.update(5 :: _))(ref.update(6 :: _))
                   .as(())
               )
        _     <- app.invoke(Chunk.empty)
        value <- ref.get
      } yield assertTrue(value == List(6, 5, 4, 3, 2, 1))
    },
    test("finalizers run even when app fails") {
      for {
        ref <- Ref.make(false)
        app  = ZIOAppDefault.fromZIO(
                 ZIO
                   .acquireRelease(ZIO.unit)(_ => ref.set(true))
                   .zipRight(ZIO.fail("error"))
               )
        _     <- app.invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
        value <- ref.get
      } yield assertTrue(value)
    },
    test("finalizers run even when app dies") {
      for {
        ref <- Ref.make(false)
        app  = ZIOAppDefault.fromZIO(
                 ZIO
                   .acquireRelease(ZIO.unit)(_ => ref.set(true))
                   .zipRight(ZIO.die(new RuntimeException("died")))
               )
        _     <- app.invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
        value <- ref.get
      } yield assertTrue(value)
    },
    test("exit code reflects specific failure types") {
      for {
        // Test that different failure types result in proper exit codes
        code1 <- ZIOApp.fromZIO(ZIO.fail(())).invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
        code2 <- ZIOApp.fromZIO(ZIO.die(new Exception)).invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
        code3 <- ZIOApp.fromZIO(ZIO.interrupt).invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
      } yield assertTrue(code1 == ExitCode.failure) &&
        assertTrue(code2 == ExitCode.failure) &&
        assertTrue(code3 == ExitCode.failure)
    },
    test("gracefulShutdownTimeout is respected") {
      // This test verifies that the gracefulShutdownTimeout is accessible
      // and can be overridden
      val app = new ZIOAppDefault {
        override val gracefulShutdownTimeout: Duration = 5.seconds
        override def run: ZIO[ZIOAppArgs with Scope, Any, Any] = ZIO.never
      }
      assertTrue(app.gracefulShutdownTimeout == 5.seconds)
    },
    test("app completes successfully with ExitCode.success") {
      for {
        ref <- Ref.make(0)
        app  = ZIOAppDefault.fromZIO(ref.update(_ + 1))
        code <- app.invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
        v   <- ref.get
      } yield assertTrue(code == ExitCode.success) && assertTrue(v == 1)
    },
    test("app exits with failure when using exit") {
      for {
        code <- ZIOAppDefault
                 .fromZIO(ZIO.succeed(ExitCode(42)))
                 .invoke(Chunk.empty)
                 .exitCode: @nowarn("cat=deprecation")
      } yield assertTrue(code == ExitCode(42))
    },
    test("nested finalizers all run") {
      for {
        ref <- Ref.make(0)
        app  = ZIOAppDefault.fromZIO(
                 ZIO.acquireRelease(ref.update(_ + 1))(_ => ref.update(_ - 1)) *>
                   ZIO.acquireRelease(ref.update(_ + 1))(_ => ref.update(_ - 1)) *>
                   ZIO.acquireRelease(ref.update(_ + 1))(_ => ref.update(_ - 1)) *>
                   ZIO.unit
               )
        _     <- app.invoke(Chunk.empty)
        value <- ref.get
      } yield assertTrue(value == 0) // All acquired resources should be released
    },
    test("scope finalizers are run on normal completion") {
      for {
        ref <- Ref.make(false)
        app  = ZIOAppDefault.fromZIO(
                 ZIO.acquireRelease(ZIO.unit)(_ => ref.set(true))
               )
        _     <- app.invoke(Chunk.empty)
        value <- ref.get
      } yield assertTrue(value)
    },
    test("interruption causes finalizers to run") {
      for {
        ref       <- Ref.make(false)
        started   <- Promise.make[Nothing, Unit]
        effect     = (started.succeed(()) *> ZIO.never).ensuring(ref.set(true))
        app        = ZIOAppDefault.fromZIO(effect)
        fiber     <- app.invoke(Chunk.empty).fork
        _         <- started.await
        _         <- fiber.interrupt
        value     <- ref.get
      } yield assertTrue(value)
    },
    test("defect in finalizer does not prevent other finalizers") {
      for {
        ref1 <- Ref.make(false)
        ref2 <- Ref.make(false)
        app  = ZIOAppDefault.fromZIO(
                 ZIO
                   .acquireRelease(ref1.set(true))(_ => ZIO.die(new RuntimeException("finalizer error")))
                   .acquireRelease(ref2.set(true))(_ => ZIO.unit)
                   .as(())
               )
        // This should not throw - finalizer errors should be caught
        _     <- app.invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
        value <- ref2.get
      } yield assertTrue(value) // Second finalizer should still run
    },
    test("test args are accessible in run") {
      for {
        ref <- Ref.make(Chunk.empty[String])
        app  = ZIOAppDefault.fromZIO(ZIOAppArgs.getArgs.flatMap(args => ref.set(args)))
        _     <- app.invoke(Chunk("arg1", "arg2"))
        value <- ref.get
      } yield assertTrue(value == Chunk("arg1", "arg2"))
    },
    test("app composition runs both apps") {
      for {
        ref1 <- Ref.make(0)
        ref2 <- Ref.make(0)
        app1 = ZIOApp.fromZIO(ref1.update(_ + 1))
        app2 = ZIOApp.fromZIO(ref2.update(_ + 1))
        _   <- (app1 <> app2).invoke(Chunk.empty)
        v1  <- ref1.get
        v2  <- ref2.get
      } yield assertTrue(v1 == 1) && assertTrue(v2 == 1)
    },
    test("bootstrap layer is provided to run") {
      for {
        ref <- Ref.make(false)
        app = new ZIOAppDefault {
                override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
                  ZLayer.fromZIO(ref.set(true))
                override val run: ZIO[ZIOAppArgs with Scope, Any, Any] = ZIO.unit
              }
        _     <- app.invoke(Chunk.empty)
        value <- ref.get
      } yield assertTrue(value)
    }
  )
}
