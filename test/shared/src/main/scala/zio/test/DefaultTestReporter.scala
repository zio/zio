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

  def render[E, S](executedSpec: ExecutedSpec[String, E, S]): Seq[RenderedResult] = {
    def loop(executedSpec: ExecutedSpec[String, E, S], depth: Int): Seq[RenderedResult] =
      executedSpec.caseValue match {
        case Spec.SuiteCase(label, executedSpecs, _) =>
          val hasFailures = executedSpecs.exists(_.exists {
            case Spec.TestCase(_, test) => test.isLeft; case _ => false
          })
          val status        = if (hasFailures) Failed else Passed
          val renderedLabel = if (hasFailures) renderFailureLabel(label, depth) else renderSuccessLabel(label, depth)
          rendered(Suite, label, status, depth, renderedLabel) +: executedSpecs.flatMap(loop(_, depth + tabSize))
        case Spec.TestCase(label, result) =>
          Seq(
            result match {
              case Right(TestSuccess.Succeeded(_)) =>
                rendered(Test, label, Passed, depth, withOffset(depth)(green("+") + " " + label))
              case Right(TestSuccess.Ignored) =>
                rendered(Test, label, Ignored, depth)
              case Left(TestFailure.Assertion(result)) =>
                result.fold(
                  details => rendered(Test, label, Failed, depth, renderFailure(label, depth, details): _*)
                )(_ && _, _ || _, !_)
              case Left(TestFailure.Runtime(cause)) =>
                rendered(
                  Test,
                  label,
                  Failed,
                  depth,
                  (Seq(renderFailureLabel(label, depth)) ++ Seq(renderCause(cause, depth))): _*
                )
            }
          )
      }
    loop(executedSpec, 0)
  }

  def apply[L, E, S](): TestReporter[L, E, S] = { (duration: Duration, executedSpec: ExecutedSpec[L, E, S]) =>
    ZIO
      .foreach(render(executedSpec.mapLabel(_.toString))) { res =>
        ZIO.foreach(res.rendered)(TestLogger.logLine)
      } *> logStats(duration, executedSpec)
  }

  private def logStats[L, E, S](duration: Duration, executedSpec: ExecutedSpec[L, E, S]) = {
    def loop(executedSpec: ExecutedSpec[String, E, S]): (Int, Int, Int) =
      executedSpec.caseValue match {
        case Spec.SuiteCase(_, executedSpecs, _) =>
          executedSpecs.map(loop).foldLeft((0, 0, 0)) {
            case ((x1, x2, x3), (y1, y2, y3)) => (x1 + y1, x2 + y2, x3 + y3)
          }
        case Spec.TestCase(_, result) =>
          result match {
            case Left(_)                         => (0, 0, 1)
            case Right(TestSuccess.Succeeded(_)) => (1, 0, 0)
            case Right(TestSuccess.Ignored)      => (0, 1, 0)
          }
      }
    val (success, ignore, failure) = loop(executedSpec.mapLabel(_.toString))
    val total                      = success + ignore + failure
    TestLogger.logLine(
      cyan(
        s"Ran $total test${if (total == 1) "" else "s"} in ${duration.render}: $success succeeded, $ignore ignored, $failure failed"
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
    case FailureDetails(fragment, whole, genFailureDetails) =>
      renderGenFailureDetails(genFailureDetails, offset) ++ renderAssertion(fragment, whole, offset)
  }

  private def renderGenFailureDetails[A](failureDetails: Option[GenFailureDetails], offset: Int): Seq[String] =
    failureDetails match {
      case Some(details) =>
        val shrinked = details.shrinkedInput.toString
        val initial  = details.initialInput.toString
        val renderShrinked = withOffset(offset + tabSize)(
          s"Test failed after ${details.iterations + 1} iteration${if (details.iterations > 0) "s" else ""} with input: ${red(shrinked)}"
        )
        if (initial == shrinked) Seq(renderShrinked)
        else Seq(renderShrinked, withOffset(offset + tabSize)(s"Original input before shrinking was: ${red(initial)}"))
      case None => Seq()
    }

  private def renderAssertion(fragment: AssertionValue, whole: AssertionValue, offset: Int): Seq[String] =
    if (whole.assertion == fragment.assertion)
      Seq(renderFragment(fragment, offset))
    else
      Seq(renderWhole(fragment, whole, offset), renderFragment(fragment, offset))

  private def renderWhole(fragment: AssertionValue, whole: AssertionValue, offset: Int) =
    withOffset(offset + tabSize) {
      blue(whole.value.toString) +
        renderSatisfied(whole) +
        highlight(cyan(whole.assertion.toString), fragment.assertion.toString)
    }

  private def renderFragment(fragment: AssertionValue, offset: Int) =
    withOffset(offset + tabSize) {
      blue(fragment.value.toString) +
        renderSatisfied(fragment) +
        cyan(fragment.assertion.toString)
    }

  private def renderSatisfied(fragment: AssertionValue): String =
    if (fragment.assertion.test(fragment.value)) " satisfied "
    else " did not satisfy "

  private def renderCause(cause: Cause[Any], offset: Int): String =
    cause.dieOption match {
      case Some(TestTimeoutException(message)) => message
      case _                                   => cause.prettyPrint.split("\n").map(withOffset(offset + tabSize)).mkString("\n")
    }

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

case class RenderedResult(caseType: CaseType, label: String, status: Status, offset: Int, rendered: Seq[String]) {
  self =>

  def &&(that: RenderedResult): RenderedResult =
    (self.status, that.status) match {
      case (Ignored, _)     => that
      case (_, Ignored)     => self
      case (Failed, Failed) => self.copy(rendered = self.rendered ++ that.rendered.tail)
      case (Passed, _)      => that
      case (_, Passed)      => self
    }

  def ||(that: RenderedResult): RenderedResult =
    (self.status, that.status) match {
      case (Ignored, _)     => that
      case (_, Ignored)     => self
      case (Failed, Failed) => self.copy(rendered = self.rendered ++ that.rendered.tail)
      case (Passed, _)      => self
      case (_, Passed)      => that
    }

  def unary_! : RenderedResult =
    self.status match {
      case Ignored => self
      case Failed  => self.copy(status = Passed)
      case Passed  => self.copy(status = Failed)
    }
}
