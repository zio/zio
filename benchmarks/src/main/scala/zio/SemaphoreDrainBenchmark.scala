package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.internal.SemaphorePlatform

import java.util.concurrent.TimeUnit

/**
 * Measures the uncontended acquire/release pair on `SemaphorePlatform`
 * directly, with no fiber runtime in the way, so that the cost of the release
 * path itself is visible.
 */
@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Measurement(iterations = 10, timeUnit = TimeUnit.SECONDS, time = 1)
@Warmup(iterations = 10, timeUnit = TimeUnit.SECONDS, time = 1)
@Fork(2)
class SemaphoreDrainBenchmark {

  var sem: SemaphorePlatform = _

  @Setup(Level.Trial)
  def setup(): Unit = sem = new SemaphorePlatform(1L, true)

  @Benchmark
  def acquireRelease(): Boolean = {
    val ok = sem.tryAcquire(1L)
    sem.release(1L)
    ok
  }
}
