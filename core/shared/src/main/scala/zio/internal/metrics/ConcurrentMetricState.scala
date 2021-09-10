package zio.internal.metrics

import zio.metrics._

import java.util.concurrent.atomic.{AtomicReference, DoubleAdder}

private[zio] sealed trait ConcurrentMetricState { self =>
  def key: MetricKey
  def help: String

  def toMetricState: MetricState =
    self match {
      case ConcurrentMetricState.Counter(key, help, value) =>
        MetricState.counter(key, help, value.doubleValue)
      case ConcurrentMetricState.Gauge(key, help, value) =>
        MetricState.gauge(key, help, value.get)
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

  final case class Counter(key: MetricKey.Counter, help: String, value: DoubleAdder) extends ConcurrentMetricState {
    def count: Double =
      value.doubleValue()
    def increment(v: Double): (Double, Double) = {
      value.add(v)
      (value.sum(), v)
    }
  }

  final case class Gauge(key: MetricKey.Gauge, help: String, value: AtomicReference[Double])
      extends ConcurrentMetricState {
    def set(v: Double): (Double, Double) = {
      val old = value.getAndSet(v)
      (v, v - old)
    }
    def adjust(v: Double): (Double, Double) =
      (value.updateAndGet(_ + v), v)
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
