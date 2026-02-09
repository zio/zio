package zio.internal

import org.openjdk.jmh.annotations.{Scope => JmhScope, _}
import org.openjdk.jmh.infra.Blackhole
import zio._
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit

/**
 * Benchmarks for issue #9878: ZScheduler parks+unparks workers too frequently
 *
 * These benchmarks measure the impact of reducing maybeUnparkWorker frequency
 * through batching and intelligent guards.
 */
@State(JmhScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(value = 3)
class UnparkFrequencyBenchmark {

  val scheduler: zio.Executor = zio.Executor.makeDefault()

  /**
   * Baseline: Many small tasks submitted rapidly This triggers
   * maybeUnparkWorker on every submit Measures throughput with current (fixed)
   * implementation
   */
  @Benchmark
  def rapidSubmitManySmallTasks(): Int = {
    val io = for {
      promise <- Promise.make[Nothing, Unit]
      ref     <- Ref.make(10000)
      effect   = ref.updateAndGet(_ - 1).flatMap(n => if (n == 0) promise.succeed(()) else ZIO.unit)
      _       <- repeat(10000)(effect.forkDaemon)
      _       <- promise.await
    } yield 0

    unsafeRun(io.onExecutor(scheduler))
  }

  /**
   * Stress test: Rapid fire submissions with yields Tests submitAndYield path
   * which also calls maybeUnparkWorker
   */
  @Benchmark
  def rapidSubmitWithYields(): Int = {
    val io = for {
      promise <- Promise.make[Nothing, Unit]
      ref     <- Ref.make(1000)
      effect = repeat(100)(ZIO.yieldNow) *>
                 ref.updateAndGet(_ - 1).flatMap(n => if (n == 0) promise.succeed(()) else ZIO.unit)
      _ <- repeat(1000)(effect.forkDaemon)
      _ <- promise.await
    } yield 0

    unsafeRun(io.onExecutor(scheduler))
  }

  /**
   * Burst scenario: Idle scheduler receives burst of work Tests cold start and
   * threshold behavior
   */
  @Benchmark
  def burstAfterIdle(): Int = {
    val io = for {
      // Let workers park
      _ <- ZIO.sleep(Duration.fromMillis(10))
      // Burst of work
      promise <- Promise.make[Nothing, Unit]
      ref     <- Ref.make(1000)
      effect   = ref.updateAndGet(_ - 1).flatMap(n => if (n == 0) promise.succeed(()) else ZIO.unit)
      _       <- repeat(1000)(effect.forkDaemon)
      _       <- promise.await
    } yield 0

    unsafeRun(io.onExecutor(scheduler))
  }

  /**
   * Mixed workload: Some workers busy, new work arriving Tests threshold logic
   * with partial worker utilization
   */
  @Benchmark
  def mixedWorkload(): Int = {
    val io = for {
      promise <- Promise.make[Nothing, Unit]
      ref     <- Ref.make(5000)
      // Some long-running tasks to keep workers busy
      _ <- repeat(poolSize / 2)(ZIO.sleep(Duration.fromMillis(5)).forkDaemon)
      // New work arriving
      effect = ref.updateAndGet(_ - 1).flatMap(n => if (n == 0) promise.succeed(()) else ZIO.unit)
      _     <- repeat(5000)(effect.forkDaemon)
      _     <- promise.await
    } yield 0

    unsafeRun(io.onExecutor(scheduler))
  }

  /**
   * Ping-pong: Heavy contention scenario Stress tests the scheduler with high
   * park/unpark potential
   */
  @Benchmark
  def pingPongContention(): Int = {
    val io = for {
      promise <- Promise.make[Nothing, Unit]
      ref     <- Ref.make(500)
      queue   <- Queue.bounded[Unit](1)
      effect = queue.offer(()).forkDaemon *>
                 queue.take *>
                 ref.updateAndGet(_ - 1).flatMap(n => if (n == 0) promise.succeed(()) else ZIO.unit)
      _ <- repeat(500)(effect.forkDaemon)
      _ <- promise.await
    } yield 0

    unsafeRun(io.onExecutor(scheduler))
  }

  private val poolSize = java.lang.Runtime.getRuntime.availableProcessors
}

/**
 * Comparison benchmarks: Before and after fix Note: To run "before" benchmarks,
 * you'd need to revert to original ZScheduler These benchmarks help quantify
 * the improvement
 */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Measurement(iterations = 10, timeUnit = TimeUnit.SECONDS, time = 2)
@Warmup(iterations = 10, timeUnit = TimeUnit.SECONDS, time = 2)
@Fork(value = 2)
class UnparkLatencyBenchmark {

  val scheduler: zio.Executor = zio.Executor.makeDefault()

  /**
   * Measures latency of a single submit operation Lower = better (less overhead
   * from unpark logic)
   */
  @Benchmark
  def singleSubmitLatency(bh: Blackhole): Unit = {
    val io = for {
      promise <- Promise.make[Nothing, Unit]
      _       <- promise.succeed(()).forkDaemon
      _       <- promise.await
    } yield ()

    bh.consume(unsafeRun(io.onExecutor(scheduler)))
  }

  /**
   * Measures P99 latency under load Ensures batching doesn't cause latency
   * spikes
   */
  @Benchmark
  def submitUnderLoadLatency(bh: Blackhole): Unit = {
    val io = for {
      // Create background load
      _ <- repeat(100)(ZIO.unit.forkDaemon)
      // Measure latency of single task
      promise <- Promise.make[Nothing, Unit]
      start   <- Clock.nanoTime
      _       <- promise.succeed(()).forkDaemon
      _       <- promise.await
      end     <- Clock.nanoTime
    } yield end - start

    bh.consume(unsafeRun(io.onExecutor(scheduler)))
  }
}
