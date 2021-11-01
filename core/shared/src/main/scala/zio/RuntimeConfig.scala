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

package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A `RuntimeConfig` provides the minimum capabilities necessary to bootstrap
 * execution of `ZIO` tasks.
 */
final case class RuntimeConfig(
  blockingExecutor: Executor,
  executor: Executor,
  fatal: Throwable => Boolean,
  reportFatal: Throwable => Nothing,
  supervisor: Supervisor[Any],
  enableCurrentFiber: Boolean,
  logger: ZLogger[Any],
  logRuntime: Boolean,
  superviseOperations: Boolean
) { self =>
  def @@(aspect: RuntimeConfigAspect): RuntimeConfig = aspect(self)

  @deprecated("2.0.0", "Use RuntimeConfig#copy instead")
  def withBlockingExecutor(e: Executor): RuntimeConfig = copy(blockingExecutor = e)

  @deprecated("2.0.0", "Use RuntimeConfig#copy instead")
  def withExecutor(e: Executor): RuntimeConfig = copy(executor = e)

  @deprecated("2.0.0", "Use RuntimeConfig#copy instead")
  def withFatal(f: Throwable => Boolean): RuntimeConfig = copy(fatal = f)

  @deprecated("2.0.0", "Use RuntimeConfig#copy instead")
  def withReportFatal(f: Throwable => Nothing): RuntimeConfig = copy(reportFatal = f)

  @deprecated("2.0.0", "Use RuntimeConfig#copy instead")
  def withSupervisor(s0: Supervisor[Any]): RuntimeConfig = copy(supervisor = s0)
}

object RuntimeConfig extends RuntimeConfigPlatformSpecific
