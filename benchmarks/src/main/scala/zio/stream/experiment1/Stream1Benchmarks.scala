package zio
package stream
package experiment1

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import zio.IOBenchmarks.unsafeRun

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class Stream1Benchmarks {

  @Param(Array("10000"))
  var count: Long = _

  @Benchmark
  def filterMapSum = {
    import ZTransducer1._

    val stream = ZStream1.repeatPull(ZIO.succeedNow(1))
    val pipe = take[Any, Int](count) >>> filter[Any, Int](_ % 2 == 0) >>> map[Any, Int, Long](_.toLong)
    val sink   = ZSink1.sum[Any, Long]
    val result = stream.transduce(pipe).run(sink)

    unsafeRun(result)
  }

  @Benchmark
  def zioFilterMapSum = {
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
