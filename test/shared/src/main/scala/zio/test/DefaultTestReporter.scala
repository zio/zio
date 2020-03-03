/*
 * Copyright 2019-2020 John A. De Goes and the ZIO Contributors
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

import scala.io.AnsiColor

import zio.duration.Duration
import zio.test.ConsoleUtils.{ cyan, red, _ }
import zio.test.MessageMarkup.{ Fragment, Message }
import zio.test.RenderedResult.CaseType._
import zio.test.RenderedResult.Status._
import zio.test.RenderedResult.{ CaseType, Status }
import zio.{ Cause, UIO, URIO }

object DefaultTestReporter {

  def render[E](
    executedSpec: ExecutedSpec[E],
    testAnnotationRenderer: TestAnnotationRenderer
  ): UIO[Seq[RenderedResult[String]]] = {
    def loop(
      executedSpec: ExecutedSpec[E],
      depth: Int,
      ancestors: List[TestAnnotationMap]
    ): UIO[Seq[RenderedResult[String]]] =
      executedSpec.caseValue match {
        case c @ Spec.SuiteCase(label, executedSpecs, _) =>
          for {
            specs <- executedSpecs
            failures <- UIO.foreach(specs)(_.exists {
                         case Spec.TestCase(_, test, _) => test.map(_.isLeft);
                         case _                         => UIO.succeedNow(false)
                       })
            annotations <- Spec(c).fold[UIO[TestAnnotationMap]] {
                            case Spec.SuiteCase(_, specs, _) =>
                              specs.flatMap(UIO.collectAll(_).map(_.foldLeft(TestAnnotationMap.empty)(_ ++ _)))
                            case Spec.TestCase(_, _, annotations) => UIO.succeedNow(annotations)
                          }
            hasFailures = failures.exists(identity)
            status      = if (hasFailures) Failed else Passed
            renderedLabel = if (specs.isEmpty) Seq.empty
            else if (hasFailures) Seq(renderFailureLabel(label, depth))
            else Seq(renderSuccessLabel(label, depth))
            renderedAnnotations = testAnnotationRenderer.run(ancestors, annotations)
            rest                <- UIO.foreach(specs)(loop(_, depth + tabSize, annotations :: ancestors)).map(_.flatten)
          } yield rendered(Suite, label, status, depth, (renderedLabel): _*)
            .withAnnotations(renderedAnnotations) +: rest
        case Spec.TestCase(label, result, annotations) =>
          result.flatMap { result =>
            val renderedAnnotations = testAnnotationRenderer.run(ancestors, annotations)
            val renderedResult = result match {
              case Right(TestSuccess.Succeeded(_)) =>
                UIO.succeedNow(rendered(Test, label, Passed, depth, withOffset(depth)(green("+") + " " + label)))
              case Right(TestSuccess.Ignored) =>
                UIO.succeedNow(rendered(Test, label, Ignored, depth))
              case Left(TestFailure.Assertion(result)) =>
                result.run.flatMap(result =>
                  result
                    .fold(details =>
                      renderFailure(label, depth, details)
                        .map(failures => rendered(Test, label, Failed, depth, failures: _*))
                    )(_.zipWith(_)(_ && _), _.zipWith(_)(_ || _), _.map(!_))
                )
              case Left(TestFailure.Runtime(cause)) =>
                renderCause(cause, depth).map { string =>
                  rendered(
                    Test,
                    label,
                    Failed,
                    depth,
                    (Seq(renderFailureLabel(label, depth)) ++ Seq(string)): _*
                  )

                }
            }
            renderedResult.map(result => Seq(result.withAnnotations(renderedAnnotations)))
          }
      }
    loop(executedSpec, 0, List.empty)
  }

  def apply[E](
    testAnnotationRenderer: TestAnnotationRenderer
  ): TestReporter[E] = { (duration: Duration, executedSpec: ExecutedSpec[E]) =>
    for {
      rendered <- render(executedSpec, testAnnotationRenderer).map(_.flatMap(_.rendered))
      stats    <- logStats(duration, executedSpec)
      _        <- TestLogger.logLine((rendered ++ Seq(stats)).mkString("\n"))
    } yield ()
  }

  private def logStats[E](duration: Duration, executedSpec: ExecutedSpec[E]): URIO[TestLogger, String] = {
    def loop(executedSpec: ExecutedSpec[E]): UIO[(Int, Int, Int)] =
      executedSpec.caseValue match {
        case Spec.SuiteCase(_, executedSpecs, _) =>
          for {
            specs <- executedSpecs
            stats <- UIO.foreach(specs)(loop)
          } yield stats.foldLeft((0, 0, 0)) {
            case ((x1, x2, x3), (y1, y2, y3)) => (x1 + y1, x2 + y2, x3 + y3)
          }
        case Spec.TestCase(_, result, _) =>
          result.map {
            case Left(_)                         => (0, 0, 1)
            case Right(TestSuccess.Succeeded(_)) => (1, 0, 0)
            case Right(TestSuccess.Ignored)      => (0, 1, 0)
          }
      }
    for {
      stats                      <- loop(executedSpec)
      (success, ignore, failure) = stats
      total                      = success + ignore + failure
    } yield cyan(
      s"Ran $total test${if (total == 1) "" else "s"} in ${duration.render}: $success succeeded, $ignore ignored, $failure failed"
    )
  }

  private def renderSuccessLabel(label: String, offset: Int) =
    withOffset(offset)(green("+") + " " + label)

  private def renderFailure(
    label: String,
    offset: Int,
    details: FailureDetails
  ): UIO[Seq[String]] =
    renderFailureDetails(details, offset).map(renderFailureLabel(label, offset) +: _)

  private def renderFailureLabel(label: String, offset: Int): String =
    withOffset(offset)(red("- " + label))

  private def renderFailureDetails(
    failureDetails: FailureDetails,
    offset: Int
  ): UIO[Seq[String]] =
    FailureRenderer
      .renderFailureDetails(failureDetails, offset)
      .map(renderToStringLines)

  private def renderCause(cause: Cause[Any], offset: Int): UIO[String] =
    FailureRenderer
      .renderCause(cause, offset)
      .map(renderToStringLines(_).mkString("\n"))

  private def renderToStringLines(message: Message): Seq[String] = {
    def renderFragment(f: Fragment) =
      if (f.ansiColorCode.nonEmpty) f.ansiColorCode + f.text + AnsiColor.RESET
      else f.text
    message.lines.map { line =>
      withOffset(line.offset)(line.fragments.foldLeft("")((str, f) => str + renderFragment(f)))
    }
  }

  private def withOffset(n: Int)(s: String): String =
    " " * n + s

  private val tabSize = 2

  private def rendered(
    caseType: CaseType,
    label: String,
    result: Status,
    offset: Int,
    rendered: String*
  ): RenderedResult[String] =
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

case class RenderedResult[T](caseType: CaseType, label: String, status: Status, offset: Int, rendered: Seq[T]) {
  self =>

  def &&(that: RenderedResult[T]): RenderedResult[T] =
    (self.status, that.status) match {
      case (Ignored, _)     => that
      case (_, Ignored)     => self
      case (Failed, Failed) => self.copy(rendered = self.rendered ++ that.rendered.tail)
      case (Passed, _)      => that
      case (_, Passed)      => self
    }

  def ||(that: RenderedResult[T]): RenderedResult[T] =
    (self.status, that.status) match {
      case (Ignored, _)     => that
      case (_, Ignored)     => self
      case (Failed, Failed) => self.copy(rendered = self.rendered ++ that.rendered.tail)
      case (Passed, _)      => self
      case (_, Passed)      => that
    }

  def unary_! : RenderedResult[T] =
    self.status match {
      case Ignored => self
      case Failed  => self.copy(status = Passed)
      case Passed  => self.copy(status = Failed)
    }

  def withAnnotations(annotations: Seq[String])(implicit ev: T =:= String): RenderedResult[T] = {
    val strRendered = rendered.map(ev)
    if (rendered.isEmpty || annotations.isEmpty) self
    else {
      val renderedAnnotations     = annotations.mkString(" - ", ", ", "")
      val renderedWithAnnotations = Seq(strRendered.head + renderedAnnotations) ++ strRendered.tail
      self.copy(rendered = renderedWithAnnotations.asInstanceOf[Seq[T]])
    }
  }
}
