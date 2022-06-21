package zio.internal.metrics

import zio._
import zio.metrics._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

private[zio] class ConcurrentMetricRegistry {

  private val map: ConcurrentHashMap[MetricKey[MetricKeyType], MetricHook.Root] =
    new ConcurrentHashMap[MetricKey[MetricKeyType], MetricHook.Root]()

  def snapshot()(implicit unsafe: Unsafe): Set[MetricPair.Untyped] = {
    val iterator = map.entrySet().iterator()
    val result   = scala.collection.mutable.Set[MetricPair.Untyped]()
    while (iterator.hasNext) {
      val value = iterator.next()
      val key   = value.getKey()
      val hook  = value.getValue()
      result.add(MetricPair.make(key, hook.get()))
    }
    result.toSet
  }

  def get[Type <: MetricKeyType](
    key: MetricKey[Type]
  )(implicit unsafe: Unsafe): MetricHook[key.keyType.In, key.keyType.Out] = {
    type Result = MetricHook[key.keyType.In, key.keyType.Out]

    var hook0: MetricHook[_, zio.metrics.MetricState.Untyped] = map.get(key)

    if (hook0 == null) {
      (key.keyType match {
        case MetricKeyType.Counter             => getCounter(key.asInstanceOf[MetricKey.Counter])
        case MetricKeyType.Frequency           => getSetCount(key.asInstanceOf[MetricKey.Frequency])
        case MetricKeyType.Gauge               => getGauge(key.asInstanceOf[MetricKey.Gauge])
        case MetricKeyType.Histogram(_)        => getHistogram(key.asInstanceOf[MetricKey.Histogram])
        case MetricKeyType.Summary(_, _, _, _) => getSummary(key.asInstanceOf[MetricKey.Summary])
      }).asInstanceOf[Result]
    } else hook0.asInstanceOf[Result]
  }

  private def getCounter(key: MetricKey.Counter)(implicit unsafe: Unsafe): MetricHook.Counter = {
    var value = map.get(key)
    if (value eq null) {
      val counter = ConcurrentMetricHooks.counter(key)
      map.putIfAbsent(key, counter)
      value = map.get(key)
    }
    value.asInstanceOf[MetricHook.Counter]
  }

  private def getGauge(key: MetricKey.Gauge)(implicit unsafe: Unsafe): MetricHook.Gauge = {
    var value = map.get(key)
    if (value eq null) {
      val gauge = ConcurrentMetricHooks.gauge(key, 0.0)
      map.putIfAbsent(key, gauge)
      value = map.get(key)
    }
    value.asInstanceOf[MetricHook.Gauge]
  }

  private def getHistogram(key: MetricKey.Histogram)(implicit unsafe: Unsafe): MetricHook.Histogram = {
    var value = map.get(key)
    if (value eq null) {
      val histogram =
        ConcurrentMetricHooks.histogram(key)
      map.putIfAbsent(key, histogram)
      value = map.get(key)
    }
    value.asInstanceOf[MetricHook.Histogram]
  }

  private def getSummary(
    key: MetricKey.Summary
  )(implicit unsafe: Unsafe): MetricHook.Summary = {
    var value = map.get(key)
    if (value eq null) {
      val summary = ConcurrentMetricHooks.summary(key)
      map.putIfAbsent(key, summary)
      value = map.get(key)
    }
    value.asInstanceOf[MetricHook.Summary]
  }

  private def getSetCount(key: MetricKey.Frequency)(implicit unsafe: Unsafe): MetricHook.Frequency = {
    var value = map.get(key)
    if (value eq null) {
      val frequency = ConcurrentMetricHooks.frequency(key)
      map.putIfAbsent(key, frequency)
      value = map.get(key)
    }
    value.asInstanceOf[MetricHook.Frequency]
  }
}
