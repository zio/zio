package zio.test

import zio.test.ConsoleUtils._
import zio.test.FailureRenderer.FailureMessage.{Fragment, Message}
import zio.test.{MessageDesc => M}

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
 */
sealed trait Zoom[-In, +Out] { self =>
  def run(implicit ev: Any <:< In): (Node, Result[Out]) = run(Right(ev(())))

//  def transform(f: (Zoom.Arr[i, o] => Zoom.Arr[i, o]) forSome { type i; type o }): Zoom[In, Out] = self match {
//    case arr: Zoom.Arr[_, _]    => f(arr)
//    case Zoom.AndThen(lhs, rhs) => Zoom.AndThen(lhs, rhs.transform(f))
//  }

  def pos(start: Int, end: Int): Zoom[In, Out] =
    self match {
      case Zoom.Arr(f, renderer, _, fullCode) =>
        Zoom.Arr(f, renderer, (start, end), fullCode)
      case Zoom.AndThen(lhs, rhs) =>
        Zoom.AndThen(lhs, rhs.pos(start, end))
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
        val nodes        = results.filter(!_._2.contains(true)).map(_._1).toList
        val errorMessage = renderMessage(MessageDesc.text(s"${nodes.length} elements failed the predicate"), Right(in))
        Node(
          input = in,
          fullCode = "",
          pos = (0, 0),
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
          pos = (0, 0),
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
    pos: (Int, Int) = (0, 0),
    fullCode: String = ""
  )(implicit printer: Printer[B])
      extends Zoom[A, B] { self =>

    def withRenderer[A1 <: A](renderer0: MessageDesc[A1]): Arr[A1, B] =
      copy(renderer = Some(renderer0))

    override def run(in: Either[Throwable, A]): (Node, Result[B]) = {
      val (node, out) = f(in)
      node
        .copy(
          pos = pos,
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

  def debug(node: Node, indent: Int): Unit = {
    println(" " * indent + node.copy(fullCode = "", children = Children.Empty))
    node.children.foreach(debug(_, indent + 2))
  }
}

sealed trait Children { self =>
  def toList: List[Node] = self match {
    case Children.Empty       => List.empty
    case Children.Next(node)  => List(node)
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
  case object Empty                  extends Children
  case class Next(node: Node)        extends Children
  case class Many(nodes: List[Node]) extends Children

}

final case class Node(
  input: Any,
  fullCode: String,
  pos: (Int, Int),
  result: Any,
  errorMessage: String,
  children: Children
) {
  def clipOutput: Node = {
    val (start, _)  = pos
    val newChildren = children.transform(n => n.withCode(label).copy(pos = (n.pos._1 - start, n.pos._2 - start)))
    copy(children = newChildren)
  }

  //  def appendChild(node: Node): Node =
//    copy(children = children match {
//      case Children.Empty =>
//      case Children.Next(node) =>
//      case Children.Many(nodes) =>
//      case Nil => List(node)
//      case children => children.map(_.appendChild(node))
//    })

  def withCode(string: String): Node =
    copy(fullCode = string, children = children.transform(_.withCode(string)))

  def label: String = Try(fullCode.substring(pos._1, pos._2)).getOrElse("<code>")
}

object Node {
  def highlight(string: String, start: Int, end: Int): String =
    bold(string.take(start)) + bold(yellow(string.slice(start, end))) + bold(string.drop(end))

  def render(node: Node, acc: List[String], indent: Int, isTop: Boolean = false): List[String] = {
    val spaces = " " * indent
    node.children match {
      case Children.Empty =>
        val errorMessage =
          (node.errorMessage.linesIterator.take(1) ++ node.errorMessage.linesIterator.drop(1).map(spaces + _))
            .mkString("\n")

        s"$spaces${red("›")} $errorMessage" ::
          s"$spaces${highlight(node.fullCode, node.pos._1, node.pos._2)}" :: acc

      case Children.Next(next) =>
        val result = Option(node.result).getOrElse("null")
        render(next, s"$spaces${dim(node.label)} = ${blue(result.toString)}" :: acc, indent)

      case Children.Many(children) if isTop =>
        children.foldLeft(acc) { (acc, node) =>
          render(node, acc, indent)
        }

      case Children.Many(children0) =>
        val children = node.clipOutput.children.toList
        s"$spaces${red("›")} ${node.errorMessage}" :: highlight(node.fullCode, node.pos._1, node.pos._2) ::
          //        s"$spaces${node.label} = ${node.result}" ::
          children.foldRight(acc) { (node, acc) =>
            //            s"$spaces  > ${node.firstErrorMessage}" :: s"$spaces  ${node.result.toString}" :: acc
            render(node, acc, indent + 2)
          }
    }
  }
}

object examples {
  case class Person(name: String, age: Int)
  val person = Person("Bobo", 82)

  /**
   * > "Bobo" != "Bill"
   * person.name == "Bill"
   * .name = "Bobo"
   * person = Person("Bobo", 82)
   */
  private val ex1 = person.name == "Bill" // && (person.age == 31)

  //    final case class Node(name: String, children: List[Node])

  // person
  // "person" person
  // "person" person
  // val z1 = assert(...)
  // val z2 = assert(...)
  // val z3 = z1 && z2

//  val assertResult    = assert(bob)(hasName(equalTo("Bob")))
//  val a               = assertResult.isSuccess
//  val b               = assertResult.isSuccess
//  val newAssertResult = assertResult.negate

  //    final case class Node[A](annotations: A, children: List[Node])

  // - The full code string of the subexpression
  // - Each intermediate Result and its own code string
  // - Lens[A,B] -> (A => B, A => Message)
  // - Node -> (input: A, result: Either[Throwable, B], render: A => Message)

//  val zx1: Zoom[Any, Boolean] =
//    Zoom.succeed(person) >>> Zoom.zoom(_.name) >>> Zoom.zoom(_ == "Bill") //.label("== 'Bill'")

////  lazy val andNode = Node((), "", "&&", false, "", List(personNode, schoolNode))
////
////  lazy val personNode  = Node((), "", "person", person, "", List(nameNode))
////  lazy val nameNode    = Node(person, "", ".name", "Bob", "", List(equalToNode))
////  lazy val equalToNode = Node("Bob", "person.name == 'Bill'", " == 'Bill'", false, "Bob != Bill", List())
//
//  // school.students[.forall(_.age > 10)]
//  case class Student(age: Int)
//  case class School(students: List[Student])
//  val jimmy             = Student(12)
//  val carl              = Student(18)
//  val sam               = Student(7)
//  val grob              = Student(9)
//  val school            = School(List(jimmy, carl, sam, grob))
//  lazy val schoolNode   = Node((), "", "school", school, "", List(studentsNode))
//  lazy val studentsNode = Node(school, "", ".students", school.students, "", List(forallNode))
//  lazy val forallNode =
//    Node(
//      school.students,
//      "school.students.forall(_.age > 10)",
//      "forall",
//      false,
//      "2 elements failed",
//      List(samNode, grobNode)
//    )
//
//  lazy val samNode             = Node((), "", "_", sam, "", List(samAgeNode))
//  lazy val samAgeNode          = Node(sam, "", "_.age", sam.age, "", List(samGreaterThanNode))
//  lazy val samGreaterThanNode  = Node(sam.age, "_.age > 10", "> 10", false, "7 is not greater than 10", List())
//  lazy val grobNode            = Node((), "", "_", grob, "", List(grobAgeNode))
//  lazy val grobAgeNode         = Node(grob, "", "_.age", grob.age, "", List(grobGreaterThanNode))
//  lazy val grobGreaterThanNode = Node(grob.age, "_.age > 10", "> 10", false, "9 is not greater than 10", List())
//
//  // &&(a,b,c)
//  // Zoom[Any, Boolean]()
//
  //  @tailrec

//
//  /**
//   * - people are named certain things
//   * > ERROR: AGE ACCESS DISALLOWED
//   * school.students[.forall(_.age > 10)]
//   *   src/Interesting.scala:12
//   *
//   * > 2 elements failed the predicate
//   * school.students[.forall(_.age > 10)]
//   *   > 8 is not greater than 10
//   *   > 7 is not greater than 10
//   * .ages = List(12, 8, 18, 7)
//   * school = School(List(12, 8, 18, 7))
//   *
//   * > Bob != Bill
//   * person.name == 'Bill'
//   * .name = Bob
//   * person = Person(Bobo,82)
//   */
//
//  def main(args: Array[String]): Unit =
//    println(render(schoolNode, List.empty, 0, true).mkString("\n"))
//
//  //  println(render(andNode, List.empty).mkString("\n"))
//
//  // person.name == "Bill"
//
//  /**
//   * > "Bobo" != "Bill"
//   * person.name == "Bill"
//   * .name = "Bobo"
//   * person = Person("Bobo", 82)
//   *
//   * > 82 != 18
//   * person.age == 18
//   * .age = 82
//   * person = Person("Bobo", 82)
//   *
//   * > The predicate failed for 3 children
//   *  people [.forall(_.name == "Bill")]
//   *     > Joe != Bill
//   *     _ = Person("Joe")
//   *     > Jim != Bill
//   *     _ = Person("Joe")
//   * .people = List(...)
//   * .people = List(...)
//   */
////  Node("&&", List(Node("", List(Node("", List.empty)))))
//
//  // Translate AST into reified description
//  // Evaluate reified description and translate into result type
//  // render result type
}
