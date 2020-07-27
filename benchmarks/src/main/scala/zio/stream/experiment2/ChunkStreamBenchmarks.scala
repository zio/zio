package zio
package stream
package experiment2

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations.{ Benchmark, BenchmarkMode, Mode, OutputTimeUnit, Param, Scope, State }

import zio.IOBenchmarks.unsafeRun

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ChunkStreamBenchmarks {

  @Param(Array("10000"))
  var count: Long = _

  @Param(Array("1000"))
  var chunkSize: Int = _

  @Benchmark
  def chunkFilterMapSum: Long = {
    import ZTransducer2._

    val chunk  = Chunk.fromIterable(0 until chunkSize)
    val stream = ZStream2.repeatPull(Pull.emit(chunk)).forever
    val pipe = take[Int](count).chunked >>: map[Chunk[Int], Chunk[Int]](_.filter(_ % 2 == 0)) >>: map[Int, Long](
      _.toLong
    ).chunked
    val sink   = ZSink2.sum[Long].chunked
    val result = stream >>: pipe >>: sink

    unsafeRun(result)
  }

  @Benchmark
  def zioChunkFilterMapSum: Long = {
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
