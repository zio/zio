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
 * A `Counter` is a metric representing a single numerical value that may be
 * incremented over time. A typical use of this metric would be to track the
 * number of a certain type of request received. With a counter the quantity
 * of interest is the cumulative value over time, as opposed to a gauge where
 * the quantity of interest is the value as of a specific point in time.
 */
trait Counter {

  /**
   * The current value of the counter.
   */
  def count: UIO[Double]

  /**
   * Increments the counter by the specified amount.
   */
  def increment(value: Double): UIO[Any]
}

object Counter {

  /**
   * Construct a counter with the specified metric key.
   */
  def apply(key: MetricKey.Counter): Counter =
    metricState.getCounter(key)

  /**
   * Constructs a counter with the specified name and labels.
   */
  def apply(name: String, tags: Label*): Counter =
    apply(MetricKey.Counter(name, Chunk.fromIterable(tags)))
}
