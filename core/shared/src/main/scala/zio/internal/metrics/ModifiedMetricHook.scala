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

final case class ModifiedMetricHook[In0, Out0, -In, +Out](
  hook: MetricHook[In0, Out0],
  contramapper: In => In0,
  mapper: Out0 => Out
) { self =>
  def contramap[In2](f: In2 => In): ModifiedMetricHook[In0, Out0, In2, Out] =
    copy(contramapper = f.andThen(self.contramapper))

  val get: () => Out = () => mapper(hook.get())

  def map[Out2](f: Out => Out2): ModifiedMetricHook[In0, Out0, In, Out2] =
    copy(mapper = self.mapper.andThen(f))

  val update: In => Unit = contramapper.andThen(hook.update)

  def zip[In1, Out1, In2, Out2](
    that: ModifiedMetricHook[In1, Out1, In2, Out2]
  ): ModifiedMetricHook[(In0, In1), (Out0, Out1), (In, In2), (Out, Out2)] =
    ModifiedMetricHook(
      hook.zip(that.hook),
      (i: (In, In2)) => (self.contramapper(i._1), that.contramapper(i._2)),
      (o: (Out0, Out1)) => (self.mapper(o._1), that.mapper(o._2))
    )
}
object ModifiedMetricHook {
  def apply[In, Out](hook: MetricHook[In, Out]): ModifiedMetricHook[In, Out, In, Out] =
    ModifiedMetricHook(hook, identity(_), identity(_))
}
