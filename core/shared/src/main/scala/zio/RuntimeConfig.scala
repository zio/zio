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

import scala.annotation.tailrec

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
  loggers: Set[ZLogger[String, Any]],
  flags: RuntimeConfigFlags
) { self =>
  def @@(aspect: RuntimeConfigAspect): RuntimeConfig = aspect(self)
}

object RuntimeConfig extends RuntimeConfigPlatformSpecific
