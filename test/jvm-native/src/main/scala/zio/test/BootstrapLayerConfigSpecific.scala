package zio.test

import zio._
import zio.internal.stacktracer.Tracer

/**
 * Bootstrap layer that supports executor override via system property.
 *
 * Reads the `zio.test.executor` system property to determine which executor to
 * use.
 */
private[test] object BootstrapLayerConfigSpecific {

  def make: ZLayer[Any, Any, TestEnvironment] =
    TestExecutorConfig.fromSystemProperty match {
      case Some(TestExecutorConfig.ExecutorType.Default) => {
        implicit val trace: Trace = Tracer.newTrace
        val executor = Unsafe.unsafe(implicit unsafe => Executor.makeDefault(true))
        TestRuntime.setDefaultExecutor(executor)
        testEnvironment
      }
      case Some(TestExecutorConfig.ExecutorType.ZScheduler) | None => {
        implicit val trace: Trace = Tracer.newTrace
        val executor = Unsafe.unsafe(implicit unsafe => Executor.makeDefault(false))
        TestRuntime.setDefaultExecutor(executor)
        testEnvironment
      }
      case Some(TestExecutorConfig.ExecutorType.NIO) => {
        implicit val trace: Trace = Tracer.newTrace
        val nioExecutorLayer = ZLayer.scoped {
          zio.internal.NIOExecutor.live.build.map(_.get).flatMap { executor =>
            ZIO.addFinalizer(ZIO.succeedBlocking(executor.shutdown())).as(executor)
          }
        }
        val nioBootstrap = nioExecutorLayer.flatMap { env =>
          val executor = env.get
          TestRuntime.setDefaultExecutor(executor)
          val dependencies: ZLayer[Any, Nothing, Clock with Console with System with Random] =
            (ZLayer.succeed(executor) >>> zio.NIOClock.live) ++
              ZLayer.succeed(Console.ConsoleLive) ++
              ZLayer.succeed(System.SystemLive) ++
              ZLayer.succeed(Random.RandomLive)
          dependencies >>> TestEnvironment.live
        }
        nioBootstrap
      }
    }
}
