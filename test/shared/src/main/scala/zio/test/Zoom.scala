package zio.test

import zio.Chunk
import zio.test.ConsoleUtils._
import zio.test.FailureRenderer.FailureMessage.{Fragment, Message}
import zio.test.Node.highlight
import zio.test.{MessageDesc => M}

import scala.annotation.tailrec
import scala.io.AnsiColor
import scala.language.existentials
import scala.reflect.ClassTag
import scala.util.Try

/**
 * TODO:
 *  - Allow Boolean Logic
 *  - Improve forall-type nodes, have internal children.
 *  - Cross-Scala (2 & 3) combinator macros
 */

sealed trait Zoom[-In, +Out] { self =>
  private[test] def run(in: Either[Throwable, In]): (Node, Result[Out])

  def run(implicit ev: Any <:< In): (Node, Result[Out]) = run(Right(ev(())))

  def span(span0: (Int, Int)): Zoom[In, Out] =
    self match {
      case Zoom.Arr(f, renderer, _, fullCode) =>
        Zoom.Arr(f, renderer, span0, fullCode)
      case Zoom.AndThen(lhs, rhs) =>
        Zoom.AndThen(lhs, rhs.span(span0))
      case suspend: Zoom.Suspend[_, _] =>
        suspend
    }

  def withCode(code: String): Zoom[In, Out] = self match {
    case arr @ Zoom.Arr(_, _, _, _) =>
      arr.copy(fullCode = code)
    case Zoom.AndThen(lhs, rhs) =>
      Zoom.AndThen(lhs.withCode(code), rhs.withCode(code))
    case Zoom.Suspend(f, _) =>
      Zoom.Suspend(f, code)
    case zoom: Zoom.And =>
      zoom.copy(lhs = zoom.lhs.withCode(code), rhs = zoom.rhs.withCode(code)).asInstanceOf[Zoom[In, Out]]
    case zoom: Zoom.Not =>
      zoom.copy(zoom = zoom.zoom.withCode(code)).asInstanceOf[Zoom[In, Out]]
  }

  def andThen[Out2](that: Zoom[Out, Out2]): Zoom[In, Out2] =
    self >>> that

  def >>>[Out2](that: Zoom[Out, Out2]): Zoom[In, Out2] =
    self match {
      case Zoom.AndThen(lhs, rhs) => Zoom.AndThen(lhs, rhs >>> that)
      case _                      => Zoom.AndThen(self, that)
    }
}

sealed trait Result[+A] { self =>
  def <*>[B](that: Result[B]): Result[(A, B)] =
    (self, that) match {
      case (Result.Succeed(l), Result.Succeed(r)) => Result.Succeed(l -> r)
      case (Result.Halt, _)                       => Result.Halt
      case (_, Result.Halt)                       => Result.Halt
      case (Result.Fail(err), _)                  => Result.fail(err)
      case (_, Result.Fail(err))                  => Result.fail(err)
    }

  def foldString(fail: Throwable => String, success: A => String): String =
    self match {
      case Result.Succeed(value) => success(value)
      case Result.Fail(err)      => fail(err)
      case Result.Halt           => fail(new Error("STOPPED"))
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

  object ToEither {
    def unapply[A](value: Result[A]): Option[Either[Throwable, A]] = value match {
      case Halt           => None
      case Fail(err)      => Some(Left(err))
      case Succeed(value) => Some(Right(value))
    }
  }
}

object Zoom {

  def throwsSubtype[E <: Throwable](implicit
    classTag: ClassTag[E]
  ): Zoom[Any, Boolean] =
    zoomResult[Any, Boolean](
      {
        case Left(throwable) if classTag.unapply(throwable).isDefined =>
          Result.succeed(true)
        case _ => Result.succeed(false)
      },
      M.switch(
        e => M.value(e) + M.was + M.text("a subtype of ") + M.value(classTag.toString()),
        a => M.value(a) + M.did + M.text("throw")
      )
    )

  def throwsError: Zoom[Any, Throwable] =
    zoomResult(
      {
        case Left(err) => Result.succeed(err)
        case _         => Result.Halt
      },
      MessageDesc.text("Did not throw")
    )

  def isSome[A]: Zoom[Option[A], A] =
    zoomResult(
      {
        case Right(Some(a)) => Result.succeed(a)
        case Left(e)        => Result.fail(e)
        case _              => Result.Halt
      },
      M.choice("was Some", "was None")
    )

  // forall >>> (=== false)
  def forall[A](f: Zoom[A, Boolean]): Zoom[Iterable[A], Boolean] =
    Arr(
      { case Right(in) =>
        val results = in.map(a => f.run(Right(a)))
        val bool    = results.forall(_._2.contains(true))
        val nodes   = Chunk.fromIterable(results.filter(!_._2.contains(true)).map(_._1))
        val errorMessage =
          renderMessage(M.value(nodes.length) + M.text("elements failed the predicate"), Right(in))
        Node(
          input = in,
          result = bool,
          errorMessage = errorMessage,
          children = Children.Many(nodes)
        ) -> Result.succeed(bool)
      }
    )

  def zoom[A, B](f: A => B): Arr[A, B] = {
    def fr(r: Either[Throwable, A]): Result[B] = r match {
      case Right(value) => Result.attempt(f(value))
      case _            => Result.Halt
    }

    zoomResult(fr)
  }

  def zoomEither[A, B](f: Either[Throwable, A] => B): Arr[A, B] = {
    def fr(r: Either[Throwable, A]): Result[B] = r match {
      case Left(err)    => Result.attempt(f(Left(err)))
      case Right(value) => Result.attempt(f(Right(value)))
    }

    zoomResult(fr)
  }

  def succeed[A](a: => A): Zoom[Any, A] = zoom(_ => a)

  def equalTo[A](value: A): Zoom[A, Boolean] =
    zoom[A, Boolean](in => in == value)
      .withRenderer(M.result((a: A) => a.toString) + M.choice("==", "!=") + M.value(value))

  private def printStack(e: Throwable): MessageDesc[Any] =
    M.text(
      (e.toString +: e.getStackTrace.toIndexedSeq.takeWhile(!_.getClassName.startsWith("zio.test.Zoom"))).mkString("\n")
    )

  def zoomResult[A, B](
    f: Either[Throwable, A] => Result[B],
    renderer: MessageDesc[A] = M.switch[A](e => printStack(e), a => M.text("Failed with input:") + M.value(a))
  ): Arr[A, B] = {
    val fr = { (in: Either[Throwable, A]) =>
      val out = f(in)
      (
        Node(
          input = in,
          fullCode = "",
          span = (0, 0),
          result = out,
          errorMessage = "",
          children = Children.Empty
        ),
        out
      )
    }
    Arr(fr, Some(renderer))
  }

  def suspend[A, B](f: A => Zoom[Any, B]): Zoom[A, B] = Suspend(f)

  case class Suspend[A, B](f: A => Zoom[Any, B], code: String = "") extends Zoom[A, B] {
    override def run(in: Either[Throwable, A]): (Node, Result[B]) =
      in match {
        case Left(value)  => throw new Error(value)
        case Right(value) => f(value).withCode(code).run
      }
  }

  case class Arr[-A, +B](
    f: Either[Throwable, A] => (Node, Result[B]),
    renderer: Option[MessageDesc[A]] = None,
    span: (Int, Int) = (0, 0),
    fullCode: String = ""
  ) extends Zoom[A, B] { self =>

    def withRenderer[A1 <: A](renderer0: MessageDesc[A1]): Arr[A1, B] =
      copy(renderer = Some(renderer0))

    override def run(in: Either[Throwable, A]): (Node, Result[B]) = {
      val (node, out) = f(in)
      node
        .copy(
          span = span,
          result = out.foldString(_.toString, _.toString),
          errorMessage = renderer.map(r => renderMessage(r, in)).getOrElse(node.errorMessage)
        )
        .withCode(fullCode) -> out
    }
  }

//  zio.either
//  Fold(self, handler)
//  zio >>> throws >>> getMessage >>> length >>> (> 10)
//  AndThen(zio, throws)
//  AndThen(1, AndThen(2, AndThen(3, AndThen(4, 5))))
//  Either(1, AndThen(2, AndThen(3, AndThen(4, 5))))

//  zio >>> forall >>> getMessage >>> length >>> (> 10)
//          | | |
//

// forall
// Arrow[In, Out]
//    Tree[Boolean](children: Tree[Boolean], next: None)

// Fold(arr, left)
  //
  case class Tree[A](result: Result[A], fork: Tree[A], next: Option[Tree[A]])

//case class Res[A](value: Either[Throwable, A], )
//  zio >> (> 10)
// foreach.fold(...)
// ->  ->
//    |||

// next: Option[Tree]
// internal: List[Tree]
//  forall
//   Result(true)
  //   - Result(true)
  //   - Result(false)
  //   - Result(false)
  //   - Result(true)
  //   - Result(true)
  // AndThen(AndThen(lhs, rhs), rhs)
  case class AndThen[A, B, C](lhs: Zoom[A, B], rhs: Zoom[B, C]) extends Zoom[A, C] { self =>
    override def run(in: Either[Throwable, A]): (Node, Result[C]) = {
      val (nodeB, result) = lhs.run(in)
      result match {
        case Result.Halt => nodeB -> Result.Halt
        case Result.ToEither(either) =>
          val (nodeC, c) = rhs.run(either)
          val node       = nodeB.copy(children = Children.Next(nodeC))
          (node, c)
      }
    }
  }

  private def renderToString(message: Message): String = {
    def renderFragment(f: Fragment) =
      if (f.ansiColorCode.nonEmpty) f.ansiColorCode + f.text + AnsiColor.RESET
      else f.text

    message.lines.map { line =>
      " " * line.offset + line.fragments.foldLeft("")((str, f) => str + renderFragment(f))
    }.mkString("\n")
  }

  private def renderMessage[A](message: MessageDesc[A], either: Either[Throwable, A]) =
    renderToString(message.render(either, isSuccess = false))

  def not(
    zoom: Zoom[Any, Boolean],
    span0: (Int, Int),
    innerSpan: (Int, Int)
  ): Zoom[Any, Boolean] =
    Zoom.Not(zoom, span0, innerSpan)

  def and(
    lhs: Zoom[Any, Boolean],
    rhs: Zoom[Any, Boolean],
    span0: (Int, Int),
    ls: (Int, Int),
    rs: (Int, Int)
  ): Zoom[Any, Boolean] =
    And(lhs, rhs, span0, ls, rs, _ && _)

  def or(
    lhs: Zoom[Any, Boolean],
    rhs: Zoom[Any, Boolean],
    span0: (Int, Int),
    ls: (Int, Int),
    rs: (Int, Int)
  ): Zoom[Any, Boolean] =
    And(lhs, rhs, span0, ls, rs, _ || _)

  case class Not(zoom: Zoom[Any, Boolean], span0: (Int, Int), innerSpan: (Int, Int)) extends Zoom[Any, Boolean] {
    override def run(in: Either[Throwable, Any]): (Node, Result[Boolean]) = {
      val (node, bool) = zoom.run

      val result = bool match {
        case Result.Succeed(value) => Result.succeed(!value)
        case other                 => other
      }

      node -> result
    }
  }

  case class And(
    lhs: Zoom[Any, Boolean],
    rhs: Zoom[Any, Boolean],
    span: (Int, Int),
    leftSpan: (Int, Int),
    rightSpan: (Int, Int),
    op: (Boolean, Boolean) => Boolean,
    fullCode: String = ""
  ) extends Zoom[Any, Boolean] {

    override def run(in: Either[Throwable, Any]): (Node, Result[Boolean]) = {
      val (node1, leftBool)  = lhs.run
      val (node2, rightBool) = rhs.run
      import Result._

      val result = (leftBool, rightBool) match {
        case (Succeed(l), Succeed(r)) => Succeed(op(l, r))
        case (Halt, _)                => Halt
        case (_, Halt)                => Halt
        case (Fail(err), _)           => Fail(err)
        case (_, Fail(err))           => Fail(err)
      }

      val newChildren = (lhs, rhs) match {
        case (_: And | _: Not, _: And | _: Not) => node1.children ++ node2.children
        case (_: And | _: Not, _)               => node1.children ++ Children.Next(node2.clip(rightSpan))
        case (_, _: And | _: Not)               => Children.Next(node1.clip(leftSpan)) ++ node2.children
        case (_, _)                             => Children.Many(Chunk(node1.clip(leftSpan), node2.clip(rightSpan)))
      }

      val label = if (op(true, false)) "OR" else "AND"

      Node(
        label,
        result,
        errorMessage = "*AND*",
        children = newChildren,
        span = leftSpan._1 -> span._2,
        fullCode = node1.fullCode
      ) -> result
    }
  }

}

sealed trait Children { self =>
  def map(f: Node => Node): Children = self match {
    case Children.Empty       => Children.Empty
    case Children.Next(node)  => Children.Next(f(node))
    case Children.Many(nodes) => Children.Many(nodes.map(f))
  }

  import Children._
  def ++(that: Children): Children =
    Many(self.toChunk ++ that.toChunk)

  def toChunk: Chunk[Node] = self match {
    case Children.Empty       => Chunk.empty
    case Children.Next(node)  => Chunk(node)
    case Children.Many(nodes) => nodes
  }

  def foreach(f: Node => Unit): Unit = self match {
    case Children.Empty => ()
    case Children.Next(node) =>
      f(node)
      node.children.foreach(f)
    case Children.Many(nodes) =>
      nodes.foreach { node =>
        f(node)
        node.children.foreach(f)
      }
  }

  def transform(f: Node => Node): Children = self match {
    case Children.Empty =>
      Children.Empty
    case Children.Next(node) =>
      Children.Next(f(node).copy(children = node.children.transform(f)))
    case Children.Many(nodes) =>
      val newNodes = nodes.map(node => f(node).copy(children = node.children.transform(f)))
      Children.Many(newNodes)
  }
}

object Children {
  case object Empty                   extends Children
  case class Next(node: Node)         extends Children
  case class Many(nodes: Chunk[Node]) extends Children
}

final case class Node(
  input: Any,
  result: Any,
  errorMessage: String,
  children: Children,
  fullCode: String = "",
  span: (Int, Int) = (0, 0),
  visible: Option[(Int, Int)] = None
) {

  def visibleCode: String =
    visible.map { case (s, e) => fullCode.substring(s, e) }.getOrElse(fullCode)

  def clip(span: (Int, Int)): Node =
    copy(visible = visible.orElse(Some(span)), children = children.map(_.clip(span)))

  def clipOutput: Node =
    copy(children = children.map(_.clip(span)))
//    this
  //    val (start, _) = span
//    val newChildren =
//      children.map(n => n.withCode(label).adjustSpan(-start))
//    copy(children = newChildren).withCode(label)

  def withCode(string: String): Node =
    copy(fullCode = string, children = children.map(_.withCode(string)))

  def label: String =
    Try(fullCode.substring(span._1, span._2)).getOrElse("<code>")
}

case class FailureCase(
  errorMessage: String,
  codeString: String,
  path: Chunk[(String, String)],
  span: (Int, Int),
  nestedFailures: Chunk[FailureCase],
  result: Any
)

object FailureCase {
  def getPath(node: Node): Chunk[(String, String)] =
    (node.children match {
      case Children.Empty      => Chunk.empty
      case Children.Next(next) => (node.label, node.result.toString) +: getPath(next)
      case Children.Many(_)    => Chunk.empty
    }).reverse

  @tailrec
  def getLastNode(node: Node): Node =
    node.children match {
      case Children.Next(node) => getLastNode(node)
      case _                   => node
    }

  def fromNode(node: Node): FailureCase = {
    val lastNode = getLastNode(node)
    val highlightedCode = highlight(
      node.visibleCode,
      node.visible.map { case (s, e) =>
        (lastNode.span._1 - s, lastNode.span._2 - s)
      }.getOrElse(lastNode.span)
    )
    FailureCase(
      lastNode.errorMessage,
      highlightedCode,
      getPath(node),
      lastNode.span,
      lastNode.clipOutput.children.toChunk.map(fromNode),
      lastNode.result
    )
  }
}

object Node {
  def debug(node: Node): Unit = println(FailureCase.fromNode(node))

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

  def render(node: Node, acc: Chunk[String], indent: Int, isTop: Boolean = false): Chunk[String] =
    node.children match {
      case Children.Many(nodes) if isTop =>
        nodes.map(FailureCase.fromNode).flatMap(renderFailureCase)
      case _ =>
        renderFailureCase(FailureCase.fromNode(node))
    }
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
