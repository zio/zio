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

package zio.internal.metrics

import zio._
import zio.metrics._
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A `Summary` represents a sliding window of a time series along with metrics
 * for certain percentiles of the time series, referred to as quantiles.
 * Quantiles describe specified percentiles of the sliding window that are of
 * interest. For example, if we were using a summary to track the response time
 * for requests over the last hour then we might be interested in the 50th
 * percentile, 90th percentile, 95th percentile, and 99th percentile for
 * response times.
 */
private[zio] trait Summary {

  /**
   * The current count of all the values ever observed by this dsummary.
   */
  def count(implicit trace: ZTraceElement): UIO[Long]

  /**
   * Adds the specified value to the time series represented by the summary,
   * also recording the Instant when the value was observed
   */
  def observe(value: Double)(implicit trace: ZTraceElement): UIO[Any]

  /**
   * The values corresponding to each quantile in the summary.
   */
  def quantileValues(implicit trace: ZTraceElement): UIO[Chunk[(Double, Option[Double])]]

  /**
   * The current sum of all the values ever observed by the summary.
   */
  def sum(implicit trace: ZTraceElement): UIO[Double]

}

private[zio] object Summary {

  /**
   * Constructs a new summary with the specified key.
   */
  def apply(key: MetricKey.Summary): Summary =
    metricState.getSummary(key)

  /**
   * Constructs a new summary with the specified name, maximum age, maximum
   * size, quantiles, and labels. The quantiles must be between 0.0 and 1.0. The
   * error is a percentage and must be between 0.0 and 1.0.
   */
  def apply(
    name: String,
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double],
    tags: Chunk[MetricLabel]
  ): Summary =
    apply(MetricKey.Summary(name, maxAge, maxSize, error, quantiles, tags))
}
