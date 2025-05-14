package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
class OptimizedRaceBenchmark {
  @Param(Array("1000"))
  var size: Int = _

  /**
   * Benchmark to compare the performance of the optimized race implementation
   * with the original implementation. This benchmark measures the throughput
   * of racing operations where one side completes immediately and the other
   * side never completes.
   */
  
  @Benchmark
  def originalRaceFirst(): Int = originalRaceFirst(BenchmarkUtil)

  @Benchmark
  def optimizedRaceFirst(): Int = optimizedRaceFirst(BenchmarkUtil)

  private[this] def originalRaceFirst(runtime: Runtime[Any]): Int = {
    def loop(i: Int): UIO[Int] =
      if (i < size) {
        // Use the standard race implementation
        val left = ZIO.never
        val right = ZIO.succeed(i + 1)
        left.raceFirst(right).flatMap(loop)
      } else ZIO.succeed(i)

    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(loop(0)).getOrThrowFiberFailure()
    }
  }

  private[this] def optimizedRaceFirst(runtime: Runtime[Any]): Int = {
    def loop(i: Int): UIO[Int] =
      if (i < size) {
        // Use the optimized race implementation directly
        val left = ZIO.never
        val right = ZIO.succeed(i + 1)
        OptimizedRace.raceFirst(left, right).flatMap(loop)
      } else ZIO.succeed(i)

    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(loop(0)).getOrThrowFiberFailure()
    }
  }

  /**
   * Additional benchmarks to compare other race variants
   */
  
  @Benchmark
  def originalRace(): Int = originalRace(BenchmarkUtil)

  @Benchmark
  def optimizedRace(): Int = optimizedRace(BenchmarkUtil)

  private[this] def originalRace(runtime: Runtime[Any]): Int = {
    def loop(i: Int): UIO[Int] =
      if (i < size) {
        val left = ZIO.never
        val right = ZIO.succeed(i + 1)
        left.race(right).flatMap(loop)
      } else ZIO.succeed(i)

    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(loop(0)).getOrThrowFiberFailure()
    }
  }

  private[this] def optimizedRace(runtime: Runtime[Any]): Int = {
    def loop(i: Int): UIO[Int] =
      if (i < size) {
        val left = ZIO.never
        val right = ZIO.succeed(i + 1)
        OptimizedRace.race(left, right).flatMap(loop)
      } else ZIO.succeed(i)

    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(loop(0)).getOrThrowFiberFailure()
    }
  }
}