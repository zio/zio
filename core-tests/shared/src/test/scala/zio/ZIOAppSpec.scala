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
    test("defect translates into ExitCode.failure") {
      for {
        code <- ZIOApp.fromZIO(ZIO.dieMessage("Boom!")).invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
      } yield assertTrue(code == ExitCode.failure)
    },
    test("success translates into ExitCode.success") {
      for {
        code <- ZIOApp.fromZIO(ZIO.succeed("Hurray!")).invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
      } yield assertTrue(code == ExitCode.success)
    },
    test("command-line arguments are propagated via ZIOAppArgs") {
      for {
        ref <- Ref.make(Chunk.empty[String])
        app  = ZIOAppDefault.fromZIO(ZIOAppArgs.getArgs.flatMap(ref.set))
        args = Chunk("arg1", "arg2", "--flag")
        _   <- app.invoke(args)
        v   <- ref.get
      } yield assertTrue(v == args)
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
    test("composed apps combine environments from bootstrap layers") {
      for {
        ref <- Ref.make(0)
        app1 = ZIOApp(ZIO.serviceWithZIO[Int](n => ref.update(_ + n)), ZLayer.succeed(10))
        app2 = ZIOApp(ZIO.serviceWithZIO[String](s => ref.update(_ + s.length)), ZLayer.succeed("hello"))
        _   <- (app1 <> app2).invoke(Chunk.empty)
        v   <- ref.get
      } yield assertTrue(v == 15)
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
    test("multiple scoped finalizers run in LIFO order on interruption (#9901)") {
      for {
        running <- Promise.make[Nothing, Unit]
        ref     <- Ref.make(List.empty[Int])
        app = new ZIOAppDefault {
                val run =
                  for {
                    _ <- ZIO.acquireRelease(ZIO.unit)(_ => ref.update(1 :: _))
                    _ <- ZIO.scoped {
                           for {
                             _ <- ZIO.addFinalizer(ref.update(2 :: _))
                             _ <- ZIO.addFinalizer(ref.update(3 :: _))
                             _ <- running.succeed(()) *> ZIO.never
                           } yield ()
                         }
                  } yield ()
              }
        fiber <- app.invoke(Chunk.empty).fork
        _     <- running.await
        _     <- fiber.interrupt
        order <- ref.get
      } yield assertTrue(order == List(1, 2, 3))
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
    test("Proxy delegates bootstrap and run to underlying ZIOApp") {
      for {
        ref  <- Ref.make(0)
        base  = ZIOApp(ZIO.serviceWithZIO[Int](n => ref.set(n)), ZLayer.succeed(42))
        proxy = new ZIOApp.Proxy(base)
        _    <- proxy.invoke(Chunk.empty)
        v    <- ref.get
      } yield assertTrue(v == 42)
    }
  )
}
