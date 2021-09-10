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

/**
 * `MetricState` represents a snapshot of the current state of a metric as of
 * a poiint in time.
 */
final case class MetricState(
  name: String,
  help: String,
  labels: Chunk[MetricLabel],
  details: MetricType
) {
  override def toString(): String = {
    val lbls = if (labels.isEmpty) "" else labels.map(l => s"${l.key}->${l.value}").mkString("{", ",", "}")
    s"MetricState($name$lbls, $details)"
  }
}

object MetricState {

  /**
   * Constructs a snapshot of the state of a counter.
   */
  def counter(key: MetricKey.Counter, help: String, value: Double): MetricState =
    MetricState(key.name, help, key.tags, MetricType.Counter(value))

  /**
   * Constructs a snapshot of the state of a gauge.
   */
  def gauge(
    key: MetricKey.Gauge,
    help: String,
    startAt: Double
  ): MetricState =
    MetricState(key.name, help, key.tags, MetricType.Gauge(startAt))

  /**
   * Constructs a snapshot of the state of a histogram.
   */
  def histogram(
    key: MetricKey.Histogram,
    help: String,
    buckets: Chunk[(Double, Long)],
    count: Long,
    sum: Double
  ): MetricState =
    MetricState(
      key.name,
      help,
      key.tags,
      MetricType.DoubleHistogram(buckets, count, sum)
    )

  /**
   * Constructs a snapshot of the state of a summary.
   */
  def summary(
    key: MetricKey.Summary,
    help: String,
    quantiles: Chunk[(Double, Option[Double])],
    count: Long,
    sum: Double
  ): MetricState =
    MetricState(
      key.name,
      help,
      key.tags,
      MetricType.Summary(key.error, quantiles, count, sum)
    )

  /**
   * Constructs a snapshot of the state of a set count..
   */
  def setCount(
    key: MetricKey.SetCount,
    help: String,
    values: Chunk[(String, Long)]
  ): MetricState =
    MetricState(key.name, help, key.tags, MetricType.SetCount(key.setTag, values))
}
