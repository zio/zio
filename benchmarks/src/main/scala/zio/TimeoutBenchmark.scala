package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class TimeoutBenchmark {
  import BenchmarkUtil.unsafeRun

  @Param(Array("10000"))
  var n: Int = _

  @Benchmark
  def zioNoTimeout(): Unit = {
    val _ = unsafeRun(ZIO.foreachDiscard(1 to n)(_ => ZIO.succeed(1)))
  }

  @Benchmark
  def zioTimeout(): Unit = {
    val _ = unsafeRun(ZIO.foreachDiscard(1 to n)(_ => ZIO.succeed(1).timeout(1.minute)))
  }
}
