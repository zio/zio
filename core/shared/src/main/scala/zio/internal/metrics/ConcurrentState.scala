package zio.internal.metrics

import zio._
import zio.metrics._

import java.util.concurrent.atomic.{AtomicReference, DoubleAdder}
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
      override def gaugeChanged(key: MetricKey.Gauge, value: Double, delta: Double): Unit = {
        val iterator = listeners.iterator
        while (iterator.hasNext) {
          val listener = iterator.next()
          listener.gaugeChanged(key, value, delta)
        }
      }

      override def counterChanged(key: MetricKey.Counter, value: Double, delta: Double): Unit = {
        val iterator = listeners.iterator
        while (iterator.hasNext) {
          val listener = iterator.next()
          listener.counterChanged(key, value, delta)
        }
      }

      override def histogramChanged(key: MetricKey.Histogram, value: MetricState): Unit = {
        val iterator = listeners.iterator
        while (iterator.hasNext) {
          val listener = iterator.next()
          listener.histogramChanged(key, value)
        }
      }

      override def summaryChanged(key: MetricKey.Summary, value: MetricState): Unit = {
        val iterator = listeners.iterator
        while (iterator.hasNext) {
          val listener = iterator.next()
          listener.summaryChanged(key, value)
        }
      }

      override def setChanged(key: MetricKey.SetCount, value: MetricState): Unit = {
        val iterator = listeners.iterator
        while (iterator.hasNext) {
          val listener = iterator.next()
          listener.setChanged(key, value)
        }
      }
    }

  private val map: ConcurrentHashMap[MetricKey, ConcurrentMetricState] =
    new ConcurrentHashMap[MetricKey, ConcurrentMetricState]()

  private[zio] def snapshot: Map[MetricKey, MetricState] = {
    val iterator = map.entrySet().iterator()
    val result   = scala.collection.mutable.Map[MetricKey, MetricState]()
    while (iterator.hasNext) {
      val value = iterator.next()
      result.put(value.getKey(), value.getValue().toMetricState)
    }
    result.toMap
  }

  /**
   * Increase a named counter by some value.
   */
  def getCounter(key: MetricKey.Counter): Counter = {
    var value = map.get(key)
    if (value eq null) {
      val counter = ConcurrentMetricState.Counter(key, "", new DoubleAdder)
      map.putIfAbsent(key, counter)
      value = map.get(key)
    }
    value match {
      case counter: ConcurrentMetricState.Counter =>
        new Counter {
          override def increment(value: Double): UIO[Unit] =
            ZIO.succeed {
              val (v, d) = counter.increment(value)
              listener.counterChanged(key, v, d)
            }
        }
      case _ => Counter.none
    }
  }

  def getGauge(key: MetricKey.Gauge): Gauge = {
    var value = map.get(key)
    if (value eq null) {
      val gauge = ConcurrentMetricState.Gauge(key, "", new AtomicReference(0.0))
      map.putIfAbsent(key, gauge)
      value = map.get(key)
    }
    value match {
      case gauge: ConcurrentMetricState.Gauge =>
        new Gauge {
          def set(value: Double): UIO[Unit] =
            ZIO.succeed {
              val (v, d) = gauge.set(value)
              listener.gaugeChanged(key, v, d)
            }
          def adjust(value: Double): UIO[Unit] =
            ZIO.succeed {
              val (v, d) = gauge.adjust(value)
              listener.gaugeChanged(key, v, d)
            }
        }
      case _ => Gauge.none
    }
  }

  /**
   * Observe a value and feed it into a histogram
   */
  def getHistogram(key: MetricKey.Histogram): Histogram = {
    var value = map.get(key)
    if (value eq null) {
      val histogram = ConcurrentMetricState.Histogram(
        key,
        "",
        ConcurrentHistogram.manual(key.boundaries)
      )
      map.putIfAbsent(key, histogram)
      value = map.get(key)
    }
    value match {
      case histogram: ConcurrentMetricState.Histogram =>
        new Histogram {
          def observe(value: Double): UIO[Unit] =
            ZIO.succeed {
              histogram.observe(value)
              listener.histogramChanged(key, histogram.toMetricState)
            }
        }
      case _ => Histogram.none
    }
  }

  def getSummary(
    key: MetricKey.Summary
  ): Summary = {
    var value = map.get(key)
    if (value eq null) {
      val summary = ConcurrentMetricState.Summary(
        key,
        "",
        ConcurrentSummary.manual(key.maxSize, key.maxAge, key.error, key.quantiles)
      )
      map.putIfAbsent(key, summary)
      value = map.get(key)
    }
    value match {
      case summary: ConcurrentMetricState.Summary =>
        new Summary {
          def observe(value: Double, t: java.time.Instant): UIO[Unit] =
            ZIO.succeed {
              summary.observe(value, t)
              listener.summaryChanged(key, summary.toMetricState)
            }
        }
      case _ => Summary.none
    }
  }

  def getSetCount(key: MetricKey.SetCount): SetCount = {
    var value = map.get(key)
    if (value eq null) {
      val setCount = ConcurrentMetricState.SetCount(
        key,
        "",
        ConcurrentSetCount.manual()
      )
      map.putIfAbsent(key, setCount)
      value = map.get(key)
    }
    value match {
      case setCount: ConcurrentMetricState.SetCount =>
        new SetCount {
          def observe(word: String): UIO[Unit] =
            ZIO.succeed {
              setCount.observe(word)
              listener.setChanged(key, setCount.toMetricState)
            }
        }
      case _ => SetCount.none
    }
  }
}
