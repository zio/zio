package zio.stream

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

import zio.IOBenchmarks.unsafeRun
import zio._
import zio.stream.experimental.{ ZSink => XSink, ZStream => XStream, ZTransducer => XTransducer }

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class StreamChunkExperiments {

  @Param(Array("10000"))
  var count: Long = _

  @Param(Array("1", "10", "100", "1000"))
  var chunkSize: Int = _

  @Benchmark
  def zioChunkFilterMapSum = {
    val stream = ZStream
      .fromChunk(Chunk(1 to chunkSize: _*))
      .forever
      .take(count)
      .filter(_ % 2 == 0)
      .map(_.toLong)

    val sink   = ZSink.foldLeftChunks(0L)((s, as: Chunk[Long]) => as.fold(s)(_ + _))
    val result = stream.run(sink)

    unsafeRun(result)
  }

  @Benchmark
  def newChunkFilterMapSum = {
    val stream = XStream
      .apply(Chunk(1 to chunkSize: _*))
      .forever
      .pipe(XTransducer.take[Int](count).chunked)
      .pipe(XTransducer.filter[Int](_ % 2 == 0))
      .pipe(XTransducer.map[Int, Long](_.toLong).chunked)

    val sink   = XSink.sum[Long].chunked
    val result = stream.run(sink)

    unsafeRun(result)
  }
}
