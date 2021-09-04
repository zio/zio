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

/**
 * A `Summary` represents a sliding window of a time series along with metrics
 * for certain percentiles of the time series, referred to as quantiles.
 * Quantiles describe specified percentiles of the sliding window that are of
 * interest. For example, if we were using a summary to track the response time
 * for requests over the last hour then we might be interested in the 50th
 * percentile, 90th percentile, 95th percentile, and 99th percentile for
 * response times.
 */
trait Summary {

  /**
   * Adds the specified value to the time series represented by the summary,
   * also recording the Instant when the value was observed
   */
  def observe(value: Double, t: java.time.Instant): UIO[Any]
}

object Summary {

  /**
   * Constructs a new summary with the specified key
   */
  def apply(key: MetricKey.Summary): Summary =
    metricState.getSummary(key)

  /**
   * Constructs a new summary with the specified name, maximum age, maximum
   * size, quantiles, and labels.
   * The quantiles must be between 0.0 and 1.0.
   * The error is a percentage and must be between 0.0 and 1.0, i.e 3% => 0.03
   */
  def apply(
    name: String,
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double],
    tags: Label*
  ): Summary =
    apply(MetricKey.Summary(name, maxAge, maxSize, error, quantiles, Chunk.fromIterable(tags)))

  /**
   * A summary that does nothing.
   */
  val none: Summary =
    new Summary {
      def observe(value: Double, t: java.time.Instant): UIO[Any] =
        ZIO.unit
    }
}
