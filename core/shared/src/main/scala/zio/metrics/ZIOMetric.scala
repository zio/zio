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
trait ZIOMetric[+Type <: MetricKeyType, -In, +Out] extends ZIOAspect[Nothing, Any, Nothing, Any, Nothing, In] { self =>

  /**
   * The type of the underlying primitive metric. For example, this could be
   * [[MetricKeyType.Counter]] or [[MetricKeyType.Gauge]].
   */
  val keyType: Type

  /**
   * Retrieves the transformation applied to the underlying primitive metric
   * type, which consists of a pair of functions, which adapt the update type
   * and map the output type of the primitive metric.
   */
  def transformation: Transformation[keyType.In, keyType.Out, In, Out]

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
  def contramap[In2](f: In2 => In): ZIOMetric[Type, In2, Out] =
    new ZIOMetric[Type, In2, Out] {
      val keyType = self.keyType

      val transformation = self.transformation.contramap(f)

      def hook(extraTags: Set[MetricLabel]): MetricHook[keyType.In, keyType.Out] =
        self.hook(extraTags)
    }

  /**
   * Returns a new metric that is powered by this one, but which accepts updates
   * of any type, and translates them to updates with the specified constant
   * update value.
   */
  final def fromConst(in: => In): ZIOMetric[Type, Any, Out] =
    contramap[Any](_ => in)

  /**
   * Returns a new metric that is powered by this one, but which outputs a new
   * state type, determined by transforming the state type of this metric by the
   * specified function.
   */
  def map[Out2](f: Out => Out2): ZIOMetric[Type, In, Out2] =
    new ZIOMetric[Type, In, Out2] {
      val keyType        = self.keyType
      val transformation = self.transformation.map(f)

      def hook(extraTags: Set[MetricLabel]): MetricHook[keyType.In, keyType.Out] =
        self.hook(extraTags)
    }

  /**
   * Returns a new metric, which is identical in every way to this one, except
   * the specified tag will be added to the tags of this metric.
   */
  def tagged(key: String, value: String): ZIOMetric[Type, In, Out] =
    tagged(MetricLabel(key, value))

  /**
   * Returns a new metric, which is identical in every way to this one, except
   * the specified tags have been added to the tags of this metric.
   */
  def tagged(extraTag: MetricLabel, extraTags: MetricLabel*): ZIOMetric[Type, In, Out] =
    tagged(Set(extraTag) ++ extraTags.toSet)

  /**
   * Returns a new metric, which is identical in every way to this one, except
   * the specified tags have been added to the tags of this metric.
   */
  def tagged(extraTags0: Set[MetricLabel]): ZIOMetric[Type, In, Out] =
    new ZIOMetric[Type, In, Out] {
      val keyType        = self.keyType
      val transformation = self.transformation

      def hook(extraTags: Set[MetricLabel]): MetricHook[keyType.In, keyType.Out] =
        self.hook(if (extraTags.isEmpty) extraTags0 else (extraTags0 ++ extraTags))
    }

  /**
   * Returns a new metric, which is identical in every way to this one, except
   * dynamic tags are added based on the update values. Note that the metric
   * returned by this method does not return any useful information, due to the
   * dynamic nature of the added tags.
   */
  def taggedWith[In1 <: In](
    f: In1 => Set[MetricLabel]
  ): ZIOMetric[Type, In1, Unit] =
    new ZIOMetric[Type, In1, Out] {
      val keyType = self.keyType

      val transformation = self.transformation

      override def unsafeUpdate(in: In1, extraTags: Set[MetricLabel]): Unit =
        self.unsafeUpdate(in, f(in) ++ extraTags)

      def hook(extraTags: Set[MetricLabel]): MetricHook[keyType.In, keyType.Out] =
        self.hook(extraTags)
    }.map(_ => ())

  /**
   * Returns a ZIOAspect that will update this metric with the success value of
   * the effects that it is applied to.
   */
  def trackSuccess: ZIOAspect[Nothing, Any, Nothing, Any, Nothing, In] =
    trackSuccessWith(identity(_))

  /**
   * Returns a ZIOAspect that will update this metric with the result of
   * applying the specified function to the success value of the effects that
   * the aspect is applied to.
   */
  def trackSuccessWith[In2](f: In2 => In): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, In2] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, In2] {
      def apply[R, E, A1 <: In2](zio: ZIO[R, E, A1])(implicit trace: ZTraceElement): ZIO[R, E, A1] =
        zio.tap(in2 => update(f(in2)))
    }

  /**
   * Returns a ZIOAspect that will update this metric with the throwable defects
   * of the effects that it is applied to. To call this method, the input type
   * of the metric must be `Throwable`.
   */
  def trackDefect(implicit ev: Throwable <:< In): ZIOAspect[Nothing, Any, Nothing, Throwable, Nothing, Any] =
    trackDefectWith(identity(_))

  /**
   * Returns a ZIOAspect that will update this metric with the result of
   * applying the specified function to the defect throwables of the effects
   * that the aspect is applied to.
   */
  def trackDefectWith(f: Throwable => In): ZIOAspect[Nothing, Any, Nothing, Throwable, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Throwable, Nothing, Any] {
      val updater: Throwable => Unit = defect => unsafeUpdate(f(defect))
      def apply[R, E <: Throwable, A](zio: ZIO[R, E, A])(implicit trace: ZTraceElement): ZIO[R, E, A] =
        zio.tapDefect(cause => ZIO.succeed(cause.defects.foreach(updater)))
    }

  /**
   * Returns a ZIOAspect that will update this metric with the duration that the
   * effect takes to execute. To call this method, the input type of the metric
   * must be `Duration`.
   */
  def trackDuration(implicit ev: Duration <:< In): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    trackDurationWith(ev)

  /**
   * Returns a ZIOAspect that will update this metric with the duration that the
   * effect takes to execute. To call this method, you must supply a function
   * that can convert the Duration to the input type of this metric.
   */
  def trackDurationWith(f: Duration => In): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: ZTraceElement): ZIO[R, E, A] =
        ZIO.suspendSucceed {
          val startTime = java.lang.System.nanoTime()

          zio.map { a =>
            val endTime = java.lang.System.nanoTime()

            val duration = Duration.fromNanos(endTime - startTime)

            unsafeUpdate(f(duration))

            a
          }
        }
    }

  /**
   * Returns a ZIOAspect that will update this metric with the failure value of
   * the effects that it is applied to.
   */
  def trackError: ZIOAspect[Nothing, Any, Nothing, In, Nothing, Any] =
    trackErrorWith(identity(_))

  /**
   * Returns a ZIOAspect that will update this metric with the result of
   * applying the specified function to the error value of the effects that the
   * aspect is applied to.
   */
  def trackErrorWith[In2](f: In2 => In): ZIOAspect[Nothing, Any, Nothing, In2, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, In2, Nothing, Any] {
      val updater: In2 => UIO[Unit] = error => update(f(error))

      def apply[R, E <: In2, A](zio: ZIO[R, E, A])(implicit trace: ZTraceElement): ZIO[R, E, A] =
        zio.tapError(updater)
    }

  /**
   * Updates the metric with the specified update message. For example, if the
   * metric were a counter, the update would increment the method by the
   * provided amount.
   */
  def update(in: In)(implicit trace: ZTraceElement): UIO[Unit] =
    ZIO.succeed(unsafeUpdate(in, Set.empty))

  /**
   * Updates the primitive metric that underlies this metric, and which is
   * determined by the `keyType` field.
   */
  def updatePrimitive(in: keyType.In)(implicit trace: ZTraceElement): UIO[Unit] =
    ZIO.succeed(unsafeUpdatePrimitive(in, Set.empty))

  /**
   * Retrieves a snapshot of the value of the metric at this moment in time.
   */
  def value(implicit trace: ZTraceElement): UIO[Out] = ZIO.succeed(unsafeValue(Set.empty))

  /**
   * Retrieves a snapshot of the value of the primitive metric that underlies
   * this metric, and which is determined by the `keyType` field.
   */
  def valuePrimitive(implicit trace: ZTraceElement): UIO[keyType.Out] = ZIO.succeed(unsafeValuePrimitive(Set.empty))

  def withNow[In2](implicit ev: (In2, java.time.Instant) <:< In): ZIOMetric[Type, In2, Out] =
    contramap[In2](in2 => ev((in2, java.time.Instant.now())))

  private[zio] def unsafeUpdate(in: In, extraTags: Set[MetricLabel] = Set.empty): Unit =
    hook(extraTags).update(transformation.contramapper(in))

  private[zio] def unsafeUpdatePrimitive(in: keyType.In, extraTags: Set[MetricLabel] = Set.empty): Unit =
    hook(extraTags).update(in)

  private[zio] def unsafeValue(extraTags: Set[MetricLabel] = Set.empty): Out =
    transformation.mapper(hook(extraTags).get())

  private[zio] def unsafeValuePrimitive(extraTags: Set[MetricLabel] = Set.empty): keyType.Out = hook(extraTags).get()

  private[zio] def hook(extraTags: Set[MetricLabel]): MetricHook[keyType.In, keyType.Out]
}
object ZIOMetric {
  type Counter[-In]   = ZIOMetric[MetricKeyType.Counter, In, MetricState.Counter]
  type Gauge[-In]     = ZIOMetric[MetricKeyType.Gauge, In, MetricState.Gauge]
  type Histogram[-In] = ZIOMetric[MetricKeyType.Histogram, In, MetricState.Histogram]
  type Summary[-In]   = ZIOMetric[MetricKeyType.Summary, In, MetricState.Summary]
  type SetCount[-In]  = ZIOMetric[MetricKeyType.SetCount, In, MetricState.SetCount]

  implicit class CounterSyntax[In](counter: ZIOMetric[MetricKeyType.Counter, In, Any]) {
    def increment: UIO[Unit] = counter.updatePrimitive(1.0)

    def incrementBy(value: Double): UIO[Unit] = counter.updatePrimitive(value)

    def count: UIO[Double] = counter.valuePrimitive.map(_.count)
  }

  implicit class GaugeSyntax[In](gauge: ZIOMetric[MetricKeyType.Gauge, In, Any]) {
    def set(value: Double): UIO[Unit] = gauge.updatePrimitive(value)
  }

  def fromMetricKey[Type <: MetricKeyType](
    key: MetricKey[Type]
  ): ZIOMetric[Type, key.keyType.In, key.keyType.Out] =
    ZIOMetric(key)(Transformation.identity)

  def apply[Type <: MetricKeyType](
    key0: MetricKey[Type]
  ): MakeZIOMetric[key0.keyType.type, key0.keyType.In, key0.keyType.Out] =
    new MakeZIOMetric[key0.keyType.type, key0.keyType.In, key0.keyType.Out](
      key0.asInstanceOf[MetricKey[key0.keyType.type]]
    )

  class MakeZIOMetric[Type <: MetricKeyType { type In = In0; type Out = Out0 }, In0, Out0](key0: MetricKey[Type]) {
    def apply[In, Out](transformation0: Transformation[In0, Out0, In, Out]): ZIOMetric[Type, In, Out] =
      new ZIOMetric[Type, In, Out] {
        val keyType = key0.keyType

        def transformation: Transformation[In0, Out0, In, Out] = transformation0

        def hook(extraTags: Set[MetricLabel]): MetricHook[keyType.In, keyType.Out] =
          metricState.get(key0.tagged(extraTags))
      }
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
  def summary(
    name: String,
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double]
  ): Summary[Double] =
    summaryInstant(name, maxAge, maxSize, error, quantiles).withNow[Double]

  def summaryInstant(
    name: String,
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double]
  ): Summary[(Double, java.time.Instant)] =
    fromMetricKey(MetricKey(name, Set.empty, MetricKeyType.Summary(maxAge, maxSize, error, quantiles)))

  /**
   * A string histogram metric, which keeps track of the counts of different
   * strings.
   */
  def setCount(name: String, setTag: String): SetCount[String] =
    fromMetricKey(MetricKey(name, Set.empty, MetricKeyType.SetCount(setTag)))
}
