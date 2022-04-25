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

import zio.internal.ansi.AnsiStringOps
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.ExecutionEvent.{SectionEnd, SectionStart, Test, TopLevelFlush}
import zio.test.render.ExecutionResult.ResultType.Suite
import zio.test.render.ExecutionResult.Status.{Failed, Ignored, Passed}
import zio.test.render.ExecutionResult.{ResultType, Status}
import zio.test.render.LogLine.{Fragment, Line, Message}
import zio.test.render._
import zio.{Cause, _}

import java.util.regex.Pattern
import scala.annotation.tailrec

// TODO Needs to be re-written or simply dropped for new streaming behavior. #6484
object DefaultTestReporter {
  def apply[E](testRenderer: TestRenderer, testAnnotationRenderer: TestAnnotationRenderer)(implicit
    trace: ZTraceElement
  ): TestReporter[E] = { (duration: Duration, executedSpec: ExecutionEvent) =>
    // val rendered = testRenderer.render(render(executedSpec, true), testAnnotationRenderer)
    // val stats    = testRenderer.render(logStats(duration, executedSpec) :: Nil, testAnnotationRenderer)
    val rendered = List.empty
    val stats    = List.empty
    TestLogger.logLine((rendered ++ stats).mkString("\n")) // Ensures 1 big string is reported per ExecutedSpec
  }

  def render(
    reporterEvent: ExecutionEvent,
    includeCause: Boolean
  )(implicit trace: ZTraceElement): Seq[ExecutionResult] = // This should return a single/Option ExecutionResult now.
    reporterEvent match {
      case SectionStart(labelsReversed, _, ancestors) =>
        val depth = labelsReversed.length - 1
        labelsReversed.reverse match {
          case Nil => Seq.empty
          case nonEmptyList =>
            Seq(
              ExecutionResult.withoutSummarySpecificOutput(
                ResultType.Suite,
                label = nonEmptyList.last,
                // We no longer know if the suite has passed here, because the output is streamed
                Status.Passed,
                offset = depth * 2,
                List(TestAnnotationMap.empty), // TODO Examine all results to get this
                lines = List(fr(nonEmptyList.last).toLine)
              )
            )
        }

      case Test(labelsReversed, results, annotations, _, _, suiteId) =>
        val labels       = labelsReversed.reverse
        val initialDepth = labels.length - 1
        val (streamingOutput, summaryOutput) =
          testCaseOutput(labels, results, includeCause, suiteId)

        Seq(
          ExecutionResult(
            ResultType.Test,
            labels.headOption.getOrElse(""),
            results match {
              case Left(_) => Status.Failed
              case Right(value: TestSuccess) =>
                value match {
                  case TestSuccess.Succeeded(_) => Status.Passed
                  case TestSuccess.Ignored(_)   => Status.Ignored
                }
            },
            initialDepth * 2,
            List(annotations),
            streamingOutput,
            summaryOutput
          )
        )
      case runtimeFailure @ ExecutionEvent.RuntimeFailure(_, _, failure, _) =>
        val depth = reporterEvent.labels.length
        failure match {
          case TestFailure.Assertion(result, _) =>
            Seq(renderAssertFailure(result, runtimeFailure.labels, depth))
          case TestFailure.Runtime(cause, _) =>
            Seq(renderRuntimeCause(cause, runtimeFailure.labels, depth, includeCause))
        }
      case SectionEnd(_, _, _) =>
        Nil
      case TopLevelFlush(_) =>
        Nil
    }

  private def testCaseOutput(
    labels: List[String],
    results: Either[TestFailure[Any], TestSuccess],
    includeCause: Boolean,
    suiteId: SuiteId
  )(implicit
    trace: ZTraceElement
  ): (List[Line], List[Line]) = {
    val depth     = labels.length
    val label     = labels.last
    val flatLabel = labels.mkString(" - ")

    val renderedResult = results match {
      case Right(TestSuccess.Succeeded(_)) =>
        Some(
          rendered(
            ResultType.Test,
            label,
            Passed,
            depth,
            fr(labels.last).toLine
          )
        )
      case Right(TestSuccess.Ignored(_)) =>
        Some(
          rendered(
            ResultType.Test,
            label,
            Ignored,
            depth,
            warn(label).toLine
          )
        )
      case Left(TestFailure.Assertion(result, _)) =>
        result.failures.map { result =>
          renderedWithSummary(
            ResultType.Test,
            label,
            Failed,
            depth,
            renderFailure(label, depth, result).lines.toList,
            renderFailure(flatLabel, depth, result).lines.toList // Fully-qualified label
          )
        }

      case Left(TestFailure.Runtime(cause, _)) =>
        Some(
          renderRuntimeCause(
            cause,
            labels,
            depth,
            includeCause
          )
        )
    }
    (renderedResult.map(r => r.streamingLines).getOrElse(Nil), renderedResult.map(r => r.summaryLines).getOrElse(Nil))
  }

  private def renderSuiteIgnored(label: String, offset: Int) =
    rendered(Suite, label, Ignored, offset, warn(s"- $label").toLine)

  private def renderSuiteFailed(label: String, offset: Int) =
    rendered(Suite, label, Failed, offset, error(s"- $label").toLine)

  private def renderSuiteSucceeded(label: String, offset: Int) =
    rendered(Suite, label, Passed, offset, fr(label).toLine)

  def renderAssertFailure(result: TestResult, labels: List[String], depth: Int): ExecutionResult = {
    val streamingLabel = labels.lastOption.getOrElse("Top-level defect prevented test execution")
    val summaryLabel   = labels.mkString(" - ")
//    result.fold { details =>
    val streamingRenderedFailure = renderFailure(streamingLabel, depth, result.result).lines.toList
    val summaryRenderedFailure   = renderFailure(summaryLabel, depth, result.result).lines.toList
    renderedWithSummary(
      ResultType.Test,
      streamingLabel,
      Failed,
      depth,
      streamingRenderedFailure,
      summaryRenderedFailure
    )
//    }(
//      _ && _,
//      _ || _,
//      !_
//    )
  }

  private def renderRuntimeCause[E](cause: Cause[E], labels: List[String], depth: Int, includeCause: Boolean)(implicit
    trace: ZTraceElement
  ): ExecutionResult = {
    val streamingLabel = labels.lastOption.getOrElse("Top-level defect prevented test execution")
    val summaryLabel   = labels.mkString(" - ")

    val failureDetails =
      Seq(renderFailureLabel(streamingLabel, depth)) ++ Seq(renderCause(cause, depth))
        .filter(_ => includeCause)
        .flatMap(_.lines)

    val summaryFailureDetails =
      Seq(renderFailureLabel(summaryLabel, depth)) ++ Seq(renderCause(cause, depth))
        .filter(_ => includeCause)
        .flatMap(_.lines)

    renderedWithSummary(
      ResultType.Test,
      streamingLabel,
      Failed,
      depth,
      failureDetails.toList,
      summaryFailureDetails.toList
    )
  }

  def renderAssertionResult(assertionResult: Trace[Boolean], offset: Int): Message = {
    val failures = FailureCase.fromTrace(assertionResult, Chunk.empty)
    failures
      .map(fc =>
//        renderGenFailureDetails(genFailureDetails, offset) ++
        Message(renderFailureCase(fc, offset, None))
      )
      .foldLeft(Message.empty)(_ ++ _)
  }

  def renderFailureCase(failureCase: FailureCase, offset: Int, testLabel: Option[String]): Chunk[Line] =
    failureCase match {
      case FailureCase(errorMessage, codeString, location, path, _, nested, _, customLabel) =>
        val errorMessageLines =
          Chunk.fromIterable(errorMessage.lines) match {
            case head +: tail =>
              (error("✗ ") +: head) +: tail.map(error("  ") +: _)
            case _ => Chunk.empty
          }

        val labelLines = Chunk.fromIterable(customLabel.map(label => Line.fromString(label.bold.yellow)))

        val result =
          errorMessageLines ++ labelLines ++
            Chunk(Line.fromString(testLabel.fold(codeString)(l => s"""$codeString ?? "$l""""))) ++
            nested.flatMap(renderFailureCase(_, offset, None)).map(_.withOffset(1)) ++
            Chunk.fromIterable(path.filterNot(t => t._1 == t._2).flatMap { case (label, value) =>
              Chunk.fromIterable(value.split("\n").map(primary(_).toLine)) match {
                case head +: lines => (dim(s"${label.trim} = ") +: head) +: lines
                case _             => Vector.empty
              }
            }) ++
            Chunk(detail(s"at $location").toLine)

        result.map(_.withOffset(offset + 1))
    }

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

  private def renderFailure(label: String, offset: Int, details: Trace[Boolean]): Message =
    renderFailureLabel(label, offset) +: renderAssertionResult(details, offset) :+ Line.empty

  def renderFailureLabel(label: String, offset: Int): Line =
    withOffset(offset)(error("- " + label).toLine)

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

  def rendered(
    caseType: ResultType,
    label: String,
    result: Status,
    offset: Int,
    lines: Line*
  ): ExecutionResult =
    ExecutionResult(caseType, label, result, offset, Nil, lines.toList, lines.toList)

  def renderedWithSummary(
    caseType: ResultType,
    label: String,
    result: Status,
    offset: Int,
    lines: List[Line],
    summaryLines: List[Line]
  ): ExecutionResult =
    ExecutionResult(caseType, label, result, offset, Nil, lines, summaryLines)
}
