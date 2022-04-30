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

import scala.concurrent.ExecutionContext

private[zio] trait RuntimePlatformSpecific {

  /**
   * The default number of operations the ZIO runtime should execute before
   * yielding to other fibers.
   */
  final val defaultYieldOpCount: Int =
    2048

  final val defaultExecutor: Executor =
    Executor.fromExecutionContext(defaultYieldOpCount)(ExecutionContext.global)

  final val defaultBlockingExecutor: Executor =
    defaultExecutor

  final val defaultFatal: Set[Class[_ <: Throwable]] =
    Set.empty

  final val defaultFlags: Set[RuntimeConfigFlag] =
    Set(RuntimeConfigFlag.EnableFiberRoots)

  final val defaultLoggers: Set[ZLogger[String, Any]] =
    Set(ZLogger.default.map(println(_)).filterLogLevel(_ >= LogLevel.Info))

  final val defaultReportFatal: Throwable => Nothing =
    (t: Throwable) => {
      t.printStackTrace()
      throw t
    }

  final val defaultSupervisors: Set[Supervisor[Any]] =
    Set.empty
}
