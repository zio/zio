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

import java.util.regex.Pattern

import scala.io.AnsiColor

import zio.duration.Duration
import zio.test.ConsoleUtils.{ cyan, red, _ }
import zio.test.FailureRenderer.FailureMessage.{ Fragment, Line, Message }
import zio.test.RenderedResult.CaseType._
import zio.test.RenderedResult.Status._
import zio.test.RenderedResult.{ CaseType, Status }
import zio.test.diff.{ DiffElement, StringDiffer }
import zio.test.mock.MockException.{
  InvalidArgumentsException,
  InvalidMethodException,
  UnexpectedCallExpection,
  UnmetExpectationsException
}
import zio.test.mock.{ Method, MockException }
import zio.{ Cause, UIO, URIO }

object DefaultTestReporter {

  def render[E](
    executedSpec: ExecutedSpec[E],
    testAnnotationRenderer: TestAnnotationRenderer,
    differ: StringDiffer = StringDiffer.default
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
                      renderFailure(label, depth, details, differ)
                        .map(failures => rendered(Test, label, Failed, depth, failures: _*))
                    )(_.zipWith(_)(_ && _), _.zipWith(_)(_ || _), _.map(!_))
                )
              case Left(TestFailure.Runtime(cause)) =>
                renderCause(cause, depth, differ).map { string =>
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
    testAnnotationRenderer: TestAnnotationRenderer,
    differ: StringDiffer = StringDiffer.default
  ): TestReporter[E] = { (duration: Duration, executedSpec: ExecutedSpec[E]) =>
    for {
      rendered <- render(executedSpec, testAnnotationRenderer, differ).map(_.flatMap(_.rendered))
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
    details: FailureDetails,
    differ: StringDiffer
  ): UIO[Seq[String]] =
    renderFailureDetails(details, offset, differ).map(renderFailureLabel(label, offset) +: _)

  private def renderFailureLabel(label: String, offset: Int): String =
    withOffset(offset)(red("- " + label))

  private def renderFailureDetails(
    failureDetails: FailureDetails,
    offset: Int,
    differ: StringDiffer
  ): UIO[Seq[String]] =
    FailureRenderer
      .renderFailureDetails(failureDetails, offset, differ)
      .map(renderToStringLines)

  private def renderCause(cause: Cause[Any], offset: Int, differ: StringDiffer): UIO[String] =
    FailureRenderer
      .renderCause(cause, offset, differ)
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

object FailureRenderer {

  private val tabSize = 2

  object FailureMessage {
    case class Message(lines: Vector[Line] = Vector.empty) {
      def :+(line: Line)          = Message(lines :+ line)
      def ++(message: Message)    = Message(lines ++ message.lines)
      def map(f: Line => Line)    = copy(lines = lines.map(f))
      def splitOnLineBreaks       = Message(lines.flatMap(_.splitOnLineBreaks))
      def withOffset(offset: Int) = Message(lines.map(_.withOffset(offset)))
    }
    object Message {
      def apply(lines: Seq[Line]): Message = Message(lines.toVector)
      def apply(lineText: String): Message = Fragment(lineText).toLine.toMessage
    }
    case class Line(fragments: Vector[Fragment] = Vector.empty, offset: Int = 0) {
      def :+(fragment: Fragment)    = Line(fragments :+ fragment, offset)
      def +(fragment: Fragment)     = Line(fragments :+ fragment, offset)
      def prepend(message: Message) = Message(this +: message.lines)
      def +(line: Line)             = Message(Vector(this, line))
      def ++(line: Line)            = copy(fragments = fragments ++ line.fragments)
      def withOffset(shift: Int)    = copy(offset = offset + shift)
      def toMessage                 = Message(Vector(this))
      def splitOnLineBreaks = fragments.foldLeft(Vector.empty[Line]) {
        case (lines, fragment) =>
          val head +: tail = fragment.text.lines.toVector.map(Fragment(_, fragment.ansiColorCode))
          (lines.dropRight(1) :+
            lines.lastOption.fold(head.toLine.withOffset(offset))(prevLine =>
              if (head.text.nonEmpty) prevLine + head
              else prevLine
            )) ++
            tail.map(_.toLine.withOffset(offset))
      }
    }
    object Line {
      def fromString(text: String, offset: Int = 0): Line = Fragment(text).toLine.withOffset(offset)
    }

    case class Fragment(text: String, ansiColorCode: String = "") {
      def +:(line: Line)      = prepend(line)
      def prepend(line: Line) = Line(this +: line.fragments, line.offset)
      def +(f: Fragment)      = Line(Vector(this, f))
      def toLine              = Line(Vector(this))
    }

    def fragment(text: String, ansiColorCode: String = "") =
      if (text.isEmpty) Fragment(text)
      else Fragment(text, ansiColorCode)

    object Fragment {
      def red(s: String)    = Fragment(s, AnsiColor.RED)
      def blue(s: String)   = Fragment(s, AnsiColor.BLUE)
      def yellow(s: String) = Fragment(s, AnsiColor.YELLOW)
      def green(s: String)  = Fragment(s, AnsiColor.GREEN)
      def cyan(s: String)   = Fragment(s, AnsiColor.CYAN)
      def plain(s: String)  = Fragment(s)
    }
  }

  import FailureMessage._

  def renderFailureDetails(failureDetails: FailureDetails, offset: Int, differ: StringDiffer): UIO[Message] =
    failureDetails match {
      case FailureDetails(assertionFailureDetails, genFailureDetails) =>
        renderAssertionFailureDetails(assertionFailureDetails, offset, differ).map(
          renderGenFailureDetails(genFailureDetails, offset) ++ _
        )
    }

  private def renderAssertionFailureDetails(
    failureDetails: ::[AssertionValue],
    offset: Int,
    differ: StringDiffer
  ): UIO[Message] = {
    def loop(failureDetails: List[AssertionValue], rendered: Message): UIO[Message] =
      failureDetails match {
        case fragment :: whole :: failureDetails =>
          renderWhole(fragment, whole, offset).flatMap(s => loop(whole :: failureDetails, rendered :+ s))
        case _ =>
          UIO.succeedNow(rendered)
      }
    for {
      fragment <- renderFragment(failureDetails.head, offset, differ)
      rest     <- loop(failureDetails, Message())
    } yield fragment ++ rest
  }

  private def renderGenFailureDetails[A](failureDetails: Option[GenFailureDetails], offset: Int): Message =
    failureDetails match {
      case Some(details) =>
        val shrinked = details.shrinkedInput.toString
        val initial  = details.initialInput.toString
        val renderShrinked = withOffset(offset + tabSize)(
          Fragment(
            s"Test failed after ${details.iterations + 1} iteration${if (details.iterations > 0) "s" else ""} with input: "
          ) +
            Fragment.red(shrinked)
        )
        if (initial == shrinked) renderShrinked.toMessage
        else
          renderShrinked + withOffset(offset + tabSize)(
            Fragment(s"Original input before shrinking was: ") + Fragment.red(initial)
          )
      case None => Message()
    }

  private def renderWhole(fragment: AssertionValue, whole: AssertionValue, offset: Int): UIO[Line] =
    renderSatisfied(whole).map { satisfied =>
      withOffset(offset + tabSize) {
        Fragment.blue(whole.value.toString) +
          satisfied ++
          highlight(Fragment.cyan(whole.assertion.toString), fragment.assertion.toString)
      }
    }

  private def renderFragment(fragment: AssertionValue, offset: Int, differ: StringDiffer): UIO[Message] =
    renderSatisfied(fragment).map { satisfied =>
      val diff = maybeDiff(fragment, offset + tabSize, differ)
      withOffset(offset + tabSize) {
        Fragment.blue(fragment.value.toString) +
          satisfied +
          Fragment.cyan(fragment.assertion.toString)
      }.toMessage ++ diff
    }

  private def maybeDiff(fragment: AssertionValue, offset: Int, differ: StringDiffer): Message =
    (fragment.assertion.expected, fragment.value) match {
      case (Some(expected: String), actual: String) =>
        DiffRenderer
          .renderDiff(differ.diff(expected, actual))
          .withOffset(offset + tabSize)
      case _ => Message()
    }

  private def highlight(fragment: Fragment, substring: String, colorCode: String = AnsiColor.YELLOW): Line = {
    val parts = fragment.text.split(Pattern.quote(substring))
    if (parts.size == 1) fragment.toLine
    else
      parts.foldLeft(Line()) { (line, part) =>
        if (line.fragments.size < parts.size * 2 - 2)
          line + Fragment(part, fragment.ansiColorCode) + Fragment(substring, colorCode)
        else line + Fragment(part, fragment.ansiColorCode)
      }
  }

  private def renderSatisfied(fragment: AssertionValue): UIO[Fragment] =
    fragment.assertion.test(fragment.value).map(p => Fragment(if (p) " satisfied " else " did not satisfy "))

  def renderCause(cause: Cause[Any], offset: Int, differ: StringDiffer = StringDiffer.default): UIO[Message] =
    cause.dieOption match {
      case Some(TestTimeoutException(message)) => UIO.succeedNow(Message(message))
      case Some(exception: MockException) =>
        renderMockException(exception, differ).map(_.map(withOffset(offset + tabSize)))
      case _ =>
        UIO.succeedNow(
          Message(
            cause.prettyPrint
              .split("\n")
              .map(s => withOffset(offset + tabSize)(Line.fromString(s)))
              .toVector
          )
        )
    }

  private def renderMockException(exception: MockException, differ: StringDiffer): UIO[Message] =
    exception match {
      case InvalidArgumentsException(method, args, assertion) =>
        renderTestFailure(s"$method called with invalid arguments", assert(args)(assertion), differ)

      case InvalidMethodException(method, expectedMethod, assertion) =>
        UIO.succeedNow(
          Message(
            Seq(
              Fragment.red(s"- invalid call to $method").toLine,
              renderExpectation(expectedMethod, assertion, tabSize)
            )
          )
        )

      case UnmetExpectationsException(expectations) =>
        UIO.succeedNow(Message(Fragment.red(s"- unmet expectations").toLine +: expectations.map {
          case (expectedMethod, assertion) => renderExpectation(expectedMethod, assertion, tabSize)
        }))

      case UnexpectedCallExpection(method, args) =>
        UIO.succeedNow(
          Message(
            Seq(
              Fragment.red(s"- unexpected call to $method with arguments").toLine,
              withOffset(tabSize)(Fragment.cyan(args.toString).toLine)
            )
          )
        )
    }

  private def renderExpectation[M, I, A](method: Method[M, I, A], assertion: Assertion[I], offset: Int): Line =
    withOffset(offset)(Fragment(s"expected $method with arguments ") + Fragment.cyan(assertion.toString))

  def renderTestFailure(
    label: String,
    testResult: TestResult,
    differ: StringDiffer = StringDiffer.default
  ): UIO[Message] =
    testResult.run.flatMap(
      _.fold(details =>
        renderFailure(label, 0, details, differ)
          .map(failures => rendered(Test, label, Failed, 0, failures.lines: _*))
      )(_.zipWith(_)(_ && _), _.zipWith(_)(_ || _), _.map(!_))
        .map(_.rendered)
        .map(Message.apply)
    )

  private def rendered[T](
    caseType: CaseType,
    label: String,
    result: Status,
    offset: Int,
    rendered: T*
  ): RenderedResult[T] =
    RenderedResult(caseType, label, result, offset, rendered)

  private def renderFailure(label: String, offset: Int, details: FailureDetails, differ: StringDiffer): UIO[Message] =
    renderFailureDetails(details, offset, differ).map(renderFailureLabel(label, offset).prepend)

  private def renderFailureLabel(label: String, offset: Int): Line =
    withOffset(offset)(Fragment.red("- " + label).toLine)

  private def withOffset(i: Int)(line: FailureMessage.Line): Line = line.withOffset(i)
}

private[test] object DiffRenderer {
  def renderDiff(diff: Vector[DiffElement]) =
    diff.map {
      case DiffElement.Unchanged(text) => Fragment.green(text).toLine
      case DiffElement.Changed(actual, expected) =>
        Fragment("[-") + Fragment.red(actual) + Fragment("+") + Fragment.blue(expected) + Fragment("]")
      case DiffElement.Inserted(text) =>
        Fragment("[+") + Fragment.red(text) + Fragment("]")
      case DiffElement.Deleted(text) => Fragment("[-") + Fragment.red(text) + Fragment("]")
    }.foldLeft(Line())(_ ++ _).toMessage.splitOnLineBreaks
}
