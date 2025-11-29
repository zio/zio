package zio.internal

import zio._
import zio.test.Assertion._
import zio.test._
import zio.NIOClock

object NIOExecutorSpec extends ZIOSpec[TestEnvironment with NIOExecutor] {

  private val nIOExecutorLayer: ZLayer[Any, Config.Error, NIOExecutor] =
    ZLayer.scoped {
      NIOExecutor.live.build.map(_.get).flatMap { executor =>
        ZIO.addFinalizer(ZIO.succeedBlocking(executor.shutdown())).as(executor)
      }
    }

  /**
   * Overriding the bootstrap layer allows to replace the default services for
   * all tests within this spec.
   */
  override val bootstrap: ZLayer[Any, Any, TestEnvironment with NIOExecutor] =
    nIOExecutorLayer.flatMap { env =>
      val executor = env.get

      // This layer will provide the NIOClock and standard live services.
      val dependencies: ZLayer[Any, Nothing, NIOExecutor with Clock with Console with System with Random] =
        ZLayer.succeed(executor) ++
          (ZLayer.succeed(executor) >>> NIOClock.live) ++
          ZLayer.succeed(Console.ConsoleLive) ++
          ZLayer.succeed(System.SystemLive) ++
          ZLayer.succeed(Random.RandomLive)

      dependencies >>> (TestEnvironment.live ++ ZLayer.environment[NIOExecutor])
    }

  def spec = suite("NIOExecutorSpec")(
    test("should execute a simple effect") {
      ZIO.serviceWithZIO[NIOExecutor](executor => ZIO.succeed(assertTrue(true)).onExecutor(executor))
    },
    test("should support basic forking") {
      ZIO.serviceWithZIO[NIOExecutor] { executor =>
        (for {
          ref   <- Ref.make(0)
          fiber <- ref.set(1).fork
          _     <- fiber.join
          value <- ref.get
        } yield assertTrue(value == 1)).onExecutor(executor)
      }
    },
    test("should correctly schedule a sleep operation") {
      ZIO.serviceWithZIO[NIOExecutor] { executor =>
        Live.live {
          (for {
            start <- Clock.nanoTime
            _     <- ZIO.sleep(100.millis)
            end   <- Clock.nanoTime
          } yield assertTrue((end - start).nanos >= 100.millis)).onExecutor(executor)
        }
      }
    },
    test("should handle blocking tasks on the blocking thread pool") {
      ZIO.serviceWithZIO[NIOExecutor] { executor =>
        (for {
          startThread <- ZIO.succeed(Thread.currentThread().getName)
          blockingThread <- ZIO.succeedBlocking {
                              Thread.currentThread().getName
                            }
        } yield assertTrue(startThread.contains("NIOExecutor-Worker")) &&
          assertTrue(blockingThread.contains("zio-default-blocking")) &&
          assertTrue(!blockingThread.contains("NIOExecutor-Worker"))).onExecutor(executor)
      }
    },
    test("should propagate failures correctly") {
      ZIO.serviceWithZIO[NIOExecutor] { executor =>
        val error  = new RuntimeException("Boom")
        val effect = ZIO.fail(error)

        effect.exit.map(e => assert(e)(fails(equalTo(error)))).onExecutor(executor)
      }
    },
    test("should handle fiber interruption") {
      ZIO.serviceWithZIO[NIOExecutor] { executor =>
        (for {
          promise <- Promise.make[Nothing, Unit]
          fiber   <- (promise.succeed(()) *> ZIO.never).fork
          _       <- promise.await // Ensure the fiber has started before interrupting.
          exit    <- fiber.interrupt
        } yield assert(exit)(isInterrupted)).onExecutor(executor)
      }
    },
    test("should handle yielding with ZIO.yieldNow") {
      ZIO.serviceWithZIO[NIOExecutor] { executor =>
        (for {
          _ <- ZIO.yieldNow
        } yield assertCompletes).onExecutor(executor)
      }
    },
    test("survives a high-concurrency workload") {
      ZIO.serviceWithZIO[NIOExecutor] { executor =>
        val n = 1000 // Number of concurrent fibers to launch
        ZIO
          .foreachPar(1 to n)(_ => ZIO.succeed(1).fork.flatMap(_.join))
          .as(assertCompletes)
          .onExecutor(executor)
      }
    }
  )

}
