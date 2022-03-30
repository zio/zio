/*
 * Copyright 2019-2022 John A. De Goes and the ZIO Contributors
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

package zio.test

import org.portablescala.reflect.annotation.EnableReflectiveInstantiation
import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

@EnableReflectiveInstantiation
abstract class AbstractRunnableSpec {

  type Environment
  type Failure

  def aspects: List[TestAspectAtLeastR[Environment]]
  def runner: TestRunner[Environment, Failure]
  def spec: ZSpec[Environment, Failure]

  /**
   * the platform used by the runner
   */
  @deprecated("use runtimeConfig", "2.0.0")
  final def platform =
    runtimeConfig

  /**
   * Returns an effect that executes the spec, producing the results of the
   * execution.
   */
  final def run(implicit trace: ZTraceElement): ZIO[ZEnv with ZIOAppArgs with ExecutionEventSink, Any, Any] =
    runSpec(spec).provideCustomLayer(runner.bootstrap)

  /**
   * Returns an effect that executes a given spec, producing the results of the
   * execution.
   */
  private[zio] def runSpec(
    spec: ZSpec[Environment, Failure]
  )(implicit
    trace: ZTraceElement
  ): URIO[
    Clock with ExecutionEventSink with Random,
    Summary
  ] =
    runner.run(aspects.foldLeft(spec)(_ @@ _))

  /**
   * The runtime configuration used by the runner.
   */
  final def runtimeConfig: RuntimeConfig = runner.runtimeConfig
}
