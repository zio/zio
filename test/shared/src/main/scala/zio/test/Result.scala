package zio.test

import zio.Chunk
import zio.internal.ansi.AnsiStringOps
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.TestArrow.Span
import zio.test.ConsoleUtils._
import zio.test.render.LogLine.Message

import scala.annotation.tailrec

/**
 * The outcome of one step of an assertion: `Succeed` with a value, `Fail` when
 * the step did not hold, or `Die` when it threw an exception.
 */
sealed trait Result[+A] { self =>

  /**
   * Returns `true` for `Fail` and `Die`.
   */
  def isFailOrDie: Boolean = self match {
    case Result.Fail       => true
    case Result.Die(_)     => true
    case Result.Succeed(_) => false
  }

  /**
   * Returns `true` for `Die`.
   */
  def isDie: Boolean = self match {
    case Result.Die(_) => true
    case _             => false
  }

  /**
   * Combines two `Succeed` values with `f`. Otherwise the combination is `Fail`
   * when either side is `Fail`, and `Die` when neither side is `Fail` and one
   * is `Die`.
   */
  def zipWith[B, C](that: Result[B])(f: (A, B) => C): Result[C] =
    (self, that) match {
      case (Result.Succeed(a), Result.Succeed(b)) => Result.succeed(f(a, b))
      case (Result.Fail, _)                       => Result.fail
      case (_, Result.Fail)                       => Result.fail
      case (Result.Die(err), _)                   => Result.die(err)
      case (_, Result.Die(err))                   => Result.die(err)
    }
}

/**
 * Constructors and cases of [[Result]].
 */
object Result {

  /**
   * A successful result holding `value`.
   */
  def succeed[A](value: A): Result[A] = Succeed(value)

  /**
   * A failed result.
   */
  def fail: Result[Nothing] = Fail

  /**
   * A result for a step that threw `throwable`.
   */
  def die(throwable: Throwable): Result[Nothing] = Die(throwable)

  /**
   * The result of a step that did not hold.
   */
  case object Fail extends Result[Nothing]

  /**
   * The result of a step that threw `err`.
   */
  case class Die(err: Throwable) extends Result[Nothing]

  /**
   * The result of a step that produced `value`.
   */
  case class Succeed[+A](value: A) extends Result[A]
}

/**
 * One failure of an assertion, collected from its trace for the test report:
 * the message, the code that failed and where it is, the steps that led to it,
 * nested failures, the result and the custom label.
 */
case class FailureCase(
  errorMessage: Message,
  codeString: String,
  location: String,
  path: Chunk[(String, String)],
  span: Span,
  nestedFailures: Chunk[FailureCase],
  result: Any,
  customLabel: Option[String]
)

/**
 * Builds [[FailureCase]] values from a [[TestTrace]].
 */
object FailureCase {

  /**
   * Formats `string` for the test report, marking the part in `span`, and the
   * enclosing part in `parentSpan` when it contains `span`.
   */
  def highlight(
    string: String,
    span: Span,
    parentSpan: Option[Span] = None,
    color: String => String,
    normalColor: String => String = identity
  ): String =
    parentSpan match {
      case Some(Span(pStart, pEnd)) if pStart <= span.start && pEnd >= span.end =>
        val part1 = string.take(pStart)
        val part2 = string.slice(pStart, span.start)
        val part3 = string.slice(span.start, span.end)
        val part4 = string.slice(span.end, pEnd)
        val part5 = string.drop(pEnd)
        part1 + bold(part2) + bold(color(part3)) + bold(part4) + part5
      case _ =>
        bold(normalColor(string.take(span.start))) + bold(color(string.slice(span.start, span.end))) + bold(
          normalColor(string.drop(span.end))
        )
    }

  /**
   * Returns the last node of `trace`, following the right side of `>>>`, `&&`
   * and `||`.
   */
  @tailrec
  def rightmostNode(trace: TestTrace[Boolean]): TestTrace.Node[Boolean] = trace match {
    case node: TestTrace.Node[Boolean] => node
    case TestTrace.AndThen(_, right)   => rightmostNode(right)
    case TestTrace.And(_, right)       => rightmostNode(right)
    case TestTrace.Or(_, right)        => rightmostNode(right)
    case TestTrace.Not(trace)          => rightmostNode(trace)
  }

  /**
   * Returns the code and the value of each node along the `>>>` chain of
   * `trace`.
   */
  def getPath(trace: TestTrace[_]): Chunk[(String, String)] =
    trace match {
      case node: TestTrace.Node[_] =>
        Chunk(node.code -> PrettyPrint(node.renderResult))
      case TestTrace.AndThen(left, right) =>
        getPath(left) ++ getPath(right)
      case _ => Chunk.empty
    }

  /**
   * Returns the failure cases described by `trace`, one for each node at the
   * end of a `>>>` chain in its `&&`, `||` and `!` tree. `path` holds the steps
   * that led to `trace`.
   */
  def fromTrace(trace: TestTrace[Boolean], path: Chunk[(String, String)]): Chunk[FailureCase] =
    trace match {
      case node: TestTrace.Node[Boolean] =>
        Chunk(fromNode(node, path.reverse))
      case TestTrace.AndThen(left, right) =>
        fromTrace(right, path ++ getPath(left))
      case TestTrace.And(left, right) =>
        fromTrace(left, path) ++ fromTrace(right, path)
      case TestTrace.Or(left, right) =>
        fromTrace(left, path) ++ fromTrace(right, path)
      case TestTrace.Not(trace) =>
        fromTrace(trace, path)
    }

  private def fromNode(node: TestTrace.Node[Boolean], path: Chunk[(String, String)]): FailureCase = {
    val color = node.result match {
      case Result.Die(_) => red _
      case _             => yellow _
    }

    FailureCase(
      errorMessage = node.message.render(node.isSuccess),
      codeString = {
        node.completeCode match {
          case Some(completeCode) =>
            val idx = completeCode.indexOf(node.code)
            if (idx >= 0 && node.code.nonEmpty) {
              highlight(completeCode, Span(idx, idx + node.code.length), None, color, (_: String).cyan)
            } else {
              completeCode
            }
          case None =>
            highlight(node.fullCode.getOrElse("<CODE>"), node.span.getOrElse(Span(0, 0)), node.parentSpan, color)
        }
      },
      location = node.location.getOrElse("<LOCATION>"),
      path = path,
      span = node.span.getOrElse(Span(0, 0)),
      nestedFailures = node.children.map(fromTrace(_, Chunk.empty)).getOrElse(Chunk.empty),
      result = node.result,
      customLabel = node.customLabel
    )
  }
}
