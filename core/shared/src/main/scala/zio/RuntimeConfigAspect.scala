/*
 * Copyright 2021-2022 John A. De Goes and the ZIO Contributors
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

final case class RuntimeConfigAspect private (customize: RuntimeConfig => RuntimeConfig)
    extends (RuntimeConfig => RuntimeConfig) { self =>
  def apply(p: RuntimeConfig): RuntimeConfig = customize(p)

  def >>>(that: RuntimeConfigAspect): RuntimeConfigAspect = RuntimeConfigAspect(self.customize.andThen(that.customize))
}

object RuntimeConfigAspect {

  def addLogger(logger: ZLogger[String, Any]): RuntimeConfigAspect =
    RuntimeConfigAspect(self => self.copy(loggers = self.loggers + logger))

  def addSupervisor(supervisor: Supervisor[Any]): RuntimeConfigAspect =
    RuntimeConfigAspect(self => self.copy(supervisors = self.supervisors + supervisor))

  val enableCurrentFiber: RuntimeConfigAspect =
    RuntimeConfigAspect(self => self.copy(flags = self.flags + RuntimeConfigFlag.EnableCurrentFiber))

  val enableFiberRoots: RuntimeConfigAspect =
    RuntimeConfigAspect(self => self.copy(flags = self.flags + RuntimeConfigFlag.EnableFiberRoots))

  val identity: RuntimeConfigAspect =
    RuntimeConfigAspect(Predef.identity(_))

  def setBlockingExecutor(executor: Executor): RuntimeConfigAspect =
    RuntimeConfigAspect(_.copy(blockingExecutor = executor))

  def setExecutor(executor: Executor): RuntimeConfigAspect =
    RuntimeConfigAspect(_.copy(executor = executor))

  def setReportFatal(reportFatal: Throwable => Nothing): RuntimeConfigAspect =
    RuntimeConfigAspect(self => self.copy(reportFatal = reportFatal))

  val superviseOperations: RuntimeConfigAspect =
    RuntimeConfigAspect(self => self.copy(flags = self.flags + RuntimeConfigFlag.SuperviseOperations))

  /**
   * An aspect that adds a supervisor that tracks all forked fibers in a set.
   * Note that this may have a negative impact on performance.
   */
  def track(weak: Boolean): RuntimeConfigAspect =
    addSupervisor(Supervisor.unsafeTrack(weak))
}
