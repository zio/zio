/*
 * Copyright 2022 John A. De Goes and the ZIO Contributors
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

final case class MetricHook[-In, +Out](
  update: In => Unit,
  get: () => Out
) { self =>
  def contramap[In2](f: In2 => In): MetricHook[In2, Out] =
    MetricHook(update.compose(f), get)

  def map[Out2](f: Out => Out2): MetricHook[In, Out2] =
    MetricHook(update, () => f(get()))

  def onUpdate[In1 <: In](f: In1 => Unit): MetricHook[In1, Out] =
    MetricHook(in => { f(in); self.update(in) }, get)

  def zip[In2, Out2](that: MetricHook[In2, Out2]): MetricHook[(In, In2), (Out, Out2)] =
    MetricHook(
      t => {
        self.update(t._1)
        that.update(t._2)
      },
      () => (self.get(), that.get())
    )
}
object MetricHook {
  import zio.metrics.MetricState

  type Root    = MetricHook[_, MetricState.Untyped]
  type Untyped = MetricHook[_, _]

  type Counter   = MetricHook[Double, MetricState.Counter]
  type Gauge     = MetricHook[Double, MetricState.Gauge]
  type Histogram = MetricHook[Double, MetricState.Histogram]
  type Summary   = MetricHook[(Double, java.time.Instant), MetricState.Summary]
  type SetCount  = MetricHook[String, MetricState.SetCount]
}
