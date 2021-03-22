package zio.stm

import org.openjdk.jmh.annotations._
import zio._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 10)
@Warmup(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 10)
@Fork(1)
class TSetOpsBenchmarks {
  import IOBenchmarks.unsafeRun

  @Param(Array("10", "100", "10000", "100000"))
  var size: Int = _

  private var set: TSet[Int] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    val data = (1 to size).toList
    set = unsafeRun(TSet.fromIterable(data).commit)
  }

  @Benchmark
  def union(): Unit = unsafeRun(set.union(set).commit)
}
