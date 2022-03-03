package zio.internal.metrics

import zio.metrics._

trait ConcurrentMetricHooks {
  def counter(key: MetricKey.Counter): MetricHook.Counter

  def gauge(key: MetricKey.Gauge, startAt: Double): MetricHook.Gauge

  def histogram(key: MetricKey.Histogram): MetricHook.Histogram

  def summary(key: MetricKey.Summary): MetricHook.Summary

  def setCount(key: MetricKey.SetCount): MetricHook.SetCount
}
object ConcurrentMetricHooks extends ConcurrentMetricHooksPlatformSpecific
