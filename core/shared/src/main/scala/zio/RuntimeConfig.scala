/*
 * Copyright 2017-2022 John A. De Goes and the ZIO Contributors
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
  logger: ZLogger[String, Any],
  flags: RuntimeConfigFlags,
  services: ZEnvironment[ZEnv]
) { self =>
  def @@(aspect: RuntimeConfigAspect): RuntimeConfig = aspect(self)

  @deprecated("Use RuntimeConfig#copy instead", "2.0.0")
  def withBlockingExecutor(e: Executor): RuntimeConfig = copy(blockingExecutor = e)

  @deprecated("Use RuntimeConfig#copy instead", "2.0.0")
  def withExecutor(e: Executor): RuntimeConfig = copy(executor = e)

  @deprecated("Use RuntimeConfig#copy instead", "2.0.0")
  def withFatal(f: Throwable => Boolean): RuntimeConfig = copy(fatal = f)

  @deprecated("Use RuntimeConfig#copy instead", "2.0.0")
  def withReportFatal(f: Throwable => Nothing): RuntimeConfig = copy(reportFatal = f)

  @deprecated("Use RuntimeConfig#copy instead", "2.0.0")
  def withSupervisor(s0: Supervisor[Any]): RuntimeConfig = copy(supervisor = s0)
}

object RuntimeConfig extends RuntimeConfigPlatformSpecific
