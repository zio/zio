package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(1)
class TimeoutBenchmark {

  @Param(Array("0", "1000"))
  var size: Int = _

  private def effect: UIO[Int] =
    ZIO.foldLeft(0 until size)(0) { case (total, value) =>
      ZIO.succeed(total + value)
    }

  @Benchmark
  def baseline(): Int =
    unsafeRun(effect)

  @Benchmark
  def timeout(): Option[Int] =
    unsafeRun(effect.timeout(100.minutes))
}
