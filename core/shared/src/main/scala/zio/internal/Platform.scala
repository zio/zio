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
  enableCurrentFiber: Boolean,
  logger: ZLogger[Unit]
) { self =>
  @deprecated("2.0.0", "Use Platform#copy instead")
  def withBlockingExecutor(e: Executor): Platform = copy(blockingExecutor = e)

  @deprecated("2.0.0", "Use Platform#copy instead")
  def withExecutor(e: Executor): Platform = copy(executor = e)

  @deprecated("2.0.0", "Use Platform#copy instead")
  def withFatal(f: Throwable => Boolean): Platform = copy(fatal = f)

  @deprecated("2.0.0", "Use Platform#copy instead")
  def withReportFatal(f: Throwable => Nothing): Platform = copy(reportFatal = f)

  @deprecated("2.0.0", "Use Platform#copy instead")
  def withReportFailure(f: Cause[Any] => Unit): Platform = copy(reportFailure = f)

  @deprecated("2.0.0", "Use Platform#copy instead")
  def withSupervisor(s0: Supervisor[Any]): Platform = copy(supervisor = s0)

  @deprecated("2.0.0", "Use Platform#copy instead")
  def withTracing(t: Tracing): Platform = copy(tracing = t)
}
object Platform extends PlatformSpecific {
  private val osName =
    Option(scala.util.Try(System.getProperty("os.name")).getOrElse("")).map(_.toLowerCase()).getOrElse("")

  lazy val os: OS =
    if (osName.contains("win")) OS.Windows
    else if (osName.contains("mac")) OS.Mac
    else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) OS.Unix
    else if (osName.contains("sunos")) OS.Solaris
    else OS.Unknown

  sealed trait OS { self =>
    def isWindows: Boolean = self == OS.Windows
    def isMac: Boolean     = self == OS.Mac
    def isUnix: Boolean    = self == OS.Unix
    def isSolaris: Boolean = self == OS.Solaris
    def isUnknown: Boolean = self == OS.Unknown
  }
  object OS {
    case object Windows extends OS
    case object Mac     extends OS
    case object Unix    extends OS
    case object Solaris extends OS
    case object Unknown extends OS
  }
}
