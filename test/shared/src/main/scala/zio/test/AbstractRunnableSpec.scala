/*
 * Copyright 2019-2020 John A. De Goes and the ZIO Contributors
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

import zio.URIO
import zio.clock.Clock
import zio.test.reflect.Reflect.EnableReflectiveInstantiation

@EnableReflectiveInstantiation
abstract class AbstractRunnableSpec {

  type Environment
  type Failure
  type Label
  type Test
  type Success

  def aspects: List[TestAspect[Nothing, Environment, Nothing, Any, Nothing, Any]]
  def runner: TestRunner[Environment, Failure, Label, Test, Success]
  def spec: ZSpec[Environment, Failure, Label, Test]

  /**
   * Returns an effect that executes the spec, producing the results of the execution.
   */
  final def run: URIO[TestLogger with Clock, ExecutedSpec[Failure, Label, Success]] =
    runner.run(aspects.foldLeft(spec)(_ @@ _))

  /**
   * the platform used by the runner
   */
  final def platform = runner.platform
}
