package zio.internal

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio._
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit

/**
 * Benchmarks comparing NioScheduler (Least-Loaded) vs ZScheduler
 * (Work-Stealing).
 *
 * The NioScheduler uses a least-loaded scheduling algorithm that assigns new
 * tasks to the worker with the least workload, while the ZScheduler uses
 * work-stealing where idle workers steal tasks from busy workers.
 *
 * These benchmarks help identify which scheduler performs better for different
 * workload patterns.
 */
@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(value = 3)
class NioSchedulerBenchmarks {

  val zScheduler: zio.Executor   = zio.Executor.makeDefault()
  val nioScheduler: zio.Executor = zio.Executor.makeNio()

  // ===== Chained Fork Benchmark =====
  // Tests the overhead of forking fibers in a chain pattern

  @Benchmark
  def zioSchedulerChainedFork(): Int =
    zioChainedFork(zScheduler)

  @Benchmark
  def nioSchedulerChainedFork(): Int =
    zioChainedFork(nioScheduler)

  // ===== Fork Many Benchmark =====
  // Tests the throughput of forking many fibers concurrently

  @Benchmark
  def zioSchedulerForkMany(): Int =
    zioForkMany(zScheduler)

  @Benchmark
  def nioSchedulerForkMany(): Int =
    zioForkMany(nioScheduler)

  // ===== Ping Pong Benchmark =====
  // Tests message passing between fibers via queues

  @Benchmark
  def zioSchedulerPingPong(): Int =
    zioPingPong(zScheduler)

  @Benchmark
  def nioSchedulerPingPong(): Int =
    zioPingPong(nioScheduler)

  // ===== Yield Many Benchmark =====
  // Tests cooperative yielding between fibers

  @Benchmark
  def zioSchedulerYieldMany(): Int =
    zioYieldMany(zScheduler)

  @Benchmark
  def nioSchedulerYieldMany(): Int =
    zioYieldMany(nioScheduler)

  // ===== Parallel Map Benchmark =====
  // Tests parallel collection processing

  @Benchmark
  def zioSchedulerParallelMap(): Int =
    zioParallelMap(zScheduler)

  @Benchmark
  def nioSchedulerParallelMap(): Int =
    zioParallelMap(nioScheduler)

  // ===== Helper Methods =====

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

  def zioParallelMap(executor: zio.Executor): Int = {
    val io = for {
      result <- ZIO.foreachPar(1 to 1000)(n => ZIO.succeed(n * 2))
    } yield result.sum

    unsafeRun(io.onExecutor(executor))
  }
}
