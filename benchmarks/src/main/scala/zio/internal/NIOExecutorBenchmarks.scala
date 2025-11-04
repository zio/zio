package zio.internal

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio._
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(value = 3)
class NIOExecutorBenchmarks {

  final def fixedThreadPoolExecutor(): zio.Executor =
    zio.Executor.fromThreadPoolExecutor {
      val corePoolSize  = java.lang.Runtime.getRuntime.availableProcessors() * 2
      val maxPoolSize   = corePoolSize
      val keepAliveTime = 60000L
      val timeUnit      = TimeUnit.MILLISECONDS
      val workQueue     = new java.util.concurrent.LinkedBlockingQueue[Runnable]()
      val threadFactory = new NamedThreadFactory("zio-fixed-pool", true)

      val threadPool = new java.util.concurrent.ThreadPoolExecutor(
        corePoolSize,
        maxPoolSize,
        keepAliveTime,
        timeUnit,
        workQueue,
        threadFactory
      )
      threadPool.allowCoreThreadTimeOut(true)

      threadPool
    }

  val fixedThreadPool: zio.Executor = fixedThreadPoolExecutor()
  val zScheduler: zio.Executor      = zio.Executor.makeDefault()
  val nioExecutor: zio.Executor     = new NIOExecutor(NIOExecutor.NIOExecutorConfig.default)

  @Benchmark
  def zioFixedThreadPoolChainedFork(): Int =
    zioChainedFork(fixedThreadPool)

  @Benchmark
  def zioFixedThreadPoolForkMany(): Int =
    zioForkMany(fixedThreadPool)

  @Benchmark
  def zioFixedThreadPoolPingPong(): Int =
    zioPingPong(fixedThreadPool)

  @Benchmark
  def zioFixedThreadPoolYieldMany(): Int =
    zioYieldMany(fixedThreadPool)

  @Benchmark
  def zioSchedulerChainedFork(): Int =
    zioChainedFork(zScheduler)

  @Benchmark
  def zioSchedulerForkMany(): Int =
    zioForkMany(zScheduler)

  @Benchmark
  def zioSchedulerPingPong(): Int =
    zioPingPong(zScheduler)

  @Benchmark
  def zioSchedulerYieldMany(): Int =
    zioYieldMany(zScheduler)

  @Benchmark
  def nioExecutorChainedFork(): Int =
    zioChainedFork(nioExecutor)

  @Benchmark
  def nioExecutorForkMany(): Int =
    zioForkMany(nioExecutor)

  @Benchmark
  def nioExecutorPingPong(): Int =
    zioPingPong(nioExecutor)

  @Benchmark
  def nioExecutorYieldMany(): Int =
    zioYieldMany(nioExecutor)

  def zioChainedFork(executor: zio.Executor): Int = {
    def iterate(promise: Promise[Nothing, Unit], n: Int): UIO[Any] =
      if (n <= 0) promise.succeed(())
      else ZIO.unit.flatMap(_ => iterate(promise, n - 1).forkDaemon)

    val io = for {
      promise <- Promise.make[Nothing, Unit]
      _       <- iterate(promise, 1000).forkDaemon
      _       <- promise.await
    } yield 0

    unsafeRun(io.onExecutor(executor))
  }

  def zioForkMany(executor: zio.Executor): Int = {
    val io = for {
      promise <- Promise.make[Nothing, Unit]
      ref     <- Ref.make(10000)
      effect   = ref.modify(n => (if (n == 1) promise.succeed(()) else ZIO.unit, n - 1)).flatten
      _       <- repeat(10000)(effect.forkDaemon)
      _       <- promise.await
    } yield 0

    unsafeRun(io.onExecutor(executor))
  }

  def zioPingPong(executor: zio.Executor): Int = {
    def iterate(promise: Promise[Nothing, Unit], n: Int): UIO[Any] =
      for {
        ref   <- Ref.make(n)
        queue <- Queue.bounded[Unit](1)
        effect = queue.offer(()).forkDaemon *>
                   queue.take *>
                   ref.modify(n => (if (n == 1) promise.succeed(()) else ZIO.unit, n - 1)).flatten
        _ <- repeat(1000)(effect.forkDaemon)
      } yield ()

    val io = for {
      promise <- Promise.make[Nothing, Unit]
      _       <- iterate(promise, 1000).forkDaemon
      _       <- promise.await
    } yield 0

    unsafeRun(io.onExecutor(executor))
  }

  def zioYieldMany(executor: zio.Executor): Int = {
    val io = for {
      promise <- Promise.make[Nothing, Unit]
      ref     <- Ref.make(200)
      effect =
        repeat(1000)(ZIO.yieldNow) *> ref.modify(n => (if (n == 1) promise.succeed(()) else ZIO.unit, n - 1)).flatten
      _ <- repeat(200)(effect.forkDaemon)
      _ <- promise.await
    } yield 0

    unsafeRun(io.onExecutor(executor))
  }
}
