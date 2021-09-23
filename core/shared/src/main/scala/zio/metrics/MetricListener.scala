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

/**
 * A `MetricListener` is capable of taking some action in response to a metric
 * being recorded, such as sending that metric to a third party service.
 */
trait MetricListener { self =>
  def unsafeGaugeChanged(key: MetricKey.Gauge, value: Double, delta: Double): Unit
  def unsafeCounterChanged(key: MetricKey.Counter, absValue: Double, delta: Double): Unit
  def unsafeHistogramChanged(key: MetricKey.Histogram, value: MetricState): Unit
  def unsafeSummaryChanged(key: MetricKey.Summary, value: MetricState): Unit
  def unsafeSetChanged(key: MetricKey.SetCount, value: MetricState): Unit
}
