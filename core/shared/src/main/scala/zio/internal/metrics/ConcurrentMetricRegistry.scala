package zio.internal.metrics

import zio._
import zio.metrics._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

private[zio] class ConcurrentMetricRegistry {
  private val listener: MetricListener =
    new MetricListener {
      override def unsafeUpdate[Type <: MetricKeyType](key: MetricKey[Type]): key.keyType.In => Unit =
        (update: key.keyType.In) => {
          val iterator = listeners.iterator

          while (iterator.hasNext) {
            val listener = iterator.next()
            listener.unsafeUpdate(key)(update) // TODO: Optimize
          }
        }
    }

  private val map: ConcurrentHashMap[MetricKey[MetricKeyType], MetricHook.Root] =
    new ConcurrentHashMap[MetricKey[MetricKeyType], MetricHook.Root]()

  private val listeners = zio.internal.Platform.newConcurrentSet[MetricListener]()

  final def installListener(listener: MetricListener): Unit = {
    listeners.add(listener)
    ()
  }

  final def removeListener(listener: MetricListener): Unit = {
    listeners.remove(listener)
    ()
  }

  def snapshot(): Set[MetricPair.Untyped] = {
    val iterator = map.entrySet().iterator()
    val result   = scala.collection.mutable.Set[MetricPair.Untyped]()
    while (iterator.hasNext) {
      val value = iterator.next()
      val key   = value.getKey()
      val hook  = value.getValue()
      result.add(MetricPair.unsafeMake(key, hook.get()))
    }
    result.toSet
  }

  def get[Type <: MetricKeyType](key: MetricKey[Type]): MetricHook[key.keyType.In, key.keyType.Out] = {
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

  private def getCounter(key: MetricKey.Counter): MetricHook.Counter = {
    var value = map.get(key)
    if (value eq null) {
      val updater = listener.unsafeUpdate(key)
      val counter = ConcurrentMetricHooks.counter(key).onUpdate(updater)
      map.putIfAbsent(key, counter)
      value = map.get(key)
    }
    value.asInstanceOf[MetricHook.Counter]
  }

  private def getGauge(key: MetricKey.Gauge): MetricHook.Gauge = {
    var value = map.get(key)
    if (value eq null) {
      val updater = listener.unsafeUpdate(key)
      val gauge   = ConcurrentMetricHooks.gauge(key, 0.0).onUpdate(updater)
      map.putIfAbsent(key, gauge)
      value = map.get(key)
    }
    value.asInstanceOf[MetricHook.Gauge]
  }

  private def getHistogram(key: MetricKey.Histogram): MetricHook.Histogram = {
    var value = map.get(key)
    if (value eq null) {
      val updater = listener.unsafeUpdate(key)
      val histogram =
        ConcurrentMetricHooks.histogram(key).onUpdate(updater)
      map.putIfAbsent(key, histogram)
      value = map.get(key)
    }
    value.asInstanceOf[MetricHook.Histogram]
  }

  private def getSummary(
    key: MetricKey.Summary
  ): MetricHook.Summary = {
    var value = map.get(key)
    if (value eq null) {
      val updater = listener.unsafeUpdate(key)
      val summary = ConcurrentMetricHooks.summary(key).onUpdate(updater)
      map.putIfAbsent(key, summary)
      value = map.get(key)
    }
    value.asInstanceOf[MetricHook.Summary]
  }

  private def getSetCount(key: MetricKey.Frequency): MetricHook.Frequency = {
    var value = map.get(key)
    if (value eq null) {
      val updater   = listener.unsafeUpdate(key)
      val frequency = ConcurrentMetricHooks.frequency(key).onUpdate(updater)
      map.putIfAbsent(key, frequency)
      value = map.get(key)
    }
    value.asInstanceOf[MetricHook.Frequency]
  }
}
