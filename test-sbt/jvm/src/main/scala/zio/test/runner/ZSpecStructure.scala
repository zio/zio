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

package zio.test.runner

import zio.{ RIO, ZIO }
import zio.test.Spec.{ SuiteCase, TestCase }
import zio.test.runner.ExecutedSpecStructure.{ ExecutedSuite, ExecutedTest, Stats }
import zio.test.{ Assertion, ExecutedSpec, FailureDetails, TestResult }

sealed abstract class ExecutedSpecStructure {
  def stats: Stats
  def traverse[R](
    handleSuite: (String, Stats) => RIO[R, Unit],
    handleTestResult: (String, ZTestResult) => RIO[R, Unit]
  ): RIO[R, Unit] =
    this match {
      case ExecutedSuite(label, stats, children) =>
        for {
          _ <- ZIO.sequence(children.map(_.traverse(handleSuite, handleTestResult)))
          _ <- handleSuite(label, stats)
        } yield ()
      case ExecutedTest(label, result) => handleTestResult(label, result)
    }
}

object ExecutedSpecStructure {
  case class Stats(passed: Int = 0, failed: Int = 0, ignored: Int = 0) {
    val total: Int = passed + failed + ignored
    def +(that: Stats): Stats = copy(
      passed = passed + that.passed,
      failed = failed + that.failed,
      ignored = ignored + that.ignored
    )
  }

  def from[L, R](spec: ExecutedSpec[L]): ExecutedSpecStructure =
    spec.caseValue match {
      case SuiteCase(label, specs, _) =>
        val children = specs.map(from)
        val stats    = children.map(_.stats).foldLeft(Stats())(_ + _)
        ExecutedSuite(label.toString, stats, children)

      case TestCase(label, testResult) =>
        ExecutedTest(label.toString, ZTestResult.from(testResult))
    }

  case class ExecutedSuite(label: String, stats: Stats, children: Vector[ExecutedSpecStructure])
      extends ExecutedSpecStructure
  case class ExecutedTest(label: String, result: ZTestResult) extends ExecutedSpecStructure {
    override def stats: Stats = result.stats
  }
}

sealed abstract class ZTestResult(val stats: Stats) {
  def rendered = toString.toUpperCase
}
object ZTestResult {
  case object Ignored                                                        extends ZTestResult(Stats(ignored = 1))
  case object Success                                                        extends ZTestResult(Stats(passed = 1))
  case class Failure(details: FailureDetails, override val rendered: String) extends ZTestResult(Stats(failed = 1))

  def from(testResult: TestResult): ZTestResult =
    testResult match {
      case Assertion.Success     => Success
      case Assertion.Failure(fd) => Failure(fd, describe(fd))
      case Assertion.Ignore      => Ignored
    }

  private def describe(failureDetails: FailureDetails) = failureDetails match {
    case FailureDetails.Runtime(cause)      => cause.prettyPrint
    case FailureDetails.Predicate(_, whole) => s"FAILURE: ${whole.value} did not satisfy ${whole.predicate}"
  }
}
