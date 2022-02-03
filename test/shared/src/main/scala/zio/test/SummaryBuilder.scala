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

// TODO Needs to be completely re-written for new streaming behavior
object SummaryBuilder {
  def buildSummary[E](executedSpec: ReporterEvent)(implicit trace: ZTraceElement): Summary = {
    val success = countTestResults(executedSpec) {
      case Right(TestSuccess.Succeeded(_)) => true
      case _                               => false
    }
    val fail = countTestResults(executedSpec)(_.isLeft)
    val ignore = countTestResults(executedSpec) {
      case Right(TestSuccess.Ignored) => true
      case _                          => false
    }
    val failures = extractFailures(executedSpec)
    println("SummaryBuilder.buildSummary")
    val rendered = ConsoleRenderer
      .render(failures.flatMap(DefaultTestReporter.render(_, false)), TestAnnotationRenderer.silent)
      .mkString("\n")
    Summary(success, fail, ignore, rendered)
  }

  private def countTestResults[E](
    executedSpec: ReporterEvent
  )(pred: Either[TestFailure[E], TestSuccess] => Boolean): Int =
    executedSpec match {
      case SectionState(results) =>
        results.count(test =>
          test.test.isLeft
        )
      case Failure(labelsReversed, failure, ancestors) =>
        0
    }
//      executedSpec match {
//        case start: ExecutionEvent.SectionStart => ???
//        case end: ExecutionEvent.SectionEnd => 0
//        case test: ExecutionEvent.Test[_] => 0
//        case failure: ExecutionEvent.Failure[_] => 0
//      }

  private def extractFailures[E](executedSpec: ReporterEvent): Seq[ReporterEvent] =
    ???
    // executedSpec.fold[Seq[ExecutionEvent]] { c =>
    //   c match {
    //     case ExecutedSpec.LabeledCase(label, specs) =>
    //       specs.map(spec => ExecutedSpec.labeled(label, spec))
    //     case ExecutedSpec.MultipleCase(specs) =>
    //       val newSpecs = specs.flatMap(Chunk.fromIterable)
    //       if (newSpecs.nonEmpty) Seq(ExecutedSpec(ExecutedSpec.MultipleCase(newSpecs))) else Seq.empty
    //     case c @ ExecutedSpec.TestCase(test, _) => if (test.isLeft) Seq(ExecutedSpec(c)) else Seq.empty
    //   }
    // }
}
