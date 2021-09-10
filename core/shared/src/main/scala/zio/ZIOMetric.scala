/*
 * Copyright 2020-2021 John A. De Goes and the ZIO Contributors
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

/**
 * A `ZIOMetric` is able to add collection of metrics to a `ZIO` effect without
 * changing its environment or error types. Aspects are the idiomatic way of
 * adding collection of metrics to effects.
 */
sealed trait ZIOMetric[-A] extends ZIOAspect[Nothing, Any, Nothing, Any, Nothing, A]

object ZIOMetric {

  /**
   * A metric aspect that increments the specified counter each time the
   * effect it is applied to succeeds.
   */
  def count(name: String, tags: MetricLabel*): Counter[Any] =
    new Counter[Any](name, Chunk.fromIterable(tags)) { self =>
      def apply[R, E, A1](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(_ => increment)
    }

  /**
   * A metric aspect that increments the specified counter by a given value.
   */
  def countValue(name: String, tags: MetricLabel*): Counter[Double] =
    new Counter[Double](name, Chunk.fromIterable(tags)) {
      def apply[R, E, A1 <: Double](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(increment)
    }

  /**
   * A metric aspect that increments the specified counter by a given value.
   */
  def countValueWith[A](name: String, tags: MetricLabel*)(f: A => Double): Counter[A] =
    new Counter[A](name, Chunk.fromIterable(tags)) {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => increment(f(a)))
    }

  /**
   * A metric aspect that increments the specified counter each time the
   * effect it is applied to fails.
   */
  def countErrors(name: String, tags: MetricLabel*): Counter[Any] =
    new Counter[Any](name, Chunk.fromIterable(tags)) {
      def apply[R, E, A1](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tapError(_ => increment)
    }

  /**
   * A metric aspect that sets a gauge each time the effect it is applied to
   * succeeds.
   */
  def setGauge(name: String, tags: MetricLabel*): Gauge[Double] =
    new Gauge[Double](name, Chunk.fromIterable(tags)) {
      def apply[R, E, A1 <: Double](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(set)
    }

  /**
   * A metric aspect that sets a gauge each time the effect it is applied to
   * succeeds, using the specified function to transform the value returned by
   * the effect to the value to set the gauge to.
   */
  def setGaugeWith[A](name: String, tags: MetricLabel*)(f: A => Double): Gauge[A] =
    new Gauge[A](name, Chunk.fromIterable(tags)) {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => set(f(a)))
    }

  /**
   * A metric aspect that adjusts a gauge each time the effect it is applied
   * to succeeds.
   */
  def adjustGauge(name: String, tags: MetricLabel*): Gauge[Double] =
    new Gauge[Double](name, Chunk.fromIterable(tags)) {
      def apply[R, E, A1 <: Double](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(adjust)
    }

  /**
   * A metric aspect that adjusts a gauge each time the effect it is applied
   * to succeeds, using the specified function to transform the value returned
   * by the effect to the value to adjust the gauge with.
   */
  def adjustGaugeWith[A](name: String, tags: MetricLabel*)(f: A => Double): Gauge[A] =
    new Gauge[A](name, Chunk.fromIterable(tags)) {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => adjust(f(a)))
    }

  /**
   * A metric aspect that tracks how long the effect it is applied to takes to
   * complete execution, recording the results in a histogram.
   */
  def observeDurations[A](name: String, boundaries: Chunk[Double], tags: MetricLabel*)(
    f: Duration => Double
  ): Histogram[A] =
    new Histogram[A](name, boundaries, Chunk.fromIterable(tags)) {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.timedWith(ZIO.succeed(java.lang.System.nanoTime)).flatMap { case (duration, a) =>
          observe(f(duration)).as(a)
        }
    }

  /**
   * A metric aspect that adds a value to a histogram each time the effect it
   * is applied to succeeds.
   */
  def observeHistogram(name: String, boundaries: Chunk[Double], tags: MetricLabel*): Histogram[Double] =
    new Histogram[Double](name, boundaries, Chunk.fromIterable(tags)) {
      def apply[R, E, A1 <: Double](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(observe)
    }

  /**
   * A metric aspect that adds a value to a histogram each time the effect it
   * is applied to succeeds, using the specified function to transform the
   * value returned by the effect to the value to add to the histogram.
   */
  def observeHistogramWith[A](name: String, boundaries: Chunk[Double], tags: MetricLabel*)(
    f: A => Double
  ): Histogram[A] =
    new Histogram[A](name, boundaries, Chunk.fromIterable(tags)) {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => observe(f(a)))
    }

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
    new Summary[Double](name, maxAge, maxSize, error, quantiles, Chunk.fromIterable(tags)) {
      def apply[R, E, A1 <: Double](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(observe)
    }

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
    new Summary[A](name, maxAge, maxSize, error, quantiles, Chunk.fromIterable(tags)) {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => observe(f(a)))
    }

  /**
   * A metric aspect that counts the number of occurrences of each distinct
   * value returned by the effect it is applied to.
   */
  def occurrences(name: String, setTag: String, tags: MetricLabel*): SetCount[String] =
    new SetCount[String](name, setTag, Chunk.fromIterable(tags)) {
      def apply[R, E, A1 <: String](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(observe)
    }

  /**
   * A metric aspect that counts the number of occurrences of each distinct
   * value returned by the effect it is applied to, using the specified
   * function to transform the value returned by the effect to the value to
   * count the occurrences of.
   */
  def occurrencesWith[A](name: String, setTag: String, tags: MetricLabel*)(
    f: A => String
  ): SetCount[A] =
    new SetCount[A](name, setTag, Chunk.fromIterable(tags)) {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => observe(f(a)))
    }

  /**
   * A `Counter` is a metric representing a single numerical value that may be
   * incremented over time. A typical use of this metric would be to track the
   * number of a certain type of request received. With a counter the quantity
   * of interest is the cumulative value over time, as opposed to a gauge where
   * the quantity of interest is the value as of a specific point in time.
   */
  abstract class Counter[-A](final val name: String, final val tags: Chunk[MetricLabel]) extends ZIOMetric[A] { self =>
    private[this] val counter = internal.metrics.Counter(name, tags)

    def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1]

    /**
     * Returns a copy of this counter with the specified name and tags.
     */
    final def copy(name: String = name, tags: Chunk[MetricLabel] = tags): Counter[A] =
      new Counter[A](name, tags) {
        def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
          self.apply(zio)
        override protected lazy val metricType =
          self.metricType
      }

    /**
     * Returns the current value of this counter.
     */
    final def count: UIO[Double] =
      counter.count

    /**
     * Returns whether this counter is equal to the specified counter.
     */
    override final def equals(that: Any): Boolean = that match {
      case that: Counter[_] if (self.metricType == that.metricType) =>
        self.name == that.name && self.tags == that.tags
      case _ => false
    }

    /**
     * Returns the hash code of this counter.
     */
    override final def hashCode: Int =
      (metricType, name, tags).hashCode

    /**
     * Increments this counter by the specified amount.
     */
    final def increment(value: Double): UIO[Any] =
      counter.increment(value)

    /**
     * Increments this counter by one.
     */
    final val increment: UIO[Any] =
      counter.increment(1.0)

    /**
     * The type of this counter.
     */
    protected lazy val metricType =
      self.getClass
  }

  /**
   * A `Gauge` is a metric representing a single numerical value that may be
   * set or adjusted. A typical use of this metric would be to track the
   * current memory usage. With a guage the quantity of interest is the current
   * value, as opposed to a counter where the quantity of interest is the
   * cumulative values over time.
   */
  abstract class Gauge[A](final val name: String, final val tags: Chunk[MetricLabel]) extends ZIOMetric[A] { self =>
    private[this] val gauge = internal.metrics.Gauge(name, tags)

    def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1]

    /**
     * Adjusts this gauge by the specified amount.
     */
    def adjust(value: Double): UIO[Any] =
      gauge.adjust(value)

    /**
     * Returns a copy of this gauge with the specified name and tags.
     */
    final def copy(name: String = name, tags: Chunk[MetricLabel] = tags): Gauge[A] =
      new Gauge[A](name, tags) {
        def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
          self.apply(zio)
        override protected lazy val metricType =
          self.metricType
      }

    /**
     * Returns whether this gauge is equal to the specified gauge.
     */
    override final def equals(that: Any): Boolean = that match {
      case that: Gauge[_] if (self.metricType == that.metricType) =>
        self.name == that.name && self.tags == that.tags
      case _ => false
    }

    /**
     * Returns the hash code of this gauge.
     */
    override final def hashCode: Int =
      (metricType, name, tags).hashCode

    /**
     * Sets this gauge to the specified value.
     */
    def set(value: Double): UIO[Any] =
      gauge.set(value)

    /**
     * Returns the current value of this gauge.
     */
    final def value: UIO[Double] =
      gauge.value

    /**
     * The type of this gauge.
     */
    protected lazy val metricType =
      self.getClass
  }

  /**
   * A `Histogram` is a metric representing a collection of numerical values
   * with the distribution of the cumulative values over time. A typical use of
   * this metric would be to track the time to serve requests. Histograms allow
   * visualizing not only the value of the quantity being measured but its
   * distribution. Histograms are constructed with user specified boundaries
   * which describe the buckets to aggregate values into.
   */
  abstract class Histogram[A](
    final val name: String,
    final val boundaries: Chunk[Double],
    final val tags: Chunk[MetricLabel]
  ) extends ZIOMetric[A] { self =>
    private[this] val histogram = internal.metrics.Histogram(name, boundaries, tags)

    def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1]

    /**
     * Returns the current sum and count of values in each bucket of this
     * histogram.
     */
    val buckets: UIO[Chunk[(Double, Long)]] =
      histogram.buckets

    /**
     * Returns the current count of values in this histogram.
     */
    val count: UIO[Long] =
      histogram.count

    /**
     * Returns a copy of this histogram with the specified name, boundaries,
     * and tags.
     */
    final def copy(
      name: String = name,
      boundaries: Chunk[Double] = boundaries,
      tags: Chunk[MetricLabel] = tags
    ): Histogram[A] =
      new Histogram[A](name, boundaries, tags) {
        def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
          self.apply(zio)
        override protected lazy val metricType =
          self.metricType
      }

    /**
     * Returns whether this histogram is equal to the specified histogram.
     */
    override final def equals(that: Any): Boolean = that match {
      case that: Histogram[_] if (self.metricType == that.metricType) =>
        self.name == that.name &&
          self.boundaries == that.boundaries &&
          self.tags == that.tags
      case _ => false
    }

    /**
     * Returns the hash code of this histogram.
     */
    override final def hashCode: Int =
      (metricType, name, boundaries, tags).hashCode

    /**
     * Adds the specified value to the distribution of values represented by
     * this histogram.
     */
    def observe(value: Double): UIO[Any] =
      histogram.observe(value)

    /**
     * Returns the current sum of values in this histogram.
     */
    val sum: UIO[Double] =
      histogram.sum

    /**
     * The type of this histogram.
     */
    protected lazy val metricType =
      self.getClass
  }

  /**
   * A `Summary` represents a sliding window of a time series along with
   * metrics for certain percentiles of the time series, referred to as
   * quantiles. Quantiles describe specified percentiles of the sliding window
   * that are of interest. For example, if we were using a summary to track the
   * response time for requests over the last hour then we might be interested
   * in the 50th percentile, 90th percentile, 95th percentile, and 99th
   * percentile for response times.
   */
  abstract class Summary[A](
    final val name: String,
    final val maxAge: Duration,
    final val maxSize: Int,
    final val error: Double,
    final val quantiles: Chunk[Double],
    final val tags: Chunk[MetricLabel]
  ) extends ZIOMetric[A] { self =>
    private[this] val summary = internal.metrics.Summary(name, maxAge, maxSize, error, quantiles, tags)

    def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1]

    /**
     * Returns a copy of this summary with the specified name, maximum age,
     * maximum size, error, quantiles, and tags.
     */
    final def copy(
      name: String = name,
      maxAge: Duration = maxAge,
      maxSize: Int = maxSize,
      error: Double = error,
      quantiles: Chunk[Double] = quantiles,
      tags: Chunk[MetricLabel] = tags
    ): Summary[A] =
      new Summary[A](name, maxAge, maxSize, error, quantiles, tags) {
        def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
          self.apply(zio)
        override protected lazy val metricType =
          self.metricType
      }

    /**
     * Returns the current count of all the values ever observed by this
     * summary.
     */
    val count: UIO[Long] =
      summary.count

    /**
     * Returns whether this summary is equal to the specified summary.
     */
    override final def equals(that: Any): Boolean = that match {
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
    override final def hashCode: Int =
      (metricType, name, maxAge, maxSize, error, quantiles, tags).hashCode

    /**
     * Adds the specified value to the time series represented by this summary,
     * also recording the `Instant` when the value was observed.
     */
    def observe(value: Double): UIO[Any] =
      summary.observe(value)

    /**
     * Returns the values corresponding to each quantile in this summary.
     */
    val quantileValues: UIO[Chunk[(Double, Option[Double])]] =
      summary.quantileValues

    /**
     * Returns the current sum of all the values ever observed by this summary.
     */
    val sum: UIO[Double] =
      summary.sum

    /**
     * The type of this summary.
     */
    protected lazy val metricType =
      self.getClass
  }

  /**
   * A `SetCount` represents the number of occurrences of specified values.
   * You can think of a dry vpimy as like a set of counters associated with
   * each value except that new counters will automatically be created when new
   * values are observed. This could be used to track the frequency of
   * different types of failures, for example.
   */
  abstract class SetCount[A](final val name: String, final val setTag: String, final val tags: Chunk[MetricLabel])
      extends ZIOMetric[A] { self =>
    private[this] val setCount = internal.metrics.SetCount(name, setTag, tags)

    def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1]

    /**
     * Returns a copy of this set count with the specified name, set tag, and
     * tags.
     */
    final def copy(
      name: String = name,
      setTag: String = setTag,
      tags: Chunk[MetricLabel] = tags
    ): SetCount[A] =
      new SetCount[A](name, setTag, tags) {
        def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
          self.apply(zio)
        override protected lazy val metricType =
          self.metricType
      }

    /**
     * Returns whether this set count is equal to the specified set count.
     */
    override final def equals(that: Any): Boolean = that match {
      case that: SetCount[_] if (self.metricType == that.metricType) =>
        self.name == that.name &&
          self.setTag == that.setTag &&
          self.tags == that.tags
      case _ => false
    }

    /**
     * Returns the hash code of this set count.
     */
    override final def hashCode: Int =
      (metricType, name, setTag, tags).hashCode

    /**
     * Increments the counter associated with the specified value by one.
     */
    def observe(value: String): UIO[Any] =
      setCount.observe(value)

    /**
     * Returns the number of occurences of every value observed by this
     * set count.
     */
    val occurrences: UIO[Chunk[(String, Long)]] =
      setCount.occurrences

    /**
     * The type of this set count.
     */
    protected lazy val metricType =
      self.getClass
  }
}
