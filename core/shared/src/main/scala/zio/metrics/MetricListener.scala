package zio.metrics

import zio.Unsafe

private[zio] trait MetricListener {
  def updateHistogram(key: MetricKey[MetricKeyType.Histogram], value: Double)(implicit unsafe: Unsafe): Unit
  def updateGauge(key: MetricKey[MetricKeyType.Gauge], value: Double)(implicit unsafe: Unsafe): Unit
  def updateFrequency(key: MetricKey[MetricKeyType.Frequency], value: String)(implicit unsafe: Unsafe): Unit
  def updateSummary(key: MetricKey[MetricKeyType.Summary], value: Double, instant: java.time.Instant)(implicit unsafe: Unsafe): Unit
  def updateCounter(key: MetricKey[MetricKeyType.Counter], value: Double)(implicit unsafe: Unsafe): Unit
}
