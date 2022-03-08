package zio.internal.metrics

import zio._
import zio.metrics._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

private[zio] class ConcurrentState {
  private val listeners = zio.internal.Platform.newConcurrentSet[MetricListener]()

  final def installListener(listener: MetricListener): Unit = {
    listeners.add(listener)
    ()
  }

  final def removeListener(listener: MetricListener): Unit = {
    listeners.remove(listener)
    ()
  }

  private val listener: MetricListener =
    new MetricListener {
      override def unsafeUpdate[In](key: MetricKey.Root[In], update: In): Unit = {
        val iterator = listeners.iterator

        while (iterator.hasNext) {
          val listener = iterator.next()
          listener.unsafeUpdate(key, update)
        }
      }
    }

  private val map: ConcurrentHashMap[MetricKey.Untyped, MetricHook.Root] =
    new ConcurrentHashMap[MetricKey.Untyped, MetricHook.Root]()

  private[zio] def unsafeSnapshot(): Set[MetricPair[_, _, _]] = {
    val iterator = map.entrySet().iterator()
    val result   = scala.collection.mutable.Set[MetricPair[_, _, _]]()
    while (iterator.hasNext) {
      val value = iterator.next()
      val key   = value.getKey()
      val hook  = value.getValue()
      result.add(MetricPair(key, hook.get()))
    }
    result.toSet
  }

  def getCounter(key: MetricKey.Counter): MetricHook.Counter = {
    var value = map.get(key)
    if (value eq null) {
      val counter = ConcurrentMetricHooks.counter(key).onUpdate { (update: Double) =>
        listener.unsafeUpdate(key, update)
      }
      map.putIfAbsent(key, counter)
      value = map.get(key)
    }
    value.asInstanceOf[MetricHook.Counter]
  }

  def getGauge(key: MetricKey.Gauge): MetricHook.Gauge = {
    var value = map.get(key)
    if (value eq null) {
      val gauge = ConcurrentMetricHooks.gauge(key, 0.0).onUpdate { (update: Double) =>
        listener.unsafeUpdate(key, update)
      }
      map.putIfAbsent(key, gauge)
      value = map.get(key)
    }
    value.asInstanceOf[MetricHook.Gauge]
  }

  def getHistogram(key: MetricKey.Histogram): MetricHook.Histogram = {
    var value = map.get(key)
    if (value eq null) {
      val histogram =
        ConcurrentMetricHooks.histogram(key).onUpdate { (update: Double) =>
          listener.unsafeUpdate(key, update)
        }
      map.putIfAbsent(key, histogram)
      value = map.get(key)
    }
    value.asInstanceOf[MetricHook.Histogram]
  }

  def getSummary(
    key: MetricKey.Summary
  ): MetricHook.Summary = {
    var value = map.get(key)
    if (value eq null) {
      val summary = ConcurrentMetricHooks.summary(key).onUpdate { (update: (Double, java.time.Instant)) =>
        listener.unsafeUpdate(key, update)
      }
      map.putIfAbsent(key, summary)
      value = map.get(key)
    }
    value.asInstanceOf[MetricHook.Summary]
  }

  def getSetCount(key: MetricKey.SetCount): MetricHook.SetCount = {
    var value = map.get(key)
    if (value eq null) {
      val setCount = ConcurrentMetricHooks.setCount(key).onUpdate { (update: String) =>
        listener.unsafeUpdate(key, update)
      }
      map.putIfAbsent(key, setCount)
      value = map.get(key)
    }
    value.asInstanceOf[MetricHook.SetCount]
  }
}
