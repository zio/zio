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
      override def unsafeGaugeObserved(key: MetricKey.Gauge, value: Double, delta: Double): Unit = {
        val iterator = listeners.iterator
        while (iterator.hasNext) {
          val listener = iterator.next()
          listener.unsafeGaugeObserved(key, value, delta)
        }
      }

      override def unsafeCounterObserved(key: MetricKey.Counter, value: Double, delta: Double): Unit = {
        val iterator = listeners.iterator
        while (iterator.hasNext) {
          val listener = iterator.next()
          listener.unsafeCounterObserved(key, value, delta)
        }
      }

      override def unsafeHistogramObserved(key: MetricKey.Histogram, value: Double): Unit = {
        val iterator = listeners.iterator
        while (iterator.hasNext) {
          val listener = iterator.next()
          listener.unsafeHistogramObserved(key, value)
        }
      }

      override def unsafeSummaryObserved(key: MetricKey.Summary, value: Double): Unit = {
        val iterator = listeners.iterator
        while (iterator.hasNext) {
          val listener = iterator.next()
          listener.unsafeSummaryObserved(key, value)
        }
      }

      override def unsafeSetObserved(key: MetricKey.SetCount, value: String): Unit = {
        val iterator = listeners.iterator
        while (iterator.hasNext) {
          val listener = iterator.next()
          listener.unsafeSetObserved(key, value)
        }
      }
    }

  private val map: ConcurrentHashMap[MetricKey, ConcurrentMetricState] =
    new ConcurrentHashMap[MetricKey, ConcurrentMetricState]()

  private[zio] def states: Map[MetricKey, MetricState] = {
    val iterator = map.entrySet().iterator()
    val result   = scala.collection.mutable.Map[MetricKey, MetricState]()
    while (iterator.hasNext) {
      val value = iterator.next()
      result.put(value.getKey(), value.getValue().toMetricState)
    }
    result.toMap
  }

  private[zio] def state(key: MetricKey): Option[MetricState] =
    Option(map.get(key)).map(_.toMetricState)

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
      override def count(implicit trace: ZTraceElement): UIO[Double] =
        ZIO.succeed(unsafeCount())
      override def increment(value: Double)(implicit trace: ZTraceElement): UIO[Unit] =
        ZIO.succeed(unsafeIncrement(value))

      private[zio] def unsafeCount(): Double = counter.count

      private[zio] def unsafeIncrement(value: Double): Unit = {
        val (v, d) = counter.increment(value)
        listener.unsafeCounterObserved(key, v, d)
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
      def adjust(value: Double)(implicit trace: ZTraceElement): UIO[Unit] =
        ZIO.succeed {
          val (v, d) = gauge.adjust(value)
          listener.unsafeGaugeObserved(key, v, d)
        }
      def set(value: Double)(implicit trace: ZTraceElement): UIO[Unit] =
        ZIO.succeed {
          val (v, d) = gauge.set(value)
          listener.unsafeGaugeObserved(key, v, d)
        }
      def value(implicit trace: ZTraceElement): UIO[Double] =
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
        ConcurrentHistogram.manual(key.boundaries.chunk)
      )
      map.putIfAbsent(key, histogram)
      value = map.get(key)
    }
    val histogram = value.asInstanceOf[ConcurrentMetricState.Histogram]
    new Histogram {
      def buckets(implicit trace: ZTraceElement): UIO[Chunk[(Double, Long)]] =
        ZIO.succeed(histogram.histogram.snapshot())
      def count(implicit trace: ZTraceElement): UIO[Long] =
        ZIO.succeed(histogram.histogram.getCount())
      def observe(value: Double)(implicit trace: ZTraceElement): UIO[Unit] =
        ZIO.succeed(unsafeObserve(value))

      def sum(implicit trace: ZTraceElement): UIO[Double] =
        ZIO.succeed(histogram.histogram.getSum())

      def unsafeObserve(value: Double): Unit = {
        histogram.observe(value)
        listener.unsafeHistogramObserved(key, value)
      }
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
      def count(implicit trace: ZTraceElement): zio.UIO[Long] =
        ZIO.succeed(summary.summary.getCount())
      def observe(value: Double)(implicit trace: ZTraceElement): UIO[Unit] =
        ZIO.succeed {
          summary.observe(value, Instant.now)
          listener.unsafeSummaryObserved(key, value)
        }
      def quantileValues(implicit trace: ZTraceElement): zio.UIO[zio.Chunk[(Double, Option[Double])]] =
        ZIO.succeed(summary.summary.snapshot(Instant.now))
      def sum(implicit trace: ZTraceElement): zio.UIO[Double] =
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
      def observe(word: String)(implicit trace: ZTraceElement): UIO[Unit] =
        ZIO.succeed(unsafeObserve(word))

      def occurrences(implicit trace: ZTraceElement): UIO[Chunk[(String, Long)]] =
        ZIO.succeed(unsafeOccurrences)

      def occurrences(word: String)(implicit trace: ZTraceElement): UIO[Long] =
        ZIO.succeed(unsafeOccurrences(word))

      def unsafeObserve(word: String): Unit = {
        setCount.observe(word)
        listener.unsafeSetObserved(key, word)
      }

      private[zio] def unsafeOccurrences: Chunk[(String, Long)] =
        setCount.setCount.snapshot()

      private[zio] def unsafeOccurrences(word: String): Long =
        setCount.setCount.getCount(word)
    }
  }
}
