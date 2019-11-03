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

import zio.test.Spec.TestCase

/**
 * A `RunnableSpec` has a main function and can be run by the JVM / Scala.js.
 */
abstract class RunnableSpec[L, T](runner0: TestRunner[L, T])(spec0: => Spec[L, T]) extends AbstractRunnableSpec {
  override type Label = L
  override type Test  = T

  override def runner = runner0
  override def spec   = spec0

  /**
   * A simple main function that can be used to run the spec.
   *
   * TODO: Parse command line options.
   */
  final def main(args: Array[String]): Unit = {
    val results     = runner.unsafeRun(spec)
    val hasFailures = results.exists { case TestCase(_, test) => test.failure; case _ => false }
    try if (hasFailures) sys.exit(1) else sys.exit(0)
    catch { case _: SecurityException => }
  }
}
