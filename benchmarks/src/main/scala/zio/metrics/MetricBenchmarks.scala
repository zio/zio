package zio.metrics

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._
import zio.ZIO

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(value = 3)
class MetricBenchmarks {

  @Benchmark
  def exponentialStatic(): Unit = {
    val metric = Metric.histogram("exponential", MetricKeyType.Histogram.Boundaries.exponential(1.0, 2.0, 64))
    unsafeRun(ZIO.foreachDiscard(1 to 100000)(i => metric.update(i.toDouble)))
  }

  @Benchmark
  def exponentialDynamic(): Unit =
    unsafeRun {
      ZIO.foreachDiscard(1 to 100000) { i =>
        Metric.histogram("exponential", MetricKeyType.Histogram.Boundaries.exponential(1.0, 2.0, 64)).update(i.toDouble)
      }
    }
}
