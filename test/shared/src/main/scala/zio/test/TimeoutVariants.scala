/*
 * Copyright 2019-2021 John A. De Goes and the ZIO Contributors
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

import zio._
import zio.test.environment.Live

trait TimeoutVariants {

  /**
   * A test aspect that prints a warning to the console when a test takes
   * longer than the specified duration.
   */
  def timeoutWarning(
    duration: Duration
  ): TestAspect[Nothing, Has[Live], Nothing, Any] =
    new TestAspect[Nothing, Has[Live], Nothing, Any] {
      def some[R <: Has[Live], E](
        predicate: String => Boolean,
        spec: ZSpec[R, E]
      ): ZSpec[R, E] = {
        def loop(labels: List[String], spec: ZSpec[R, E]): ZSpec[R with Has[Live], E] =
          spec.caseValue match {
            case Spec.SuiteCase(label, specs, exec) =>
              Spec.suite(label, specs.map(_.map(loop(label :: labels, _))), exec)
            case Spec.TestCase(label, test, annotations) =>
              Spec.test(label, warn(labels, label, test, duration), annotations)
          }

        loop(Nil, spec)
      }
    }

  private def warn[R, E](
    suiteLabels: List[String],
    testLabel: String,
    test: ZTest[R, E],
    duration: Duration
  ): ZTest[R with Has[Live], E] =
    test.raceWith(Live.withLive(showWarning(suiteLabels, testLabel, duration))(_.delay(duration)))(
      (result, fiber) => fiber.interrupt *> ZIO.done(result),
      (_, fiber) => fiber.join
    )

  private def showWarning(
    suiteLabels: List[String],
    testLabel: String,
    duration: Duration
  ): URIO[Has[Live], Unit] =
    Live.live(Console.printLine(renderWarning(suiteLabels, testLabel, duration)))

  private def renderWarning(suiteLabels: List[String], testLabel: String, duration: Duration): String =
    (renderSuiteLabels(suiteLabels) + renderTest(testLabel, duration)).capitalize

  private def renderSuiteLabels(suiteLabels: List[String]): String =
    suiteLabels.map(label => "in Suite \"" + label + "\", ").reverse.mkString

  private def renderTest(testLabel: String, duration: Duration): String =
    "test " + "\"" + testLabel + "\"" + " has taken more than " + duration.render +
      " to execute. If this is not expected, consider using TestAspect.timeout to timeout runaway tests for faster diagnostics."

}
