package zio.stream

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations.{ Benchmark, BenchmarkMode, Mode, OutputTimeUnit, Param, Scope, State }
import zio.{ Chunk, ZIO }
import zio.IOBenchmarks.unsafeRun
import zio.stream.experimental.{ ZSink => XSink, ZStream => XStream, ZTransducer => XTransducer }

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class StreamExperiments {

  @Param(Array("10000"))
  var count: Long = _

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

  @Benchmark
  def newFilterMapSum = {
    val stream = XStream
      .repeatEffect(ZIO.succeedNow(1))
      .take(count)
      .filter(_ % 2 == 0)
      .pipe(XTransducer.map[Int, Long](_.toLong))

    val sink   = XSink.sum[Long]
    val result = stream.run(sink)

    unsafeRun(result)
  }
}
