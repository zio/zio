package zio.stm

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

import zio._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class TSetOpsBenchmarks {
  import IOBenchmarks.unsafeRun

  @Param(Array("100", "10000"))
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
