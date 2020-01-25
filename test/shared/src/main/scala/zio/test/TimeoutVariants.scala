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

import zio.ZIO
import zio.console
import zio.duration._
import zio.test.environment.Live

trait TimeoutVariants {

  /**
   * A test aspect that prints a warning to the console when a test takes
   * longer than the specified duration.
   */
  def timeoutWarning(
    duration: Duration
  ): TestAspect[Nothing, Live, Nothing, Any, Nothing, Any] =
    new TestAspect[Nothing, Live, Nothing, Any, Nothing, Any] {
      def some[R <: Live, E, S, L](
        predicate: L => Boolean,
        spec: ZSpec[R, E, L, S]
      ): ZSpec[R, E, L, S] = {
        def loop(labels: List[L], spec: ZSpec[R, E, L, S]): ZSpec[R with Live, E, L, S] =
          spec.caseValue match {
            case Spec.SuiteCase(label, specs, exec) =>
              Spec.suite(label, specs.map(_.map(loop(label :: labels, _))), exec)
            case Spec.TestCase(label, test) =>
              Spec.test(label, warn(labels, label, test, duration))
          }

        loop(Nil, spec)
      }
    }

  private def warn[R, E, L, S](
    suiteLabels: List[L],
    testLabel: L,
    test: ZTest[R, E, S],
    duration: Duration
  ): ZTest[R with Live, E, S] =
    test.raceWith(Live.withLive(showWarning(suiteLabels, testLabel, duration))(_.delay(duration)))(
      (result, fiber) => fiber.interrupt *> ZIO.done(result),
      (_, fiber) => fiber.join
    )

  private def showWarning[L](
    suiteLabels: List[L],
    testLabel: L,
    duration: Duration
  ): ZIO[Live, Nothing, Unit] =
    Live.live(console.putStrLn(renderWarning(suiteLabels, testLabel, duration)))

  private def renderWarning[L](suiteLabels: List[L], testLabel: L, duration: Duration): String =
    (renderSuiteLabels(suiteLabels) + renderTest(testLabel, duration)).capitalize

  private def renderSuiteLabels[L](suiteLabels: List[L]): String =
    suiteLabels.map(label => "in Suite \"" + label + "\", ").reverse.mkString

  private def renderTest[L](testLabel: L, duration: Duration): String =
    "test " + "\"" + testLabel + "\"" + " has taken more than " + duration.render +
      " to execute. If this is not expected, consider using TestAspect.timeout to timeout runaway tests for faster diagnostics."

}
