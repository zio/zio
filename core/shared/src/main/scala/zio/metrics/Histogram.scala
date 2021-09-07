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
import zio.metrics.clients._

/**
 * A `Histogram` is a metric representing a collection of numerical with the
 * distribution of the cumulative values over time. A typical use of this
 * metric would be to track the time to serve requests. Histograms allow
 * visualizing not only the value of the quantity being measured but its
 * distribution. Histograms are constructed with user specified boundaries
 * which describe the buckets to aggregate values into.
 */
trait Histogram {

  /**
   * The current sum and count of values in each bucket of the histogram.
   */
  def buckets: UIO[Chunk[(Double, Long)]]

  /**
   * The current count of values in the histogram.
   */
  def count: UIO[Long]

  /**
   * Adds the specified value to the distribution of values represented by the
   * histogram.
   */
  def observe(value: Double): UIO[Any]

  /**
   * The current sum of values in the histogram.
   */
  def sum: UIO[Double]
}

object Histogram {

  /**
   * Constructs a histogram with the specified key.
   */
  def apply(key: MetricKey.Histogram): Histogram =
    metricState.getHistogram(key)

  /**
   * Constructs a histogram with the specified name, boundaries, and labels.
   * The boundaries must be in strictly increasing order.
   */
  def apply(name: String, boundaries: Chunk[Double], tags: MetricLabel*): Histogram =
    apply(MetricKey.Histogram(name, boundaries, Chunk.fromIterable(tags)))
}
