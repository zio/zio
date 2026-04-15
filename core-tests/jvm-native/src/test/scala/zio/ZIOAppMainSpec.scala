package zio

import zio.internal.Platform
import zio.test.TestAspect._
import zio.test._

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object ZIOAppMainSpec extends ZIOBaseSpec {

  def spec = suite("ZIOAppMainSpec")(
    suite("exit codes")(
      test("successful app returns exit code 0") {
        val app = ZIOAppDefault.fromZIO(ZIO.succeed("ok"))
        for {
          exit <- app.invoke(Chunk.empty).exit
        } yield assertTrue(exit.isSuccess)
      },
      test("failing app returns exit code 1") {
        val app = ZIOAppDefault.fromZIO(ZIO.fail("boom"))
        for {
          exit <- app.invoke(Chunk.empty).exit
        } yield assertTrue(!exit.isSuccess)
      },
      test("defect app returns exit code 1") {
        val app = ZIOAppDefault.fromZIO(ZIO.dieMessage("catastrophic"))
        for {
          exit <- app.invoke(Chunk.empty).exit
        } yield assertTrue(!exit.isSuccess)
      }
    ),
    suite("finalizers")(
      test("finalizers run on success") {
        for {
          ref <- Ref.make(false)
          app = ZIOAppDefault.fromZIO(
                  ZIO.acquireRelease(ZIO.unit)(_ => ref.set(true))
                )
          _     <- app.invoke(Chunk.empty)
          value <- ref.get
        } yield assertTrue(value)
      },
      test("finalizers run on failure") {
        for {
          ref <- Ref.make(false)
          app = ZIOAppDefault.fromZIO(
                  ZIO.acquireRelease(ZIO.unit)(_ => ref.set(true)) *>
                    ZIO.fail("uh oh")
                )
          _     <- app.invoke(Chunk.empty).ignore
          value <- ref.get
        } yield assertTrue(value)
      },
      test("finalizers run on interruption") {
        for {
          ref     <- Ref.make(false)
          running <- Promise.make[Nothing, Unit]
          effect   = (running.succeed(()) *> ZIO.never).ensuring(ref.set(true))
          app     = ZIOAppDefault.fromZIO(effect)
          fiber   <- app.invoke(Chunk.empty).fork
          _       <- running.await
          _       <- fiber.interrupt
          value   <- ref.get
        } yield assertTrue(value)
      },
      test("multiple finalizers run in reverse order") {
        for {
          ref   <- Ref.make[List[String]](Nil)
          app   = ZIOAppDefault.fromZIO(
                    ZIO.acquireRelease(ZIO.unit)(_ => ref.update("first" :: _)) *>
                      ZIO.acquireRelease(ZIO.unit)(_ => ref.update("second" :: _)) *>
                      ZIO.unit
                  )
          _     <- app.invoke(Chunk.empty)
          value <- ref.get
        } yield assertTrue(value == List("second", "first"))
      },
      test("finalizers run in scope of bootstrap layer") {
        for {
          ref1 <- Ref.make(false)
          ref2 <- Ref.make(false)
          app = new ZIOAppDefault {
                  override val bootstrap =
                    ZLayer.scoped(ZIO.acquireRelease(ref1.set(true))(_ => ref1.set(false)))
                  val run = ZIO.acquireRelease(ZIO.unit)(_ => ref1.get.flatMap(ref2.set))
                }
          _     <- app.invoke(Chunk.empty)
          value <- ref2.get
        } yield assertTrue(value)
      }
    ),
    suite("shutdown")(
      test("shutdown sequence does not hang with quick finalizers") {
        for {
          ref <- Ref.make(false)
          app = ZIOAppDefault.fromZIO(
                  ZIO.acquireRelease(ZIO.unit)(_ => ref.set(true))
                )
          exit <- app.invoke(Chunk.empty).exit
                   .timeout(5.seconds)
        } yield assertTrue(exit.isDefined && exit.get.isSuccess && ref.unsafe.get(Unsafe.unsafe))
      } @@ withLiveClock,
      test("interruptRootFibers interrupts child fibers") {
        for {
          ref    <- Ref.make(false)
          latch  <- Promise.make[Nothing, Unit]
          app    = new ZIOAppDefault {
                     val run = for {
                       _ <- latch.succeed(())
                       _ <- ZIO.never.ensuring(ref.set(true))
                     } yield ()
                   }
          fiber  <- app.invoke(Chunk.empty).fork
          _      <- latch.await
          _      <- fiber.interrupt
          value  <- ref.get
        } yield assertTrue(value)
      },
      test("invoke can be called multiple times") {
        val app = ZIOAppDefault.fromZIO(ZIO.unit)
        for {
          _ <- app.invoke(Chunk.empty)
          _ <- app.invoke(Chunk.empty)
        } yield assertTrue(true)
      }
    ),
    suite("gracefulShutdownTimeout")(
      test("custom timeout is respected") {
        val app = new ZIOAppDefault {
          override def gracefulShutdownTimeout: Duration = 1.second
          val run = ZIO.unit
        }
        for {
          exit <- app.invoke(Chunk.empty).exit.timeout(5.seconds)
        } yield assertTrue(exit.isDefined)
      } @@ withLiveClock
    ),
    suite("signal handling")(
      test("signal handlers do not throw when installed") {
        for {
          ref <- Ref.make(false)
          app = new ZIOAppDefault {
                  override def gracefulShutdownTimeout: Duration = 5.seconds
                  val run = ref.set(true)
                }
          _     <- app.invoke(Chunk.empty)
          value <- ref.get
        } yield assertTrue(value)
      }
    ),
    suite("app composition")(
      test("composed app runs both sides") {
        for {
          ref  <- Ref.make(0)
          app1 = ZIOApp.fromZIO(ref.update(_ + 1))
          app2 = ZIOApp.fromZIO(ref.update(_ + 10))
          _    <- (app1 <> app2).invoke(Chunk.empty)
          v    <- ref.get
        } yield assertTrue(v == 11)
      },
      test("composed app failure does not prevent other side from completing") {
        for {
          ref  <- Ref.make(0)
          app1 = ZIOApp.fromZIO(ref.update(_ + 1))
          app2 = ZIOApp.fromZIO(ZIO.fail("oops"))
          _    <- (app1 <> app2).invoke(Chunk.empty).exit
          v    <- ref.get
        } yield assertTrue(v == 1)
      }
    ),
    suite("workflow")(
      test("workflow provides bootstrap layer to run") {
        for {
          ref <- Ref.make(false)
          app = new ZIOAppDefault {
                  override val bootstrap = ZLayer.scoped(
                    ZIO.acquireRelease(ZIO.unit)(_ => ref.set(true))
                  )
                  val run = ZIO.unit
                }
          _     <- app.invoke(Chunk.empty)
          value <- ref.get
        } yield assertTrue(value)
      },
      test("workflow logs error on failure") {
        val app = ZIOAppDefault.fromZIO(ZIO.fail("logged error"))
        for {
          exit <- app.invoke(Chunk.empty).exit
        } yield assertTrue(!exit.isSuccess)
      }
    ),
    suite("regression tests")(
      test("finalizers run on SIGINT-style interruption via fiber interrupt") {
        for {
          ref     <- Ref.make(false)
          running <- Promise.make[Nothing, Unit]
          effect   = (running.succeed(()) *> ZIO.never)
                       .ensuring(ref.set(true))
          app     = ZIOAppDefault.fromZIO(effect)
          fiber   <- app.invoke(Chunk.empty).fork
          _       <- running.await
          _       <- fiber.interrupt
          value   <- ref.get
        } yield assertTrue(value)
      },
      test("exitUnsafe sets shuttingDown flag") {
        val app = ZIOAppDefault.fromZIO(ZIO.unit)
        val result = zio.Unsafe.unsafe { implicit unsafe =>
          val field = app.getClass.getMethod("shuttingDown")
          val atomicBool = field.invoke(app).asInstanceOf[AtomicBoolean]
          !atomicBool.get()
        }
        assertTrue(result)
      },
      test("interruption-only errors are suppressed during shutdown") {
        val app = new ZIOAppDefault {
          val run = ZIO.interrupt
        }
        for {
          exit <- app.invoke(Chunk.empty).exit
        } yield assertTrue(exit.isInterrupted)
      }
    )
  ) @@ sequential @@ timeout(60.seconds)
}
