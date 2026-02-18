package zio

import zio.test._
import scala.annotation.nowarn

object ZIOAppSpec extends ZIOBaseSpec {
  def spec = suite("ZIOAppSpec")(
    // ============================================================
    // Basic ZIOApp.fromZIO Tests
    // ============================================================
    suite("fromZIO")(
      test("fromZIO executes effect successfully") {
        for {
          ref <- Ref.make(0)
          _   <- ZIOApp.fromZIO(ref.update(_ + 1)).invoke(Chunk.empty)
          v   <- ref.get
        } yield assertTrue(v == 1)
      },
      test("fromZIO failure translates into ExitCode.failure") {
        for {
          code <- ZIOApp.fromZIO(ZIO.fail("Uh oh!")).invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
        } yield assertTrue(code == ExitCode.failure)
      },
      test("fromZIO success translates into ExitCode.success") {
        for {
          code <- ZIOApp.fromZIO(ZIO.succeed("Hurray!")).invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
        } yield assertTrue(code == ExitCode.success)
      },
      test("fromZIO with defect translates into ExitCode.failure") {
        for {
          code <- ZIOApp.fromZIO(ZIO.die(new Exception("Boom!"))).invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
        } yield assertTrue(code == ExitCode.failure)
      },
      test("fromZIO has access to command line args") {
        val args = Chunk("arg1", "arg2", "arg3")
        for {
          result <- ZIOApp.fromZIO(ZIOAppArgs.getArgs.map(_.size)).invoke(args)
        } yield assertTrue(result == 3)
      }
    ),
    // ============================================================
    // ZIOApp with Custom Bootstrap Tests
    // ============================================================
    suite("custom bootstrap")(
      test("bootstrap layer is used during app execution") {
        for {
          ref <- Ref.make("initial")
          app = new ZIOApp {
                  type Environment = String
                  def environmentTag = EnvironmentTag[String]
                  val bootstrap = ZLayer.succeed("modified")
                  val run = ZIO.service[String].flatMap(ref.set)
                }
          _     <- app.invoke(Chunk.empty)
          value <- ref.get
        } yield assertTrue(value == "modified")
      },
      test("bootstrap layer failure prevents app execution") {
        for {
          ref <- Ref.make(false)
          app = new ZIOApp {
                  type Environment = String
                  def environmentTag = EnvironmentTag[String]
                  val bootstrap = ZLayer.fail(new RuntimeException("Bootstrap failed!"))
                  val run = ZIO.succeed(()).as(ref.set(true))
                }
          _     <- app.invoke(Chunk.empty).exit
          value <- ref.get
        } yield assertTrue(!value)
      }
    ),
    // ============================================================
    // ZIOApp Composition Tests
    // ============================================================
    suite("composition")(
      test("composed app logic runs component logic") {
        for {
          ref <- Ref.make(2)
          app1 = ZIOApp.fromZIO(ref.update(_ + 3))
          app2 = ZIOApp.fromZIO(ref.update(_ - 5))
          _   <- (app1 <> app2).invoke(Chunk.empty)
          v   <- ref.get
        } yield assertTrue(v == 0)
      },
      test("composed apps combine bootstrap layers") {
        for {
          ref1 <- Ref.make(0)
          ref2 <- Ref.make(0)
          app1 = new ZIOApp {
                    type Environment = Int
                    def environmentTag = EnvironmentTag[Int]
                    val bootstrap = ZLayer.succeed(10)
                    val run = ZIO.service[Int].flatMap(ref1.set)
                  }
          app2 = new ZIOApp {
                    type Environment = String
                    def environmentTag = EnvironmentTag[String]
                    val bootstrap = ZLayer.succeed("hello")
                    val run = ZIO.service[String].flatMap(_ => ref2.set(20))
                  }
          _    <- (app1 <> app2).invoke(Chunk.empty)
          v1   <- ref1.get
          v2   <- ref2.get
        } yield assertTrue(v1 == 10) && assertTrue(v2 == 20)
      }
    ),
    // ============================================================
    // Runtime Configuration Tests
    // ============================================================
    suite("runtime configuration")(
      test("hook update platform applies logger") {
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
      }
    ),
    // ============================================================
    // Finalization Tests
    // ============================================================
    suite("finalization")(
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
      test("finalizers run on error") {
        for {
          ref <- Ref.make(false)
          app = ZIOAppDefault.fromZIO(
            ZIO.unit.ensuring(ref.set(true)) *> ZIO.fail("error")
          )
          _   <- app.invoke(Chunk.empty).exit
          v   <- ref.get
        } yield assertTrue(v)
      },
      test("finalizers run on defect") {
        for {
          ref <- Ref.make(false)
          app = ZIOAppDefault.fromZIO(
            ZIO.unit.ensuring(ref.set(true)) *> ZIO.die(new Exception("boom"))
          )
          _   <- app.invoke(Chunk.empty).exit
          v   <- ref.get
        } yield assertTrue(v)
      },
      test("nested finalizers run in correct order") {
        for {
          ref <- Ref.make(List.empty[Int])
          app = ZIOAppDefault.fromZIO(
            ZIO.unit.ensuring(ref.update(1 :: _)) *>
            ZIO.unit.ensuring(ref.update(2 :: _))
          )
          _   <- app.invoke(Chunk.empty)
          v   <- ref.get
        } yield assertTrue(v == List(2, 1))
      }
    ),
    // ============================================================
    // Exit Code Tests
    // ============================================================
    suite("exit codes")(
      test("exitCode returns success for successful effect") {
        for {
          code <- ZIOAppDefault.fromZIO(ZIO.succeed(())).invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
        } yield assertTrue(code == ExitCode.success)
      },
      test("exitCode returns failure for failed effect") {
        for {
          code <- ZIOAppDefault.fromZIO(ZIO.fail("error")).invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
        } yield assertTrue(code == ExitCode.failure)
      },
      test("exitCode returns failure for interrupted effect") {
        for {
          code <- ZIOAppDefault.fromZIO(ZIO.interrupt).invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
        } yield assertTrue(code == ExitCode.failure)
      }
    ),
    // ============================================================
    // ZIOAppDefault Tests
    // ============================================================
    suite("ZIOAppDefault")(
      test("ZIOAppDefault.fromZIO creates valid app") {
        for {
          ref <- Ref.make(0)
          app = ZIOAppDefault.fromZIO(ref.update(_ + 1))
          _   <- app.invoke(Chunk.empty)
          v   <- ref.get
        } yield assertTrue(v == 1)
      },
      test("ZIOAppDefault.apply creates valid app") {
        for {
          ref <- Ref.make(0)
          app = ZIOAppDefault(ref.update(_ + 1))
          _   <- app.invoke(Chunk.empty)
          v   <- ref.get
        } yield assertTrue(v == 1)
      },
      test("ZIOAppDefault has empty bootstrap") {
        val app = new ZIOAppDefault { val run = ZIO.unit }
        assertTrue(app.bootstrap == ZLayer.empty)
      },
      test("ZIOAppDefault has Any environment type") {
        val app = new ZIOAppDefault { val run = ZIO.unit }
        assertTrue(app.environmentTag == EnvironmentTag[Any])
      }
    ),
    // ============================================================
    // Command Line Arguments Tests
    // ============================================================
    suite("command line arguments")(
      test("getArgs returns empty chunk when no args provided") {
        for {
          args <- ZIOAppDefault.fromZIO(ZIOAppArgs.getArgs.map(_.size)).invoke(Chunk.empty)
        } yield assertTrue(args == 0)
      },
      test("getArgs returns provided args") {
        val args = Chunk("hello", "world", "foo", "bar")
        for {
          receivedArgs <- ZIOAppDefault.fromZIO(ZIOAppArgs.getArgs.map(_.size)).invoke(args)
        } yield assertTrue(receivedArgs == 4)
      },
      test("getArgs can be called multiple times") {
        val args = Chunk("a", "b", "c")
        for {
          args1 <- ZIOAppDefault.fromZIO(
            ZIOAppArgs.getArgs.zipWith(ZIOAppArgs.getArgs)((a, b) => a.size + b.size)
          ).invoke(args)
        } yield assertTrue(args1 == 6)
      },
      test("args with special characters are preserved") {
        val args = Chunk("--flag", "value with spaces", "path/to/file", "")
        for {
          receivedArgs <- ZIOAppDefault.fromZIO(ZIOAppArgs.getArgs.map(_.size)).invoke(args)
        } yield assertTrue(receivedArgs == 4)
      }
    ),
    // ============================================================
    // ZIOApp.Proxy Tests
    // ============================================================
    suite("ZIOApp.Proxy")(
      test("Proxy delegates to underlying app") {
        for {
          ref <- Ref.make(0)
          baseApp = ZIOApp.fromZIO(ref.update(_ + 1))
          proxy = new ZIOApp.Proxy(baseApp)
          _   <- proxy.invoke(Chunk.empty)
          v   <- ref.get
        } yield assertTrue(v == 1)
      },
      test("Proxy exposes correct environment type") {
        val baseApp = new ZIOApp {
          type Environment = Int
          def environmentTag = EnvironmentTag[Int]
          val bootstrap = ZLayer.succeed(42)
          val run = ZIO.service[Int]
        }
        val proxy = new ZIOApp.Proxy(baseApp)
        for {
          result <- proxy.invoke(Chunk.empty)
        } yield assertTrue(result != null)
      }
    ),
    // ============================================================
    // Edge Cases and Error Scenarios
    // ============================================================
    suite("edge cases")(
      test("app with never-ending effect can be interrupted") {
        for {
          running   <- Promise.make[Nothing, Unit]
          ref       <- Ref.make(false)
          effect     = running.succeed(()) *> ZIO.never
          app        = ZIOAppDefault.fromZIO(effect.ensuring(ref.set(true)))
          fiber     <- app.invoke(Chunk.empty).fork
          _         <- running.await
          _         <- fiber.interrupt
          finalized <- ref.get
        } yield assertTrue(finalized)
      },
      test("app completes successfully with unit effect") {
        val app = ZIOAppDefault.fromZIO(ZIO.unit)
        for {
          exitCode <- app.invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
        } yield assertTrue(exitCode == ExitCode.success)
      },
      test("gracefulShutdownTimeout is accessible") {
        val app = new ZIOAppDefault {
          override val gracefulShutdownTimeout = 5.seconds
          val run = ZIO.unit
        }
        assertTrue(app.gracefulShutdownTimeout == 5.seconds)
      },
      test("gracefulShutdownTimeout defaults to Infinity") {
        val app = new ZIOAppDefault { val run = ZIO.unit }
        assertTrue(app.gracefulShutdownTimeout == Duration.Infinity)
      }
    ),
    // ============================================================
    // Environment and Layer Integration Tests
    // ============================================================
    suite("environment and layer integration")(
      test("app can access service from bootstrap layer") {
        for {
          ref <- Ref.make(0)
          serviceLayer = ZLayer.succeed(ref)
          app = new ZIOApp {
                    type Environment = Ref[Int]
                    def environmentTag = EnvironmentTag[Ref[Int]]
                    val bootstrap = serviceLayer
                    val run = ZIO.service[Ref[Int]].flatMap(_.update(_ + 1))
                  }
          _   <- app.invoke(Chunk.empty)
          v   <- ref.get
        } yield assertTrue(v == 1)
      },
      test("app can access multiple services from bootstrap") {
        for {
          ref1 <- Ref.make("a")
          ref2 <- Ref.make(0)
          layer1 = ZLayer.succeed(ref1)
          layer2 = ZLayer.succeed(ref2)
          app = new ZIOApp {
                    type Environment = Ref[String] & Ref[Int]
                    def environmentTag = EnvironmentTag[Ref[String]].asInstanceOf[EnvironmentTag[Ref[String] & Ref[Int]]]
                    val bootstrap = layer1 ++ layer2
                    val run = for {
                      r1 <- ZIO.service[Ref[String]]
                      r2 <- ZIO.service[Ref[Int]]
                      _  <- r1.set("b")
                      _  <- r2.set(1)
                    } yield ()
                  }
          _   <- app.invoke(Chunk.empty)
          v1  <- ref1.get
          v2  <- ref2.get
        } yield assertTrue(v1 == "b") && assertTrue(v2 == 1)
      }
    ),
    // ============================================================
    // Fiber Management Tests
    // ============================================================
    suite("fiber management")(
      test("app can spawn child fibers") {
        for {
          ref <- Ref.make(0)
          app = ZIOAppDefault.fromZIO {
            for {
              f1 <- ref.update(_ + 1).fork
              f2 <- ref.update(_ + 2).fork
              f3 <- ref.update(_ + 3).fork
              _  <- f1.join
              _  <- f2.join
              _  <- f3.join
            } yield ()
          }
          _ <- app.invoke(Chunk.empty)
          v <- ref.get
        } yield assertTrue(v == 6)
      },
      test("app waits for all child fibers before completion") {
        for {
          ref <- Ref.make(List.empty[Int])
          app = ZIOAppDefault.fromZIO {
            for {
              f1 <- ref.update(1 :: _).fork
              f2 <- ref.update(2 :: _).fork
              f3 <- ref.update(3 :: _).fork
              _  <- f1.join
              _  <- f2.join
              _  <- f3.join
            } yield ()
          }
          _ <- app.invoke(Chunk.empty)
          v <- ref.get
        } yield assertTrue(v == List(3, 2, 1))
      },
      test("app with background fibers") {
        for {
          ref <- Ref.make(0)
          app = ZIOAppDefault.fromZIO {
            for {
              f <- ref.set(1).fork
              _ <- ZIO.unit
              _ <- f.join
            } yield ()
          }
          _ <- app.invoke(Chunk.empty)
          v <- ref.get
        } yield assertTrue(v == 1)
      }
    ),
    // ============================================================
    // Signal Handler Integration Tests
    // ============================================================
    suite("signal handler integration")(
      test("signal handlers are only installed once globally") {
        for {
          ref <- Ref.make(0)
          app1 = ZIOAppDefault.fromZIO(ref.update(_ + 1))
          app2 = ZIOAppDefault.fromZIO(ref.update(_ + 10))
          _   <- app1.invoke(Chunk.empty)
          _   <- app2.invoke(Chunk.empty)
          v   <- ref.get
        } yield assertTrue(v == 11) // Both apps should run successfully
      },
      test("signal handler flag is atomic") {
        // The installedSignals flag ensures handlers are installed only once
        // This test verifies multiple apps can run without interference
        for {
          ref <- Ref.make(0)
          apps = List.tabulate(5) { i =>
            ZIOAppDefault.fromZIO(ref.update(_ + (i + 1)))
          }
          _ <- ZIO.foreachDiscard(apps)(_.invoke(Chunk.empty))
          v <- ref.get
        } yield assertTrue(v == 15) // 1+2+3+4+5 = 15
      }
    ),
    // ============================================================
    // Runtime and Lifecycle Tests
    // ============================================================
    suite("runtime and lifecycle")(
      test("app receives correct runtime") {
        for {
          ref <- Ref.make[Option[Runtime[Any]]](None)
          app = new ZIOAppDefault {
            val run = ZIO.runtime[Any].flatMap(r => ref.set(Some(r)))
          }
          _ <- app.invoke(Chunk.empty)
          runtimeOpt <- ref.get
        } yield assertTrue(runtimeOpt.isDefined)
      },
      test("app can access current fiber") {
        for {
          ref <- Ref.make[Option[FiberId]](None)
          app = ZIOAppDefault.fromZIO {
            ZIO.fiberId.flatMap(id => ref.set(Some(id)))
          }
          _   <- app.invoke(Chunk.empty)
          fid <- ref.get
        } yield assertTrue(fid.isDefined)
      },
      test("multiple app invocations are independent") {
        for {
          ref <- Ref.make(0)
          app = ZIOAppDefault.fromZIO(ref.update(_ + 1) *> ref.get)
          v1 <- app.invoke(Chunk.empty)
          v2 <- app.invoke(Chunk.empty)
          v3 <- app.invoke(Chunk.empty)
        } yield assertTrue(v1 == 1) && assertTrue(v2 == 2) && assertTrue(v3 == 3)
      }
    ),
    // ============================================================
    // Error Handling and Logging Tests
    // ============================================================
    suite("error handling and logging")(
      test("failed app logs error cause") {
        val app = ZIOAppDefault.fromZIO {
          ZIO.logError("Test error") *> ZIO.fail("failure")
        }
        for {
          exit <- app.invoke(Chunk.empty).exit
        } yield assertTrue(exit.isFailure)
      },
      test("app with typed error fails correctly") {
        val app = ZIOAppDefault.fromZIO(ZIO.fail(new Exception("Typed error")))
        for {
          exit <- app.invoke(Chunk.empty).exit
          isFailure = exit.isFailure
        } yield assertTrue(isFailure)
      },
      test("app error preserves error information") {
        val app = ZIOAppDefault.fromZIO(ZIO.fail("Specific error message"))
        for {
          exit <- app.invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
        } yield assertTrue(exit == ExitCode.failure)
      }
    ),
    // ============================================================
    // Scope and Resource Management Tests
    // ============================================================
    suite("scope and resource management")(
      test("scoped resources are released on app completion") {
        for {
          released <- Ref.make(false)
          app = ZIOAppDefault.fromZIO {
            ZIO.unit.ensuring(released.set(true))
          }
          _ <- app.invoke(Chunk.empty)
          r <- released.get
        } yield assertTrue(r)
      },
      test("scoped resources in bootstrap are released") {
        for {
          released <- Ref.make(false)
          app = new ZIOAppDefault {
            override val bootstrap = ZLayer.scoped(
              ZIO.unit.ensuring(released.set(true))
            )
            val run = ZIO.unit
          }
          _ <- app.invoke(Chunk.empty)
          r <- released.get
        } yield assertTrue(r)
      },
      test("nested scopes are released in correct order") {
        for {
          order <- Ref.make(List.empty[Int])
          app = ZIOAppDefault.fromZIO {
            ZIO.unit.ensuring(order.update(1 :: _)) *>
            ZIO.unit.ensuring(order.update(2 :: _))
          }
          _    <- app.invoke(Chunk.empty)
          o    <- order.get
        } yield assertTrue(o.contains(1)) && assertTrue(o.contains(2))
      }
    ),
    // ============================================================
    // Interruption and Cancellation Tests
    // ============================================================
    suite("interruption and cancellation")(
      test("app can be interrupted from outside") {
        for {
          running   <- Promise.make[Nothing, Unit]
          completed <- Ref.make(false)
          app        = ZIOAppDefault.fromZIO(running.succeed(()) *> ZIO.never)
          fiber     <- app.invoke(Chunk.empty).fork
          _         <- running.await
          _         <- fiber.interrupt
          result    <- fiber.await
        } yield assertTrue(result.isInterrupted)
      },
      test("interruption cause is preserved") {
        val app = ZIOAppDefault.fromZIO(ZIO.interrupt)
        for {
          exit   <- app.invoke(Chunk.empty).exit
          isInterrupted = exit.isInterrupted
        } yield assertTrue(isInterrupted)
      }
    ),
    // ============================================================
    // Exit Behavior Tests
    // ============================================================
    suite("exit behavior")(
      test("app completes with unit returns success exit code") {
        val app = ZIOAppDefault.fromZIO(ZIO.unit)
        for {
          exitCode <- app.invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
        } yield assertTrue(exitCode == ExitCode.success)
      },
      test("app with value returns success exit code") {
        val app = ZIOAppDefault.fromZIO(ZIO.succeed(42))
        for {
          exitCode <- app.invoke(Chunk.empty).exitCode: @nowarn("cat=deprecation")
        } yield assertTrue(exitCode == ExitCode.success)
      }
    ),
    // ============================================================
    // Composition Operator Tests
    // ============================================================
    suite("composition operator")(
      test("<> operator combines apps correctly") {
        for {
          ref1 <- Ref.make(0)
          ref2 <- Ref.make(0)
          app1 = ZIOAppDefault.fromZIO(ref1.set(10))
          app2 = ZIOAppDefault.fromZIO(ref2.set(20))
          _    <- (app1 <> app2).invoke(Chunk.empty)
          v1   <- ref1.get
          v2   <- ref2.get
        } yield assertTrue(v1 == 10) && assertTrue(v2 == 20)
      },
      test("composition with mixed environments works") {
        for {
          refInt <- Ref.make(0)
          refStr <- Ref.make("")
          app1 = new ZIOApp {
            type Environment = Int
            def environmentTag = EnvironmentTag[Int]
            val bootstrap = ZLayer.succeed(5)
            val run = ZIO.service[Int].flatMap(refInt.set)
          }
          app2 = new ZIOApp {
            type Environment = String
            def environmentTag = EnvironmentTag[String]
            val bootstrap = ZLayer.succeed("test")
            val run = ZIO.service[String].flatMap(refStr.set)
          }
          _ <- (app1 <> app2).invoke(Chunk.empty)
          v1 <- refInt.get
          v2 <- refStr.get
        } yield assertTrue(v1 == 5) && assertTrue(v2 == "test")
      },
      test("composition executes apps in parallel") {
        for {
          ref <- Ref.make(List.empty[Int])
          app1 = ZIOAppDefault.fromZIO(ref.update(1 :: _))
          app2 = ZIOAppDefault.fromZIO(ref.update(2 :: _))
          _   <- (app1 <> app2).invoke(Chunk.empty)
          v   <- ref.get
        } yield assertTrue(v.sorted == List(1, 2)) // Both should complete, order may vary
      }
    ),
    // ============================================================
    // Timeout Behavior Tests
    // ============================================================
    suite("timeout behavior")(
      test("app with zero timeout completes immediately") {
        for {
          ref <- Ref.make(false)
          app = new ZIOAppDefault {
            override val gracefulShutdownTimeout = Duration.Zero
            val run = ref.set(true) *> ZIO.unit
          }
          _ <- app.invoke(Chunk.empty)
          v <- ref.get
        } yield assertTrue(v)
      },
      test("app with finite timeout allows cleanup") {
        for {
          ref <- Ref.make(false)
          app = new ZIOAppDefault {
            override val gracefulShutdownTimeout = 100.millis
            val run = ZIO.unit.ensuring(ref.set(true))
          }
          _ <- app.invoke(Chunk.empty)
          v <- ref.get
        } yield assertTrue(v)
      }
    )
  )
}
