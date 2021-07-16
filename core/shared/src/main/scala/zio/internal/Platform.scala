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
import zio.{Cause, FiberRef, LogLevel, Supervisor}

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
  enableCurrentFiber: Boolean,
  logger: (LogLevel, () => String, Map[FiberRef.Runtime[_], AnyRef], List[String]) => Unit
) { self =>
  @deprecated("2.0.0", "Use Platform#copy instead")
  def withBlockingExecutor(e: Executor): Platform = copy(blockingExecutor = e)

  @deprecated("2.0.0", "Use Platform#copy instead")
  def withExecutor(e: Executor): Platform = copy(executor = e)

  @deprecated("2.0.0", "Use Platform#copy instead")
  def withFatal(f: Throwable => Boolean): Platform = copy(fatal = f)

  @deprecated("2.0.0", "Use Platform#copy instead")
  def withLogger(l: (LogLevel, () => String, Map[FiberRef.Runtime[_], AnyRef], List[String]) => Unit): Platform = 
    copy(logger = l)

  @deprecated("2.0.0", "Use Platform#copy instead")
  def withReportFatal(f: Throwable => Nothing): Platform = copy(reportFatal = f)

  @deprecated("2.0.0", "Use Platform#copy instead")
  def withReportFailure(f: Cause[Any] => Unit): Platform = copy(reportFailure = f)

  @deprecated("2.0.0", "Use Platform#copy instead")
  def withSupervisor(s0: Supervisor[Any]): Platform = copy(supervisor = s0)

  @deprecated("2.0.0", "Use Platform#copy instead")
  def withTracing(t: Tracing): Platform = copy(tracing = t)
}
object Platform extends PlatformSpecific