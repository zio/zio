/*
 * Copyright 2020-2024 John A. De Goes and the ZIO Contributors
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
 * A `MetricState` describes the state of a metric. The type parameter of a
 * metric state corresponds to the type of the metric key (MetricKeyType). This
 * phantom type parameter is used to tie keys to their expected states.
 */
sealed trait MetricState[+Type]

/**
 * The states of the five primitive metric types.
 */
object MetricState {

  /**
   * A metric state whose metric type is not known statically.
   */
  type Untyped = MetricState[Any]

  /**
   * The state of a counter: the sum of all the updates.
   */
  final case class Counter(count: Double) extends MetricState[MetricKeyType.Counter]

  /**
   * The state of a frequency: the number of occurrences of each string.
   */
  final case class Frequency(
    occurrences: Map[String, Long]
  ) extends MetricState[MetricKeyType.Frequency]

  /**
   * The state of a gauge: its current value.
   */
  final case class Gauge(value: Double) extends MetricState[MetricKeyType.Gauge]

  /**
   * The state of a histogram. `buckets` pairs each boundary, in ascending
   * order, with the number of observed values less than or equal to it.
   * `count`, `min`, `max` and `sum` cover every observed value; with no
   * observation, `min` is `Double.MaxValue` and `max` is `Double.MinValue`.
   */
  final case class Histogram(
    buckets: Chunk[(Double, Long)],
    count: Long,
    min: Double,
    max: Double,
    sum: Double
  ) extends MetricState[MetricKeyType.Histogram]

  /**
   * The state of a summary. `quantiles` pairs each requested quantile, in
   * ascending order, with its value among the samples in the window, or `None`
   * when the window is empty. `count`, `min`, `max` and `sum` cover every
   * observed value; with no observation, `min` is `Double.MaxValue` and `max`
   * is `Double.MinValue`. `error` is the value given to the constructor.
   */
  final case class Summary(
    error: Double,
    quantiles: Chunk[(Double, Option[Double])],
    count: Long,
    min: Double,
    max: Double,
    sum: Double
  ) extends MetricState[MetricKeyType.Summary]
}
