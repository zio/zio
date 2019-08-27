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

import zio.clock.Clock
import zio.console
import zio.console.Console
import zio.duration._
import zio.test.mock.Live
import zio.ZIO

/**
 * A `TimeoutStrategy` adds logic to a `ZSpec` to handle long-running and
 * potentially non-terminating tests.
 */
trait TimeoutStrategy {
  def apply[R, E, L](spec: ZSpec[R, E, L]): ZSpec[R with Live[Clock] with Live[Console], E, L]
}

object TimeoutStrategy {

  /**
   * Fail tests with a runtime exception if they take longer than the specified
   * duration.
   */
  final case class Error(duration: Duration) extends TimeoutStrategy {
    def apply[R, E, L](spec: ZSpec[R, E, L]): ZSpec[R with Live[Clock] with Live[Console], E, L] =
      TestAspect.timeout(duration)(spec)
  }

  /**
   * Print a warning to the console when tests take longer than the specified
   * duration.
   */
  final case class Warn(duration: Duration) extends TimeoutStrategy {
    def apply[R, E, L](spec: ZSpec[R, E, L]): ZSpec[R with Live[Clock] with Live[Console], E, L] = {
      def loop(labels: List[L], spec: ZSpec[R, E, L]): ZSpec[R with Live[Clock] with Live[Console], E, L] =
        spec.caseValue match {
          case Spec.SuiteCase(label, specs, exec) =>
            Spec.suite(label, specs.map(loop(label :: labels, _)), exec)
          case Spec.TestCase(label, test) =>
            Spec.test(label, warn(labels, label, test, duration))
        }

      loop(Nil, spec)
    }
  }

  /**
   * Do nothing.
   */
  final case object Ignore extends TimeoutStrategy {
    def apply[R, E, L](spec: ZSpec[R, E, L]): ZSpec[R, E, L] =
      spec
  }

  private def warn[R, E, L](
    suiteLabels: List[L],
    testLabel: L,
    test: ZTest[R, E],
    duration: Duration
  ): ZTest[R with Live[Clock] with Live[Console], E] =
    test.raceWith(Live.withLive(showWarning(suiteLabels, testLabel, duration))(_.delay(duration)))(
      (result, fiber) => fiber.interrupt *> ZIO.done(result),
      (_, fiber) => fiber.join
    )

  private def showWarning[L](
    suiteLabels: List[L],
    testLabel: L,
    duration: Duration
  ): ZIO[Live[Console], Nothing, Unit] =
    Live.live(console.putStrLn(renderWarning(suiteLabels, testLabel, duration)))

  private def renderWarning[L](suiteLabels: List[L], testLabel: L, duration: Duration): String =
    (renderSuiteLabels(suiteLabels) + renderTest(testLabel, duration)).capitalize

  private def renderSuiteLabels[L](suiteLabels: List[L]): String =
    suiteLabels.map(label => "in Suite \"" + label + "\", ").reverse.mkString

  private def renderTest[L](testLabel: L, duration: Duration): String =
    "test " + "\"" + testLabel + "\"" + " has taken more than " + renderDuration(duration) +
      " to execute. If this is not expected consider using TestAspect.timeout to timeout runaway tests for faster diagnostics."

  private def renderDuration(duration: Duration): String = {
    val millis = duration.toMillis
    if ((millis != 0) && (millis % 60000 == 0)) {
      val minutes = millis / 60000
      if (minutes == 1) "1 minute" else s"$minutes minutes"
    } else {
      val seconds = millis / 1000
      if (seconds == 1) "1 second" else s"$seconds seconds"
    }
  }
}
