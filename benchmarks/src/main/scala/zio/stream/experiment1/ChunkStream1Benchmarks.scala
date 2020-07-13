package zio
package stream
package experiment1

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations.{Benchmark, BenchmarkMode, Mode, OutputTimeUnit, Param, Scope, State}
import zio.IOBenchmarks.unsafeRun

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ChunkStream1Benchmarks {

  @Param(Array("10000"))
  var count: Long = _

  @Param(Array("1000"))
  var chunkSize: Int = _

  @Benchmark
  def chunkFilterMapSum = {
    import ZTransducer1._

    val chunk = Chunk.fromIterable(0 until chunkSize)
    val stream = ZStream1(chunk).forever
    val pipe = take[Any, Int](count).chunked >>> filter[Any, Int](_ % 2 == 0).chunked >>> map[Any, Int, Long](_.toLong).chunked
    val sink   = ZSink1.sum[Any, Long].chunked
    val result = stream.transduce(pipe).run(sink)

    unsafeRun(result)
  }

  @Benchmark
  def zioChunkFilterMapSum = {
    val chunk = Chunk.fromIterable(0 until chunkSize)
    val stream = ZStream
      .fromChunk(chunk)
      .forever
      .take(count)
      .filter(_ % 2 == 0)
      .map(_.toLong)

    val sink   = ZSink.foldLeftChunks(0L)((s, as: Chunk[Long]) => as.fold(s)(_ + _))
    val result = stream.run(sink)

    unsafeRun(result)
  }
}
