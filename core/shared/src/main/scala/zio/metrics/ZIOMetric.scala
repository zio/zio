/*
 * Copyright 2022 John A. De Goes and the ZIO Contributors
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

package zio.metrics

import zio._
import zio.metrics.MetricKey
import zio.metrics.MetricKeyType.Histogram

import zio.internal.metrics._

/**
 * A `ZIOMetric[In, Out]` represents a concurrent metric, which accepts updates
 * of type `In`, which are aggregated to a stateful value of type `Out`.
 *
 * For example, a counter metric would have type `ZIOMetric[Double, Double]`,
 * representing the fact that the metric can be updated with doubles (the amount
 * to increment or decrement the counter by), and the state of the counter is a
 * double.
 *
 * There are five primitive metric types supported by ZIO:
 *
 *   - Counters
 *   - Gauges
 *   - Histograms
 *   - Summaries
 *   - Set Counts
 *
 * The companion object contains constructors for these primitive metrics. All
 * metrics are derived from these primitive metrics.
 */
trait ZIOMetric[+Type, -In, +Out] extends ZIOAspect[Nothing, Any, Nothing, Any, Nothing, In] { self =>
  type KeyIn
  type KeyOut

  /**
   * Retrieves the metric key associated with the metric, which uniquely
   * identifies the metric, including its name, type, tags, and construction
   * parameters.
   */
  val key: MetricKey[Type, KeyIn, KeyOut]

  /**
   * Retrieves the metric hook that powers the metric. There is no need to use
   * this in application-level code.
   */
  private[zio] def hook: ModifiedMetricHook[KeyIn, KeyOut, In, Out]

  /**
   * Applies the metric computation to the result of the specified effect.
   */
  final def apply[R, E, A1 <: In](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
    zio.tap(update(_))

  /**
   * Returns a new metric that is powered by this one, but which accepts updates
   * of the specified new type, which must be transformable to the input type of
   * this metric.
   */
  def contramap[In2](f: In2 => In): ZIOMetric.Full[Type, KeyIn, KeyOut, In2, Out] =
    ZIOMetric(key, hook.contramap(f))

  /**
   * Returns a new metric that is powered by this one, but which outputs a new
   * state type, determined by transforming the state type of this metric by the
   * specified function.
   */
  def map[Out2](f: Out => Out2): ZIOMetric.Full[Type, KeyIn, KeyOut, In, Out2] =
    ZIOMetric(key, hook.map(f))

  /**
   * Returns a new metric, which is identical in every way to this one, except
   * the specified tag will be added to the tags of this metric.
   */
  def tagged(key: String, value: String): ZIOMetric.Full[Type, KeyIn, KeyOut, In, Out] =
    tagged(MetricLabel(key, value))

  /**
   * Returns a new metric, which is identical in every way to this one, except
   * the specified tags have been added to the tags of this metric.
   */
  def tagged(extraTag: MetricLabel, extraTags: MetricLabel*): ZIOMetric.Full[Type, KeyIn, KeyOut, In, Out] =
    tagged(Chunk(extraTag) ++ Chunk.fromIterable(extraTags))

  /**
   * Returns a new metric, which is identical in every way to this one, except
   * the specified tags have been added to the tags of this metric.
   */
  def tagged(extraTags: Chunk[MetricLabel]): ZIOMetric.Full[Type, KeyIn, KeyOut, In, Out] =
    ZIOMetric(key.tagged(extraTags), hook)

  /**
   * Returns a ZIO aspect that can update a metric derived from this one, but
   * which can dynamically augment the metric with new tags based on the success
   * value of the effects that it is applied to. Note that this method does not
   * return a new metric, because it cannot: dynamically computing tags based on
   * success values results in the loss of structure that all metrics must have.
   */
  def taggedWith[In1 <: In](f: In1 => Chunk[MetricLabel]): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, In1] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, In1] {
      def apply[R, E, A1 <: In1](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
        zio.map { (a: A1) =>
          key.tagged(f(a)).metricHook.update(self.hook.contramapper(a))

          a
        }
    }

  /**
   * Updates the metric with the specified update message. For example, if the
   * metric were a counter, the update would increment the method by the
   * provided amount.
   */
  def update(in: In)(implicit trace: ZTraceElement): UIO[Unit] = ZIO.succeed(unsafeUpdate(in))

  /**
   * Retrieves a snapshot of the value of the metric at this moment in time.
   */
  def value(implicit trace: ZTraceElement): UIO[Out] = ZIO.succeed(unsafeValue())

  private[zio] def unsafeUpdate(in: In): Unit = hook.update(in)

  private[zio] def unsafeValue(): Out = hook.get()
}
object ZIOMetric {
  type Root[+Type, In, Out] = Full[Type, In, Out, In, Out]

  type Full[+Type, KeyIn0, KeyOut0, -In, +Out] =
    ZIOMetric[Type, In, Out] { type KeyIn = KeyIn0; type KeyOut = KeyOut0 }

  type Counter[-In] = ZIOMetric.Full[MetricKeyType.Counter, Double, MetricState.Counter, In, MetricState.Counter]
  type Gauge[-In]   = ZIOMetric.Full[MetricKeyType.Gauge, Double, MetricState.Gauge, In, MetricState.Gauge]
  type Histogram[-In] =
    ZIOMetric.Full[MetricKeyType.Histogram, Double, MetricState.Histogram, In, MetricState.Histogram]
  type Summary[-In]  = ZIOMetric.Full[MetricKeyType.Summary, Double, MetricState.Summary, In, MetricState.Summary]
  type SetCount[-In] = ZIOMetric.Full[MetricKeyType.SetCount, String, MetricState.SetCount, In, MetricState.SetCount]

  implicit class CounterSyntax[In](counter: Counter[In]) {
    def increment(implicit numeric: Numeric[In]): UIO[Unit] = counter.update(numeric.fromInt(1))

    def increment(value: In): UIO[Unit] = counter.update(value)

    def count: UIO[Double] = counter.value.map(_.count)
  }

  implicit class GaugeSyntax[In](gauge: Gauge[In]) {
    def set(value: In): UIO[Unit] = gauge.update(value)
  }

  implicit class HistogramSyntax[In](histogram: Histogram[In]) {
    def observe(value: In): UIO[Unit] = histogram.update(value)
  }

  implicit class SummarySyntax[In](summary: Summary[In]) {
    def observe(value: In): UIO[Unit] = summary.update(value)
  }

  implicit class SetCountSyntax[In](setCount: Summary[In]) {
    def observe(value: In): UIO[Unit] = setCount.update(value)
  }

  def fromMetricKey[Type, In, Out](
    key: MetricKey[Type, In, Out]
  ): ZIOMetric.Full[Type, In, Out, In, Out] =
    ZIOMetric(key, ModifiedMetricHook(key.metricHook))

  def apply[Type, KeyIn0, KeyOut0, In, Out](
    key0: MetricKey[Type, KeyIn0, KeyOut0],
    hook0: ModifiedMetricHook[KeyIn0, KeyOut0, In, Out]
  ): ZIOMetric.Full[Type, KeyIn0, KeyOut0, In, Out] =
    new ZIOMetric[Type, In, Out] {
      type KeyIn  = KeyIn0
      type KeyOut = KeyOut0

      val key: MetricKey[Type, KeyIn, KeyOut]              = key0
      def hook: ModifiedMetricHook[KeyIn, KeyOut, In, Out] = hook0
    }

  /**
   * A counter, which can be incremented.
   */
  def counter(name: String): Counter[Double] =
    fromMetricKey(MetricKey.counter(name))

  /**
   * A gauge, which can be set to a value.
   */
  def gauge(name: String): Gauge[Double] =
    fromMetricKey(MetricKey.gauge(name))

  /**
   * A numeric histogram metric, which keeps track of the count of numbers that
   * fall in buckets of the specified boundaries.
   */
  def histogram(name: String, boundaries: Histogram.Boundaries): Histogram[Double] =
    fromMetricKey(MetricKey.histogram(name, boundaries))

  /**
   * A summary metric.
   */
  def summary(name: String, maxAge: Duration, maxSize: Int, error: Double, quantiles: Chunk[Double]): Summary[Double] =
    fromMetricKey(MetricKey(name, Chunk.empty, MetricKeyType.Summary(maxAge, maxSize, error, quantiles)))

  /**
   * A string histogram metric, which keeps track of the counts of different
   * strings.
   */
  def setCount(name: String, setTag: String): SetCount[String] =
    fromMetricKey(MetricKey(name, Chunk.empty, MetricKeyType.SetCount(setTag)))
}
