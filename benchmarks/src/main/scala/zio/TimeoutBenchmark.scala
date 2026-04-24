package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class TimeoutBenchmark {
  import BenchmarkUtil.unsafeRun

  @Param(Array("0", "100", "10000"))
  var n: Int = _

  private var effect: UIO[Int] = _

  @Setup(Level.Trial)
  def setup(): Unit =
    effect = ZIO.foldLeft(0 until n)(0) { case (sum, value) =>
      ZIO.succeed(sum + value)
    }

  @Benchmark
  def zioBaseline(): Int =
    unsafeRun(effect)

  @Benchmark
  def zioTimeout(): Int =
    unsafeRun(effect.timeoutTo(-1)(identity)(1.hour))
}
