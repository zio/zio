/*
 * Copyright 2020-2022 John A. De Goes and the ZIO Contributors
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
 * A `Gauge` is a metric representing a single numerical value that may be set
 * or adjusted. A typical use of this metric would be to track the current
 * memory usage. With a gauge the quantity of interest is the current value, as
 * opposed to a counter where the quantity of interest is the cumulative values
 * over time.
 */
private[zio] trait Gauge {

  /**
   * Adjusts the gauge by the specified amount.
   */
  def adjust(value: Double)(implicit trace: ZTraceElement): UIO[Any]

  /**
   * Sets the gauge to the specified value.
   */
  def set(value: Double)(implicit trace: ZTraceElement): UIO[Any]

  /**
   * The current value of the gauge.
   */
  def value(implicit trace: ZTraceElement): UIO[Double]

  private[zio] def metricKey: MetricKey.Gauge
}

private[zio] object Gauge {

  /**
   * Construct a gauge with the specified key.
   */

  def apply(key: MetricKey.Gauge): Gauge =
    metricState.getGauge(key)

  /**
   * Constructs a gauge with the specified name and labels.
   */
  def apply(name: String, tags: Chunk[MetricLabel]): Gauge =
    apply(MetricKey.Gauge(name, tags))
}
