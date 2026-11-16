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
  def zioBaseline(): Unit = {
    val _ = unsafeRun(ZIO.foreachDiscard(0 until n)(_ => ZIO.succeed(1)))
  }

  @Benchmark
  def zioTimeout(): Unit = {
    val _ = unsafeRun(ZIO.foreachDiscard(0 until n)(_ => ZIO.succeed(1).timeout(1.minute)))
  }

  @Benchmark
  def zioTimeoutAsync(): Unit = {
    val _ = unsafeRun(ZIO.foreachDiscard(0 until n)(_ => ZIO.yieldNow.timeout(1.minute)))
  }
}
