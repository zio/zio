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

    val hook0: MetricHook[_, zio.metrics.MetricState.Untyped] = map.get(key)

    if (hook0 eq null) {
      (key.keyType match {
        case MetricKeyType.Counter             => getCounter(key.asInstanceOf[MetricKey.Counter])
        case MetricKeyType.Frequency           => getSetCount(key.asInstanceOf[MetricKey.Frequency])
        case MetricKeyType.Gauge               => getGauge(key.asInstanceOf[MetricKey.Gauge])
        case MetricKeyType.Histogram(_)        => getHistogram(key.asInstanceOf[MetricKey.Histogram])
        case MetricKeyType.Summary(_, _, _, _) => getSummary(key.asInstanceOf[MetricKey.Summary])
      }).asInstanceOf[Result]
    } else hook0.asInstanceOf[Result]
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
      // Hoist casts out of while loops: `value.asInstanceOf[Double]` unboxes on every call,
      // so performing it once before the loop avoids repeated unboxing overhead.
      var i = 0
      key.keyType match {
        case MetricKeyType.Gauge =>
          val k = key.asInstanceOf[MetricKey.Gauge]
          val v = value.asInstanceOf[Double]
          eventType match {
            case MetricEventType.Modify =>
              while (i < len) {
                listeners(i).modifyGauge(k, v)
                i = i + 1
              }
            case MetricEventType.Update =>
              while (i < len) {
                listeners(i).updateGauge(k, v)
                i = i + 1
              }
          }
        case MetricKeyType.Histogram(_) =>
          val k = key.asInstanceOf[MetricKey.Histogram]
          val v = value.asInstanceOf[Double]
          while (i < len) {
            listeners(i).updateHistogram(k, v)
            i = i + 1
          }
        case MetricKeyType.Frequency =>
          val k = key.asInstanceOf[MetricKey.Frequency]
          val v = value.asInstanceOf[String]
          while (i < len) {
            listeners(i).updateFrequency(k, v)
            i = i + 1
          }
        case MetricKeyType.Summary(_, _, _, _) =>
          val k            = key.asInstanceOf[MetricKey.Summary]
          val (v, instant) = value.asInstanceOf[(Double, java.time.Instant)]
          while (i < len) {
            listeners(i).updateSummary(k, v, instant)
            i = i + 1
          }
        case MetricKeyType.Counter =>
          val k = key.asInstanceOf[MetricKey.Counter]
          val v = value.asInstanceOf[Double]
          while (i < len) {
            listeners(i).updateCounter(k, v)
            i = i + 1
          }
      }
    }
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
