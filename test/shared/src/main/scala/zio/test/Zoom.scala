package zio.test

import zio.Chunk
import zio.test.Assert.Span
import zio.test.ConsoleUtils._

import scala.annotation.tailrec

/**
 * TODO:
 *  - Allow Boolean Logic
 *  - Improve forall-type nodes, have internal children.
 *  - Cross-Scala (2 & 3) combinator macros
 */

sealed trait Result[+A] { self =>
  def isDie: Boolean = self match {
    case Result.Die(_) => true
    case _             => false
  }

  def zip[B](that: Result[B]): Result[(A, B)] = zipWith(that)(_ -> _)

  def zipWith[B, C](that: Result[B])(f: (A, B) => C): Result[C] =
    (self, that) match {
      case (Result.Succeed(a), Result.Succeed(b)) => Result.succeed(f(a, b))
      case (Result.Fail, _)                       => Result.fail
      case (_, Result.Fail)                       => Result.fail
      case (Result.Die(err), _)                   => Result.die(err)
      case (_, Result.Die(err))                   => Result.die(err)
    }

  def contains[A1 >: A](a: A1): Boolean =
    self match {
      case Result.Succeed(value) if value == a => true
      case _                                   => false
    }
}

object Result {
  def succeed[A](value: A): Result[A] =
    Succeed(value)

  def fail: Result[Nothing] = Fail

  def die(throwable: Throwable): Result[Nothing] =
    Die(throwable)

  case object Fail                 extends Result[Nothing]
  case class Die(err: Throwable)   extends Result[Nothing]
  case class Succeed[+A](value: A) extends Result[A]
}

case class FailureCase(
  errorMessage: String,
  codeString: String,
  path: Chunk[(String, String)],
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
      case None =>
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

  def getPath(trace: Trace[_]): Chunk[(String, String)] =
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

  private def fromNode(node: Trace.Node[Boolean], path: Chunk[(String, String)] = Chunk.empty): FailureCase = {
    val color = node.result match {
      case Result.Die(_) => red _
      case _             => yellow _
    }

    FailureCase(
      node.message.render(node.isSuccess),
      highlight(node.fullCode.getOrElse("<CODE>"), node.span.getOrElse(Span(0, 0)), node.parentSpan, color),
      path,
      node.span.getOrElse(Span(0, 0)),
      Chunk.empty,
      node.result
    )
  }

  def renderFailureCase(failureCase: FailureCase): Chunk[String] =
    failureCase match {
      case FailureCase(_, _, _, _, _, result) if result.toString == "true" =>
        Chunk.empty
      case FailureCase(errorMessage, _, _, _, nested, _) if errorMessage == "*AND*" =>
        nested.flatMap(renderFailureCase).map("  " + _)
      case FailureCase(errorMessage, codeString, path, _, nested, _) =>
        val lines = Chunk(s"${red("›")} $errorMessage", codeString) ++
          nested.flatMap(renderFailureCase).map("  " + _) ++
          Chunk.fromIterable(path.map { case (label, value) => dim(s"$label = ") + blue(value) }) ++ Chunk("")
        lines
    }
}
