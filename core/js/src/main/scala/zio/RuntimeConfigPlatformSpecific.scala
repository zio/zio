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
import scala.scalajs.js.Dynamic.{global => jsglobal}

private[zio] trait RuntimeConfigPlatformSpecific {

  /**
   * A Runtime with settings suitable for benchmarks, specifically with
   * auto-yielding disabled.
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
    val blockingExecutor = executor0

    val executor = executor0

    val fatal = (_: Throwable) => false

    val logger: ZLogger[String, Unit] =
      (
        trace: ZTraceElement,
        fiberId: FiberId,
        level: LogLevel,
        message: () => String,
        cause: Cause[Any],
        context: Map[FiberRef[_], Any],
        spans: List[LogSpan],
        annotations: Map[String, String]
      ) => {
        try {
          // TODO: Improve output & use console.group for spans, etc.
          val line = ZLogger.default(trace, fiberId, level, message, cause, context, spans, annotations)

          if (level == LogLevel.Fatal) jsglobal.console.error(line)
          else if (level == LogLevel.Error) jsglobal.console.error(line)
          else if (level == LogLevel.Debug) jsglobal.console.debug(line)
          else jsglobal.console.log(line)

          ()
        } catch {
          case t if !fatal(t) => ()
        }
      }

    val reportFatal = (t: Throwable) => {
      t.printStackTrace()
      throw t
    }

    val supervisor = Supervisor.none

    RuntimeConfig(
      blockingExecutor,
      executor,
      fatal,
      reportFatal,
      supervisor,
      Set(logger),
      RuntimeConfigFlags.empty + RuntimeConfigFlag.EnableFiberRoots
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
  final def makeDefault(yieldOpCount: Int = defaultYieldOpCount): RuntimeConfig =
    fromExecutor(Executor.fromExecutionContext(yieldOpCount)(ExecutionContext.global))
}
