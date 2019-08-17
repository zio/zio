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

import zio.console.putStrLn
import zio.test.RenderedResult.CaseType._
import zio.test.RenderedResult.Status._
import zio.test.RenderedResult.{ CaseType, Status }
import zio.{ Cause, ZIO }

import scala.{ Console => SConsole }

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
            case Assertion.Success =>
              rendered(Test, label, Passed, depth, withOffset(depth)(green("+") + " " + label))
            case Assertion.Failure(details) =>
              rendered(Test, label, Failed, depth, renderFailure(label, depth, details): _*)
            case Assertion.Ignore => rendered(Test, label, Ignored, depth)
          })
      }
    loop(executedSpec, 0)
  }

  def apply(): TestReporter[String] = { executedSpec: ExecutedSpec[String] =>
    ZIO
      .foreach(render(executedSpec)) { res =>
        ZIO.foreach(res.rendered)(putStrLn)
      }
      .unit
  }

  private def renderSuccessLabel(label: String, offset: Int) =
    withOffset(offset)(green("+") + " " + label)

  private def renderFailure(label: String, offset: Int, details: FailureDetails) =
    renderFailureLabel(label, offset) +: renderFailureDetails(details, offset)

  private def renderFailureLabel(label: String, offset: Int) =
    withOffset(offset)(red("- " + label))

  private def renderFailureDetails(failureDetails: FailureDetails, offset: Int): Seq[String] = failureDetails match {
    case FailureDetails.Predicate(fragment, whole) => renderPredicate(fragment, whole, offset)
    case FailureDetails.Runtime(cause)             => Seq(renderCause(cause, offset))
  }

  private def renderPredicate(fragment: PredicateValue, whole: PredicateValue, offset: Int): Seq[String] =
    if (whole.predicate == fragment.predicate)
      Seq(renderFragment(fragment, offset))
    else
      Seq(renderWhole(fragment, whole, offset), renderFragment(fragment, offset))

  private def renderWhole(fragment: PredicateValue, whole: PredicateValue, offset: Int) =
    withOffset(offset + tabSize) {
      blue(whole.value.toString) +
        " did not satisfy " +
        highlight(cyan(whole.predicate.toString), fragment.predicate.toString)
    }

  private def renderFragment(fragment: PredicateValue, offset: Int) =
    withOffset(offset + tabSize) {
      blue(fragment.value.toString) +
        " did not satisfy " +
        cyan(fragment.predicate.toString)
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

  private def yellow(s: String): String =
    SConsole.YELLOW + s + SConsole.RESET

  private def highlight(string: String, substring: String): String =
    string.replace(substring, yellow(substring))

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
