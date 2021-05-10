package zio.test

import zio.Chunk
import zio.test.ConsoleUtils._
import zio.test.FailureRenderer.FailureMessage.{Fragment, Message}
import zio.test.{MessageDesc => M}

import scala.annotation.tailrec
import scala.io.AnsiColor
import scala.language.existentials
import scala.reflect.ClassTag
import scala.util.Try

// Result >>> (Result && Result)

trait Printer[-A] {
  def apply(a: A): String
}

object Printer extends LowPriPrinter1 {
  implicit val stringPrinter: Printer[String] =
    (a: String) => '"' + a + '"'
}

trait LowPriPrinter1 extends LowPriPrinter0 {
  implicit def optionPrinter[A](implicit printer: Printer[A]): Printer[Option[A]] = {
    case Some(value) => s"Some(${printer(value)})"
    case None        => "None"
  }

  implicit def listPrinter[A](implicit printer: Printer[A]): Printer[List[A]] = list =>
    "List(" + list.map(printer(_)).mkString(", ") + ")"
}

trait LowPriPrinter0 {
  implicit def anyPrinter[A]: Printer[A] = new Printer[A] {
    override def apply(a: A): String = a.toString
  }
}

/**
 * TODO:
 *  - Allow Boolean Logic
 *  - Improve forall-type nodes, have internal children.
 *  - Cross-Scala (2 & 3) combinator macros
 */
sealed trait Zoom[-In, +Out] { self =>
  def run(implicit ev: Any <:< In): (Node, Result[Out]) = run(Right(ev(())))

//  def transform(f: (Zoom.Arr[i, o] => Zoom.Arr[i, o]) forSome { type i; type o }): Zoom[In, Out] = self match {
//    case arr: Zoom.Arr[_, _]    => f(arr)
//    case Zoom.AndThen(lhs, rhs) => Zoom.AndThen(lhs, rhs.transform(f))
//  }

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

  def run(in: Either[Throwable, In]): (Node, Result[Out])
}

sealed trait Result[+A] { self =>
  def fold(identity: Throwable => String, toString: A => String): String =
    self match {
      case Result.Succeed(value) => toString(value)
      case Result.Fail(err)      => identity(err)
      case Result.Stop           => identity(new Error("STOPPED"))
    }

  def contains[A1 >: A](a: A1): Boolean =
    self match {
      case Result.Succeed(value) if value == a => true
      case _                                   => false
    }

//  def flatMap[B](f: A => Result[B]): Result[B] = either match {
//    case Left(e)      => Result(Left(e))
//    case Right(value) => f(value)
//  }
}

object Result {
  def attempt[A](a: => A): Result[A] =
    Try(a).toEither.fold(fail, succeed)

  def fromEither[A](either: Either[Throwable, A]): Result[A] =
    either.fold(fail, succeed)

  case object Stop                 extends Result[Nothing]
  case class Fail(err: Throwable)  extends Result[Nothing]
  case class Succeed[+A](value: A) extends Result[A]

  def fail(throwable: Throwable): Result[Nothing] =
    Fail(throwable)

  def succeed[A](value: A): Result[A] =
    Succeed(value)

  object ToEither {
    def unapply[A](value: Result[A]): Option[Either[Throwable, A]] = value match {
      case Stop           => None
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
        case _         => Result.Stop
      },
      MessageDesc.text("Did not throw")
    )

  def isSome[A]: Zoom[Option[A], A] =
    zoomResult(
      {
        case Right(Some(a)) => Result.succeed(a)
        case Left(e)        => Result.fail(e)
        case _              => Result.Stop
      },
      M.choice("was Some", "was None")
    )

  def forall[A](f: Zoom[A, Boolean]): Zoom[Iterable[A], Boolean] =
    Arr(
      { case Right(in) =>
        val results      = in.map(a => f.run(Right(a)))
        val bool         = results.forall(_._2.contains(true))
        val nodes        = Chunk.fromIterable(results.filter(!_._2.contains(true)).map(_._1))
        val errorMessage = renderMessage(MessageDesc.text(s"${nodes.length} elements failed the predicate"), Right(in))
        Node(
          input = in,
          result = bool,
          errorMessage = errorMessage,
          children = Children.Many(nodes)
        ) -> Result.succeed(bool)
      }
    )

  def main(args: Array[String]): Unit =
    println("HOWDY")

  def zoom[A, B: Printer](f: A => B): Arr[A, B] = {
    def fr(r: Either[Throwable, A]): Result[B] = r match {
      case Right(value) => Result.attempt(f(value))
      case _            => Result.Stop
    }

    zoomResult(fr)
  }

  def zoomEither[A, B: Printer](f: Either[Throwable, A] => B): Arr[A, B] = {
    def fr(r: Either[Throwable, A]): Result[B] = r match {
      case Left(err)    => Result.attempt(f(Left(err)))
      case Right(value) => Result.attempt(f(Right(value)))
    }

    zoomResult(fr)
  }

  def succeed[A: Printer](a: => A): Zoom[Any, A] = zoom(_ => a)

  def equalTo[A](value: A)(implicit printer: Printer[A]): Zoom[A, Boolean] =
    zoom[A, Boolean](in => in == value)
      .withRenderer(M.result((a: A) => printer(a)) + M.choice("==", "!=") + M.value(printer(value)))

  private def printStack(e: Throwable): MessageDesc[Any] =
    M.text(
      (e.toString +: e.getStackTrace.toIndexedSeq.takeWhile(!_.getClassName.startsWith("zio.test.Zoom"))).mkString("\n")
    )

  def zoomResult[A, B: Printer](
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
  )(implicit printer: Printer[B])
      extends Zoom[A, B] { self =>

    def withRenderer[A1 <: A](renderer0: MessageDesc[A1]): Arr[A1, B] =
      copy(renderer = Some(renderer0))

    override def run(in: Either[Throwable, A]): (Node, Result[B]) = {
      val (node, out) = f(in)
      node
        .copy(
          span = span,
          result = out.fold(_.toString, printer(_)),
          errorMessage = renderer.map(r => renderMessage(r, in)).getOrElse(node.errorMessage)
        )
        .withCode(fullCode) -> out
    }
  }

  case class AndThen[A, B, C](lhs: Zoom[A, B], rhs: Zoom[B, C]) extends Zoom[A, C] { self =>
    override def run(in: Either[Throwable, A]): (Node, Result[C]) = {
      val (nodeB, result) = lhs.run(in)
      result match {
        case Result.Stop => nodeB -> Result.Stop
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
    op: (Boolean, Boolean) => Boolean
  ) extends Zoom[Any, Boolean] {

    override def run(in: Either[Throwable, Any]): (Node, Result[Boolean]) = {
      val (node1, leftBool)  = lhs.run
      val (node2, rightBool) = rhs.run
      import Result._

      val result = (leftBool, rightBool) match {
        case (Succeed(l), Succeed(r)) => Succeed(op(l, r))
        case (Stop, _)                => Stop
        case (_, Stop)                => Stop
        case (Fail(err), _)           => Fail(err)
        case (_, Fail(err))           => Fail(err)
      }

      val leftCode  = Try(node1.fullCode.substring(leftSpan._1, leftSpan._2)).getOrElse(node1.fullCode)
      val rightCode = Try(node2.fullCode.substring(rightSpan._1, rightSpan._2)).getOrElse(node2.fullCode)
      println(leftCode, leftSpan, rightSpan)
      println(rightCode, leftSpan, rightSpan)

      val newChildren = (lhs, rhs) match {
        case (_: And, _: And) => node1.children ++ node2.children
        case (_: And, _)      => node1.children ++ Children.Next(node2.clip(rightSpan))
        case (_, _: And)      => Children.Next(node1.clip(leftSpan)) ++ node2.children
        case (_, _)           => Children.Many(Chunk(node1.clip(leftSpan), node2.clip(rightSpan)))
      }

      println(node1.label)
      Node(
        if (op(true, false)) "OR" else "AND",
        result,
        errorMessage = "",
        children = newChildren,
        span = span
      ) -> result
    }
  }

}

sealed trait Children { self =>
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
  span: (Int, Int) = (0, 0)
) {

  def adjustSpan(amt: Int): Node =
    copy(span = (span._1 + amt, span._2 + amt))
      .copy(children = children.transform(_.adjustSpan(amt)))

  def clip(span: (Int, Int)): Node =
    withCode(fullCode.substring(span._1, span._2)).adjustSpan(-span._1)

  def clipOutput: Node = {
    val (start, _)  = span
    val newChildren = children.transform(n => n.withCode(label).copy(span = (n.span._1 - start, n.span._2 - start)))
    copy(children = newChildren)
  }

  def withCode(string: String): Node =
    copy(fullCode = string, children = children.transform(_.withCode(string)))

  def label: String =
    Try(fullCode.substring(span._1, span._2)).getOrElse("<code>")
}

case class FailureCase( //
  errorMessage: String,
  codeString: String,
  path: Chunk[(String, String)],
  span: (Int, Int),
  nestedFailures: Chunk[FailureCase]
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
    FailureCase(
      lastNode.errorMessage,
      lastNode.fullCode,
      getPath(node),
      lastNode.span,
      lastNode.clipOutput.children.toChunk.map(fromNode)
    )
  }
}

object Node {
  def debug(node: Node, indent: Int): Unit =
    println(node.children.toChunk.map(n => FailureCase.fromNode(n)).mkString("\n"))
//    println(FailureCase.fromNode(node))
  //    println((" " * indent) + node.label + " " + node.copy(children = Children.Empty))
//    node.children.toList.foreach(debug(_, indent + 2))

  def highlight(string: String, span: (Int, Int)): String =
    bold(string.take(span._1)) + bold(yellow(string.slice(span._1, span._2))) + bold(string.drop(span._2))

  def renderFailureCase(failureCase: FailureCase, indent: Int): Chunk[String] = failureCase match {
    case FailureCase(errorMessage, codeString, path, span, nested) =>
      val lines = Chunk(s"${red("›")} $errorMessage", highlight(codeString, span)) ++
        nested.flatMap(renderFailureCase(_, indent + 2)) ++
        Chunk.fromIterable(path.map { case (label, value) => dim(s"$label = ") + blue(value) }) ++ Chunk("")
      lines.map(" " * 2 + _)
  }

  def render(node: Node, acc: Chunk[String], indent: Int, isTop: Boolean = false): Chunk[String] =
    node.children match {
      case Children.Many(nodes) if isTop =>
        nodes.map(FailureCase.fromNode).flatMap(renderFailureCase(_, indent))
      case _ =>
        renderFailureCase(FailureCase.fromNode(node), indent)
    }

  def rend(node: Node, acc: Chunk[String], indent: Int, isTop: Boolean = false): Chunk[String] = {
    val spaces = " " * indent
    node.children match {
      case Children.Empty =>
        val errorMessage =
          (node.errorMessage.linesIterator.take(1) ++ node.errorMessage.linesIterator.drop(1).map(spaces + _))
            .mkString("\n")

        s"$spaces${red("›")} $errorMessage" +:
          s"$spaces${highlight(node.fullCode, node.span)}" +: acc

      case Children.Next(next) =>
        val result = Option(node.result).getOrElse("null")
        rend(next, s"$spaces${dim(node.label)} = ${blue(result.toString)}" +: acc, indent)

      case Children.Many(children) if isTop =>
        println(s"TOP ${children.map(_.label)}")
//        val children = children0.map(_.clipOutput)
        children
          .foldLeft(acc) { (acc, node) =>
            rend(node, acc, indent)
          }

      case Children.Many(children0) =>
        val children = node.clipOutput.children.toChunk
        s"$spaces${red("›")} ${node.errorMessage}" +: highlight(node.fullCode, node.span) +:
          children.foldRight(acc) { (node, acc) =>
            rend(node, acc, indent + 2)
          }
    }
  }
}
