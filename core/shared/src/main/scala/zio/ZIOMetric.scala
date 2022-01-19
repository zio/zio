/*
 * Copyright 2020-2022 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A `ZIOMetric` is able to add collection of metrics to a `ZIO` effect without
 * changing its environment or error types. Aspects are the idiomatic way of
 * adding collection of metrics to effects.
 */
sealed trait ZIOMetric[-A] extends ZIOAspect[Nothing, Any, Nothing, Any, Nothing, A]

object ZIOMetric {
  type MetricAspect[-A] = ZIOAspect[Nothing, Any, Nothing, Any, Nothing, A]

  /**
   * A metric aspect that increments the specified counter each time the effect
   * it is applied to succeeds.
   */
  def count(name: String, tags: MetricLabel*): Counter[Any] =
    new Counter[Any](
      name,
      Chunk.fromIterable(tags),
      metric =>
        new MetricAspect[Any] {
          def apply[R, E, A1](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
            zio.tap(_ => metric.increment)
        }
    )

  /**
   * A metric aspect that increments the specified counter by a given value.
   */
  def countValue(name: String, tags: MetricLabel*): Counter[Double] =
    new Counter[Double](
      name,
      Chunk.fromIterable(tags),
      metric =>
        new MetricAspect[Double] {
          def apply[R, E, A1 <: Double](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
            zio.tap(metric.increment)
        }
    )

  /**
   * A metric aspect that increments the specified counter by a given value.
   */
  def countValueWith[A](name: String, tags: MetricLabel*)(f: A => Double): Counter[A] =
    new Counter[A](
      name,
      Chunk.fromIterable(tags),
      metric =>
        new MetricAspect[A] {
          def apply[R, E, A1 <: A](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
            zio.tap(a => metric.increment(f(a)))
        }
    )

  /**
   * A metric aspect that increments the specified counter each time the effect
   * it is applied to fails.
   */
  def countErrors(name: String, tags: MetricLabel*): Counter[Any] =
    new Counter[Any](
      name,
      Chunk.fromIterable(tags),
      metric =>
        new MetricAspect[Any] {
          def apply[R, E, A1](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
            zio.tapError(_ => metric.increment)
        }
    )

  /**
   * A metric aspect that sets a gauge each time the effect it is applied to
   * succeeds.
   */
  def setGauge(name: String, tags: MetricLabel*): Gauge[Double] =
    new Gauge[Double](
      name,
      Chunk.fromIterable(tags),
      metric =>
        new MetricAspect[Double] {
          def apply[R, E, A1 <: Double](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
            zio.tap(metric.set)
        }
    )

  /**
   * A metric aspect that sets a gauge each time the effect it is applied to
   * succeeds, using the specified function to transform the value returned by
   * the effect to the value to set the gauge to.
   */
  def setGaugeWith[A](name: String, tags: MetricLabel*)(f: A => Double): Gauge[A] =
    new Gauge[A](
      name,
      Chunk.fromIterable(tags),
      metric =>
        new MetricAspect[A] {
          def apply[R, E, A1 <: A](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
            zio.tap(a => metric.set(f(a)))
        }
    )

  /**
   * A metric aspect that adjusts a gauge each time the effect it is applied to
   * succeeds.
   */
  def adjustGauge(name: String, tags: MetricLabel*): Gauge[Double] =
    new Gauge[Double](
      name,
      Chunk.fromIterable(tags),
      metric =>
        new MetricAspect[Double] {
          def apply[R, E, A1 <: Double](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
            zio.tap(metric.adjust)
        }
    )

  /**
   * A metric aspect that adjusts a gauge each time the effect it is applied to
   * succeeds, using the specified function to transform the value returned by
   * the effect to the value to adjust the gauge with.
   */
  def adjustGaugeWith[A](name: String, tags: MetricLabel*)(f: A => Double): Gauge[A] =
    new Gauge[A](
      name,
      Chunk.fromIterable(tags),
      metric =>
        new MetricAspect[A] {
          def apply[R, E, A1 <: A](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
            zio.tap(a => metric.adjust(f(a)))
        }
    )

  /**
   * A metric aspect that tracks how long the effect it is applied to takes to
   * complete execution, recording the results in a histogram.
   */
  def observeDurations[A](name: String, boundaries: Histogram.Boundaries, tags: MetricLabel*)(
    f: Duration => Double
  ): Histogram[A] =
    new Histogram[A](
      name,
      boundaries,
      Chunk.fromIterable(tags),
      metric =>
        new MetricAspect[A] {
          def apply[R, E, A1 <: A](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
            zio.timedWith(ZIO.succeed(java.lang.System.nanoTime)).flatMap { case (duration, a) =>
              metric.observe(f(duration)).as(a)
            }
        }
    )

  /**
   * A metric aspect that adds a value to a histogram each time the effect it is
   * applied to succeeds.
   */
  def observeHistogram(
    name: String,
    boundaries: Histogram.Boundaries,
    tags: MetricLabel*
  ): Histogram[Double] =
    new Histogram[Double](
      name,
      boundaries,
      Chunk.fromIterable(tags),
      metric =>
        new MetricAspect[Double] {
          def apply[R, E, A1 <: Double](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
            zio.tap(metric.observe)
        }
    )

  /**
   * A metric aspect that adds a value to a histogram each time the effect it is
   * applied to succeeds, using the specified function to transform the value
   * returned by the effect to the value to add to the histogram.
   */
  def observeHistogramWith[A](name: String, boundaries: Histogram.Boundaries, tags: MetricLabel*)(
    f: A => Double
  ): Histogram[A] =
    new Histogram[A](
      name,
      boundaries,
      Chunk.fromIterable(tags),
      metric =>
        new MetricAspect[A] {
          def apply[R, E, A1 <: A](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
            zio.tap(a => metric.observe(f(a)))
        }
    )

  /**
   * A metric aspect that adds a value to a summary each time the effect it is
   * applied to succeeds.
   */
  def observeSummary(
    name: String,
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double],
    tags: MetricLabel*
  ): Summary[Double] =
    new Summary[Double](
      name,
      maxAge,
      maxSize,
      error,
      quantiles,
      Chunk.fromIterable(tags),
      metric =>
        new MetricAspect[Double] {
          def apply[R, E, A1 <: Double](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
            zio.tap(metric.observe)
        }
    )

  /**
   * A metric aspect that adds a value to a summary each time the effect it is
   * applied to succeeds, using the specified function to transform the value
   * returned by the effect to the value to add to the summary.
   */
  def observeSummaryWith[A](
    name: String,
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double],
    tags: MetricLabel*
  )(f: A => Double): Summary[A] =
    new Summary[A](
      name,
      maxAge,
      maxSize,
      error,
      quantiles,
      Chunk.fromIterable(tags),
      metric =>
        new MetricAspect[A] {
          def apply[R, E, A1 <: A](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
            zio.tap(a => metric.observe(f(a)))
        }
    )

  /**
   * A metric aspect that counts the number of occurrences of each distinct
   * value returned by the effect it is applied to.
   */
  def occurrences(name: String, setTag: String, tags: MetricLabel*): SetCount[String] =
    new SetCount[String](
      name,
      setTag,
      Chunk.fromIterable(tags),
      metric =>
        new MetricAspect[String] {
          def apply[R, E, A1 <: String](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
            zio.tap(metric.observe)
        }
    )

  /**
   * A metric aspect that counts the number of occurrences of each distinct
   * value returned by the effect it is applied to, using the specified function
   * to transform the value returned by the effect to the value to count the
   * occurrences of.
   */
  def occurrencesWith[A](name: String, setTag: String, tags: MetricLabel*)(
    f: A => String
  ): SetCount[A] =
    new SetCount[A](
      name,
      setTag,
      Chunk.fromIterable(tags),
      metric =>
        new MetricAspect[A] {
          def apply[R, E, A1 <: A](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
            zio.tap(a => metric.observe(f(a)))
        }
    )

  /**
   * A `Counter` is a metric representing a single numerical value that may be
   * incremented over time. A typical use of this metric would be to track the
   * number of a certain type of request received. With a counter the quantity
   * of interest is the cumulative value over time, as opposed to a gauge where
   * the quantity of interest is the value as of a specific point in time.
   */
  final class Counter[A](val name: String, val tags: Chunk[MetricLabel], aspect: Counter[A] => MetricAspect[A])
      extends ZIOMetric[A] { self =>

    private val appliedAspect                                  = aspect(this)
    private[zio] var counter                                   = internal.metrics.Counter(name, tags)
    private var counterRef: FiberRef[internal.metrics.Counter] = _

    private def withCounter[A](f: internal.metrics.Counter => UIO[A])(implicit trace: ZTraceElement): UIO[A] =
      if (counter ne null) f(counter) else counterRef.getWith(f)

    def apply[R, E, A1 <: A](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
      appliedAspect.apply(zio)

    /**
     * Returns a copy of this counter with the specified name and tags.
     */
    def copy(name: String = name, tags: Chunk[MetricLabel] = tags): Counter[A] =
      new Counter[A](name, tags, aspect)

    /**
     * Returns the current value of this counter.
     */
    def count(implicit trace: ZTraceElement): UIO[Double] =
      withCounter(_.count)

    /**
     * Returns whether this counter is equal to the specified counter.
     */
    override def equals(that: Any): Boolean = that match {
      case that: Counter[_] if (self.metricType == that.metricType) =>
        self.name == that.name && self.tags == that.tags
      case _ => false
    }

    /**
     * Returns the hash code of this counter.
     */
    override def hashCode: Int =
      (metricType, name, tags).hashCode

    /**
     * Increments this counter by the specified amount.
     */
    def increment(value: Double)(implicit trace: ZTraceElement): UIO[Any] =
      withCounter(_.increment(value))

    /**
     * Increments this counter by one.
     */
    def increment(implicit trace: ZTraceElement): UIO[Any] =
      withCounter(_.increment(1.0))

    /**
     * Converts this counter metric to one where the tags depend on the measured
     * effect's result value
     */
    def taggedWith(f: A => Chunk[MetricLabel]): ZIOMetric[A] = {
      val cloned = copy()
      cloned.counterRef = ZFiberRef.unsafeMake(cloned.counter)
      cloned.counter = null

      new ZIOMetric[A] {
        override final def apply[R, E, A1 <: A](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
          cloned.appliedAspect(zio.tap(changeCounter))

        private def changeCounter(value: A)(implicit trace: ZTraceElement): UIO[Unit] =
          cloned.counterRef.update { counter =>
            val extraTags = f(value)
            val allTags   = cloned.tags ++ extraTags
            if (counter.metricKey.tags != allTags) {
              internal.metrics.Counter(cloned.name, allTags)
            } else {
              counter
            }
          }
      }
    }

    /**
     * The type of this counter.
     */
    protected lazy val metricType =
      aspect.getClass
  }

  /**
   * A `Gauge` is a metric representing a single numerical value that may be set
   * or adjusted. A typical use of this metric would be to track the current
   * memory usage. With a guage the quantity of interest is the current value,
   * as opposed to a counter where the quantity of interest is the cumulative
   * values over time.
   */
  final class Gauge[A](val name: String, val tags: Chunk[MetricLabel], aspect: Gauge[A] => MetricAspect[A])
      extends ZIOMetric[A] { self =>
    private val appliedAspect = aspect(this)

    private[zio] var gauge                                 = internal.metrics.Gauge(name, tags)
    private var gaugeRef: FiberRef[internal.metrics.Gauge] = _

    private def withGauge[A](f: internal.metrics.Gauge => UIO[A])(implicit trace: ZTraceElement): UIO[A] =
      if (gauge ne null) f(gauge) else gaugeRef.getWith(f)

    def apply[R, E, A1 <: A](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
      appliedAspect.apply(zio)

    /**
     * Adjusts this gauge by the specified amount.
     */
    def adjust(value: Double)(implicit trace: ZTraceElement): UIO[Any] =
      withGauge(_.adjust(value))

    /**
     * Returns a copy of this gauge with the specified name and tags.
     */
    def copy(name: String = name, tags: Chunk[MetricLabel] = tags): Gauge[A] =
      new Gauge[A](name, tags, aspect)

    /**
     * Returns whether this gauge is equal to the specified gauge.
     */
    override def equals(that: Any): Boolean = that match {
      case that: Gauge[_] if (self.metricType == that.metricType) =>
        self.name == that.name && self.tags == that.tags
      case _ => false
    }

    /**
     * Returns the hash code of this gauge.
     */
    override def hashCode: Int =
      (metricType, name, tags).hashCode

    /**
     * Sets this gauge to the specified value.
     */
    def set(value: Double)(implicit trace: ZTraceElement): UIO[Any] =
      withGauge(_.set(value))

    /**
     * Returns the current value of this gauge.
     */
    def value(implicit trace: ZTraceElement): UIO[Double] =
      withGauge(_.value)

    /**
     * Converts this gauge metric to one where the tags depend on the measured
     * effect's result value
     */
    def taggedWith(f: A => Chunk[MetricLabel]): ZIOMetric[A] = {
      val cloned = copy()
      cloned.gaugeRef = ZFiberRef.unsafeMake(cloned.gauge)
      cloned.gauge = null
      new ZIOMetric[A] {
        override final def apply[R, E, A1 <: A](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
          cloned.apply(zio.tap(changeGauge))

        private def changeGauge(value: A)(implicit trace: ZTraceElement): UIO[Unit] =
          cloned.gaugeRef.update { gauge =>
            val extraTags = f(value)
            val allTags   = cloned.tags ++ extraTags
            if (gauge.metricKey.tags != allTags) {
              internal.metrics.Gauge(cloned.name, allTags)
            } else {
              gauge
            }
          }
      }
    }

    /**
     * The type of this gauge.
     */
    protected lazy val metricType =
      aspect.getClass
  }

  /**
   * A `Histogram` is a metric representing a collection of numerical values
   * with the distribution of the cumulative values over time. A typical use of
   * this metric would be to track the time to serve requests. Histograms allow
   * visualizing not only the value of the quantity being measured but its
   * distribution. Histograms are constructed with user specified boundaries
   * which describe the buckets to aggregate values into.
   */
  final class Histogram[A](
    val name: String,
    val boundaries: Histogram.Boundaries,
    val tags: Chunk[MetricLabel],
    aspect: Histogram[A] => MetricAspect[A]
  ) extends ZIOMetric[A] { self =>
    private val appliedAspect                                      = aspect(this)
    private[zio] var histogram                                     = internal.metrics.Histogram(name, boundaries, tags)
    private var histogramRef: FiberRef[internal.metrics.Histogram] = _

    private def withHistogram[A](f: internal.metrics.Histogram => UIO[A])(implicit trace: ZTraceElement): UIO[A] =
      if (histogram ne null) f(histogram) else histogramRef.getWith(f)

    def apply[R, E, A1 <: A](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
      appliedAspect.apply(zio)

    /**
     * Returns the current sum and count of values in each bucket of this
     * histogram.
     */
    def buckets(implicit trace: ZTraceElement): UIO[Chunk[(Double, Long)]] =
      withHistogram(_.buckets)

    /**
     * Returns the current count of values in this histogram.
     */
    def count(implicit trace: ZTraceElement): UIO[Long] =
      withHistogram(_.count)

    /**
     * Returns a copy of this histogram with the specified name, boundaries, and
     * tags.
     */
    def copy(
      name: String = name,
      boundaries: Histogram.Boundaries = boundaries,
      tags: Chunk[MetricLabel] = tags
    ): Histogram[A] =
      new Histogram[A](name, boundaries, tags, aspect)

    /**
     * Returns whether this histogram is equal to the specified histogram.
     */
    override def equals(that: Any): Boolean = that match {
      case that: Histogram[_] if (self.metricType == that.metricType) =>
        self.name == that.name &&
          self.boundaries == that.boundaries &&
          self.tags == that.tags
      case _ => false
    }

    /**
     * Returns the hash code of this histogram.
     */
    override def hashCode: Int =
      (metricType, name, boundaries, tags).hashCode

    /**
     * Adds the specified value to the distribution of values represented by
     * this histogram.
     */
    def observe(value: Double)(implicit trace: ZTraceElement): UIO[Any] =
      withHistogram(_.observe(value))

    /**
     * Returns the current sum of values in this histogram.
     */
    def sum(implicit trace: ZTraceElement): UIO[Double] =
      withHistogram(_.sum)

    /**
     * Converts this histogram metric to one where the tags depend on the
     * measured effect's result value
     */
    def taggedWith(f: A => Chunk[MetricLabel]): ZIOMetric[A] = {
      val cloned = self.copy()
      cloned.histogramRef = ZFiberRef.unsafeMake(cloned.histogram)
      cloned.histogram = null

      new ZIOMetric[A] {
        override final def apply[R, E, A1 <: A](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
          cloned.apply(zio.tap(changeHistogram))

        private def changeHistogram(value: A)(implicit trace: ZTraceElement): UIO[Unit] =
          cloned.histogramRef.update { histogram =>
            val extraTags = f(value)
            val allTags   = cloned.tags ++ extraTags
            if (histogram.metricKey.tags != allTags) {
              internal.metrics.Histogram(cloned.name, cloned.boundaries, allTags)
            } else {
              histogram
            }
          }
      }
    }

    /**
     * The type of this histogram.
     */
    protected lazy val metricType =
      aspect.getClass
  }

  object Histogram {
    final case class Boundaries(chunk: Chunk[Double])

    object Boundaries {

      def fromChunk(chunk: Chunk[Double]): Boundaries = Boundaries((chunk ++ Chunk(Double.MaxValue)).distinct)

      /**
       * A helper method to create histogram bucket boundaries for a histogram
       * with linear increasing values
       */
      def linear(start: Double, width: Double, count: Int): Boundaries =
        fromChunk(Chunk.fromArray(0.until(count).map(i => start + i * width).toArray))

      /**
       * A helper method to create histogram bucket boundaries for a histogram
       * with exponentially increasing values
       */
      def exponential(start: Double, factor: Double, count: Int): Boundaries =
        fromChunk(Chunk.fromArray(0.until(count).map(i => start * Math.pow(factor, i.toDouble)).toArray))
    }
  }

  /**
   * A `Summary` represents a sliding window of a time series along with metrics
   * for certain percentiles of the time series, referred to as quantiles.
   * Quantiles describe specified percentiles of the sliding window that are of
   * interest. For example, if we were using a summary to track the response
   * time for requests over the last hour then we might be interested in the
   * 50th percentile, 90th percentile, 95th percentile, and 99th percentile for
   * response times.
   */
  final class Summary[A](
    val name: String,
    val maxAge: Duration,
    val maxSize: Int,
    val error: Double,
    val quantiles: Chunk[Double],
    val tags: Chunk[MetricLabel],
    aspect: Summary[A] => MetricAspect[A]
  ) extends ZIOMetric[A] { self =>
    private val appliedAspect                                  = aspect(this)
    private[zio] var summary                                   = internal.metrics.Summary(name, maxAge, maxSize, error, quantiles, tags)
    private var summaryRef: FiberRef[internal.metrics.Summary] = _

    private def withSummary[A](f: internal.metrics.Summary => UIO[A])(implicit trace: ZTraceElement): UIO[A] =
      if (summary ne null) f(summary) else summaryRef.getWith(f)

    def apply[R, E, A1 <: A](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
      appliedAspect(zio)

    /**
     * Returns a copy of this summary with the specified name, maximum age,
     * maximum size, error, quantiles, and tags.
     */
    def copy(
      name: String = name,
      maxAge: Duration = maxAge,
      maxSize: Int = maxSize,
      error: Double = error,
      quantiles: Chunk[Double] = quantiles,
      tags: Chunk[MetricLabel] = tags
    ): Summary[A] =
      new Summary[A](name, maxAge, maxSize, error, quantiles, tags, aspect)

    /**
     * Returns the current count of all the values ever observed by this
     * summary.
     */
    def count(implicit trace: ZTraceElement): UIO[Long] =
      withSummary(_.count)

    /**
     * Returns whether this summary is equal to the specified summary.
     */
    override def equals(that: Any): Boolean = that match {
      case that: Summary[_] if (self.metricType == that.metricType) =>
        self.name == that.name &&
          self.maxAge == that.maxAge &&
          self.maxSize == that.maxSize &&
          self.error == that.error &&
          self.quantiles == that.quantiles &&
          self.tags == that.tags
      case _ => false
    }

    /**
     * Returns the hash code of this summary.
     */
    override def hashCode: Int =
      (metricType, name, maxAge, maxSize, error, quantiles, tags).hashCode

    /**
     * Adds the specified value to the time series represented by this summary,
     * also recording the `Instant` when the value was observed.
     */
    def observe(value: Double)(implicit trace: ZTraceElement): UIO[Any] =
      withSummary(_.observe(value))

    /**
     * Returns the values corresponding to each quantile in this summary.
     */
    def quantileValues(implicit trace: ZTraceElement): UIO[Chunk[(Double, Option[Double])]] =
      withSummary(_.quantileValues)

    /**
     * Returns the current sum of all the values ever observed by this summary.
     */
    def sum(implicit trace: ZTraceElement): UIO[Double] =
      withSummary(_.sum)

    /**
     * Converts this summary metric to one where the tags depend on the measured
     * effect's result value
     */
    def taggedWith(f: A => Chunk[MetricLabel]): ZIOMetric[A] = {
      val cloned = copy()
      cloned.summaryRef = ZFiberRef.unsafeMake(cloned.summary)
      cloned.summary = null

      new ZIOMetric[A] {
        override final def apply[R, E, A1 <: A](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
          cloned.apply(zio.tap(changeSummary))

        private def changeSummary(value: A)(implicit trace: ZTraceElement): UIO[Unit] =
          cloned.summaryRef.update { summary =>
            val extraTags = f(value)
            val allTags   = cloned.tags ++ extraTags
            if (summary.metricKey.tags != allTags) {
              internal.metrics.Summary(
                cloned.name,
                cloned.maxAge,
                cloned.maxSize,
                cloned.error,
                cloned.quantiles,
                allTags
              )
            } else {
              summary
            }
          }
      }
    }

    /**
     * The type of this summary.
     */
    protected lazy val metricType =
      aspect.getClass
  }

  /**
   * A `SetCount` represents the number of occurrences of specified values. You
   * can think of a dry vpimy as like a set of counters associated with each
   * value except that new counters will automatically be created when new
   * values are observed. This could be used to track the frequency of different
   * types of failures, for example.
   */
  final class SetCount[A](
    val name: String,
    val setTag: String,
    val tags: Chunk[MetricLabel],
    aspect: SetCount[A] => MetricAspect[A]
  ) extends ZIOMetric[A] { self =>
    private val appliedAspect                                    = aspect(this)
    private[zio] var setCount                                    = internal.metrics.SetCount(name, setTag, tags)
    private var setCountRef: FiberRef[internal.metrics.SetCount] = _

    private def withSetCount[A](f: internal.metrics.SetCount => UIO[A])(implicit trace: ZTraceElement): UIO[A] =
      if (setCount ne null) f(setCount) else setCountRef.getWith(f)

    def apply[R, E, A1 <: A](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
      appliedAspect(zio)

    /**
     * Returns a copy of this set count with the specified name, set tag, and
     * tags.
     */
    def copy(
      name: String = name,
      setTag: String = setTag,
      tags: Chunk[MetricLabel] = tags
    ): SetCount[A] =
      new SetCount[A](name, setTag, tags, aspect)

    /**
     * Returns whether this set count is equal to the specified set count.
     */
    override def equals(that: Any): Boolean = that match {
      case that: SetCount[_] if (self.metricType == that.metricType) =>
        self.name == that.name &&
          self.setTag == that.setTag &&
          self.tags == that.tags
      case _ => false
    }

    /**
     * Returns the hash code of this set count.
     */
    override def hashCode: Int =
      (metricType, name, setTag, tags).hashCode

    /**
     * Increments the counter associated with the specified value by one.
     */
    def observe(value: String)(implicit trace: ZTraceElement): UIO[Any] =
      withSetCount(_.observe(value))

    /**
     * Returns the number of occurrences of every value observed by this set
     * count.
     */
    def occurrences(implicit trace: ZTraceElement): UIO[Chunk[(String, Long)]] =
      withSetCount(_.occurrences)

    /**
     * Converts this set count metric to one where the tags depend on the
     * measured effect's result value
     */
    def taggedWith(f: A => Chunk[MetricLabel]): ZIOMetric[A] = {
      val cloned = copy()
      cloned.setCountRef = ZFiberRef.unsafeMake(cloned.setCount)
      cloned.setCount = null

      new ZIOMetric[A] {
        override final def apply[R, E, A1 <: A](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
          cloned.apply(zio.tap(changeSetCount))

        private def changeSetCount(value: A)(implicit trace: ZTraceElement): UIO[Unit] =
          cloned.setCountRef.update { setCount =>
            val extraTags = f(value)
            val allTags   = cloned.tags ++ extraTags
            if (setCount.metricKey.tags != allTags) {
              internal.metrics.SetCount(cloned.name, cloned.setTag, allTags)
            } else {
              setCount
            }
          }
      }
    }

    /**
     * The type of this set count.
     */
    protected lazy val metricType =
      aspect.getClass
  }
}
