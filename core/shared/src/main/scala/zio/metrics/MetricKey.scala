/*
 * Copyright 2022-2023 John A. De Goes and the ZIO Contributors
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
sealed case class MetricKey[+Type] private (
  name: String,
  keyType: Type,
  tags: Set[MetricLabel] = Set.empty
) { self =>

  def description: Option[String] = None

  def copy[Type2](name: String = name, keyType: Type2 = keyType, tags: Set[MetricLabel] = tags): MetricKey[Type2] =
    new MetricKey(name, keyType, tags) {
      override def description: Option[String] = self.description
    }

  /**
   * Returns a new `MetricKey` with the specified description.
   */
  def described(description0: String): MetricKey[Type] =
    new MetricKey[Type](self.name, self.keyType, self.tags) {
      override def description: Option[String] = Some(description0)
    }

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
  type Frequency = MetricKey[MetricKeyType.Frequency]

  import MetricKeyType.Histogram.Boundaries

  /**
   * Creates a metric key for a counter, with the specified name.
   */
  def counter(name: String): Counter =
    MetricKey(name, MetricKeyType.Counter)

  /**
   * Creates a metric key for a categorical frequency table, with the specified
   * name.
   */
  def frequency(name: String): Frequency =
    MetricKey(name, MetricKeyType.Frequency)

  /**
   * Creates a metric key for a gauge, with the specified name.
   */
  def gauge(name: String): Gauge =
    MetricKey(name, MetricKeyType.Gauge)

  /**
   * Creates a metric key for a histogram, with the specified name.
   */
  def histogram(
    name: String,
    boundaries: Boundaries
  ): Histogram =
    MetricKey(name, MetricKeyType.Histogram(boundaries))

  /**
   * Creates a metric key for a summary, with the specified name.
   */
  def summary(
    name: String,
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double]
  ): Summary =
    MetricKey(name, MetricKeyType.Summary(maxAge, maxSize, error, quantiles))
}
