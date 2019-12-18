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

import scala.{ Console => SConsole }

import zio.duration.Duration
import zio.test.mock.{ Method, MockException }
import zio.test.mock.MockException.{ InvalidArgumentsException, InvalidMethodException, UnmetExpectationsException }
import zio.test.RenderedResult.CaseType._
import zio.test.RenderedResult.Status._
import zio.test.RenderedResult.{ CaseType, Status }
import zio.{ Cause, UIO, URIO }

object DefaultTestReporter {

  def render[E, S](executedSpec: ExecutedSpec[E, String, S]): UIO[Seq[RenderedResult]] = {
    def loop(executedSpec: ExecutedSpec[E, String, S], depth: Int): UIO[Seq[RenderedResult]] =
      executedSpec.caseValue match {
        case Spec.SuiteCase(label, executedSpecs, _) =>
          for {
            specs <- executedSpecs
            failures <- UIO.foreach(specs)(_.exists {
                         case Spec.TestCase(_, test) => test.map(_.isLeft);
                         case _                      => UIO.succeed(false)
                       })
            hasFailures = failures.exists(identity)
            status      = if (hasFailures) Failed else Passed
            renderedLabel = if (specs.isEmpty) Seq.empty
            else if (hasFailures) Seq(renderFailureLabel(label, depth))
            else Seq(renderSuccessLabel(label, depth))
            rest <- UIO.foreach(specs)(loop(_, depth + tabSize)).map(_.flatten)
          } yield rendered(Suite, label, status, depth, renderedLabel: _*) +: rest
        case Spec.TestCase(label, result) =>
          result.flatMap {
            case Right(TestSuccess.Succeeded(_)) =>
              UIO.succeed(Seq(rendered(Test, label, Passed, depth, withOffset(depth)(green("+") + " " + label))))
            case Right(TestSuccess.Ignored) =>
              UIO.succeed(Seq(rendered(Test, label, Ignored, depth)))
            case Left(TestFailure.Assertion(result)) =>
              result.run.flatMap(
                result =>
                  result
                    .fold(
                      details =>
                        renderFailure(label, depth, details)
                          .map(failures => rendered(Test, label, Failed, depth, failures: _*))
                    )(_.zipWith(_)(_ && _), _.zipWith(_)(_ || _), _.map(!_))
                    .map(Seq(_))
              )

            case Left(TestFailure.Runtime(cause)) =>
              renderCause(cause, depth).map { string =>
                Seq(
                  rendered(
                    Test,
                    label,
                    Failed,
                    depth,
                    (Seq(renderFailureLabel(label, depth)) ++ Seq(string)): _*
                  )
                )
              }
          }
      }
    loop(executedSpec, 0)
  }

  def apply[E, S](): TestReporter[E, String, S] = { (duration: Duration, executedSpec: ExecutedSpec[E, String, S]) =>
    for {
      rendered <- render(executedSpec.mapLabel(_.toString)).map(_.flatMap(_.rendered))
      stats    <- logStats(duration, executedSpec)
      _        <- TestLogger.logLine((rendered ++ Seq(stats)).mkString("\n"))
    } yield ()
  }

  private def logStats[E, L, S](duration: Duration, executedSpec: ExecutedSpec[E, L, S]): URIO[TestLogger, String] = {
    def loop(executedSpec: ExecutedSpec[E, String, S]): UIO[(Int, Int, Int)] =
      executedSpec.caseValue match {
        case Spec.SuiteCase(_, executedSpecs, _) =>
          for {
            specs <- executedSpecs
            stats <- UIO.foreach(specs)(loop)
          } yield stats.foldLeft((0, 0, 0)) {
            case ((x1, x2, x3), (y1, y2, y3)) => (x1 + y1, x2 + y2, x3 + y3)
          }
        case Spec.TestCase(_, result) =>
          result.map {
            case Left(_)                         => (0, 0, 1)
            case Right(TestSuccess.Succeeded(_)) => (1, 0, 0)
            case Right(TestSuccess.Ignored)      => (0, 1, 0)
          }
      }
    for {
      stats                      <- loop(executedSpec.mapLabel(_.toString))
      (success, ignore, failure) = stats
      total                      = success + ignore + failure
    } yield cyan(
      s"Ran $total test${if (total == 1) "" else "s"} in ${duration.render}: $success succeeded, $ignore ignored, $failure failed"
    )
  }

  private def renderSuccessLabel(label: String, offset: Int) =
    withOffset(offset)(green("+") + " " + label)

  private def renderFailure(label: String, offset: Int, details: FailureDetails): UIO[Seq[String]] =
    renderFailureDetails(details, offset).map(renderFailureLabel(label, offset) +: _)

  private def renderFailureLabel(label: String, offset: Int): String =
    withOffset(offset)(red("- " + label))

  private def renderFailureDetails(failureDetails: FailureDetails, offset: Int): UIO[Seq[String]] =
    failureDetails match {
      case FailureDetails(assertionFailureDetails, genFailureDetails) =>
        renderAssertionFailureDetails(assertionFailureDetails, offset).map(
          renderGenFailureDetails(genFailureDetails, offset) ++ _
        )
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

  private def renderAssertionFailureDetails(failureDetails: ::[AssertionValue], offset: Int): UIO[Seq[String]] = {
    def loop(failureDetails: List[AssertionValue], rendered: Seq[String]): UIO[Seq[String]] =
      failureDetails match {
        case fragment :: whole :: failureDetails =>
          renderWhole(fragment, whole, offset).flatMap(s => loop(whole :: failureDetails, rendered :+ s))
        case _ =>
          UIO.succeed(rendered)
      }
    for {
      fragment <- renderFragment(failureDetails.head, offset)
      rest     <- loop(failureDetails, Seq())
    } yield Seq(fragment) ++ rest
  }

  private def renderWhole(fragment: AssertionValue, whole: AssertionValue, offset: Int): UIO[String] =
    renderSatisfied(whole).map { satisfied =>
      withOffset(offset + tabSize) {
        blue(whole.value.toString) +
          satisfied +
          highlight(cyan(whole.assertion.toString), fragment.assertion.toString)
      }
    }

  private def renderFragment(fragment: AssertionValue, offset: Int): UIO[String] =
    renderSatisfied(fragment).map { satisfied =>
      withOffset(offset + tabSize) {
        blue(fragment.value.toString) +
          satisfied +
          cyan(fragment.assertion.toString)
      }
    }

  private def renderSatisfied(fragment: AssertionValue): UIO[String] =
    fragment.assertion.test(fragment.value).map { p =>
      if (p) " satisfied " else " did not satisfy "
    }

  private def renderCause(cause: Cause[Any], offset: Int): UIO[String] =
    cause.dieOption match {
      case Some(TestTimeoutException(message)) => UIO.succeed(message)
      case Some(exception: MockException) =>
        renderMockException(exception).map(_.split("\n").map(withOffset(offset + tabSize)).mkString("\n"))
      case _ => UIO.succeed(cause.prettyPrint.split("\n").map(withOffset(offset + tabSize)).mkString("\n"))
    }

  private def renderMockException(exception: MockException): UIO[String] =
    exception match {
      case InvalidArgumentsException(method, args, assertion) =>
        renderTestFailure(s"$method called with invalid arguments", assert(args)(assertion))

      case InvalidMethodException(method, expectedMethod, assertion) =>
        UIO.succeed(
          List(
            red(s"- invalid call to $method"),
            renderExpectation(expectedMethod, assertion, tabSize)
          ).mkString("\n")
        )

      case UnmetExpectationsException(expectations) =>
        UIO.succeed((red(s"- unmet expectations") :: expectations.map {
          case (expectedMethod, assertion) => renderExpectation(expectedMethod, assertion, tabSize)
        }).mkString("\n"))
    }

  private def renderTestFailure(label: String, testResult: TestResult): UIO[String] =
    testResult.run.flatMap(
      _.failures.fold(UIO.succeed(""))(
        _.fold(
          details => renderFailure(label, 0, details).map(failures => rendered(Test, label, Failed, 0, failures: _*))
        )(_.zipWith(_)(_ && _), _.zipWith(_)(_ || _), _.map(!_)).map(_.rendered.mkString("\n"))
      )
    )

  private def renderExpectation[M, I, A](method: Method[M, I, A], assertion: Assertion[I], offset: Int): String =
    withOffset(offset)(s"expected $method with arguments ${cyan(assertion.toString)}")

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
