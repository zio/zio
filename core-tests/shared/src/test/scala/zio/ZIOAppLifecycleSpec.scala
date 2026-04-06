package zio

import zio.test._

import scala.annotation.nowarn

/**
 * Cross-platform tests for ZIOApp via the `invoke()` pathway.
 *
 * Complements `ZIOAppSpec` with edge cases around bootstrap layers, scoped
 * resources, error handling in finalizers, app composition, and args threading.
 * These tests run on JVM, JS, and Native without spawning child processes.
 *
 * @see [[https://github.com/zio/zio/issues/9909 #9909]]
 */
object ZIOAppLifecycleSpec extends ZIOBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("ZIOAppLifecycleSpec")(
    bootstrapSuite,
    finalizerEdgeCaseSuite,
    compositionSuite,
    argsSuite,
    errorHandlingSuite
  )

  // ---------------------------------------------------------------------------
  // Bootstrap layer
  // ---------------------------------------------------------------------------

  private val bootstrapSuite = suite("bootstrap layer")(
    test("bootstrap layer resources are released on completion") {
      for {
        ref <- Ref.make(false)
        app = new ZIOAppDefault {
                override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
                  ZLayer.scoped(ZIO.acquireRelease(ZIO.unit)(_ => ref.set(true)))
                val run = ZIO.unit
              }
        _        <- app.invoke(Chunk.empty)
        released <- ref.get
      } yield assertTrue(released)
    },
    test("bootstrap failure results in exit code failure") {
      val app = new ZIOAppDefault {
        override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] = ZLayer.fail("bootstrap broke")
        val run                                              = ZIO.unit
      }
      for {
        code <- app.invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
      } yield assertTrue(code == ExitCode.failure)
    },
    test("bootstrap resources are available during run") {
      for {
        ref <- Ref.make(0)
        app = new ZIOApp {
                type Environment = Ref[Int]
                val environmentTag: EnvironmentTag[Ref[Int]]                       = EnvironmentTag[Ref[Int]]
                val bootstrap: ZLayer[ZIOAppArgs, Any, Ref[Int]]                   = ZLayer.succeed(ref)
                val run: ZIO[Ref[Int] with ZIOAppArgs with Scope, Any, Any] =
                  ZIO.serviceWithZIO[Ref[Int]](_.update(_ + 10))
              }
        _   <- app.invoke(Chunk.empty)
        v   <- ref.get
      } yield assertTrue(v == 10)
    }
  )

  // ---------------------------------------------------------------------------
  // Finalizer edge cases
  // ---------------------------------------------------------------------------

  private val finalizerEdgeCaseSuite = suite("finalizer edge cases")(
    test("defect in finalizer does not prevent app from completing") {
      for {
        ref <- Ref.make(false)
        app = new ZIOAppDefault {
                val run = ZIO.scoped {
                  for {
                    _ <- ZIO.acquireRelease(ZIO.unit)(_ => ref.set(true))
                    _ <- ZIO.acquireRelease(ZIO.unit)(_ => ZIO.die(new RuntimeException("boom")))
                  } yield ()
                }
              }
        _   <- app.invoke(Chunk.empty).exit
        ran <- ref.get
      } yield assertTrue(ran)
    },
    test("nested scoped resources are released in correct order") {
      for {
        order <- Ref.make(List.empty[String])
        app = new ZIOAppDefault {
                val run = ZIO.scoped {
                  for {
                    _ <- ZIO.acquireRelease(ZIO.unit)(_ => order.update(_ :+ "outer"))
                    _ <- ZIO.scoped {
                           ZIO.acquireRelease(ZIO.unit)(_ => order.update(_ :+ "inner"))
                         }
                  } yield ()
                }
              }
        _      <- app.invoke(Chunk.empty)
        result <- order.get
      } yield assertTrue(result == List("inner", "outer"))
    },
    test("finalizer on interrupted effect runs") {
      for {
        running <- Promise.make[Nothing, Unit]
        ref     <- Ref.make(false)
        app = new ZIOAppDefault {
                val run = ZIO.scoped {
                  ZIO.acquireRelease(running.succeed(()))(_ => ref.set(true)) *> ZIO.never
                }
              }
        fiber     <- app.invoke(Chunk.empty).fork
        _         <- running.await
        _         <- fiber.interrupt
        finalized <- ref.get
      } yield assertTrue(finalized)
    }
  )

  // ---------------------------------------------------------------------------
  // App composition
  // ---------------------------------------------------------------------------

  private val compositionSuite = suite("app composition")(
    test("composed apps run both effects") {
      for {
        ref <- Ref.make(List.empty[String])
        app1 = ZIOApp.fromZIO(ref.update(_ :+ "first"))
        app2 = ZIOApp.fromZIO(ref.update(_ :+ "second"))
        _      <- (app1 <> app2).invoke(Chunk.empty)
        result <- ref.get
      } yield assertTrue(result.contains("first")) &&
        assertTrue(result.contains("second"))
    },
    test("composed app fails if either side fails") {
      val app1 = ZIOApp.fromZIO(ZIO.fail("oops"))
      val app2 = ZIOApp.fromZIO(ZIO.succeed(42))
      for {
        code <- (app1 <> app2).invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
      } yield assertTrue(code == ExitCode.failure)
    }
  )

  // ---------------------------------------------------------------------------
  // Args threading
  // ---------------------------------------------------------------------------

  private val argsSuite = suite("args threading")(
    test("ZIOAppArgs are accessible from run") {
      for {
        ref <- Ref.make(Chunk.empty[String])
        app = new ZIOAppDefault {
                val run = getArgs.flatMap(args => ref.set(args))
              }
        _    <- app.invoke(Chunk("foo", "bar"))
        args <- ref.get
      } yield assertTrue(args == Chunk("foo", "bar"))
    },
    test("empty args work correctly") {
      for {
        ref <- Ref.make(Chunk("placeholder"))
        app = new ZIOAppDefault {
                val run = getArgs.flatMap(args => ref.set(args))
              }
        _    <- app.invoke(Chunk.empty)
        args <- ref.get
      } yield assertTrue(args.isEmpty)
    }
  )

  // ---------------------------------------------------------------------------
  // Error handling
  // ---------------------------------------------------------------------------

  private val errorHandlingSuite = suite("error handling")(
    test("die in run emits failure exit code") {
      val app = ZIOApp.fromZIO(ZIO.die(new RuntimeException("fatal")))
      for {
        code <- app.invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
      } yield assertTrue(code == ExitCode.failure)
    },
    test("interruption in run results in failure exit code") {
      val app = ZIOApp.fromZIO(ZIO.interrupt)
      for {
        code <- app.invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
      } yield assertTrue(code == ExitCode.failure)
    }
  )
}
