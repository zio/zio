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

import zio.test.RenderedResult.CaseType._
import zio.test.RenderedResult.Status._
import zio.test.RenderedResult.{ CaseType, Status }
import zio.{ Cause, ZIO }
import scala.{ Console => SConsole }
import zio.duration.Duration

object DefaultTestReporter {

  def render(executedSpec: ExecutedSpec[String]): Seq[RenderedResult] = {
    def loop(executedSpec: ExecutedSpec[String], depth: Int): Seq[RenderedResult] =
      executedSpec.caseValue match {
        case Spec.SuiteCase(label, executedSpecs, _) =>
          val hasFailures = executedSpecs.exists(_.exists {
            case Spec.TestCase(_, test) => test.failure; case _ => false
          })
          val status        = if (hasFailures) Failed else Passed
          val renderedLabel = if (hasFailures) renderFailureLabel(label, depth) else renderSuccessLabel(label, depth)
          rendered(Suite, label, status, depth, renderedLabel) +: executedSpecs.flatMap(loop(_, depth + tabSize))
        case Spec.TestCase(label, result) =>
          Seq(result match {
            case AssertResult.Success =>
              rendered(Test, label, Passed, depth, withOffset(depth)(green("+") + " " + label))
            case AssertResult.Failure(details) =>
              rendered(Test, label, Failed, depth, renderFailure(label, depth, details): _*)
            case AssertResult.Ignore => rendered(Test, label, Ignored, depth)
          })
      }
    loop(executedSpec, 0)
  }

  def apply[L](): TestReporter[L] = { (duration: Duration, executedSpec: ExecutedSpec[L]) =>
    ZIO
      .foreach(render(executedSpec.mapLabel(_.toString))) { res =>
        ZIO.foreach(res.rendered)(TestLogger.logLine)
      } *> logStats(duration, executedSpec)
  }

  private def logStats[L](duration: Duration, executedSpec: ExecutedSpec[L]) = {
    def loop(executedSpec: ExecutedSpec[String]): (Int, Int, Int) =
      executedSpec.caseValue match {
        case Spec.SuiteCase(_, executedSpecs, _) =>
          executedSpecs.map(loop).foldLeft((0, 0, 0)) {
            case ((x1, x2, x3), (y1, y2, y3)) => (x1 + y1, x2 + y2, x3 + y3)
          }
        case Spec.TestCase(_, result) =>
          result match {
            case AssertResult.Success    => (1, 0, 0)
            case AssertResult.Ignore     => (0, 1, 0)
            case AssertResult.Failure(_) => (0, 0, 1)
          }
      }
    val (success, ignore, failure) = loop(executedSpec.mapLabel(_.toString))
    val total                      = success + ignore + failure
    val seconds                    = duration.toMillis / 1000
    TestLogger.logLine(
      cyan(
        s"Ran $total test${if (total == 1) "" else "s"} in $seconds second${if (seconds == 1) "" else "s"}: $success succeeded, $ignore ignored, $failure failed"
      )
    )
  }

  private def renderSuccessLabel(label: String, offset: Int) =
    withOffset(offset)(green("+") + " " + label)

  private def renderFailure(label: String, offset: Int, details: FailureDetails) =
    renderFailureLabel(label, offset) +: renderFailureDetails(details, offset)

  private def renderFailureLabel(label: String, offset: Int) =
    withOffset(offset)(red("- " + label))

  private def renderFailureDetails(failureDetails: FailureDetails, offset: Int): Seq[String] = failureDetails match {
    case FailureDetails.Assertion(fragment, whole) => renderAssertion(fragment, whole, offset)
    case FailureDetails.Runtime(cause)             => Seq(renderCause(cause, offset))
  }

  private def renderAssertion(fragment: AssertionValue, whole: AssertionValue, offset: Int): Seq[String] =
    if (whole.assertion == fragment.assertion)
      Seq(renderFragment(fragment, offset))
    else
      Seq(renderWhole(fragment, whole, offset), renderFragment(fragment, offset))

  private def renderWhole(fragment: AssertionValue, whole: AssertionValue, offset: Int) =
    withOffset(offset + tabSize) {
      blue(whole.value.toString) +
        " did not satisfy " +
        highlight(cyan(whole.assertion.toString), fragment.assertion.toString)
    }

  private def renderFragment(fragment: AssertionValue, offset: Int) =
    withOffset(offset + tabSize) {
      blue(fragment.value.toString) +
        " did not satisfy " +
        cyan(fragment.assertion.toString)
    }

  private def renderCause(cause: Cause[Any], offset: Int): String =
    cause.prettyPrint.split("\n").map(withOffset(offset + tabSize)).mkString("\n")

  private def withOffset(n: Int)(s: String): String =
    " " * n + s

  private def green(s: String): String =
    SConsole.GREEN + s + SConsole.RESET

  private def red(s: String): String =
    SConsole.RED + s + SConsole.RESET

  private def blue(s: String): String =
    SConsole.BLUE + s + SConsole.RESET

  private def cyan(s: String): String =
    SConsole.CYAN + s + SConsole.RESET

  private def yellowThenCyan(s: String): String =
    SConsole.YELLOW + s + SConsole.CYAN

  private def highlight(string: String, substring: String): String =
    string.replace(substring, yellowThenCyan(substring))

  private val tabSize = 2

  private def rendered(
    caseType: CaseType,
    label: String,
    result: Status,
    offset: Int,
    rendered: String*
  ): RenderedResult =
    RenderedResult(caseType, label, result, offset, rendered)
}

object RenderedResult {
  sealed trait Status
  object Status {
    case object Failed  extends Status
    case object Passed  extends Status
    case object Ignored extends Status
  }

  sealed trait CaseType
  object CaseType {
    case object Test  extends CaseType
    case object Suite extends CaseType
  }
}

case class RenderedResult(caseType: CaseType, label: String, status: Status, offset: Int, rendered: Seq[String])
