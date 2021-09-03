/*
 * Copyright 2021 John A. De Goes and the ZIO Contributors
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

import zio.internal.{Tracing, ZLogger}

final case class RuntimeConfigAspect(customize: RuntimeConfig => RuntimeConfig)
    extends (RuntimeConfig => RuntimeConfig) { self =>
  def apply(p: RuntimeConfig): RuntimeConfig = customize(p)

  def >>>(that: RuntimeConfigAspect): RuntimeConfigAspect = RuntimeConfigAspect(self.customize.andThen(that.customize))
}
object RuntimeConfigAspect extends ((RuntimeConfig => RuntimeConfig) => RuntimeConfigAspect) {

  def addLogger(logger: ZLogger[Any]): RuntimeConfigAspect =
    RuntimeConfigAspect(self => self.copy(logger = self.logger ++ logger))

  def addReportFatal(f: Throwable => Nothing): RuntimeConfigAspect =
    RuntimeConfigAspect(self => self.copy(reportFatal = t => { self.reportFatal(t); f(t) }))

  def addSupervisor(supervisor: Supervisor[Any]): RuntimeConfigAspect =
    RuntimeConfigAspect(self => self.copy(supervisor = self.supervisor ++ supervisor))

  val identity: RuntimeConfigAspect =
    RuntimeConfigAspect(Predef.identity(_))

  def setBlockingExecutor(executor: Executor): RuntimeConfigAspect =
    RuntimeConfigAspect(_.copy(blockingExecutor = executor))

  def setExecutor(executor: Executor): RuntimeConfigAspect =
    RuntimeConfigAspect(_.copy(executor = executor))

  def setTracing(tracing: Tracing): RuntimeConfigAspect =
    RuntimeConfigAspect(_.copy(tracing = tracing))
}
