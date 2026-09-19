package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(4)
class TimeoutBenchmark {
  import BenchmarkUtil.unsafeRun

  @Param(Array("10000"))
  var n: Int = _

  var range: List[Int] = _

  @Setup(Level.Trial)
  def setup(): Unit =
    range = (0 to n).toList

  @Benchmark
  def zioBaseline(): Unit = {
    val _ =
      unsafeRun(
        ZIO.foreachDiscard(range)(_ => ZIO.succeed(42))
      )
  }

  @Benchmark
  def zioTimeout(): Unit = {
    val _ =
      unsafeRun(
        ZIO.foreachDiscard(range)(_ => ZIO.succeed(42).timeout(1.hour))
      )
  }
}
