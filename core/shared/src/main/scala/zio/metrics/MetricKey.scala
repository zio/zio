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
final case class MetricKey[+Type] private (
  name: String,
  tags: Set[MetricLabel],
  keyType: Type
) { self =>

  /**
   * Returns a new `MetricKey` with the specified tag appended.
   */
  def tagged(key: String, value: String): MetricKey[Type] =
    tagged(Set(MetricLabel(key, value)))

  /**
   * Returns a new `MetricKey` with the specified tags appended.
   */
  def tagged(extraTag: MetricLabel, extraTags: MetricLabel*): MetricKey[Type] =
    tagged(Set(extraTag) ++ extraTags.toSet)

  /**
   * Returns a new `MetricKey` with the specified tags appended.
   */
  def tagged(extraTags: Set[MetricLabel]): MetricKey[Type] =
    if (extraTags.isEmpty) self else copy(tags = tags ++ extraTags)
}
object MetricKey {
  type Untyped = MetricKey[Any]

  type Counter   = MetricKey[MetricKeyType.Counter]
  type Gauge     = MetricKey[MetricKeyType.Gauge]
  type Histogram = MetricKey[MetricKeyType.Histogram]
  type Summary   = MetricKey[MetricKeyType.Summary]
  type SetCount  = MetricKey[MetricKeyType.SetCount]

  import MetricKeyType.Histogram.Boundaries

  /**
   * Creates a metric key for a counter, with the specified name & parameters.
   */
  def counter(name: String): Counter =
    MetricKey(name, Set.empty, MetricKeyType.Counter)

  /**
   * Creates a metric key for a gauge, with the specified name & parameters.
   */
  def gauge(name: String): Gauge =
    MetricKey(name, Set.empty, MetricKeyType.Gauge)

  /**
   * Creates a metric key for a histogram, with the specified name & parameters.
   */
  def histogram(
    name: String,
    boundaries: Boundaries
  ): Histogram =
    MetricKey(name, Set.empty, MetricKeyType.Histogram(boundaries))

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
    MetricKey(name, Set.empty, MetricKeyType.Summary(maxAge, maxSize, error, quantiles))

  /**
   * Creates a metric key for a set count, with the specified name & parameters.
   */
  def setCount(name: String, setTag: String): SetCount =
    MetricKey(name, Set.empty, MetricKeyType.SetCount(setTag))
}
