/*
 * Copyright 2022-2024 John A. De Goes and the ZIO Contributors
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
import zio.metrics.MetricKeyType.Histogram

import java.time.temporal.ChronoUnit

/**
 * A `Metric[Type, In, Out]` represents a concurrent metric, which accepts
 * updates of type `In`, which are aggregated to a stateful value of type `Out`.
 * `Type` is the type of the underlying primitive metric, such as
 * `MetricKeyType.Counter`.
 *
 * For example, a counter created with `Metric.counterDouble` has type
 * `Metric[MetricKeyType.Counter, Double, MetricState.Counter]`, representing
 * the fact that the metric can be updated with doubles (the amount to add to
 * the counter, which may be negative), and the state of the counter is a
 * `MetricState.Counter` that holds a double.
 *
 * There are five primitive metric types supported by ZIO:
 *
 *   - Counters
 *   - Frequencies
 *   - Gauges
 *   - Histograms
 *   - Summaries
 *
 * The companion object contains constructors for these primitive metrics, and
 * operators such as `contramap`, `map`, `tagged` and `zip` derive new metrics
 * from them. A metric is also a `ZIOAspect`: `effect @@ metric` updates the
 * metric with each success value of `effect`.
 *
 * The state of a metric lives in a registry shared by the whole application,
 * keyed by its [[MetricKey]]: two metrics created with the same name, type,
 * description and tags read and update the same state.
 *
 * {{{
 * import zio._
 *
 * val requests = Metric.counter("requests")
 *
 * val program: UIO[Double] =
 *   for {
 *     _     <- requests.increment
 *     _     <- ZIO.succeed(2L) @@ requests
 *     state <- requests.value
 *   } yield state.count
 * // 3.0
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/observability/metrics/ Metrics]]
 */
trait Metric[+Type, -In, +Out] extends ZIOAspect[Nothing, Any, Nothing, Any, Nothing, In] { self =>

  /**
   * The type of the underlying primitive metric. For example, this could be
   * [[MetricKeyType.Counter]] or [[MetricKeyType.Gauge]].
   */
  val keyType: Type

  /**
   * Applies the metric computation to the result of the specified effect: the
   * returned effect updates this metric with the success value of `zio`. A
   * failed effect leaves the metric unchanged.
   */
  final def apply[R, E, A1 <: In](zio: ZIO[R, E, A1])(implicit trace: Trace): ZIO[R, E, A1] =
    zio.tap(update(_))

  /**
   * Returns a new metric that is powered by this one, but which accepts updates
   * of the specified new type, which must be transformable to the input type of
   * this metric.
   */
  final def contramap[In2](f: In2 => In): Metric[Type, In2, Out] =
    new Metric[Type, In2, Out] {
      val keyType = self.keyType

      override private[zio] val unsafe: UnsafeAPI =
        new UnsafeAPI {
          override def update(in: In2, extraTags: Set[MetricLabel])(implicit unsafe: Unsafe): Unit =
            self.unsafe.update(f(in), extraTags)

          override def value(extraTags: Set[MetricLabel])(implicit unsafe: Unsafe): Out =
            self.unsafe.value(extraTags)

          override def modify(in: In2, extraTags: Set[MetricLabel])(implicit unsafe: Unsafe): Unit =
            self.unsafe.modify(f(in), extraTags)
        }
    }

  /**
   * Returns a new metric that is powered by this one, but which accepts updates
   * of any type, and translates them to updates with the specified constant
   * update value. `in` is evaluated on each update.
   */
  final def fromConst(in: => In): Metric[Type, Any, Out] =
    contramap[Any](_ => in)

  /**
   * Returns a new metric that is powered by this one, but which outputs a new
   * state type, determined by transforming the state type of this metric by the
   * specified function.
   */
  final def map[Out2](f: Out => Out2): Metric[Type, In, Out2] =
    new Metric[Type, In, Out2] {
      val keyType = self.keyType

      override private[zio] val unsafe: UnsafeAPI =
        new UnsafeAPI {
          override def update(in: In, extraTags: Set[MetricLabel])(implicit unsafe: Unsafe): Unit =
            self.unsafe.update(in, extraTags)

          override def value(extraTags: Set[MetricLabel])(implicit unsafe: Unsafe): Out2 =
            f(self.unsafe.value(extraTags))

          override def modify(in: In, extraTags: Set[MetricLabel])(implicit unsafe: Unsafe): Unit =
            self.unsafe.modify(in, extraTags)
        }
    }

  /**
   * Returns a new metric that is powered by this one, but whose `keyType` is
   * the result of applying the specified function to the `keyType` of this
   * metric. Updates and the state are those of this metric.
   */
  final def mapType[Type2](f: Type => Type2): Metric[Type2, In, Out] =
    new Metric[Type2, In, Out] {
      val keyType = f(self.keyType)

      override private[zio] val unsafe: UnsafeAPI =
        new UnsafeAPI {
          override def update(in: In, extraTags: Set[MetricLabel])(implicit unsafe: Unsafe): Unit =
            self.unsafe.update(in, extraTags)

          override def value(extraTags: Set[MetricLabel])(implicit unsafe: Unsafe): Out =
            self.unsafe.value(extraTags)

          override def modify(in: In, extraTags: Set[MetricLabel])(implicit unsafe: Unsafe): Unit =
            self.unsafe.modify(in, extraTags)
        }
    }

  /**
   * Modifies the metric with the specified update message. For a gauge, the
   * amount is added to the current value (where `update` sets it). For
   * counters, frequencies, histograms and summaries, `modify` does the same as
   * `update`.
   */
  final def modify(in: => In)(implicit trace: Trace): UIO[Unit] =
    FiberRef.currentTags.getWith { tags =>
      ZIO.succeed(unsafe.modify(in, tags)(Unsafe.unsafe))
    }

  /**
   * Returns a new metric, which is identical in every way to this one, except
   * the specified tag will be added to the tags of this metric.
   *
   * @see
   *   [[https://zio.dev/reference/observability/metrics/metriclabel/ MetricLabel]]
   */
  final def tagged(key: String, value: String): Metric[Type, In, Out] =
    tagged(MetricLabel(key, value))

  /**
   * Returns a new metric, which is identical in every way to this one, except
   * the specified tags have been added to the tags of this metric.
   */
  final def tagged(extraTag: MetricLabel, extraTags: MetricLabel*): Metric[Type, In, Out] =
    tagged(Set(extraTag) ++ extraTags.toSet)

  /**
   * Returns a new metric, which is identical in every way to this one, except
   * the specified tags have been added to the tags of this metric. The tags are
   * a set of labels, so adding a label whose key is already present with
   * another value keeps both labels.
   */
  final def tagged(extraTags0: Set[MetricLabel]): Metric[Type, In, Out] =
    new Metric[Type, In, Out] {
      val keyType = self.keyType

      override private[zio] val unsafe: UnsafeAPI =
        new UnsafeAPI {
          override def update(in: In, extraTags: Set[MetricLabel])(implicit unsafe: Unsafe): Unit =
            self.unsafe.update(in, extraTags0 ++ extraTags)

          override def value(extraTags: Set[MetricLabel])(implicit unsafe: Unsafe): Out =
            self.unsafe.value(extraTags0 ++ extraTags)

          override def modify(in: In, extraTags: Set[MetricLabel])(implicit unsafe: Unsafe): Unit =
            self.unsafe.modify(in, extraTags0 ++ extraTags)
        }
    }

  /**
   * Returns a new metric, which is identical in every way to this one, except
   * dynamic tags are added based on the update values: an update with `in`
   * updates this metric tagged with `f(in)`. Note that the metric returned by
   * this method does not return any useful information, due to the dynamic
   * nature of the added tags: its value is `()`. Read the state with `tagged`
   * and the same tags.
   */
  final def taggedWith[In1 <: In](
    f: In1 => Set[MetricLabel]
  ): Metric[Type, In1, Unit] =
    new Metric[Type, In1, Out] {
      val keyType = self.keyType

      override private[zio] val unsafe: UnsafeAPI =
        new UnsafeAPI {
          override def update(in: In1, extraTags: Set[MetricLabel])(implicit unsafe: Unsafe): Unit =
            self.unsafe.update(in, f(in) ++ extraTags)

          override def value(extraTags: Set[MetricLabel])(implicit unsafe: Unsafe): Out =
            self.unsafe.value(extraTags)

          override def modify(in: In1, extraTags: Set[MetricLabel])(implicit unsafe: Unsafe): Unit =
            self.unsafe.modify(in, f(in) ++ extraTags)
        }
    }.map(_ => ())

  /**
   * Returns a ZIOAspect that will update this metric with the specified
   * constant value every time the aspect is applied to an effect, regardless of
   * whether that effect fails or succeeds. It also updates the metric when the
   * effect dies or interrupts itself with `ZIO.interrupt`; when the fiber is
   * interrupted by another fiber, the metric is not updated.
   */
  final def trackAll(in: => In): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        zio.foldCauseZIO(
          cause => update(in) *> Exit.failCause(cause),
          a => update(in) *> ZIO.succeed(a)
        )
    }

  /**
   * Returns a ZIOAspect that will update this metric with the throwable defects
   * of the effects that it is applied to, once for each defect in the cause. To
   * call this method, the input type of the metric must be `Throwable`.
   */
  final def trackDefect(implicit ev: Throwable <:< In): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    trackDefectWith(identity(_))

  /**
   * Returns a ZIOAspect that will update this metric with the result of
   * applying the specified function to the defect throwables of the effects
   * that the aspect is applied to.
   */
  final def trackDefectWith(f: Throwable => In): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      val updater: Throwable => UIO[Unit] =
        defect => update(f(defect))

      def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        zio.tapDefect(cause => ZIO.foreachDiscard(cause.defects)(updater))
    }

  /**
   * Returns a ZIOAspect that will update this metric with the duration that the
   * effect takes to execute, measured with `java.lang.System.nanoTime`, when
   * the effect succeeds. A failed effect is not recorded. To call this method,
   * the input type of the metric must be `Duration`.
   *
   * @see
   *   [[https://zio.dev/reference/observability/metrics/histogram/ Histogram]]
   */
  final def trackDuration(implicit ev: Duration <:< In): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    trackDurationWith(ev)

  /**
   * Returns a ZIOAspect that will update this metric with the duration that the
   * effect takes to execute, when the effect succeeds. A failed effect is not
   * recorded. To call this method, you must supply a function that can convert
   * the Duration to the input type of this metric.
   */
  final def trackDurationWith(f: Duration => In): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        ZIO.suspendSucceed {
          val startTime = java.lang.System.nanoTime()

          zio.tap { a =>
            val endTime  = java.lang.System.nanoTime()
            val duration = Duration.fromNanos(endTime - startTime)

            update(f(duration))
          }
        }
    }

  /**
   * Returns a ZIOAspect that will update this metric with the failure value of
   * the effects that it is applied to. A cause that holds both a failure and a
   * defect updates it with the failure; a cause with only defects does not
   * update it.
   */
  final def trackError: ZIOAspect[Nothing, Any, Nothing, In, Nothing, Any] =
    trackErrorWith(identity(_))

  /**
   * Returns a ZIOAspect that will update this metric with the result of
   * applying the specified function to the error value of the effects that the
   * aspect is applied to.
   */
  final def trackErrorWith[In2](f: In2 => In): ZIOAspect[Nothing, Any, Nothing, In2, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, In2, Nothing, Any] {
      val updater: In2 => UIO[Unit] = error => update(f(error))

      def apply[R, E <: In2, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        zio.tapError(updater)
    }

  /**
   * Returns a ZIOAspect that will update this metric with the success value of
   * the effects that it is applied to.
   */
  final def trackSuccess: ZIOAspect[Nothing, Any, Nothing, Any, Nothing, In] =
    trackSuccessWith(identity(_))

  /**
   * Returns a ZIOAspect that will update this metric with the result of
   * applying the specified function to the success value of the effects that
   * the aspect is applied to.
   */
  final def trackSuccessWith[In2](f: In2 => In): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, In2] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, In2] {
      def apply[R, E, A1 <: In2](zio: ZIO[R, E, A1])(implicit trace: Trace): ZIO[R, E, A1] =
        zio.tap(in2 => update(f(in2)))
    }

  /**
   * Updates the metric with the specified update message. For example, if the
   * metric were a counter, the update would increment the metric by the
   * provided amount. A gauge is set to the value, a histogram or summary
   * records it as an observation, and a frequency counts one occurrence of the
   * string. The tags set with `ZIO.tagged` in the current fiber are added to
   * the tags of the metric.
   */
  final def update(in: => In)(implicit trace: Trace): UIO[Unit] =
    FiberRef.currentTags.getWith { tags =>
      ZIO.succeed(unsafe.update(in, tags)(Unsafe.unsafe))
    }

  /**
   * Retrieves a snapshot of the value of the metric at this moment in time, for
   * the tags of this metric plus the tags set with `ZIO.tagged` in the current
   * fiber.
   */
  final def value(implicit trace: Trace): UIO[Out] =
    FiberRef.currentTags.getWith { tags =>
      ZIO.succeed(unsafe.value(tags)(Unsafe.unsafe))
    }

  /**
   * Returns a new metric that accepts updates of type `In2` and passes them to
   * this metric paired with `java.time.Instant.now()`. `Metric.summary` uses it
   * to turn a `summaryInstant` metric into one that takes plain values.
   */
  final def withNow[In2](implicit ev: (In2, java.time.Instant) <:< In): Metric[Type, In2, Out] =
    contramap[In2](in2 => ev((in2, java.time.Instant.now())))

  private[zio] trait UnsafeAPI {

    /**
     * Updates the metric with `in` right away, adding `extraTags` to its tags.
     */
    def update(in: In, extraTags: Set[MetricLabel] = Set.empty)(implicit unsafe: Unsafe): Unit

    /**
     * Returns the state of the metric for its tags plus `extraTags`.
     */
    def value(extraTags: Set[MetricLabel] = Set.empty)(implicit unsafe: Unsafe): Out

    /**
     * Modifies the metric with `in` right away, adding `extraTags` to its tags.
     */
    def modify(in: In, extraTags: Set[MetricLabel] = Set.empty)(implicit unsafe: Unsafe): Unit
  }

  private[zio] def unsafe: UnsafeAPI
}

/**
 * Constructors for the primitive metrics (counters, frequencies, gauges,
 * histograms and summaries), type aliases for them, and the metrics that the
 * ZIO runtime updates.
 */
object Metric {

  /**
   * A counter metric that accepts updates of type `In`.
   */
  type Counter[-In] = Metric[MetricKeyType.Counter, In, MetricState.Counter]

  /**
   * A gauge metric that accepts updates of type `In`.
   */
  type Gauge[-In] = Metric[MetricKeyType.Gauge, In, MetricState.Gauge]

  /**
   * A histogram metric that accepts updates of type `In`.
   */
  type Histogram[-In] = Metric[MetricKeyType.Histogram, In, MetricState.Histogram]

  /**
   * A summary metric that accepts updates of type `In`.
   */
  type Summary[-In] = Metric[MetricKeyType.Summary, In, MetricState.Summary]

  /**
   * A frequency metric that accepts updates of type `In`.
   */
  type Frequency[-In] = Metric[MetricKeyType.Frequency, In, MetricState.Frequency]

  /**
   * Operators for metrics that use their type parameters invariantly.
   */
  implicit class InvariantSyntax[Type, In, Out](self: Metric[Type, In, Out]) {

    /**
     * Combines this metric with `that` into a metric that accepts a tuple,
     * updates each metric with its part, and returns the states of both.
     */
    final def zip[Type2, In2, Out2](that: Metric[Type2, In2, Out2])(implicit
      z1: Zippable[Type, Type2],
      uz: Unzippable[In, In2],
      z2: Zippable[Out, Out2]
    ): Metric[z1.Out, uz.In, z2.Out] =
      new Metric[z1.Out, uz.In, z2.Out] {
        val keyType = z1.zip(self.keyType, that.keyType)

        override private[zio] val unsafe: UnsafeAPI =
          new UnsafeAPI {
            def update(in: uz.In, extraTags: Set[MetricLabel] = Set.empty)(implicit unsafe: Unsafe): Unit = {
              val (l, r) = uz.unzip(in)
              self.unsafe.update(l, extraTags)
              that.unsafe.update(r, extraTags)
            }

            def value(extraTags: Set[MetricLabel] = Set.empty)(implicit unsafe: Unsafe): z2.Out =
              z2.zip(self.unsafe.value(extraTags), that.unsafe.value(extraTags))

            def modify(in: uz.In, extraTags: Set[MetricLabel] = Set.empty)(implicit unsafe: Unsafe): Unit = {
              val (l, r) = uz.unzip(in)
              self.unsafe.modify(l, extraTags)
              that.unsafe.modify(r, extraTags)
            }
          }
      }
  }

  /**
   * Operators for counter metrics.
   *
   * @see
   *   [[https://zio.dev/reference/observability/metrics/counter/ Counter]]
   */
  implicit class CounterSyntax[In](counter: Metric[MetricKeyType.Counter, In, Any]) {

    /**
     * Adds 1 to the counter.
     */
    def increment(implicit numeric: Numeric[In]): UIO[Unit] = counter.update(numeric.fromInt(1))

    /**
     * Adds `value` to the counter. A negative value decreases the count.
     */
    def incrementBy(value: => In)(implicit numeric: Numeric[In]): UIO[Unit] = counter.update(value)
  }

  /**
   * Operators for gauge metrics.
   *
   * @see
   *   [[https://zio.dev/reference/observability/metrics/gauge/ Gauge]]
   */
  implicit class GaugeSyntax[In](gauge: Metric[MetricKeyType.Gauge, In, Any]) {

    /**
     * Subtracts 1 from the gauge.
     */
    def decrement(implicit numeric: Numeric[In]): UIO[Unit] = gauge.modify(numeric.fromInt(-1))

    /**
     * Subtracts `value` from the gauge.
     */
    def decrementBy(value: => In)(implicit numeric: Numeric[In]): UIO[Unit] = gauge.modify(numeric.negate(value))

    /**
     * Adds 1 to the gauge.
     */
    def increment(implicit numeric: Numeric[In]): UIO[Unit] = gauge.modify(numeric.fromInt(1))

    /**
     * Adds `value` to the gauge.
     */
    def incrementBy(value: => In)(implicit numeric: Numeric[In]): UIO[Unit] = gauge.modify(value)

    /**
     * Sets the gauge to `value`.
     */
    def set(value: => In): UIO[Unit] = gauge.update(value)
  }

  /**
   * Core metrics that are updated by the ZIO runtime system. The runtime
   * updates them only while the `RuntimeFlag.RuntimeMetrics` flag is on, for
   * example after providing the `Runtime.enableRuntimeMetrics` layer.
   */
  object runtime {

    /**
     * Counts the class names of the failures and defects of fibers that end in
     * failure (`zio_fiber_failure_causes`).
     */
    val fiberFailureCauses = Metric.frequency("zio_fiber_failure_causes")

    /**
     * Counts the fibers started, by the source location where each was forked
     * (`zio_fiber_fork_locations`).
     */
    val fiberForkLocations = Metric.frequency("zio_fiber_fork_locations")

    /**
     * Counts the fibers started (`zio_fiber_started`).
     */
    val fibersStarted = Metric.counter("zio_fiber_started")

    /**
     * Counts the fibers that end with a success (`zio_fiber_successes`).
     */
    val fiberSuccesses = Metric.counter("zio_fiber_successes")

    /**
     * Counts the fibers that end in failure (`zio_fiber_failures`).
     */
    val fiberFailures = Metric.counter("zio_fiber_failures")

    /**
     * Records the lifetime of each fiber that ends, in seconds
     * (`zio_fiber_lifetimes`), in 20 buckets that start at 0.001 and double up
     * to 524.288, plus the `Double.MaxValue` bucket.
     */
    val fiberLifetimes =
      Metric.histogram(
        "zio_fiber_lifetimes",
        MetricKeyType.Histogram.Boundaries.exponential(0.001, 2.0, 20)
      ) // ~18 minutes
  }

  /**
   * A counter, which can be incremented by longs. The count is kept as a double
   * that starts at `0.0`; a negative amount decreases it.
   *
   * @see
   *   [[https://zio.dev/reference/observability/metrics/counter/ Counter]]
   */
  def counter(name: String): Counter[Long] =
    counterDouble(name).contramap[Long](_.toDouble)

  /**
   * A counter, which can be incremented by longs, with the specified
   * description. The description is part of the metric key, so this counter has
   * a different state from a counter with the same name and no description.
   */
  def counter(name: String, description: String): Counter[Long] =
    counterDouble(name, description).contramap[Long](_.toDouble)

  /**
   * A counter, which can be incremented by doubles.
   */
  def counterDouble(name: String): Counter[Double] =
    fromMetricKey(MetricKey.counter(name))

  /**
   * A counter, which can be incremented by doubles, with the specified
   * description.
   */
  def counterDouble(name: String, description: String): Counter[Double] =
    fromMetricKey(MetricKey.counter(name, description))

  /**
   * A counter, which can be incremented by integers.
   */
  def counterInt(name: String): Counter[Int] =
    counterDouble(name).contramap[Int](_.toDouble)

  /**
   * A counter, which can be incremented by integers, with the specified
   * description.
   */
  def counterInt(name: String, description: String): Counter[Int] =
    counterDouble(name, description).contramap[Int](_.toDouble)

  /**
   * A string histogram metric, which keeps track of the counts of different
   * strings. Each update counts one occurrence of the string.
   *
   * @see
   *   [[https://zio.dev/reference/observability/metrics/frequency/ Frequency]]
   */
  def frequency(name: String): Frequency[String] =
    fromMetricKey(MetricKey.frequency(name))

  /**
   * A string histogram metric, which keeps track of the counts of different
   * strings, with the specified description.
   */
  def frequency(name: String, description: String): Frequency[String] =
    fromMetricKey(MetricKey.frequency(name, description))

  /**
   * Creates a metric from a metric key. This is the primary constructor for
   * [[zio.metrics.Metric]].
   */
  def fromMetricKey[Type <: MetricKeyType](
    key: MetricKey[Type]
  ): Metric[Type, key.keyType.In, key.keyType.Out] =
    new Metric[Type, key.keyType.In, key.keyType.Out] {
      val keyType = key.keyType

      override private[zio] val unsafe: UnsafeAPI =
        new UnsafeAPI {
          def update(in: key.keyType.In, extraTags: Set[MetricLabel] = Set.empty)(implicit
            unsafe: Unsafe
          ): Unit = {
            val fullKey = key.tagged(extraTags).asInstanceOf[MetricKey[key.keyType.type]]
            hook(fullKey).update(in)
            metricRegistry.notifyListeners(fullKey, in, MetricEventType.Update)
          }

          def value(extraTags: Set[MetricLabel] = Set.empty)(implicit unsafe: Unsafe): key.keyType.Out = {
            val fullKey = key.tagged(extraTags).asInstanceOf[MetricKey[key.keyType.type]]
            hook(fullKey).get()
          }

          def modify(in: key.keyType.In, extraTags: Set[MetricLabel] = Set.empty)(implicit
            unsafe: Unsafe
          ): Unit = {
            val fullKey = key.tagged(extraTags).asInstanceOf[MetricKey[key.keyType.type]]
            hook(fullKey).modify(in)
            metricRegistry.notifyListeners(fullKey, in, MetricEventType.Modify)
          }

        }

      def hook(fullKey: MetricKey[key.keyType.type]): MetricHook[key.keyType.In, key.keyType.Out] =
        metricRegistry.get(fullKey)(Unsafe.unsafe)
    }

  /**
   * A gauge, which can be set to a value. `update` and `set` replace the value,
   * while `modify`, `increment` and `decrement` adjust it. A new gauge starts
   * at `0.0`.
   *
   * @see
   *   [[https://zio.dev/reference/observability/metrics/gauge/ Gauge]]
   */
  def gauge(name: String): Gauge[Double] =
    fromMetricKey(MetricKey.gauge(name))

  /**
   * A gauge, which can be set to a value, with the specified description.
   */
  def gauge(name: String, description: String): Gauge[Double] =
    fromMetricKey(MetricKey.gauge(name, description))

  /**
   * A numeric histogram metric, which keeps track of the count of numbers that
   * fall in bins with the specified boundaries. Each value is counted in every
   * bucket whose boundary is greater than or equal to it, so the bucket counts
   * in the state are cumulative.
   *
   * @see
   *   [[https://zio.dev/reference/observability/metrics/histogram/ Histogram]]
   */
  def histogram(name: String, boundaries: Histogram.Boundaries): Histogram[Double] =
    fromMetricKey(MetricKey.histogram(name, boundaries))

  /**
   * A numeric histogram metric, which keeps track of the count of numbers that
   * fall in bins with the specified boundaries, with the specified description.
   */
  def histogram(name: String, description: String, boundaries: Histogram.Boundaries): Histogram[Double] =
    fromMetricKey(MetricKey.histogram(name, description, boundaries))

  /**
   * Creates a metric that ignores input and produces constant output.
   */
  def succeed[Out](out: => Out): Metric[Unit, Any, Out] =
    new Metric[Unit, Any, Out] {
      val keyType = ()

      override private[zio] val unsafe: UnsafeAPI =
        new UnsafeAPI {
          def update(in: Any, extraTags: Set[MetricLabel] = Set.empty)(implicit
            unsafe: Unsafe
          ): Unit = ()

          def value(extraTags: Set[MetricLabel] = Set.empty)(implicit unsafe: Unsafe): Out =
            out

          def modify(in: Any, extraTags: Set[MetricLabel] = Set.empty)(implicit
            unsafe: Unsafe
          ): Unit = ()
        }
    }

  /**
   * A summary metric, which keeps the last `maxSize` observed values and
   * reports the requested `quantiles` of the ones that are at most `maxAge`
   * old. Each update is stamped with the current time. The `count`, `min`,
   * `max` and `sum` of the state cover every observed value. A quantile is
   * `None` when no value is in the window. `error` is copied to the state; the
   * quantiles are calculated without it.
   *
   * @see
   *   [[https://zio.dev/reference/observability/metrics/summary/ Summary]]
   */
  def summary(
    name: String,
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double]
  ): Summary[Double] =
    summaryInstant(name, maxAge, maxSize, error, quantiles).withNow[Double]

  /**
   * A summary metric with the specified description. It behaves like the
   * `summary` without a description.
   */
  def summary(
    name: String,
    description: String,
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double]
  ): Summary[Double] =
    summaryInstant(name, description, maxAge, maxSize, error, quantiles).withNow[Double]

  /**
   * A summary metric whose updates carry the time of the observation with the
   * value. Only the values whose time is between `maxAge` ago and now count
   * towards the quantiles.
   */
  def summaryInstant(
    name: String,
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double]
  ): Summary[(Double, java.time.Instant)] =
    fromMetricKey(MetricKey.summary(name, maxAge, maxSize, error, quantiles))

  /**
   * A summary metric whose updates carry the time of the observation with the
   * value, with the specified description.
   */
  def summaryInstant(
    name: String,
    description: String,
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double]
  ): Summary[(Double, java.time.Instant)] =
    fromMetricKey(MetricKey.summary(name, description, maxAge, maxSize, error, quantiles))

  /**
   * Creates a timer metric, based on a histogram, which keeps track of
   * durations in the specified unit of time (milliseconds, seconds, etc.). The
   * unit of time will automatically be added to the metric as a tag: the
   * `time_unit` tag holds the lower-case name of the unit, such as `millis` for
   * `ChronoUnit.MILLIS`. The histogram has 64 boundaries that start at 1.0 and
   * double each time, plus `Double.MaxValue`.
   *
   * @see
   *   [[https://zio.dev/reference/observability/metrics/histogram/ Histogram]]
   */
  def timer(
    name: String,
    chronoUnit: ChronoUnit
  ): Metric[MetricKeyType.Histogram, Duration, MetricState.Histogram] =
    timer(name, chronoUnit, Chunk.iterate(1.0, 64)(_ * 2.0))

  /**
   * Creates a timer metric, based on a histogram, which keeps track of
   * durations in the specified unit of time (milliseconds, seconds, etc.), with
   * the specified description. The unit of time will automatically be added to
   * the metric as a tag: the `time_unit` tag holds the lower-case name of the
   * unit, such as `millis` for `ChronoUnit.MILLIS`. The histogram has 64
   * boundaries that start at 1.0 and double each time, plus `Double.MaxValue`.
   */
  def timer(
    name: String,
    description: String,
    chronoUnit: ChronoUnit
  ): Metric[MetricKeyType.Histogram, Duration, MetricState.Histogram] =
    timer(name, description, chronoUnit, Chunk.iterate(1.0, 64)(_ * 2.0))

  /**
   * Creates a timer metric, based on a histogram with the specified boundaries,
   * which keeps track of durations in the specified unit of time as doubles
   * (1500 milliseconds is 1.5 in `ChronoUnit.SECONDS`). The boundaries go
   * through `Histogram.Boundaries.fromChunk`, which drops the values that are
   * not positive. The `time_unit` tag holds the lower-case name of the unit,
   * such as `millis`.
   *
   * {{{
   * import zio._
   * import java.time.temporal.ChronoUnit
   *
   * val requestLatency =
   *   Metric.timer("request_latency", ChronoUnit.MILLIS, Chunk(10.0, 50.0, 100.0))
   *
   * val handled: UIO[Int] = ZIO.succeed(200) @@ requestLatency.trackDuration
   * }}}
   */
  def timer(
    name: String,
    chronoUnit: ChronoUnit,
    boundaries: Chunk[Double]
  ): Metric[MetricKeyType.Histogram, Duration, MetricState.Histogram] = {
    val base = Metric
      .histogram(name, Histogram.Boundaries.fromChunk(boundaries))
      .tagged(MetricLabel("time_unit", chronoUnit.toString.toLowerCase()))

    base.contramap[Duration] { (duration: Duration) =>
      duration.toNanos.toDouble / chronoUnit.getDuration.toNanos
    }
  }

  /**
   * Creates a timer metric, based on a histogram with the specified boundaries
   * and description, which keeps track of durations in the specified unit of
   * time. The `time_unit` tag holds the lower-case name of the unit, such as
   * `millis`.
   */
  def timer(
    name: String,
    description: String,
    chronoUnit: ChronoUnit,
    boundaries: Chunk[Double]
  ): Metric[MetricKeyType.Histogram, Duration, MetricState.Histogram] = {
    val base = Metric
      .histogram(name, description, Histogram.Boundaries.fromChunk(boundaries))
      .tagged(MetricLabel("time_unit", chronoUnit.toString.toLowerCase()))

    base.contramap[Duration] { (duration: Duration) =>
      duration.toNanos.toDouble / chronoUnit.getDuration.toNanos
    }
  }
}
