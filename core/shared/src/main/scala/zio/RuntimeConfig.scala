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
  logger: ZLogger[String, Any],
  flags: RuntimeConfigFlags
) { self =>
  def @@(aspect: RuntimeConfigAspect): RuntimeConfig = aspect(self)
}

object RuntimeConfig extends RuntimeConfigPlatformSpecific {

  sealed trait Patch { self =>
    import Patch._

    def apply(oldValue: RuntimeConfig): RuntimeConfig = {

      @tailrec
      def loop(runtimeConfig: RuntimeConfig, patches: List[Patch]): RuntimeConfig =
        patches match {
          case AddRuntimeConfigFlag(flag) :: patches =>
            loop(runtimeConfig.copy(flags = runtimeConfig.flags + flag), patches)
          case AndThen(first, second) :: patches =>
            loop(runtimeConfig, first :: second :: patches)
          case Empty :: patches =>
            loop(runtimeConfig, patches)
          case RemoveRuntimeConfigFlag(flag) :: patches =>
            loop(runtimeConfig.copy(flags = runtimeConfig.flags - flag), patches)
          case SetBlockingExecutor(executor) :: patches =>
            loop(runtimeConfig.copy(blockingExecutor = executor), patches)
          case SetExecutor(executor) :: patches =>
            loop(runtimeConfig.copy(executor = executor), patches)
          case SetFatal(fatal) :: patches =>
            loop(runtimeConfig.copy(fatal = fatal), patches)
          case SetLogger(logger) :: patches =>
            loop(runtimeConfig.copy(logger = logger), patches)
          case SetReportFatal(reportFatal) :: patches =>
            loop(runtimeConfig.copy(reportFatal = reportFatal), patches)
          case SetSupervisor(supervisor) :: patches =>
            loop(runtimeConfig.copy(supervisor = supervisor), patches)
          case Nil =>
            runtimeConfig
        }

      loop(oldValue, List(self))
    }

    def combine(that: Patch): Patch =
      (self, that) match {
        case (self, Empty) => self
        case (Empty, that) => that
        case (self, that)  => AndThen(self, that)
      }
  }

  object Patch {

    def diff(oldValue: RuntimeConfig, newValue: RuntimeConfig): Patch = {

      def diffBlockingExecutor(oldValue: Executor, newValue: Executor): Patch =
        if (oldValue == newValue) Empty else SetBlockingExecutor(newValue)

      def diffExecutor(oldValue: Executor, newValue: Executor): Patch =
        if (oldValue == newValue) Empty else SetExecutor(newValue)

      def diffFatal(oldValue: Throwable => Boolean, newValue: Throwable => Boolean): Patch =
        if (oldValue == newValue) Empty else SetFatal(newValue)

      def diffLogger(oldValue: ZLogger[String, Any], newValue: ZLogger[String, Any]): Patch =
        if (oldValue == newValue) Empty else SetLogger(newValue)

      def diffReportFatal(oldValue: Throwable => Nothing, newValue: Throwable => Nothing): Patch =
        if (oldValue == newValue) Empty else SetReportFatal(newValue)

      def diffRuntimeConfigFlags(oldValue: RuntimeConfigFlags, newValue: RuntimeConfigFlags): Patch = {
        val (removed, patch) = newValue.flags.foldLeft[(Set[RuntimeConfigFlag], Patch)](oldValue.flags -> empty) {
          case ((set, patch), flag) =>
            if (set.contains(flag)) (set - flag, patch)
            else (set, patch.combine(Patch.AddRuntimeConfigFlag(flag)))
        }
        removed.foldLeft(patch)((patch, flag) => patch.combine(Patch.RemoveRuntimeConfigFlag(flag)))
      }

      def diffSupervisor(oldValue: Supervisor[Any], newValue: Supervisor[Any]): Patch =
        if (oldValue == newValue) Empty else SetSupervisor(newValue)

      val diffs = List(
        diffBlockingExecutor(oldValue.blockingExecutor, newValue.blockingExecutor),
        diffExecutor(oldValue.executor, newValue.executor),
        diffFatal(oldValue.fatal, newValue.fatal),
        diffLogger(oldValue.logger, newValue.logger),
        diffReportFatal(oldValue.reportFatal, newValue.reportFatal),
        diffRuntimeConfigFlags(oldValue.flags, newValue.flags),
        diffSupervisor(oldValue.supervisor, newValue.supervisor)
      )

      diffs.foldLeft(empty)(_ combine _)
    }

    val empty: Patch =
      Empty

    private final case class AddRuntimeConfigFlag(flag: RuntimeConfigFlag)     extends Patch
    private final case class AndThen(first: Patch, second: Patch)              extends Patch
    private case object Empty                                                  extends Patch
    private final case class RemoveRuntimeConfigFlag(flag: RuntimeConfigFlag)  extends Patch
    private final case class SetBlockingExecutor(executor: Executor)           extends Patch
    private final case class SetExecutor(executor: Executor)                   extends Patch
    private final case class SetFatal(fatal: Throwable => Boolean)             extends Patch
    private final case class SetLogger(logger: ZLogger[String, Any])           extends Patch
    private final case class SetReportFatal(reportfatal: Throwable => Nothing) extends Patch
    private final case class SetSupervisor(supervisor: Supervisor[Any])        extends Patch
  }
}
