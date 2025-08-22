/*
 * Copyright 2022-2024 John A. De Goes and the ZIO Contributors
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

private[zio] sealed trait MetricHook[-In, +Out] {
  def update: In => Unit
  def get: () => Out
  def modify: In => Unit
}

private[zio] final case class MetricHookDouble[+Out](
  update: Double => Unit,
  get: () => Out,
  modify: Double => Unit
) extends MetricHook[Double, Out]

private[zio] final case class MetricHookAnyRef[-In, +Out](
  update: In => Unit,
  get: () => Out,
  modify: In => Unit
) extends MetricHook[In, Out]

private[zio] object MetricHook {
  import zio.metrics.MetricState

  final case class SummaryValue(value: Double, timestamp: java.time.Instant)

  type Root    = MetricHook[_, MetricState.Untyped]
  type Untyped = MetricHook[_, _]

  type Counter   = MetricHookDouble[MetricState.Counter]
  type Gauge     = MetricHookDouble[MetricState.Gauge]
  type Histogram = MetricHookDouble[MetricState.Histogram]
  type Summary   = MetricHookAnyRef[SummaryValue, MetricState.Summary]
  type Frequency = MetricHookAnyRef[String, MetricState.Frequency]
}
