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
sealed abstract case class MetricKey2[+Type <: MetricKeyType, In0, Out0] private (
  name: String,
  tags: Chunk[MetricLabel],
  keyType: Type { type In = In0; type Out = Out0 }
) { self =>
  private[zio] lazy val metricHook: internal.metrics.MetricHook[In0, Out0] =
    unsafeGetMetricHook(keyType)

  /**
   * Returns a new `MetricKey` with the specified tag appended.
   */
  def tagged(key: String, value: String): MetricKey2[Type, In0, Out0] =
    tagged(Chunk(MetricLabel(key, value)))

  /**
   * Returns a new `MetricKey` with the specified tags appended.
   */
  def tagged(extraTag: MetricLabel, extraTags: MetricLabel*): MetricKey2[Type, In0, Out0] =
    tagged(Chunk(extraTag) ++ Chunk.fromIterable(extraTags))

  /**
   * Returns a new `MetricKey` with the specified tags appended.
   */
  def tagged(extraTags: Chunk[MetricLabel]): MetricKey2[Type, In0, Out0] =
    if (tags.isEmpty) self
    else MetricKey2[Type, In0, Out0](name, tags ++ extraTags, keyType)
}
object MetricKey2 {
  import zio.ZIOMetric.Histogram.Boundaries

  def apply[Type <: MetricKeyType, In0, Out0](
    name: String,
    tags: Chunk[MetricLabel],
    keyType: Type { type In = In0; type Out = Out0 }
  ): MetricKey2[Type, In0, Out0] = new MetricKey2(name, tags, keyType) {}

  /**
   * Creates a metric key for a counter, with the specified name & parameters.
   */
  def counter(name: String): MetricKey2[MetricKeyType.Counter, Double, MetricState2.Counter] =
    MetricKey2(name, Chunk.empty, MetricKeyType.Counter)

  /**
   * Creates a metric key for a gauge, with the specified name & parameters.
   */
  def gauge(name: String): MetricKey2[MetricKeyType.Gauge, Double, MetricState2.Gauge] =
    MetricKey2(name, Chunk.empty, MetricKeyType.Gauge)

  /**
   * Creates a metric key for a histogram, with the specified name & parameters.
   */
  def histogram(
    name: String,
    boundaries: Boundaries
  ): MetricKey2[MetricKeyType.Histogram, Double, MetricState2.Histogram] =
    MetricKey2(name, Chunk.empty, MetricKeyType.Histogram(boundaries))

  /**
   * Creates a metric key for a summary, with the specified name & parameters.
   */
  def summary(
    name: String,
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double]
  ): MetricKey2[MetricKeyType.Summary, Double, MetricState2.Summary] =
    MetricKey2(name, Chunk.empty, MetricKeyType.Summary(maxAge, maxSize, error, quantiles))

  /**
   * Creates a metric key for a set count, with the specified name & parameters.
   */
  def setCount(name: String, setTag: String): MetricKey2[MetricKeyType.SetCount, String, MetricState2.SetCount] =
    MetricKey2(name, Chunk.empty, MetricKeyType.SetCount(setTag))
}
