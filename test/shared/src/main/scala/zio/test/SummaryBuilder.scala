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

import zio.{Chunk, ZTraceElement}
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.render.ConsoleRenderer

object SummaryBuilder {
  def buildSummary[E](reporterEvent: ReporterEvent, oldSummary: Summary)(implicit trace: ZTraceElement): Summary = {
    val success = countTestResults(reporterEvent) {
      case Right(TestSuccess.Succeeded(_)) => true
      case _                               => false
    }
    val fail = countTestResults(reporterEvent) {
      case Right(_) => false
      case _        => true
    }
    val ignore = countTestResults(reporterEvent) {
      case Right(TestSuccess.Ignored) => true
      case _                          => false
    }
    val failures = extractFailures(reporterEvent)

    val rendered =
//      TODO Check impact of hard-coded false here
      ConsoleRenderer
        .render(failures.flatMap(DefaultTestReporter.render(_, false)), TestAnnotationRenderer.silent)
        .mkString("\n")

    val newSummaryPiece = Summary(success, fail, ignore, rendered)
    Summary(
      oldSummary.success + newSummaryPiece.success,
      oldSummary.fail + newSummaryPiece.fail,
      oldSummary.ignore + newSummaryPiece.ignore,
      oldSummary.summary + newSummaryPiece.summary
    )

  }

  private def countTestResults[E](
    executedSpec: ReporterEvent
  )(pred: Either[TestFailure[_], TestSuccess] => Boolean): Int =
    executedSpec match {
      case SectionState(results: Chunk[ExecutionEvent.Test[_]], _) =>
        results.count { t =>
          pred.apply(t.test)
        }
      case RuntimeFailure(_, _, _, _) =>
        0

      case SectionHeader(_, _) => 0
    }

  private def extractFailures[E](reporterEvent: ReporterEvent): Seq[ReporterEvent] =
    reporterEvent match {
      case SectionState(results, _) =>
        if (
          results.exists { case ExecutionEvent.Test(_, test, _, _, _, _) =>
            test match {
              case Left(_) =>
                true
              case _ =>
                false
            }
          }
        ) {
          Seq(reporterEvent)
        } else {
          Seq.empty
        }
      case RuntimeFailure(_, _, _, _) => Seq(reporterEvent)
      case SectionHeader(_, _)        => Seq.empty
    }
}
