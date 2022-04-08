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

import zio.internal.Blocking
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.concurrent.ExecutionContext

private[zio] trait RuntimeConfigPlatformSpecific {

  /**
   * A Runtime with settings suitable for benchmarks, specifically with Tracing
   * and auto-yielding disabled.
   *
   * Tracing adds a constant ~2x overhead on FlatMaps, however, it's an optional
   * feature and it's not valid to compare the performance of ZIO with enabled
   * Tracing with effect types _without_ a comparable feature.
   */
  lazy val benchmark: RuntimeConfig = makeDefault(Int.MaxValue)

  /**
   * The default runtime configuration, with settings designed to work well for
   * mainstream usage. Advanced users should consider making their own runtime
   * configuration customized for specific application requirements.
   */
  lazy val default: RuntimeConfig = makeDefault()

  /**
   * The default number of operations the ZIO runtime should execute before
   * yielding to other fibers.
   */
  final val defaultYieldOpCount = 2048

  /**
   * A `RuntimeConfig` created from Scala's global execution context.
   */
  lazy val global: RuntimeConfig = fromExecutionContext(ExecutionContext.global)

  /**
   * Creates a runtime configuration from an `Executor`.
   */
  final def fromExecutor(executor0: Executor): RuntimeConfig = {
    val blockingExecutor = Blocking.blockingExecutor

    val executor = executor0

    val logger =
      ZLogger.default.map(println(_)).filterLogLevel(_ >= LogLevel.Info)

    val reportFatal = (t: Throwable) => {
      t.printStackTrace()
      try {
        java.lang.System.exit(-1)
        throw t
      } catch { case _: Throwable => throw t }
    }

    RuntimeConfig(
      blockingExecutor,
      executor,
      Set(classOf[VirtualMachineError]),
      reportFatal,
      Set.empty,
      Set(logger),
      Set(RuntimeConfigFlag.EnableFiberRoots)
    )
  }

  /**
   * Creates a RuntimeConfig from an execution context.
   */
  final def fromExecutionContext(ec: ExecutionContext, yieldOpCount: Int = defaultYieldOpCount): RuntimeConfig =
    fromExecutor(Executor.fromExecutionContext(yieldOpCount)(ec))

  /**
   * Makes a new default runtime configuration. This is a side-effecting method.
   */
  def makeDefault(yieldOpCount: Int = defaultYieldOpCount): RuntimeConfig =
    fromExecutor(Executor.makeDefault(yieldOpCount))
}
