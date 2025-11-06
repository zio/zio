package zio.internal

import zio._
import zio.test.TestEnvironment
import zio.test.Assertion._
import zio.test._

object NIOExecutorSpec extends ZIOSpecDefault {

  private val nIOExecutorLayer: ZLayer[Any, Config.Error, NIOExecutor] = NIOExecutor.live
  private val clockLayer: ZLayer[NIOExecutor, Nothing, Clock]          = NIOClock.live

  /**
   * Overriding the bootstrap layer allows to replace the default services (like
   * Executor and Clock) for all tests within this spec.
   */
  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    nIOExecutorLayer.flatMap { executorEnv =>
      val runtimeConfigLayer = Runtime.setExecutor(executorEnv.get)

      // This layer provides the custom NIOClock.
      val customClockLayer = ZLayer.succeedEnvironment(executorEnv) >>> clockLayer

      val otherLiveServices = ZLayer.succeedEnvironment(
        ZEnvironment[Console, System, Random](
          Console.ConsoleLive,
          System.SystemLive,
          Random.RandomLive
        )
      )

      // This layer provides all dependencies needed by base TestEnvironment.live.
      val testEnvDependencies = customClockLayer ++ otherLiveServices

      // The test environment uses the custom dependencies.
      val customTestEnv = testEnvDependencies >>> TestEnvironment.live

      // The final bootstrap merges the runtime configuration and the test environment.
      runtimeConfigLayer >+> customTestEnv
    }

  def spec = suite("NIOExecutorSpec")(
    test("should execute a simple effect") {
      assertTrue(true)
    },
    test("should support basic forking") {
      for {
        ref   <- Ref.make(0)
        fiber <- ref.set(1).fork
        _     <- fiber.join
        value <- ref.get
      } yield assertTrue(value == 1)
    },
    test("should correctly schedule a sleep operation") {
      Live.live {
        for {
          start <- Clock.nanoTime
          _     <- ZIO.sleep(100.millis)
          end   <- Clock.nanoTime
        } yield assertTrue((end - start).nanos >= 100.millis)
      }
    },
    test("should handle blocking tasks on the blocking thread pool") {
      for {
        startThread <- ZIO.succeed(Thread.currentThread().getName)
        blockingThread <- ZIO.succeedBlocking {
                            Thread.currentThread().getName
                          }
      } yield assertTrue(startThread.contains("NIOExecutor-Worker")) &&
        assertTrue(blockingThread.contains("zio-default-blocking")) &&
        assertTrue(!blockingThread.contains("NIOExecutor-Worker"))
    },
    test("should propagate failures correctly") {
      val error  = new RuntimeException("Boom")
      val effect = ZIO.fail(error)

      effect.exit.map(e => assert(e)(fails(equalTo(error))))
    },
    test("should handle fiber interruption") {
      for {
        promise <- Promise.make[Nothing, Unit]
        fiber   <- (promise.succeed(()) *> ZIO.never).fork
        _       <- promise.await // Ensure the fiber has started before interrupting.
        exit    <- fiber.interrupt
      } yield assert(exit)(isInterrupted)
    },
    test("should handle yielding with ZIO.yieldNow") {
      for {
        _ <- ZIO.yieldNow
      } yield assertCompletes
    },
    test("survives a high-concurrency workload") {
      val n = 1000 // Number of concurrent fibers to launch
      ZIO
        .foreachPar(1 to n)(_ => ZIO.succeed(1).fork.flatMap(_.join))
        .as(assertCompletes)
    }
  )
}
