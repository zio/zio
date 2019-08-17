/*
 * Copyright 2019 John A. De Goes and the ZIO Contributors
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
import zio.test.reflect.Reflect.EnableReflectiveInstantiation

/**
 * A `RunnableSpec` has a main function and can be run by the JVM / Scala.js.
 */
@EnableReflectiveInstantiation
abstract class AbstractRunnableSpec {

  type Label
  type Test

  def runner: TestRunner[Label, Test]
  def spec: Spec[Label, Test]

  /**
   * A simple main function that can be used to run the spec.
   *
   * TODO: Parse command line options.
   */
  final def main(args: Array[String]): Unit = { val _ = runner.unsafeRunSync(spec) }

  /**
   * Returns an effect that executes the spec, producing the results of the execution.
   */
  final def run: URIO[TestLogger, ExecutedSpec[Label]] = runner.run(spec)

  /**
   * the platform used by the runner
   */
  final def platform = runner.platform
}

abstract class RunnableSpec[L, T](runner0: TestRunner[L, T])(spec0: => Spec[L, T]) extends AbstractRunnableSpec {
  override type Label = L
  override type Test  = T

  override def runner = runner0
  override def spec   = spec0
}
