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
import scala.util.Try

import zio.duration.Duration
import zio.test.ConsoleUtils.{ cyan, red, _ }
import zio.test.FailureRenderer.FailureMessage.{ Fragment, Message }
import zio.test.RenderedResult.CaseType._
import zio.test.RenderedResult.Status._
import zio.test.RenderedResult.{ CaseType, Status }
import zio.test.mock.Expectation
import zio.test.mock.internal.{ InvalidCall, MockException }
import zio.{ Cause, Has, UIO, URIO, ZIO }

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

  def apply[E](testAnnotationRenderer: TestAnnotationRenderer): TestReporter[E] = {
    (duration: Duration, executedSpec: ExecutedSpec[E]) =>
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

  private def renderFailure(label: String, offset: Int, details: FailureDetails): UIO[Seq[String]] =
    renderFailureDetails(details, offset).map(renderFailureLabel(label, offset) +: _)

  private def renderFailureLabel(label: String, offset: Int): String =
    withOffset(offset)(red("- " + label))

  private def renderFailureDetails(failureDetails: FailureDetails, offset: Int): UIO[Seq[String]] =
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

object FailureRenderer {

  private val tabSize = 2

  object FailureMessage {
    case class Message(lines: Vector[Line] = Vector.empty) {
      def +:(line: Line)          = Message(line +: lines)
      def :+(line: Line)          = Message(lines :+ line)
      def ++(message: Message)    = Message(lines ++ message.lines)
      def drop(n: Int)            = Message(lines.drop(n))
      def map(f: Line => Line)    = Message(lines = lines.map(f))
      def withOffset(offset: Int) = Message(lines.map(_.withOffset(offset)))
    }
    object Message {
      def apply(lines: Seq[Line]): Message = Message(lines.toVector)
      def apply(lineText: String): Message = Fragment(lineText).toLine.toMessage
      val empty: Message                   = Message()
    }
    case class Line(fragments: Vector[Fragment] = Vector.empty, offset: Int = 0) {
      def :+(fragment: Fragment)    = Line(fragments :+ fragment)
      def +(fragment: Fragment)     = Line(fragments :+ fragment)
      def prepend(message: Message) = Message(this +: message.lines)
      def +(line: Line)             = Message(Vector(this, line))
      def ++(line: Line)            = copy(fragments = fragments ++ line.fragments)
      def withOffset(shift: Int)    = copy(offset = offset + shift)
      def toMessage                 = Message(Vector(this))
    }
    object Line {
      def fromString(text: String, offset: Int = 0): Line = Fragment(text).toLine.withOffset(offset)
      val empty: Line                                     = Line()
    }
    case class Fragment(text: String, ansiColorCode: String = "") {
      def +:(line: Line)      = prepend(line)
      def prepend(line: Line) = Line(this +: line.fragments, line.offset)
      def +(f: Fragment)      = Line(Vector(this, f))
      def toLine              = Line(Vector(this))
    }
  }

  import FailureMessage._

  def renderFailureDetails(failureDetails: FailureDetails, offset: Int): UIO[Message] =
    failureDetails match {
      case FailureDetails(assertionFailureDetails, genFailureDetails) =>
        renderAssertionFailureDetails(assertionFailureDetails, offset).map(
          renderGenFailureDetails(genFailureDetails, offset) ++ _
        )
    }

  private def renderAssertionFailureDetails(failureDetails: ::[AssertionValue], offset: Int): UIO[Message] = {
    def loop(failureDetails: List[AssertionValue], rendered: Message): UIO[Message] =
      failureDetails match {
        case fragment :: whole :: failureDetails =>
          renderWhole(fragment, whole, offset).flatMap(s => loop(whole :: failureDetails, rendered :+ s))
        case _ =>
          UIO.succeedNow(rendered)
      }
    for {
      fragment <- renderFragment(failureDetails.head, offset)
      rest     <- loop(failureDetails, Message.empty)
    } yield fragment.toMessage ++ rest
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
            red(shrinked)
        )
        if (initial == shrinked) renderShrinked.toMessage
        else
          renderShrinked + withOffset(offset + tabSize)(
            Fragment(s"Original input before shrinking was: ") + red(initial)
          )
      case None => Message.empty
    }

  private def renderWhole(fragment: AssertionValue, whole: AssertionValue, offset: Int): UIO[Line] =
    renderSatisfied(whole).map { satisfied =>
      withOffset(offset + tabSize) {
        blue(whole.value.toString) +
          satisfied ++
          highlight(cyan(whole.assertion.toString), fragment.assertion.toString)
      }
    }

  private def renderFragment(fragment: AssertionValue, offset: Int): UIO[Line] =
    renderSatisfied(fragment).map { satisfied =>
      withOffset(offset + tabSize) {
        blue(fragment.value.toString) +
          satisfied +
          cyan(fragment.assertion.toString)
      }
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

  private def renderSatisfied(fragment: AssertionValue): UIO[Fragment] =
    fragment.assertion.test(fragment.value).map(p => Fragment(if (p) " satisfied " else " did not satisfy "))

  def renderCause(cause: Cause[Any], offset: Int): UIO[Message] =
    cause.dieOption match {
      case Some(TestTimeoutException(message)) => UIO.succeedNow(Message(message))
      case Some(exception: MockException) =>
        renderMockException(exception).map(_.map(withOffset(offset + tabSize)))
      case _ =>
        UIO.succeed(
          Message(
            cause.prettyPrint
              .split("\n")
              .map(s => withOffset(offset + tabSize)(Line.fromString(s)))
              .toVector
          )
        )
    }

  private def renderMockException(exception: MockException): UIO[Message] =
    exception match {
      case MockException.InvalidCallException(failures) =>
        renderUnmatchedExpectations(failures).map { message =>
          val header = red(s"- could not find a matching expectation").toLine
          header +: message
        }

      case MockException.UnsatisfiedExpectationsException(expectation) =>
        renderUnsatisfiedExpectations(expectation).map { message =>
          val header = red(s"- unsatisfied expectations").toLine
          header +: message
        }

      case MockException.UnexpectedCallExpection(method, args) =>
        UIO.succeedNow(
          Message(
            Seq(
              red(s"- unexpected call to $method with arguments").toLine,
              withOffset(tabSize)(cyan(args.toString).toLine)
            )
          )
        )

      case MockException.InvalidRangeException(range) =>
        UIO.succeedNow(
          Message(
            Seq(
              red(s"- invalid repetition range ${range.start} to ${range.end} by ${range.step}").toLine
            )
          )
        )
    }

  private def renderUnmatchedExpectations(failedMatches: List[InvalidCall]): UIO[Message] =
    ZIO
      .foreach(failedMatches) {
        case InvalidCall.InvalidArguments(method, args, assertion) =>
          renderTestFailure("", assert(args)(assertion)).map { message =>
            val header = red(s"- $method called with invalid arguments").toLine
            (header +: message.drop(1)).withOffset(tabSize)
          }

        case InvalidCall.InvalidMethod(method, expectedMethod, assertion) =>
          UIO.succeedNow(
            Message(
              Seq(
                withOffset(tabSize)(red(s"- invalid call to $method").toLine),
                withOffset(tabSize * 2)(
                  Fragment(s"expected $expectedMethod with arguments ") + cyan(assertion.toString)
                )
              )
            )
          )

        case InvalidCall.InvalidPolyType(method, args, expectedMethod, assertion) =>
          UIO.succeedNow(
            Message(
              Seq(
                withOffset(tabSize)(red(s"- $method called with arguments $args and invalid polymorphic type").toLine),
                withOffset(tabSize * 2)(
                  Fragment(s"expected $expectedMethod with arguments ") + cyan(assertion.toString)
                )
              )
            )
          )
      }
      .map(_.reverse.foldLeft(Message.empty)(_ ++ _))

  private def renderUnsatisfiedExpectations[R <: Has[_]](expectation: Expectation[R]): UIO[Message] = {

    def loop(stack: List[(Int, Expectation[R])], lines: Vector[Line]): Vector[Line] =
      stack match {
        case Nil =>
          lines

        case (ident, Expectation.And(children, false, _, _)) :: tail =>
          val title       = Line.fromString("in any order", ident)
          val unsatisfied = children.filter(!_.satisfied).map(ident + tabSize -> _)
          loop(unsatisfied ++ tail, lines :+ title)

        case (ident, Expectation.Call(method, assertion, _, false, _, _)) :: tail =>
          val rendered =
            withOffset(ident)(Fragment(s"$method with arguments ") + cyan(assertion.toString))
          loop(tail, lines :+ rendered)

        case (ident, Expectation.Chain(children, false, _, _)) :: tail =>
          val title       = Line.fromString("in sequential order", ident)
          val unsatisfied = children.filter(!_.satisfied).map(ident + tabSize -> _)
          loop(unsatisfied ++ tail, lines :+ title)

        case (ident, Expectation.Or(children, false, _, _)) :: tail =>
          val title       = Line.fromString("one of", ident)
          val unsatisfied = children.map(ident + tabSize -> _)
          loop(unsatisfied ++ tail, lines :+ title)

        case (ident, Expectation.Repeated(child, range, false, _, _, _, completed)) :: tail =>
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
    UIO.succeedNow(Message(lines))
  }

  def renderTestFailure(label: String, testResult: TestResult): UIO[Message] =
    testResult.run.flatMap(
      _.failures.fold(UIO.succeedNow(Message.empty))(
        _.fold(details =>
          renderFailure(label, 0, details)
            .map(failures => rendered(Test, label, Failed, 0, failures.lines: _*))
        )(_.zipWith(_)(_ && _), _.zipWith(_)(_ || _), _.map(!_))
          .map(_.rendered)
          .map(Message.apply)
      )
    )

  private def rendered[T](
    caseType: CaseType,
    label: String,
    result: Status,
    offset: Int,
    rendered: T*
  ): RenderedResult[T] =
    RenderedResult(caseType, label, result, offset, rendered)

  private def renderFailure(label: String, offset: Int, details: FailureDetails): UIO[Message] =
    renderFailureDetails(details, offset).map(renderFailureLabel(label, offset).prepend)

  private def renderFailureLabel(label: String, offset: Int): Line =
    withOffset(offset)(red("- " + label).toLine)

  private def red(s: String)                                      = FailureMessage.Fragment(s, AnsiColor.RED)
  private def blue(s: String)                                     = FailureMessage.Fragment(s, AnsiColor.BLUE)
  private def cyan(s: String)                                     = FailureMessage.Fragment(s, AnsiColor.CYAN)
  private def withOffset(i: Int)(line: FailureMessage.Line): Line = line.withOffset(i)

}
