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

package zio.metrics

import zio._
import zio.internal.metrics._

import java.time.Instant

/**
 * A `ZIOMetric` is able to add collection of metrics to a `ZIO` effect without
 * changing its environment or error types. Aspects are the idiomatic way of
 * adding collection of metrics to effects.
 */
trait ZIOMetric[-A] extends ZIOAspect[Nothing, Any, Nothing, Any, Nothing, A]

object ZIOMetric {

  /**
   * A metric aspect that increments the specified counter each time the
   * effect it is applied to succeeds.
   */
  def count(name: String, tags: Label*): Counter[Any] =
    new Counter[Any](name, tags: _*) {
      def apply[R, E, A1](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(_ => increment)
    }

  /**
   * A metric aspect that increments the specified counter by a given value.
   */
  def countValue(name: String, tags: Label*): Counter[Double] =
    new Counter[Double](name, tags: _*) {
      def apply[R, E, A1 <: Double](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(increment)
    }

  /**
   * A metric aspect that increments the specified counter by a given value.
   */
  def countValueWith[A](name: String, tags: Label*)(f: A => Double): Counter[A] =
    new Counter[A](name, tags: _*) {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => increment(f(a)))
    }

  /**
   * A metric aspect that increments the specified counter each time the
   * effect it is applied to fails.
   */
  def countErrors(name: String, tags: Label*): Counter[Any] =
    new Counter[Any](name, tags: _*) {
      def apply[R, E, A1](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tapError(_ => increment)
    }

  /**
   * A metric aspect that sets a gauge each time the effect it is applied to
   * succeeds.
   */
  def setGauge(name: String, tags: Label*): Gauge[Double] =
    new Gauge[Double](name, tags: _*) {
      def apply[R, E, A1 <: Double](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(set)
    }

  /**
   * A metric aspect that sets a gauge each time the effect it is applied to
   * succeeds, using the specified function to transform the value returned by
   * the effect to the value to set the gauge to.
   */
  def setGaugeWith[A](name: String, tags: Label*)(f: A => Double): Gauge[A] =
    new Gauge[A](name, tags: _*) {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => set(f(a)))
    }

  /**
   * A metric aspect that adjusts a gauge each time the effect it is applied
   * to succeeds.
   */
  def adjustGauge(name: String, tags: Label*): Gauge[Double] =
    new Gauge[Double](name, tags: _*) {
      def apply[R, E, A1 <: Double](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(adjust)
    }

  /**
   * A metric aspect that adjusts a gauge each time the effect it is applied
   * to succeeds, using the specified function to transform the value returned
   * by the effect to the value to adjust the gauge with.
   */
  def adjustGaugeWith[A](name: String, tags: Label*)(f: A => Double): Gauge[A] =
    new Gauge[A](name, tags: _*) {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => adjust(f(a)))
    }

  /**
   * A metric aspect that tracks how long the effect it is applied to takes to
   * complete execution, recording the results in a histogram.
   */
  def observeDurations[A](name: String, boundaries: Chunk[Double], tags: Label*)(
    f: Duration => Double
  ): Histogram[A] =
    new Histogram[A](name, boundaries, tags: _*) {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.timedWith(ZIO.succeed(java.lang.System.nanoTime)).flatMap { case (duration, a) =>
          observe(f(duration)).as(a)
        }
    }

  /**
   * A metric aspect that adds a value to a histogram each time the effect it
   * is applied to succeeds.
   */
  def observeHistogram(name: String, boundaries: Chunk[Double], tags: Label*): Histogram[Double] =
    new Histogram[Double](name, boundaries, tags: _*) {
      def apply[R, E, A1 <: Double](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(observe)
    }

  /**
   * A metric aspect that adds a value to a histogram each time the effect it
   * is applied to succeeds, using the specified function to transform the
   * value returned by the effect to the value to add to the histogram.
   */
  def observeHistogramWith[A](name: String, boundaries: Chunk[Double], tags: Label*)(
    f: A => Double
  ): Histogram[A] =
    new Histogram[A](name, boundaries, tags: _*) {
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
    tags: Label*
  ): Summary[Double] =
    new Summary[Double](name, maxAge, maxSize, error, quantiles, tags: _*) {
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
    tags: Label*
  )(f: A => Double): Summary[A] =
    new Summary[A](name, maxAge, maxSize, error, quantiles, tags: _*) {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => observe(f(a)))
    }

  /**
   * A metric aspect that counts the number of occurrences of each distinct
   * value returned by the effect it is applied to.
   */
  def occurrences(name: String, setTag: String, tags: Label*): Occurences[String] =
    new Occurences[String](name, setTag, tags: _*) {
      def apply[R, E, A1 <: String](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(observe)
    }

  /**
   * A metric aspect that counts the number of occurrences of each distinct
   * value returned by the effect it is applied to, using the specified
   * function to transform the value returned by the effect to the value to
   * count the occurrences of.
   */
  def occurrencesWith[A](name: String, setTag: String, tags: Label*)(
    f: A => String
  ): Occurences[A] =
    new Occurences[A](name, setTag, tags: _*) {
      def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1] =
        zio.tap(a => observe(f(a)))
    }

  abstract class Counter[-A](name: String, tags: Label*) extends ZIOMetric[A] {
    private val key     = MetricKey.Counter(name, Chunk.fromIterable(tags))
    private val counter = metricState.getCounter(key)
    val increment: UIO[Any] =
      counter.increment(1.0)
    def increment(value: Double): UIO[Any] =
      counter.increment(value)
    def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1]
  }

  abstract class Gauge[A](name: String, tags: Label*) extends ZIOMetric[A] {
    private val key   = MetricKey.Gauge(name, Chunk.fromIterable(tags))
    private val gauge = metricState.getGauge(key)
    def set(value: Double): UIO[Any] =
      gauge.set(value)
    def adjust(value: Double): UIO[Any] =
      gauge.adjust(value)
    def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1]
  }

  abstract class Histogram[A](name: String, boundaries: Chunk[Double], tags: Label*) extends ZIOMetric[A] {
    private val key       = MetricKey.Histogram(name, boundaries, Chunk.fromIterable(tags))
    private val histogram = metricState.getHistogram(key)
    def observe(value: Double): UIO[Any] =
      histogram.observe(value)
    def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1]
  }

  abstract class Summary[A](
    name: String,
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double],
    tags: Label*
  ) extends ZIOMetric[A] {
    private val key     = MetricKey.Summary(name, maxAge, maxSize, error, quantiles, Chunk.fromIterable(tags))
    private val summary = metricState.getSummary(key)
    def observe(value: Double): UIO[Any] =
      summary.observe(value, Instant.now())
    def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1]
  }

  abstract class Occurences[A](name: String, setTag: String, tags: Label*) extends ZIOMetric[A] {
    private val key      = MetricKey.SetCount(name, setTag, Chunk.fromIterable(tags))
    private val setCount = metricState.getSetCount(key)
    def observe(value: String): UIO[Any] =
      setCount.observe(value)
    def apply[R, E, A1 <: A](zio: ZIO[R, E, A1]): ZIO[R, E, A1]
  }
}
