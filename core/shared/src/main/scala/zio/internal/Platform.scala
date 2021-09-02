package zio.internal

import zio.RuntimeConfig

import scala.concurrent.ExecutionContext

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

object Platform extends PlatformSpecific {

  /**
   * A Runtime with settings suitable for benchmarks, specifically with Tracing
   * and auto-yielding disabled.
   *
   * Tracing adds a constant ~2x overhead on FlatMaps, however, it's an
   * optional feature and it's not valid to compare the performance of ZIO with
   * enabled Tracing with effect types _without_ a comparable feature.
   */
  @deprecated("use RuntimeConfig.benchmark", "2.0.0")
  lazy val benchmark: Platform =
    RuntimeConfig.benchmark

  /**
   * The default platform, configured with settings designed to work well for
   * mainstream usage. Advanced users should consider making their own platform
   * customized for specific application requirements.
   */
  @deprecated("use RuntimeConfig.default", "2.0.0")
  lazy val default: Platform =
    RuntimeConfig.default

  /**
   * The default number of operations the ZIO runtime should execute before
   * yielding to other fibers.
   */
  @deprecated("use RuntimeConfig.defaultYieldOpCount", "2.0.0")
  final val defaultYieldOpCount =
    RuntimeConfig.defaultYieldOpCount

  /**
   * A `Platform` created from Scala's global execution context.
   */
  @deprecated("use RuntimeConfig.global", "2.0.0")
  lazy val global: Platform =
    RuntimeConfig.global

  /**
   * Creates a platform from an `Executor`.
   */
  @deprecated("use RuntimeConfig.fromExecutor", "2.0.0")
  final def fromExecutor(executor0: Executor): Platform =
    RuntimeConfig.fromExecutor(executor0)

  /**
   * Creates a Platform from an execution context.
   */
  @deprecated("use RuntimeConfig.fromExecutionContext", "2.0.0")
  final def fromExecutionContext(ec: ExecutionContext, yieldOpCount: Int = 2048): Platform =
    RuntimeConfig.fromExecutionContext(ec, yieldOpCount)

  /**
   * Makes a new default platform. This is a side-effecting method.
   */
  @deprecated("use RuntimeConfig.makeDefault", "2.0.0")
  final def makeDefault(yieldOpCount: Int = 2048): Platform =
    RuntimeConfig.makeDefault(yieldOpCount)
}
