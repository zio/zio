package zio.test

import zio.Chunk
import zio.test.Arrow.Span
import zio.test.ConsoleUtils._

import scala.annotation.tailrec

sealed trait Result[+A] { self =>
  def isFailOrDie: Boolean = self match {
    case Result.Fail       => true
    case Result.Die(_)     => true
    case Result.Succeed(_) => false
  }

  def isDie: Boolean = self match {
    case Result.Die(_) => true
    case _             => false
  }

  def zipWith[B, C](that: Result[B])(f: (A, B) => C): Result[C] =
    (self, that) match {
      case (Result.Succeed(a), Result.Succeed(b)) => Result.succeed(f(a, b))
      case (Result.Fail, _)                       => Result.fail
      case (_, Result.Fail)                       => Result.fail
      case (Result.Die(err), _)                   => Result.die(err)
      case (_, Result.Die(err))                   => Result.die(err)
    }
}

object Result {
  def succeed[A](value: A): Result[A] = Succeed(value)

  def fail: Result[Nothing] = Fail

  def die(throwable: Throwable): Result[Nothing] = Die(throwable)

  case object Fail                 extends Result[Nothing]
  case class Die(err: Throwable)   extends Result[Nothing]
  case class Succeed[+A](value: A) extends Result[A]
}

case class FailureCase(
  errorMessage: String,
  codeString: String,
  location: String,
  path: Chunk[(String, Any)],
  span: Span,
  nestedFailures: Chunk[FailureCase],
  result: Any
)

object FailureCase {
  def highlight(string: String, span: Span, parentSpan: Option[Span] = None, color: String => String): String =
    parentSpan match {
      case Some(Span(pStart, pEnd)) if pStart <= span.start && pEnd >= span.end =>
        val part1 = string.take(pStart)
        val part2 = string.slice(pStart, span.start)
        val part3 = string.slice(span.start, span.end)
        val part4 = string.slice(span.end, pEnd)
        val part5 = string.drop(pEnd)
        part1 + bold(part2) + bold(color(part3)) + bold(part4) + part5
      case _ =>
        bold(string.take(span.start)) + bold(color(string.slice(span.start, span.end))) + bold(string.drop(span.end))
    }

  @tailrec
  def rightmostNode(trace: Trace[Boolean]): Trace.Node[Boolean] = trace match {
    case node: Trace.Node[Boolean] => node
    case Trace.AndThen(_, right)   => rightmostNode(right)
    case Trace.And(_, right)       => rightmostNode(right)
    case Trace.Or(_, right)        => rightmostNode(right)
    case Trace.Not(trace)          => rightmostNode(trace)
  }

  def getPath(trace: Trace[_]): Chunk[(String, Any)] =
    trace match {
      case node: Trace.Node[_] =>
        Chunk(node.code -> node.renderResult)
      case Trace.AndThen(left, right) =>
        getPath(left) ++ getPath(right)
      case _ => Chunk.empty
    }

  def fromTrace(trace: Trace[Boolean]): Chunk[FailureCase] =
    trace match {
      case node: Trace.Node[Boolean] =>
        Chunk(fromNode(node, Chunk.empty))
      case andThen @ Trace.AndThen(_, right) =>
        val node = rightmostNode(right)
        val path = getPath(andThen).reverse.drop(1)
        Chunk(fromNode(node, path))
      case Trace.And(left, right) =>
        fromTrace(left) ++ fromTrace(right)
      case Trace.Or(left, right) =>
        fromTrace(left) ++ fromTrace(right)
      case Trace.Not(trace) =>
        fromTrace(trace)
    }

  private def fromNode(node: Trace.Node[Boolean], path: Chunk[(String, Any)]): FailureCase = {
    val color = node.result match {
      case Result.Die(_) => red _
      case _             => yellow _
    }

    FailureCase(
      node.message.render(node.isSuccess),
      highlight(node.fullCode.getOrElse("<CODE>"), node.span.getOrElse(Span(0, 0)), node.parentSpan, color),
      node.location.getOrElse("<LOCATION>"),
      path,
      node.span.getOrElse(Span(0, 0)),
      node.children.map(fromTrace).getOrElse(Chunk.empty),
      node.result
    )
  }

  def renderFailureCase(failureCase: FailureCase, isNested: Boolean = false): Chunk[String] =
    failureCase match {
      case FailureCase(errorMessage, _, _, path, _, _, _) if isNested =>
        val errorMessageLines =
          Chunk.fromIterable(errorMessage.split("\n")) match {
            case head +: tail => (red("• ") + head) +: tail.map("  " + _)
            case _            => Chunk.empty
          }

        errorMessageLines ++
          Chunk.fromIterable(path.drop(path.length - 1).map { case (label, value) =>
            dim(s"$label = ") + blue(PrettyPrint(value))
          })

      case FailureCase(errorMessage, codeString, location, path, _, nested, _) =>
        val errorMessageLines =
          Chunk.fromIterable(errorMessage.split("\n")) match {
            case head +: tail => (red("• ") + head) +: tail.map("  " + _)
            case _            => Chunk.empty
          }

        errorMessageLines ++ Chunk(codeString) ++ nested.flatMap(renderFailureCase(_, true)).map("  " + _) ++
          Chunk.fromIterable(path.map { case (label, value) => dim(s"$label = ") + blue(PrettyPrint(value)) }) ++
          Chunk(cyan(s"at $location")) ++ Chunk("")

    }
}
