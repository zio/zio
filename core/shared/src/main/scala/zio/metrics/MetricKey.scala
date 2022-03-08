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
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A `MetricKey` is a unique key associated with each metric. The key is based
 * on a combination of the metric type, the name and tags associated with the
 * metric, and any other information to describe a a metric, such as the
 * boundaries of a histogram. In this way, it is impossible to ever create
 * different metrics with conflicting keys.
 */
sealed abstract case class MetricKey[+Type, In0, Out0] private (
  name: String,
  tags: Chunk[MetricLabel],
  keyType: Type,
  metricHook: internal.metrics.MetricHook[In0, Out0]
) { self =>

  /**
   * Returns a new `MetricKey` with the specified tag appended.
   */
  def tagged(key: String, value: String): MetricKey[Type, In0, Out0] =
    tagged(Chunk(MetricLabel(key, value)))

  /**
   * Returns a new `MetricKey` with the specified tags appended.
   */
  def tagged(extraTag: MetricLabel, extraTags: MetricLabel*): MetricKey[Type, In0, Out0] =
    tagged(Chunk(extraTag) ++ Chunk.fromIterable(extraTags))

  /**
   * Returns a new `MetricKey` with the specified tags appended.
   */
  def tagged(extraTags: Chunk[MetricLabel]): MetricKey[Type, In0, Out0] =
    if (tags.isEmpty) self
    else new MetricKey[Type, In0, Out0](name, tags ++ extraTags, keyType, metricHook) {}
}
object MetricKey {
  type Untyped = MetricKey[Any, _, _]

  type Root0[In, Type <: MetricKeyType] = MetricKey[Type, In, _ <: MetricState[Type]]

  type Root[In0] = Root0[In0, MetricKeyType { type In = In0 }]

  type Counter   = MetricKey[MetricKeyType.Counter, Double, MetricState.Counter]
  type Gauge     = MetricKey[MetricKeyType.Gauge, Double, MetricState.Gauge]
  type Histogram = MetricKey[MetricKeyType.Histogram, Double, MetricState.Histogram]
  type Summary   = MetricKey[MetricKeyType.Summary, (Double, java.time.Instant), MetricState.Summary]
  type SetCount  = MetricKey[MetricKeyType.SetCount, String, MetricState.SetCount]

  import MetricKeyType.Histogram.Boundaries

  def apply[Type <: MetricKeyType, In0, Out0](
    name: String,
    tags: Chunk[MetricLabel],
    keyType: Type { type In = In0; type Out = Out0 }
  ): MetricKey[Type, In0, Out0] = {
    import zio.internal.metrics._

    lazy val metricKey = new MetricKey[Type, In0, Out0](name, tags, keyType, hook) {}

    lazy val hook: MetricHook[In0, Out0] =
      keyType match {
        case MetricKeyType.Counter             => metricState.getCounter(metricKey.asInstanceOf[MetricKey.Counter])
        case MetricKeyType.Gauge               => metricState.getGauge(metricKey.asInstanceOf[MetricKey.Gauge])
        case MetricKeyType.Histogram(_)        => metricState.getHistogram(metricKey.asInstanceOf[MetricKey.Histogram])
        case MetricKeyType.Summary(_, _, _, _) => metricState.getSummary(metricKey.asInstanceOf[MetricKey.Summary])
        case MetricKeyType.SetCount(_)         => metricState.getSetCount(metricKey.asInstanceOf[MetricKey.SetCount])
      }

    metricKey
  }

  /**
   * Creates a metric key for a counter, with the specified name & parameters.
   */
  def counter(name: String): Counter =
    MetricKey(name, Chunk.empty, MetricKeyType.Counter)

  /**
   * Creates a metric key for a gauge, with the specified name & parameters.
   */
  def gauge(name: String): Gauge =
    MetricKey(name, Chunk.empty, MetricKeyType.Gauge)

  /**
   * Creates a metric key for a histogram, with the specified name & parameters.
   */
  def histogram(
    name: String,
    boundaries: Boundaries
  ): Histogram =
    MetricKey(name, Chunk.empty, MetricKeyType.Histogram(boundaries))

  /**
   * Creates a metric key for a summary, with the specified name & parameters.
   */
  def summary(
    name: String,
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double]
  ): Summary =
    MetricKey(name, Chunk.empty, MetricKeyType.Summary(maxAge, maxSize, error, quantiles))

  /**
   * Creates a metric key for a set count, with the specified name & parameters.
   */
  def setCount(name: String, setTag: String): SetCount =
    MetricKey(name, Chunk.empty, MetricKeyType.SetCount(setTag))
}
