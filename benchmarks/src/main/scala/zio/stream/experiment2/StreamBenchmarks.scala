package zio
package stream
package experiment2

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations.{ Benchmark, BenchmarkMode, Mode, OutputTimeUnit, Param, Scope, State }

import zio.IOBenchmarks.unsafeRun

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class StreamBenchmarks {

  @Param(Array("10000"))
  var count: Long = _

  @Benchmark
  def filterMapSum: Long = {

    val stream = ZStream2.repeatPull(Pull.emit(1)).take(count).filter(_ % 2 == 0)
    val pipe   = ZTransducer2.map[Int, Long](_.toLong)
    val sink   = ZSink2.sum[Long]
    val result = stream >>: pipe >>: sink

    unsafeRun(result)
  }

  @Benchmark
  def zioFilterMapSum: Long = {
    val stream = ZStream
      .repeatEffect(ZIO.succeedNow(1))
      .take(count)
      .filter(_ % 2 == 0)
      .map(_.toLong)

    val sink   = ZSink.foldLeftChunks(0L)((s, as: Chunk[Long]) => as.fold(s)(_ + _))
    val result = stream.run(sink)

    unsafeRun(result)
  }
}
