package zio.metrics

import zio.Unsafe

/**
 * A listener that `MetricClient.addListener` installs to be told about each
 * metric change. Each method receives the full key of the metric, tags
 * included, and the update value.
 */
private[zio] trait MetricListener {

  /**
   * Called when a gauge is adjusted by `value` with `modify`.
   */
  def modifyGauge(key: MetricKey[MetricKeyType.Gauge], value: Double)(implicit unsafe: Unsafe): Unit

  /**
   * Called when a histogram observes `value`, with `update` or `modify`.
   */
  def updateHistogram(key: MetricKey[MetricKeyType.Histogram], value: Double)(implicit unsafe: Unsafe): Unit

  /**
   * Called when a gauge is set to `value` with `update`.
   */
  def updateGauge(key: MetricKey[MetricKeyType.Gauge], value: Double)(implicit unsafe: Unsafe): Unit

  /**
   * Called when a frequency counts an occurrence of `value`, with `update` or
   * `modify`.
   */
  def updateFrequency(key: MetricKey[MetricKeyType.Frequency], value: String)(implicit unsafe: Unsafe): Unit

  /**
   * Called when a summary observes `value` at `instant`, with `update` or
   * `modify`.
   */
  def updateSummary(key: MetricKey[MetricKeyType.Summary], value: Double, instant: java.time.Instant)(implicit
    unsafe: Unsafe
  ): Unit

  /**
   * Called when `value` is added to a counter, with `update` or `modify`.
   */
  def updateCounter(key: MetricKey[MetricKeyType.Counter], value: Double)(implicit unsafe: Unsafe): Unit
}
