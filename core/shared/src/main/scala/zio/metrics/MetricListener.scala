package zio.metrics

private[zio] trait MetricListener {
  def updateHistogram(key: MetricKey[MetricKeyType.Histogram], value: Double): Unit
  def updateGauge(key: MetricKey[MetricKeyType.Gauge], value: Double): Unit
  def updateFrequency(key: MetricKey[MetricKeyType.Frequency], value: String): Unit
  def updateSummary(key: MetricKey[MetricKeyType.Summary], value: Double, instant: java.time.Instant): Unit
  def updateCounter(key: MetricKey[MetricKeyType.Counter], value: Double): Unit
}
