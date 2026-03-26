package zio.internal.metrics

import zio._
import zio.metrics._

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

private[zio] class ConcurrentMetricRegistry {

  private val listenersRef: AtomicReference[Array[MetricListener]] =
    new AtomicReference[Array[MetricListener]](Array.empty[MetricListener])

  private val map: ConcurrentHashMap[MetricKey[MetricKeyType], MetricHook.Root] =
    new ConcurrentHashMap[MetricKey[MetricKeyType], MetricHook.Root]()

  def snapshot()(implicit unsafe: Unsafe): Set[MetricPair.Untyped] = {
    val iterator = map.entrySet().iterator()
    val result   = Set.newBuilder[MetricPair.Untyped]
    result.sizeHint(map.size())
    while (iterator.hasNext) {
      val value = iterator.next()
      val key   = value.getKey
      val hook  = value.getValue
      result += MetricPair.make(key, hook.get())
    }
    result.result()
  }

  def get[Type <: MetricKeyType](
    key: MetricKey[Type]
  )(implicit unsafe: Unsafe): MetricHook[key.keyType.In, key.keyType.Out] = {
    type Result = MetricHook[key.keyType.In, key.keyType.Out]

    (
      key.keyType match {
        case MetricKeyType.Counter =>
          map
            .computeIfAbsent(
              key.asInstanceOf[MetricKey.Counter],
              k => ConcurrentMetricHooks.counter(k.asInstanceOf[MetricKey.Counter])
            )
        case MetricKeyType.Frequency =>
          map
            .computeIfAbsent(
              key.asInstanceOf[MetricKey.Frequency],
              k => ConcurrentMetricHooks.frequency(k.asInstanceOf[MetricKey.Frequency])
            )
        case MetricKeyType.Gauge =>
          map
            .computeIfAbsent(
              key.asInstanceOf[MetricKey.Gauge],
              k => ConcurrentMetricHooks.gauge(k.asInstanceOf[MetricKey.Gauge], 0.0)
            )
        case _: MetricKeyType.Histogram =>
          map.computeIfAbsent(
            key.asInstanceOf[MetricKey.Histogram],
            k => ConcurrentMetricHooks.histogram(k.asInstanceOf[MetricKey.Histogram])
          )
        case _: MetricKeyType.Summary =>
          map
            .computeIfAbsent(
              key.asInstanceOf[MetricKey.Summary],
              k => ConcurrentMetricHooks.summary(k.asInstanceOf[MetricKey.Summary])
            )
      }
    ).asInstanceOf[Result]
  }

  def remove[Type <: MetricKeyType](key: MetricKey[Type])(implicit unsafe: Unsafe): Boolean =
    map.remove(key) ne null

  @tailrec
  final def addListener(listener: MetricListener)(implicit unsafe: Unsafe): Unit = {
    val oldListeners = listenersRef.get()
    val newListeners = oldListeners :+ listener
    if (!listenersRef.compareAndSet(oldListeners, newListeners)) addListener(listener)
    else ()
  }

  @tailrec
  final def removeListener(listener: MetricListener)(implicit unsafe: Unsafe): Unit = {
    val oldListeners = listenersRef.get()
    val newListeners = oldListeners.filter(_ ne listener)
    if (!listenersRef.compareAndSet(oldListeners, newListeners)) removeListener(listener)
    else ()
  }

  private[zio] def notifyListeners[T](
    key: MetricKey[MetricKeyType.WithIn[T]],
    value: T,
    eventType: MetricEventType
  )(implicit trace: Trace, unsafe: Unsafe): Unit = {
    val listeners = listenersRef.get()
    val len       = listeners.length

    if (len > 0) {
      var i = 0
      key.keyType match {
        case MetricKeyType.Gauge =>
          eventType match {
            case MetricEventType.Modify =>
              while (i < len) {
                listeners(i).modifyGauge(key.asInstanceOf[MetricKey.Gauge], value.asInstanceOf[Double])
                i = i + 1
              }
            case MetricEventType.Update =>
              while (i < len) {
                listeners(i).updateGauge(key.asInstanceOf[MetricKey.Gauge], value.asInstanceOf[Double])
                i = i + 1
              }
          }
        case MetricKeyType.Histogram(_) =>
          while (i < len) {
            listeners(i).updateHistogram(key.asInstanceOf[MetricKey.Histogram], value.asInstanceOf[Double])
            i = i + 1
          }
        case MetricKeyType.Frequency =>
          while (i < len) {
            listeners(i).updateFrequency(key.asInstanceOf[MetricKey.Frequency], value.asInstanceOf[String])
            i = i + 1
          }
        case MetricKeyType.Summary(_, _, _, _) =>
          val sv = value.asInstanceOf[MetricHook.SummaryValue]
          while (i < len) {
            listeners(i).updateSummary(key.asInstanceOf[MetricKey.Summary], sv.value, sv.timestamp)
            i = i + 1
          }
        case MetricKeyType.Counter =>
          while (i < len) {
            listeners(i).updateCounter(key.asInstanceOf[MetricKey.Counter], value.asInstanceOf[Double])
            i = i + 1
          }
      }
    }
  }

}
