/*
 * Copyright 2019-2022 John A. De Goes and the ZIO Contributors
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

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.render.ExecutionResult.ResultType.{Suite, Test}
import zio.test.render.ExecutionResult.Status.{Failed, Ignored, Passed}
import zio.test.render.ExecutionResult.{ResultType, Status}
import zio.test.render.LogLine.{Fragment, Line, Message}
import zio.test.render._
import zio.{Cause, _}

import java.util.regex.Pattern
import scala.annotation.tailrec
import scala.util.Try

object DefaultTestReporter {
  def render[E](
    executedSpec: ExecutedSpec[E],
    includeCause: Boolean
  )(implicit trace: ZTraceElement): Seq[ExecutionResult] = {
    def loop(
      executedSpec: ExecutedSpec[E],
      depth: Int,
      ancestors: List[TestAnnotationMap],
      labels: List[String]
    ): Seq[ExecutionResult] =
//      println("Looping in test reporter...")
      executedSpec.caseValue match {
        case ExecutedSpec.LabeledCase(label, spec) =>
          loop(spec, depth, ancestors, label :: labels)
        case ExecutedSpec.MultipleCase(specs) =>
          val hasFailures = executedSpec.exists {
            case ExecutedSpec.TestCase(test, _) => test.isLeft
            case _                              => false
          }

          val annotations = executedSpec.fold[TestAnnotationMap] {
            case ExecutedSpec.LabeledCase(_, annotations) => annotations
            case ExecutedSpec.MultipleCase(annotations)   => annotations.foldLeft(TestAnnotationMap.empty)(_ ++ _)
            case ExecutedSpec.TestCase(_, annotations)    => annotations
          }

          val (status, renderedLabel) =
            if (specs.isEmpty) (Ignored, Seq(renderSuiteIgnored(labels.reverse.mkString(" - "), depth)))
            else if (hasFailures) (Failed, Seq(renderSuiteFailed(labels.reverse.mkString(" - "), depth)))
            else (Passed, Seq(renderSuiteSucceeded(labels.reverse.mkString(" - "), depth)))

          val allAnnotations = annotations :: ancestors
          val rest           = specs.flatMap(loop(_, depth + 1, allAnnotations, List.empty))

          rendered(Suite, labels.reverse.mkString(" - "), status, depth, renderedLabel.flatMap(_.lines): _*)
            .withAnnotations(allAnnotations) +: rest

        case ExecutedSpec.TestCase(result, annotations) =>
          val renderedResult = result match {
            case Right(TestSuccess.Succeeded(_)) =>
              Some(
                rendered(Test, labels.reverse.mkString(" - "), Passed, depth, fr(labels.reverse.mkString(" - ")).toLine)
              )
            case Right(TestSuccess.Ignored) =>
              Some(
                rendered(
                  Test,
                  labels.reverse.mkString(" - "),
                  Ignored,
                  depth,
                  warn(labels.reverse.mkString(" - ")).toLine
                )
              )
            case Left(TestFailure.Assertion(result)) =>
              result
                .fold[Option[TestResult]] {
                  case result: AssertionResult.FailureDetailsResult => Some(BoolAlgebra.success(result))
                  case AssertionResult.TraceResult(trace, genFailureDetails, label) =>
                    Trace
                      .prune(trace, false)
                      .map(a => BoolAlgebra.success(AssertionResult.TraceResult(a, genFailureDetails, label)))
                }(
                  {
                    case (Some(a), Some(b)) => Some(a && b)
                    case (Some(a), None)    => Some(a)
                    case (None, Some(b))    => Some(b)
                    case _                  => None
                  },
                  {
                    case (Some(a), Some(b)) => Some(a || b)
                    case (Some(a), None)    => Some(a)
                    case (None, Some(b))    => Some(b)
                    case _                  => None
                  },
                  _.map(!_)
                )
                .map {
                  _.fold(details =>
                    rendered(
                      Test,
                      labels.reverse.mkString(" - "),
                      Failed,
                      depth,
                      renderFailure(labels.reverse.mkString(" - "), depth, details).lines: _*
                    )
                  )(
                    _ && _,
                    _ || _,
                    !_
                  )
                }

            case Left(TestFailure.Runtime(cause)) =>
              Some(renderRuntimeCause(cause, labels.reverse.mkString(" - "), depth, includeCause))
          }
          renderedResult.map(r => Seq(r.withAnnotations(annotations :: ancestors))).getOrElse(Seq.empty)
      }

    loop(executedSpec, 0, List.empty, List.empty)
  }

  def apply[E](testRenderer: TestRenderer, testAnnotationRenderer: TestAnnotationRenderer)(implicit
    trace: ZTraceElement
  ): TestReporter[E] = { (duration: Duration, executedSpec: ExecutedSpec[E]) =>
    val rendered = testRenderer.render(render(executedSpec, true), testAnnotationRenderer)
    val stats    = testRenderer.render(logStats(duration, executedSpec) :: Nil, testAnnotationRenderer)
    TestLogger.logLine((rendered ++ stats).mkString("\n"))
  }

  private def logStats[E](duration: Duration, executedSpec: ExecutedSpec[E]): ExecutionResult = {
    val (success, ignore, failure) = executedSpec.fold[(Int, Int, Int)] {
      case ExecutedSpec.LabeledCase(_, stats) => stats
      case ExecutedSpec.MultipleCase(stats) =>
        stats.foldLeft((0, 0, 0)) { case ((x1, x2, x3), (y1, y2, y3)) =>
          (x1 + y1, x2 + y2, x3 + y3)
        }
      case ExecutedSpec.TestCase(result, _) =>
        result match {
          case Left(_)                         => (0, 0, 1)
          case Right(TestSuccess.Succeeded(_)) => (1, 0, 0)
          case Right(TestSuccess.Ignored)      => (0, 1, 0)
        }
    }
    val total = success + ignore + failure
    val stats = detail(
      s"Ran $total test${if (total == 1) "" else "s"} in ${duration.render}: $success succeeded, $ignore ignored, $failure failed"
    )

    rendered(ResultType.Other, "", Status.Passed, 0, stats.toLine)
  }

  private def renderSuiteIgnored(label: String, offset: Int) =
    rendered(Suite, label, Ignored, offset, warn(s"- $label").toLine)

  private def renderSuiteFailed(label: String, offset: Int) =
    rendered(Suite, label, Failed, offset, error(s"- $label").toLine)

  private def renderSuiteSucceeded(label: String, offset: Int) =
    rendered(Suite, label, Passed, offset, fr(label).toLine)

  def renderAssertFailure(result: TestResult, label: String, depth: Int): ExecutionResult =
    result.fold(details => rendered(Test, label, Failed, depth, renderFailure(label, depth, details).lines: _*))(
      _ && _,
      _ || _,
      !_
    )

  private def renderRuntimeCause[E](cause: Cause[E], label: String, depth: Int, includeCause: Boolean)(implicit
    trace: ZTraceElement
  ) = {
    val failureDetails =
      Seq(renderFailureLabel(label, depth)) ++ Seq(renderCause(cause, depth)).filter(_ => includeCause).flatMap(_.lines)

    rendered(Test, label, Failed, depth, failureDetails: _*)
  }

  def renderAssertionResult(assertionResult: AssertionResult, offset: Int): Message =
    assertionResult match {
      case AssertionResult.TraceResult(trace, genFailureDetails, label) =>
        val failures = FailureCase.fromTrace(trace)
        failures
          .map(fc =>
            renderGenFailureDetails(genFailureDetails, offset) ++
              Message(renderFailureCase(fc, offset, label))
          )
          .foldLeft(Message.empty)(_ ++ _)

      case AssertionResult.FailureDetailsResult(failureDetails, genFailureDetails) =>
        renderGenFailureDetails(genFailureDetails, offset) ++
          renderFailureDetails(failureDetails, offset)
    }

  def renderFailureCase(failureCase: FailureCase, offset: Int, testLabel: Option[String]): Chunk[Line] =
    failureCase match {
      case FailureCase(errorMessage, codeString, location, path, _, nested, _) =>
        val errorMessageLines =
          Chunk.fromIterable(errorMessage.lines) match {
            case head +: tail => (error("✗ ") +: head) +: tail.map(error("  ") +: _)
            case _            => Chunk.empty
          }

        val result =
          errorMessageLines ++
            Chunk(Line.fromString(testLabel.fold(codeString)(l => s"""$codeString ?? "$l""""))) ++
            nested.flatMap(renderFailureCase(_, offset, None)).map(_.withOffset(1)) ++
            Chunk.fromIterable(path.flatMap { case (label, value) =>
              Chunk.fromIterable(PrettyPrint(value).split("\n").map(primary(_).toLine)) match {
                case head +: lines => (dim(s"${label.trim} = ") +: head) +: lines
                case _             => Vector.empty
              }
            }) ++
            Chunk(detail(s"at $location").toLine)

        result.map(_.withOffset(offset + 1))
    }

  private def renderAssertionFailureDetails(failureDetails: ::[AssertionValue], offset: Int): Message = {
    @tailrec
    def loop(failureDetails: List[AssertionValue], rendered: Message): Message =
      failureDetails match {
        case fragment :: whole :: failureDetails =>
          loop(whole :: failureDetails, rendered :+ renderWhole(fragment, whole, offset))
        case _ =>
          rendered
      }

    renderFragment(failureDetails.head, offset).toMessage ++ loop(
      failureDetails,
      Message.empty
    ) ++ renderAssertionLocation(failureDetails.last, offset)
  }

  private def renderAssertionLocation(av: AssertionValue, offset: Int) = av.sourceLocation.fold(Message()) { location =>
    detail(s"at $location").toLine
      .withOffset(offset + 1)
      .toMessage
  }

  private def renderSatisfied(assertionValue: AssertionValue): Fragment =
    if (assertionValue.result.isSuccess) Fragment(" satisfied ")
    else Fragment(" did not satisfy ")

  def renderCause(cause: Cause[Any], offset: Int)(implicit trace: ZTraceElement): Message = {
    val defects = cause.defects
    val timeouts = defects.collect { case TestTimeoutException(message) =>
      Message(message)
    }
    val remaining =
      cause.stripSomeDefects { case TestTimeoutException(_) =>
        true
      }
    val prefix = timeouts.foldLeft(Message.empty)(_ ++ _)

    remaining match {
      case Some(remainingCause) =>
        prefix ++ Message(
          remainingCause.prettyPrint
            .split("\n")
            .map(s => withOffset(offset + 1)(Line.fromString(s)))
            .toVector
        )
      case None =>
        prefix
    }
  }

  def renderTestFailure(label: String, testResult: TestResult): Message =
    testResult.failures.fold(Message.empty) { details =>
      Message {
        details
          .fold(assertionResult =>
            rendered(Test, label, Failed, 0, renderFailure(label, 0, assertionResult).lines: _*)
          )(
            _ && _,
            _ || _,
            !_
          )
          .lines
      }
    }

  private def renderFailure(label: String, offset: Int, details: AssertionResult): Message =
    renderFailureLabel(label, offset) +: renderAssertionResult(details, offset) :+ Line.empty

  def renderFailureLabel(label: String, offset: Int): Line =
    withOffset(offset)(error("- " + label).toLine)

  def renderFailureDetails(failureDetails: FailureDetails, offset: Int): Message =
    renderAssertionFailureDetails(failureDetails.assertion, offset)

  private def renderGenFailureDetails[A](failureDetails: Option[GenFailureDetails], offset: Int): Message =
    failureDetails match {
      case Some(details) =>
        val shrunken = details.shrunkenInput.toString
        val initial  = details.initialInput.toString
        val renderShrunken = withOffset(offset + 1)(
          Fragment(
            s"Test failed after ${details.iterations + 1} iteration${if (details.iterations > 0) "s" else ""} with input: "
          ) +
            error(shrunken)
        )
        if (initial == shrunken) renderShrunken.toMessage
        else
          renderShrunken + withOffset(offset + 1)(
            Fragment(s"Original input before shrinking was: ") + error(initial)
          )
      case None => Message.empty
    }

  private def renderFragment(fragment: AssertionValue, offset: Int): Line =
    withOffset(offset + 1) {
      primary(renderValue(fragment)) +
        renderSatisfied(fragment) +
        detail(fragment.printAssertion)
    }

  private def renderWhole(fragment: AssertionValue, whole: AssertionValue, offset: Int): Line =
    withOffset(offset + 1) {
      primary(renderValue(whole)) +
        renderSatisfied(whole) ++
        highlight(detail(whole.printAssertion), fragment.printAssertion)
    }

  private def highlight(fragment: Fragment, substring: String, style: Fragment.Style = Fragment.Style.Warning): Line = {
    val parts = fragment.text.split(Pattern.quote(substring))
    if (parts.size == 1) fragment.toLine
    else
      parts.foldLeft(Line.empty) { (line, part) =>
        if (line.fragments.size < parts.size * 2 - 2)
          line + Fragment(part, fragment.style) + Fragment(substring, style)
        else line + Fragment(part, fragment.style)
      }
  }

  private def renderValue(av: AssertionValue) = (av.value, av.expression) match {
    case (v, Some(expression)) if !expressionRedundant(v.toString, expression) => s"`$expression` = $v"
    case (v, _)                                                                => v.toString
  }

  private def expressionRedundant(valueStr: String, expression: String) = {
    // toString drops double quotes, and for tuples and collections doesn't include spaces after the comma
    def strip(s: String) = s
      .replace("\"", "")
      .replace(" ", "")
      .replace("\n", "")
      .replace("\\n", "")
    strip(valueStr) == strip(expression)
  }

  def rendered(
    caseType: ResultType,
    label: String,
    result: Status,
    offset: Int,
    lines: Line*
  ): ExecutionResult =
    ExecutionResult(caseType, label, result, offset, Nil, lines.toList)
}
