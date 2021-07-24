/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
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

import zio.internal.tracing.TracingConfig
import zio.{Cause, Supervisor}

/**
 * A `Platform` provides the minimum capabilities necessary to bootstrap
 * execution of `ZIO` tasks.
 */
final case class Platform(
  blockingExecutor: Executor,
  executor: Executor,
  tracing: Tracing,
  fatal: Throwable => Boolean,
  reportFatal: Throwable => Nothing,
  reportFailure: Cause[Any] => Unit,
  supervisor: Supervisor[Any],
  enableCurrentFiber: Boolean
) { self =>
  def withBlockingExecutor(e: Executor): Platform = copy(blockingExecutor = e)

  def withExecutor(e: Executor): Platform = copy(executor = e)

  def withTracing(t: Tracing): Platform = copy(tracing = t)

  def withTracingConfig(config: TracingConfig): Platform = copy(tracing = tracing.copy(tracingConfig = config))

  def withFatal(f: Throwable => Boolean): Platform = copy(fatal = f)

  def withReportFatal(f: Throwable => Nothing): Platform = copy(fatal = f)

  def withReportFailure(f: Cause[Any] => Unit): Platform = copy(reportFailure = f)

  def withSupervisor(s0: Supervisor[Any]): Platform = copy(supervisor = s0)
}
object Platform extends PlatformSpecific
