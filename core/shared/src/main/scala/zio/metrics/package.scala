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

package zio

import zio.internal.metrics._

package object metrics {
  private[zio] def unsafeGetMetricHook[Type <: MetricKeyType](key: Type): MetricHook[key.In, key.Out] = ???

  def updateMetric[Type <: MetricKeyType, In, Out](key: MetricKey[Type, In, Out], value: In): UIO[Unit] =
    ZIO.succeed(key.metricHook.update(value))

  def getMetric[Type <: MetricKeyType, In, Out](key: MetricKey[Type, In, Out]): UIO[Out] =
    ZIO.succeed(key.metricHook.get())
}
