package zio.metrics.clients

/**
 * A `MetricListener` is capable of taking some action in response to a metric
 * being recorded, such as sending that metric to a third party service.
 */
trait MetricListener { self =>
  def unsafeGaugeChanged(key: MetricKey.Gauge, value: Double, delta: Double): Unit
  def unsafeCounterChanged(key: MetricKey.Counter, absValue: Double, delta: Double): Unit
  def unsafeHistogramChanged(key: MetricKey.Histogram, value: MetricState): Unit
  def unsafeSummaryChanged(key: MetricKey.Summary, value: MetricState): Unit
  def unsafeSetChanged(key: MetricKey.SetCount, value: MetricState): Unit
}
