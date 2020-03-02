package zio.test

import java.util.regex.Pattern

import scala.io.AnsiColor

import zio.test.RenderedResult.CaseType.Test
import zio.test.RenderedResult.Status.Failed
import zio.test.RenderedResult.{ CaseType, Status }
import zio.test.mock.MockException.{
  InvalidArgumentsException,
  InvalidMethodException,
  UnexpectedCallExpection,
  UnmetExpectationsException
}
import zio.test.mock.{ Method, MockException }
import zio.{ Cause, UIO }

object FailureRenderer {

  private val tabSize = 2

  import MessageMarkup._

  def renderFailureDetails(failureDetails: FailureDetails, offset: Int): UIO[Message] =
    failureDetails match {
      case FailureDetails(assertionFailureDetails, genFailureDetails) =>
        renderAssertionFailureDetails(assertionFailureDetails, offset).map(
          renderGenFailureDetails(genFailureDetails, offset) ++ _
        )
    }

  private def renderAssertionFailureDetails(
    failureDetails: ::[AssertionValue],
    offset: Int
  ): UIO[Message] = {
    def loop(failureDetails: List[AssertionValue], rendered: Message): UIO[Message] =
      failureDetails match {
        case fragment :: whole :: failureDetails =>
          renderWhole(fragment, whole, offset).flatMap(s => loop(whole :: failureDetails, rendered :+ s))
        case _ =>
          UIO.succeedNow(rendered)
      }
    for {
      fragment <- renderFragment(failureDetails.head, offset)
      rest     <- loop(failureDetails, Message())
    } yield fragment ++ rest
  }

  private def renderGenFailureDetails[A](failureDetails: Option[GenFailureDetails], offset: Int): Message =
    failureDetails match {
      case Some(details) =>
        val shrinked = details.shrinkedInput.toString
        val initial  = details.initialInput.toString
        val renderShrinked = withOffset(offset + tabSize)(
          Fragment.plain(
            s"Test failed after ${details.iterations + 1} iteration${if (details.iterations > 0) "s" else ""} with input: "
          ) +
            Fragment.red(shrinked)
        )
        if (initial == shrinked) renderShrinked.toMessage
        else
          renderShrinked + withOffset(offset + tabSize)(
            Fragment.plain(s"Original input before shrinking was: ") + Fragment.red(initial)
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

  private def renderFragment(fragment: AssertionValue, offset: Int): UIO[Message] =
    renderSatisfied(fragment).map { satisfied =>
      val diff = maybeDiff(fragment, offset + tabSize)
      withOffset(offset + tabSize) {
        Fragment.blue(fragment.value.toString) +
          satisfied +
          Fragment.cyan(fragment.assertion.toString)
      }.toMessage ++ diff
    }

  private def maybeDiff(fragment: AssertionValue, offset: Int): Message = {
    val diffRes = for {
      diffing <- fragment.assertion.diffing
      diff    <- diffing(fragment.value)
    } yield diff

    diffRes.fold(Message())(d =>
      DiffRenderer
        .renderDiff(d.components)
        .withOffset(offset + tabSize)
    )
  }

  private def highlight(fragment: Fragment, substring: String, colorCode: String = AnsiColor.YELLOW): Line = {
    val parts = fragment.text.split(Pattern.quote(substring))
    if (parts.size == 1) fragment.toLine
    else
      parts.foldLeft(Line()) { (line, part) =>
        if (line.fragments.size < parts.size * 2 - 2)
          line + Fragment.of(part, fragment.ansiColorCode) + Fragment.of(substring, colorCode)
        else line + Fragment.of(part, fragment.ansiColorCode)
      }
  }

  private def renderSatisfied(fragment: AssertionValue): UIO[Fragment] =
    fragment.assertion.test(fragment.value).map(p => Fragment.plain(if (p) " satisfied " else " did not satisfy "))

  def renderCause(cause: Cause[Any], offset: Int): UIO[Message] =
    cause.dieOption match {
      case Some(TestTimeoutException(message)) => UIO.succeedNow(Message(message))
      case Some(exception: MockException) =>
        renderMockException(exception).map(_.map(withOffset(offset + tabSize)))
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

  private def renderMockException(exception: MockException): UIO[Message] =
    exception match {
      case InvalidArgumentsException(method, args, assertion) =>
        renderTestFailure(s"$method called with invalid arguments", assert(args)(assertion))

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
    withOffset(offset)(Fragment.plain(s"expected $method with arguments ") + Fragment.cyan(assertion.toString))

  def renderTestFailure(
    label: String,
    testResult: TestResult
  ): UIO[Message] =
    testResult.run.flatMap(
      _.fold(details =>
        renderFailure(label, 0, details)
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

  private def renderFailure(label: String, offset: Int, details: FailureDetails): UIO[Message] =
    renderFailureDetails(details, offset).map(renderFailureLabel(label, offset).prepend)

  private def renderFailureLabel(label: String, offset: Int): Line =
    withOffset(offset)(Fragment.red("- " + label).toLine)

  private def withOffset(i: Int)(line: MessageMarkup.Line): Line = line.withOffset(i)
}
