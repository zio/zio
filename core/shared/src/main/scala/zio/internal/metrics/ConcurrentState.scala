package zio.internal.metrics

import zio._
import zio.metrics._
import zio.metrics.clients._

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
      override def unsafeGaugeChanged(key: MetricKey.Gauge, value: Double, delta: Double): Unit = {
        val iterator = listeners.iterator
        while (iterator.hasNext) {
          val listener = iterator.next()
          listener.unsafeGaugeChanged(key, value, delta)
        }
      }

      override def unsafeCounterChanged(key: MetricKey.Counter, value: Double, delta: Double): Unit = {
        val iterator = listeners.iterator
        while (iterator.hasNext) {
          val listener = iterator.next()
          listener.unsafeCounterChanged(key, value, delta)
        }
      }

      override def unsafeHistogramChanged(key: MetricKey.Histogram, value: MetricState): Unit = {
        val iterator = listeners.iterator
        while (iterator.hasNext) {
          val listener = iterator.next()
          listener.unsafeHistogramChanged(key, value)
        }
      }

      override def unsafeSummaryChanged(key: MetricKey.Summary, value: MetricState): Unit = {
        val iterator = listeners.iterator
        while (iterator.hasNext) {
          val listener = iterator.next()
          listener.unsafeSummaryChanged(key, value)
        }
      }

      override def unsafeSetChanged(key: MetricKey.SetCount, value: MetricState): Unit = {
        val iterator = listeners.iterator
        while (iterator.hasNext) {
          val listener = iterator.next()
          listener.unsafeSetChanged(key, value)
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
      val counter = ConcurrentMetricState.Counter(key, "", ConcurrentCounter.manual())
      map.putIfAbsent(key, counter)
      value = map.get(key)
    }
    val counter = value.asInstanceOf[ConcurrentMetricState.Counter]
    new Counter {
      override def count: UIO[Double] =
        ZIO.succeed(counter.count)
      override def increment(value: Double): UIO[Unit] =
        ZIO.succeed {
          val (v, d) = counter.increment(value)
          listener.unsafeCounterChanged(key, v, d)
        }
    }
  }

  def getGauge(key: MetricKey.Gauge): Gauge = {
    var value = map.get(key)
    if (value eq null) {
      val gauge = ConcurrentMetricState.Gauge(key, "", ConcurrentGauge.manual(0.0))
      map.putIfAbsent(key, gauge)
      value = map.get(key)
    }
    val gauge = value.asInstanceOf[ConcurrentMetricState.Gauge]
    new Gauge {
      def adjust(value: Double): UIO[Unit] =
        ZIO.succeed {
          val (v, d) = gauge.adjust(value)
          listener.unsafeGaugeChanged(key, v, d)
        }
      def set(value: Double): UIO[Unit] =
        ZIO.succeed {
          val (v, d) = gauge.set(value)
          listener.unsafeGaugeChanged(key, v, d)
        }
      def value: UIO[Double] =
        ZIO.succeed(gauge.get)
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
    val histogram = value.asInstanceOf[ConcurrentMetricState.Histogram]
    new Histogram {
      def buckets: UIO[Chunk[(Double, Long)]] =
        ZIO.succeed(histogram.histogram.snapshot())
      def count: UIO[Long] =
        ZIO.succeed(histogram.histogram.getCount())
      def observe(value: Double): UIO[Unit] =
        ZIO.succeed {
          histogram.observe(value)
          listener.unsafeHistogramChanged(key, histogram.toMetricState)
        }
      def sum: UIO[Double] =
        ZIO.succeed(histogram.histogram.getSum())
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
    val summary = value.asInstanceOf[ConcurrentMetricState.Summary]
    new Summary {
      val count: zio.UIO[Long] =
        ZIO.succeed(summary.summary.getCount())
      def observe(value: Double): UIO[Unit] =
        ZIO.succeed {
          summary.observe(value, Instant.now)
          listener.unsafeSummaryChanged(key, summary.toMetricState)
        }
      val quantileValues: zio.UIO[zio.Chunk[(Double, Option[Double])]] =
        ZIO.succeed(summary.summary.snapshot(Instant.now))
      val sum: zio.UIO[Double] =
        ZIO.succeed(summary.summary.getSum())
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
    val setCount = value.asInstanceOf[ConcurrentMetricState.SetCount]
    new SetCount {
      def observe(word: String): UIO[Unit] =
        ZIO.succeed {
          setCount.observe(word)
          listener.unsafeSetChanged(key, setCount.toMetricState)
        }
      val occurrences: UIO[Chunk[(String, Long)]] =
        ZIO.succeed(setCount.setCount.snapshot())
    }
  }
}
