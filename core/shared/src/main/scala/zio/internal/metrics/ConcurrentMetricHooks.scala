package zio.internal.metrics

import zio.metrics._

private[zio] trait ConcurrentMetricHooks {
  def counter(key: MetricKey.Counter): MetricHook.Counter

  def gauge(key: MetricKey.Gauge, startAt: Double): MetricHook.Gauge

  def histogram(key: MetricKey.Histogram): MetricHook.Histogram

  def summary(key: MetricKey.Summary): MetricHook.Summary

  def frequency(key: MetricKey.Frequency): MetricHook.Frequency
}
private[zio] object ConcurrentMetricHooks extends ConcurrentMetricHooksPlatformSpecific
