package zio.stm

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

import zio._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 10)
@Warmup(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 10)
@Fork(1)
class STMRetryBenchmark {
  import IOBenchmarks.unsafeRun

  private var longUpdates: List[UIO[Unit]] = _

  private val Size = 10000

  @Setup(Level.Trial)
  def setup(): Unit = {
    val data       = (1 to Size).toList.zipWithIndex
    val map        = unsafeRun(TMap.fromIterable(data).commit)
    val schedule   = Schedule.recurs(1000).unit
    val longUpdate = map.transformValues(_ + 1).commit.repeat(schedule)

    longUpdates = List(longUpdate, longUpdate, longUpdate)
  }

  @Benchmark
  def concurrentLongTransactions(): Unit =
    unsafeRun(UIO.forkAll_(longUpdates))
}
