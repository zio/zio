package zio.internal.metrics

import zio.metrics._
import zio.stacktracer.TracingImplicits.disableAutoTrace

private[zio] sealed trait ConcurrentMetricState { self =>
  def key: MetricKey
  def help: String

  def toMetricState: MetricState =
    self match {
      case ConcurrentMetricState.Counter(key, help, counter) =>
        MetricState.counter(key, help, counter.count)
      case ConcurrentMetricState.Gauge(key, help, gauge) =>
        MetricState.gauge(key, help, gauge.get)
      case ConcurrentMetricState.Histogram(key, help, histogram) =>
        MetricState.histogram(key, help, histogram.snapshot(), histogram.getCount(), histogram.getSum())
      case ConcurrentMetricState.Summary(key, help, summary) =>
        MetricState.summary(
          key,
          help,
          summary.snapshot(java.time.Instant.now()),
          summary.getCount(),
          summary.getSum()
        )
      case ConcurrentMetricState.SetCount(key, help, setCount) =>
        MetricState.setCount(key, help, setCount.snapshot())
    }
}

private[zio] object ConcurrentMetricState {

  final case class Counter(key: MetricKey.Counter, help: String, counter: ConcurrentCounter)
      extends ConcurrentMetricState {
    def count: Double                          = counter.count
    def increment(v: Double): (Double, Double) = counter.increment(v)
  }

  final case class Gauge(key: MetricKey.Gauge, help: String, gauge: ConcurrentGauge) extends ConcurrentMetricState {
    def set(v: Double): (Double, Double)    = gauge.set(v)
    def adjust(v: Double): (Double, Double) = gauge.adjust(v)
    def get: Double                         = gauge.get
  }

  final case class Histogram(
    key: MetricKey.Histogram,
    help: String,
    histogram: ConcurrentHistogram
  ) extends ConcurrentMetricState {
    def observe(value: Double): Unit =
      histogram.observe(value)
  }

  final case class Summary(
    key: MetricKey.Summary,
    help: String,
    summary: ConcurrentSummary
  ) extends ConcurrentMetricState {
    def observe(value: Double, t: java.time.Instant): Unit =
      summary.observe(value, t)
  }

  final case class SetCount(
    key: MetricKey.SetCount,
    help: String,
    setCount: ConcurrentSetCount
  ) extends ConcurrentMetricState {
    def observe(word: String): Unit = setCount.observe(word)
  }

  final case class TimeStampedDouble(value: Double, timeStamp: java.time.Instant)
}
