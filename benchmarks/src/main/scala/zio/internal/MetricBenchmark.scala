package zio.internal

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.Unsafe
import zio.metrics.MetricKeyType.Frequency
import zio.metrics._

import java.time.Instant
import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 15, time = 1)
@Measurement(iterations = 15, time = 1)
@Fork(3)
class MetricBenchmark {

  @Param(Array("true", "false"))
  var registerListener: String = _

  @Setup
  def setup(): Unit = {
    if (registerListener == "true") {
      MetricClient.addListener(listener)
    }
  }

  @TearDown
  def tearDown(): Unit = {
    MetricClient.removeListener(listener)
  }

  private val metric = Metric.counter("Test counter")

  private val listener = new MetricListener {
    override def updateHistogram(key: MetricKey[MetricKeyType.Histogram], value: Double)(implicit unsafe: Unsafe): Unit = ()

    override def updateGauge(key: MetricKey[MetricKeyType.Gauge], value: Double)(implicit unsafe: Unsafe): Unit = ()

    override def updateFrequency(key: MetricKey[Frequency], value: String)(implicit unsafe: Unsafe): Unit = ()

    override def updateSummary(key: MetricKey[MetricKeyType.Summary], value: Double, instant: Instant)(implicit unsafe: Unsafe): Unit = ()

    override def updateCounter(key: MetricKey[MetricKeyType.Counter], value: Double)(implicit unsafe: Unsafe): Unit = ()
  }

  @Benchmark
  def updateMetric(): Unit = {
    metric.unsafe.update(3L)(Unsafe.unsafe)
  }
}
