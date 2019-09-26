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

import zio.{ Runtime, ZIO }
import zio.test.Spec.TestCase

/**
 * A `RunnableSpec` has a main function and can be run by the JVM / Scala.js.
 */
abstract class RunnableSpec[R, L, T, E, S](runner0: TestRunner[R, L, T, E, S])(spec0: => Spec[R, E, L, T])
    extends AbstractRunnableSpec {
  override type Environment = R
  override type Label       = L
  override type Test        = T
  override type Failure     = E
  override type Success     = S

  override def runner = runner0
  override def spec   = spec0

  /**
   * A simple main function that can be used to run the spec.
   *
   * TODO: Parse command line options.
   */
  final def main(args: Array[String]): Unit = {
    val results = runner.unsafeRun(spec)
    val hasFailures = Runtime((), runner.platform).unsafeRun {
      results.exists {
        case TestCase(_, test) => test.map(_.isLeft)
        case _                 => ZIO.succeed(false)
      }
    }
    try if (hasFailures) sys.exit(1) else sys.exit(0)
    catch { case _: SecurityException => }
  }
}
