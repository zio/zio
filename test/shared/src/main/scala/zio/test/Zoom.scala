package zio.test

import zio.Chunk
import zio.test.ConsoleUtils.{blue, bold, dim, red, yellow}

import scala.language.existentials
import scala.util.Try

/**
 * TODO:
 *  - Allow Boolean Logic
 *  - Improve forall-type nodes, have internal children.
 *  - Cross-Scala (2 & 3) combinator macros
 */

sealed trait Result[+A] { self =>
  def zip[B](that: Result[B]): Result[(A, B)] = zipWith(that)(_ -> _)

  def zipWith[B, C](that: Result[B])(f: (A, B) => C): Result[C] =
    (self, that) match {
      case (Result.Succeed(a), Result.Succeed(b)) => Result.Succeed(f(a, b))
      case (Result.Halt, _)                       => Result.Halt
      case (_, Result.Halt)                       => Result.Halt
      case (Result.Fail(err), _)                  => Result.fail(err)
      case (_, Result.Fail(err))                  => Result.fail(err)
    }

  def contains[A1 >: A](a: A1): Boolean =
    self match {
      case Result.Succeed(value) if value == a => true
      case _                                   => false
    }
}

object Result {
  def attempt[A](a: => A): Result[A] =
    Try(a).toEither.fold(fail, succeed)

  def fail(throwable: Throwable): Result[Nothing] =
    Fail(throwable)

  def succeed[A](value: A): Result[A] =
    Succeed(value)

  case object Halt                 extends Result[Nothing]
  case class Fail(err: Throwable)  extends Result[Nothing]
  case class Succeed[+A](value: A) extends Result[A]
}

//final case class Node(
//  input: Any,
//  result: Any,
//  errorMessage: String,
//  children: Children,
//  fullCode: String = "",
//  span: (Int, Int) = (0, 0),
//  visible: Option[(Int, Int)] = None
//) {
//
//  def visibleCode: String =
//    visible.map { case (s, e) => fullCode.substring(s, e) }.getOrElse(fullCode)
//
//  def clip(span: (Int, Int)): Node =
//    copy(visible = visible.orElse(Some(span)), children = children.map(_.clip(span)))
//
//  def clipOutput: Node =
//    copy(children = children.map(_.clip(span)))
////    this
//  //    val (start, _) = span
////    val newChildren =
////      children.map(n => n.withCode(label).adjustSpan(-start))
////    copy(children = newChildren).withCode(label)
//
//  def withCode(string: String): Node =
//    copy(fullCode = string, children = children.map(_.withCode(string)))
//
//  def label: String =
//    Try(fullCode.substring(span._1, span._2)).getOrElse("<code>")
//}

case class FailureCase(
  errorMessage: String,
  codeString: String,
  path: Chunk[(String, String)],
  span: (Int, Int),
  nestedFailures: Chunk[FailureCase],
  result: Any
)

object FailureCase {
//  def debug(node: Trace[_]): Unit = println(FailureCase.fromNode(node))

  def highlight(string: String, span: (Int, Int)): String =
    bold(string.take(span._1)) + bold(yellow(string.slice(span._1, span._2))) + bold(string.drop(span._2))

  def renderFailureCase(failureCase: FailureCase): Chunk[String] =
//    println("FAIL")
//    println(failureCase.result)
    failureCase match {
      case FailureCase(_, _, _, _, _, result) if result.toString == "true" =>
        Chunk.empty
      case FailureCase(errorMessage, _, _, _, nested, _) if errorMessage == "*AND*" =>
        nested.flatMap(renderFailureCase).map("  " + _)
      case FailureCase(errorMessage, codeString, path, span, nested, _) =>
        val lines = Chunk(s"${red("›")} $errorMessage", codeString) ++
          nested.flatMap(renderFailureCase).map("  " + _) ++
          Chunk.fromIterable(path.map { case (label, value) => dim(s"$label = ") + blue(value) }) ++ Chunk("")
        lines
    }

  def render(node: Trace[_], acc: Chunk[String], indent: Int, isTop: Boolean = false): Chunk[String] =
    ???
//    node.children match {
//      case Children.Many(nodes) if isTop =>
//        nodes.map(FailureCase.fromNode).flatMap(renderFailureCase)
//      case _ =>
//        renderFailureCase(FailureCase.fromNode(node))
//    }
}

//trait Printer[-A] {
//  def apply(a: A): String
//}
//
//object Printer extends LowPriPrinter1 {
//  implicit val stringPrinter[String] =
//  (a: String) => '"' + a + '"'
//}
//
//trait LowPriPrinter1 extends LowPriPrinter0 {
//  implicit def optionPrinter[A](implicit printer[A])[Option[A]] = {
//    case Some(value) => s"Some(${printer(value)})"
//    case None        => "None"
//  }
//
//  implicit def listPrinter[A](implicit printer[A])[List[A]] = list =>
//    "List(" + list.map(printer(_)).mkString(", ") + ")"
//}
//
//trait LowPriPrinter0 {
//  implicit def anyPrinter[A][A] = new Printer[A] {
//    override def apply(a: A): String = a.toString
//  }
//}
