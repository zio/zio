/*
 * Copyright 2019-2021 John A. De Goes and the ZIO Contributors
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

package zio.internal

import zio.internal.stacktracer.Tracer
import zio.internal.stacktracer.impl.AkkaLineNumbersTracer
import zio.internal.tracing.TracingConfig

final case class Tracing(tracer: Tracer, tracingConfig: TracingConfig)

object Tracing {
  def enabled: Tracing =
    Tracing(Tracer.globallyCached(new AkkaLineNumbersTracer), TracingConfig.enabled)

  def enabledWith(tracingConfig: TracingConfig): Tracing =
    Tracing(Tracer.globallyCached(new AkkaLineNumbersTracer), tracingConfig)

  def disabled: Tracing = Tracing(Tracer.Empty, TracingConfig.disabled)
}
