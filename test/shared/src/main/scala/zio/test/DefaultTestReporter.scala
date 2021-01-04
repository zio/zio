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

import zio.duration._
import zio.test.ConsoleUtils.{cyan, red, _}
import zio.test.FailureRenderer.FailureMessage.{Fragment, Message}
import zio.test.RenderedResult.CaseType._
import zio.test.RenderedResult.Status._
import zio.test.RenderedResult.{CaseType, Status}
import zio.test.mock.Expectation
import zio.test.mock.internal.{InvalidCall, MockException}
import zio.{Cause, Has}

import java.util.regex.Pattern
import scala.io.AnsiColor
import scala.util.Try

object DefaultTestReporter {

  def render[E](
    executedSpec: ExecutedSpec[E],
    testAnnotationRenderer: TestAnnotationRenderer,
    includeCause: Boolean
  ): Seq[RenderedResult[String]] = {
    def loop(
      executedSpec: ExecutedSpec[E],
      depth: Int,
      ancestors: List[TestAnnotationMap]
    ): Seq[RenderedResult[String]] =
      executedSpec.caseValue match {
        case ExecutedSpec.SuiteCase(label, specs) =>
          val hasFailures = executedSpec.exists {
            case ExecutedSpec.TestCase(_, test, _) => test.isLeft
            case _                                 => false
          }
          val annotations = executedSpec.fold[TestAnnotationMap] {
            case ExecutedSpec.SuiteCase(_, annotations)   => annotations.foldLeft(TestAnnotationMap.empty)(_ ++ _)
            case ExecutedSpec.TestCase(_, _, annotations) => annotations
          }
          val status = if (hasFailures) Failed else Passed
          val renderedLabel =
            if (specs.isEmpty) Seq(renderIgnoreLabel(label, depth))
            else if (hasFailures) Seq(renderFailureLabel(label, depth))
            else Seq(renderSuccessLabel(label, depth))
          val renderedAnnotations = testAnnotationRenderer.run(ancestors, annotations)
          val rest                = specs.flatMap(loop(_, depth + tabSize, annotations :: ancestors))
          rendered(Suite, label, status, depth, (renderedLabel): _*).withAnnotations(renderedAnnotations) +: rest
        case ExecutedSpec.TestCase(label, result, annotations) =>
          val renderedAnnotations = testAnnotationRenderer.run(ancestors, annotations)
          val renderedResult = result match {
            case Right(TestSuccess.Succeeded(_)) =>
              rendered(Test, label, Passed, depth, withOffset(depth)(green("+") + " " + label))
            case Right(TestSuccess.Ignored) =>
              rendered(Test, label, Ignored, depth, withOffset(depth)(yellow("-") + " " + yellow(label)))
            case Left(TestFailure.Assertion(result)) =>
              result.fold(details => rendered(Test, label, Failed, depth, renderFailure(label, depth, details): _*))(
                _ && _,
                _ || _,
                !_
              )
            case Left(TestFailure.Runtime(cause)) =>
              rendered(
                Test,
                label,
                Failed,
                depth,
                (Seq(renderFailureLabel(label, depth)) ++ Seq(renderCause(cause, depth)).filter(_ => includeCause)): _*
              )
          }
          Seq(renderedResult.withAnnotations(renderedAnnotations))
      }
    loop(executedSpec, 0, List.empty)
  }

  def apply[E](testAnnotationRenderer: TestAnnotationRenderer): TestReporter[E] = {
    (duration: Duration, executedSpec: ExecutedSpec[E]) =>
      val rendered = render(executedSpec, testAnnotationRenderer, true).flatMap(_.rendered)
      val stats    = logStats(duration, executedSpec)
      TestLogger.logLine((rendered ++ Seq(stats)).mkString("\n"))
  }

  private def logStats[E](duration: Duration, executedSpec: ExecutedSpec[E]): String = {
    val (success, ignore, failure) = executedSpec.fold[(Int, Int, Int)] {
      case ExecutedSpec.SuiteCase(_, stats) =>
        stats.foldLeft((0, 0, 0)) { case ((x1, x2, x3), (y1, y2, y3)) =>
          (x1 + y1, x2 + y2, x3 + y3)
        }
      case ExecutedSpec.TestCase(_, result, _) =>
        result match {
          case Left(_)                         => (0, 0, 1)
          case Right(TestSuccess.Succeeded(_)) => (1, 0, 0)
          case Right(TestSuccess.Ignored)      => (0, 1, 0)
        }
    }
    val total = success + ignore + failure
    cyan(
      s"Ran $total test${if (total == 1) "" else "s"} in ${duration.render}: $success succeeded, $ignore ignored, $failure failed"
    )
  }

  private def renderSuccessLabel(label: String, offset: Int) =
    withOffset(offset)(green("+") + " " + label)

  private def renderIgnoreLabel(label: String, offset: Int) =
    withOffset(offset)(yellow("- ") + yellow(label) + " - " + TestAnnotation.ignored.identifier + " suite")

  private def renderFailure(label: String, offset: Int, details: FailureDetails): Seq[String] =
    renderFailureLabel(label, offset) +: renderFailureDetails(details, offset)

  private def renderFailureLabel(label: String, offset: Int): String =
    withOffset(offset)(red("- " + label))

  private def renderFailureDetails(failureDetails: FailureDetails, offset: Int): Seq[String] =
    renderToStringLines(FailureRenderer.renderFailureDetails(failureDetails, offset))

  private def renderCause(cause: Cause[Any], offset: Int): String =
    renderToStringLines(FailureRenderer.renderCause(cause, offset)).mkString("\n")

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
  sealed abstract class Status
  object Status {
    case object Failed  extends Status
    case object Passed  extends Status
    case object Ignored extends Status
  }

  sealed abstract class CaseType
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
      def +:(line: Line)                   = Message(line +: lines)
      def :+(line: Line)                   = Message(lines :+ line)
      def ++(message: Message)             = Message(lines ++ message.lines)
      def drop(n: Int): Message            = Message(lines.drop(n))
      def map(f: Line => Line): Message    = Message(lines = lines.map(f))
      def withOffset(offset: Int): Message = Message(lines.map(_.withOffset(offset)))
    }
    object Message {
      def apply(lines: Seq[Line]): Message = Message(lines.toVector)
      def apply(lineText: String): Message = Fragment(lineText).toLine.toMessage
      val empty: Message                   = Message()
    }
    case class Line(fragments: Vector[Fragment] = Vector.empty, offset: Int = 0) {
      def :+(fragment: Fragment)             = Line(fragments :+ fragment)
      def +(fragment: Fragment)              = Line(fragments :+ fragment)
      def prepend(message: Message): Message = Message(this +: message.lines)
      def +(line: Line)                      = Message(Vector(this, line))
      def ++(line: Line)                     = copy(fragments = fragments ++ line.fragments)
      def withOffset(shift: Int): Line       = copy(offset = offset + shift)
      def toMessage: Message                 = Message(Vector(this))
    }
    object Line {
      def fromString(text: String, offset: Int = 0): Line = Fragment(text).toLine.withOffset(offset)
      val empty: Line                                     = Line()
    }
    case class Fragment(text: String, ansiColorCode: String = "") {
      def +:(line: Line)            = prepend(line)
      def prepend(line: Line): Line = Line(this +: line.fragments, line.offset)
      def +(f: Fragment)            = Line(Vector(this, f))
      def toLine: Line              = Line(Vector(this))
    }
  }

  import FailureMessage._

  def renderFailureDetails(failureDetails: FailureDetails, offset: Int): Message =
    renderGenFailureDetails(failureDetails.gen, offset) ++
      renderAssertionFailureDetails(failureDetails.assertion, offset)

  private def renderAssertionFailureDetails(failureDetails: ::[AssertionValue], offset: Int): Message = {
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

  private def renderGenFailureDetails[A](failureDetails: Option[GenFailureDetails], offset: Int): Message =
    failureDetails match {
      case Some(details) =>
        val shrunken = details.shrunkenInput.toString
        val initial  = details.initialInput.toString
        val renderShrunken = withOffset(offset + tabSize)(
          Fragment(
            s"Test failed after ${details.iterations + 1} iteration${if (details.iterations > 0) "s" else ""} with input: "
          ) +
            red(shrunken)
        )
        if (initial == shrunken) renderShrunken.toMessage
        else
          renderShrunken + withOffset(offset + tabSize)(
            Fragment(s"Original input before shrinking was: ") + red(initial)
          )
      case None => Message.empty
    }

  private def renderWhole(fragment: AssertionValue, whole: AssertionValue, offset: Int): Line =
    withOffset(offset + tabSize) {
      blue(renderValue(whole)) +
        renderSatisfied(whole) ++
        highlight(cyan(whole.printAssertion), fragment.printAssertion)
    }

  private def renderFragment(fragment: AssertionValue, offset: Int): Line =
    withOffset(offset + tabSize) {
      blue(renderValue(fragment)) +
        renderSatisfied(fragment) +
        cyan(fragment.printAssertion)
    }

  private def renderValue(av: AssertionValue) = (av.value, av.expression) match {
    case (v, Some(expression)) if v.toString != expression => s"`$expression` = $v"
    case (v, _)                                            => v.toString
  }

  private def renderAssertionLocation(av: AssertionValue, offset: Int) = av.sourceLocation.fold(Message()) { location =>
    blue(s"at $location").toLine
      .withOffset(offset + 2 * tabSize)
      .toMessage
  }

  private def highlight(fragment: Fragment, substring: String, colorCode: String = AnsiColor.YELLOW): Line = {
    val parts = fragment.text.split(Pattern.quote(substring))
    if (parts.size == 1) fragment.toLine
    else
      parts.foldLeft(Line.empty) { (line, part) =>
        if (line.fragments.size < parts.size * 2 - 2)
          line + Fragment(part, fragment.ansiColorCode) + Fragment(substring, colorCode)
        else line + Fragment(part, fragment.ansiColorCode)
      }
  }

  private def renderSatisfied(fragment: AssertionValue): Fragment =
    if (fragment.result.isSuccess) Fragment(" satisfied ")
    else Fragment(" did not satisfy ")

  def renderCause(cause: Cause[Any], offset: Int): Message =
    cause.dieOption match {
      case Some(TestTimeoutException(message)) => Message(message)
      case Some(exception: MockException) =>
        renderMockException(exception).map(withOffset(offset + tabSize))
      case _ =>
        Message(
          cause.prettyPrint
            .split("\n")
            .map(s => withOffset(offset + tabSize)(Line.fromString(s)))
            .toVector
        )
    }

  private def renderMockException(exception: MockException): Message =
    exception match {
      case MockException.InvalidCallException(failures) =>
        val header = red(s"- could not find a matching expectation").toLine
        header +: renderUnmatchedExpectations(failures)

      case MockException.UnsatisfiedExpectationsException(expectation) =>
        val header = red(s"- unsatisfied expectations").toLine
        header +: renderUnsatisfiedExpectations(expectation)

      case MockException.UnexpectedCallException(method, args) =>
        Message(
          Seq(
            red(s"- unexpected call to $method with arguments").toLine,
            withOffset(tabSize)(cyan(args.toString).toLine)
          )
        )

      case MockException.InvalidRangeException(range) =>
        Message(
          Seq(
            red(s"- invalid repetition range ${range.start} to ${range.end} by ${range.step}").toLine
          )
        )
    }

  private def renderUnmatchedExpectations(failedMatches: List[InvalidCall]): Message =
    failedMatches.map {
      case InvalidCall.InvalidArguments(invoked, args, assertion) =>
        val header = red(s"- $invoked called with invalid arguments").toLine
        (header +: renderTestFailure("", assertImpl(args)(assertion)).drop(1)).withOffset(tabSize)

      case InvalidCall.InvalidCapability(invoked, expected, assertion) =>
        Message(
          Seq(
            withOffset(tabSize)(red(s"- invalid call to $invoked").toLine),
            withOffset(tabSize * 2)(
              Fragment(s"expected $expected with arguments ") + cyan(assertion.toString)
            )
          )
        )

      case InvalidCall.InvalidPolyType(invoked, args, expected, assertion) =>
        Message(
          Seq(
            withOffset(tabSize)(red(s"- $invoked called with arguments $args and invalid polymorphic type").toLine),
            withOffset(tabSize * 2)(
              Fragment(s"expected $expected with arguments ") + cyan(assertion.toString)
            )
          )
        )
    }.reverse.foldLeft(Message.empty)(_ ++ _)

  private def renderUnsatisfiedExpectations[R <: Has[_]](expectation: Expectation[R]): Message = {

    def loop(stack: List[(Int, Expectation[R])], lines: Vector[Line]): Vector[Line] =
      stack match {
        case Nil =>
          lines

        case (ident, Expectation.And(children, state, _, _)) :: tail if state.isFailed =>
          val title       = Line.fromString("in any order", ident)
          val unsatisfied = children.filter(_.state.isFailed).map(ident + tabSize -> _)
          loop(unsatisfied ++ tail, lines :+ title)

        case (ident, Expectation.Call(method, assertion, _, state, _)) :: tail if state.isFailed =>
          val rendered =
            withOffset(ident)(Fragment(s"$method with arguments ") + cyan(assertion.toString))
          loop(tail, lines :+ rendered)

        case (ident, Expectation.Chain(children, state, _, _)) :: tail if state.isFailed =>
          val title       = Line.fromString("in sequential order", ident)
          val unsatisfied = children.filter(_.state.isFailed).map(ident + tabSize -> _)
          loop(unsatisfied ++ tail, lines :+ title)

        case (ident, Expectation.Or(children, state, _, _)) :: tail if state.isFailed =>
          val title       = Line.fromString("one of", ident)
          val unsatisfied = children.map(ident + tabSize -> _)
          loop(unsatisfied ++ tail, lines :+ title)

        case (ident, Expectation.Repeated(child, range, state, _, _, completed)) :: tail if state.isFailed =>
          val min = Try(range.min.toString).getOrElse("0")
          val max = Try(range.max.toString).getOrElse("∞")
          val title =
            Line.fromString(
              s"repeated $completed times not in range $min to $max by ${range.step}",
              ident
            )
          val unsatisfied = (ident + tabSize -> child)
          loop(unsatisfied :: tail, lines :+ title)

        case _ :: tail =>
          loop(tail, lines)
      }

    val lines = loop(List(tabSize -> expectation), Vector.empty)
    Message(lines)
  }

  def renderTestFailure(label: String, testResult: TestResult): Message =
    testResult.failures.fold(Message.empty) { details =>
      Message {
        details
          .fold(failures => rendered(Test, label, Failed, 0, renderFailure(label, 0, failures).lines: _*))(
            _ && _,
            _ || _,
            !_
          )
          .rendered
      }
    }

  private def rendered[T](
    caseType: CaseType,
    label: String,
    result: Status,
    offset: Int,
    rendered: T*
  ): RenderedResult[T] =
    RenderedResult(caseType, label, result, offset, rendered)

  private def renderFailure(label: String, offset: Int, details: FailureDetails): Message =
    renderFailureLabel(label, offset).prepend(renderFailureDetails(details, offset))

  private def renderFailureLabel(label: String, offset: Int): Line =
    withOffset(offset)(red("- " + label).toLine)

  private def red(s: String)                                      = FailureMessage.Fragment(s, AnsiColor.RED)
  private def blue(s: String)                                     = FailureMessage.Fragment(s, AnsiColor.BLUE)
  private def cyan(s: String)                                     = FailureMessage.Fragment(s, AnsiColor.CYAN)
  private def withOffset(i: Int)(line: FailureMessage.Line): Line = line.withOffset(i)

}
