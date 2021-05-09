package zio.test

import zio.duration
import zio.test.ConsoleUtils.{blue, bold, dim, red, yellow}
import zio.test.FailureRenderer.FailureMessage.{Fragment, Message}
import zio.test.examples.{Node, render}
import zio.test.{MessageDesc => M}

import scala.annotation.tailrec
import scala.io.AnsiColor

// Result >>> (Result && Result)

trait Printer[-A] {
  def apply(a: A): String
}

object Printer extends LowPriPrinter {
  implicit val stringPrinter: Printer[String] =
    (a: String) => '"' + a + '"'

  implicit def optionPrinter[A](implicit printer: Printer[A]): Printer[Option[A]] = {
    case Some(value) => s"Some(${printer(value)})"
    case None        => "None"
  }

  implicit def listPrinter[A](implicit printer: Printer[A]): Printer[List[A]] = (list) =>
    "List(" + list.map(printer(_)).mkString(", ") + ")"
}

trait LowPriPrinter {
  implicit def anyPrinter[A]: Printer[A] = new Printer[A] {
    override def apply(a: A): String = a.toString
  }
}

sealed trait Zoom[-In, +Out] { self =>
  def run(implicit ev: Any <:< In): (Node, Out) = run(())

  def pos(start: Int, end: Int): Zoom[In, Out] = label(Some(start, end))

  def withCode(string: String): Zoom[In, Out] =
    new Zoom[In, Out] {
      override def run(in: In): (Node, Out) = {
        val (node, out) = self.run(in)
        val newNode =
          node.withCode(string)

        newNode -> out
      }
    }

  def label[In1 <: In](
    pos0: Option[(Int, Int)] = None,
    fullCode: Option[String] = None,
    renderer: Option[MessageDesc[In1]] = None
  ): Zoom[In1, Out] =
    Zoom.Label(self = self, pos = pos0, fullCode = fullCode, renderer = renderer)

  def andThen[Out2](that: Zoom[Out, Out2]): Zoom[In, Out2] = Zoom.AndThen(self, that)

  def >>>[Out2](that: Zoom[Out, Out2]): Zoom[In, Out2] =
    self match {
      case Zoom.AndThen(lhs, rhs) => Zoom.AndThen(lhs, Zoom.AndThen(rhs, that))
      case _                      => Zoom.AndThen(self, that)
    }

//  def run(in: In): (Node, Either[Throwable, Out])
  def run(in: In): (Node, Out)
}

object Zoom {

  def forall[A](f: Zoom[A, Boolean]): Zoom[Iterable[A], Boolean] =
    new Zoom[Iterable[A], Boolean] {
      override def run(in: Iterable[A]): (Node, Boolean) = {
        val results      = in.map(f.run)
        val bool         = results.forall(_._2)
        val nodes        = results.filter(!_._2).map(_._1).toList
        val errorMessage = renderMessage(MessageDesc.text(s"${nodes.length} elements failed the predicate"), in)
        Node(
          input = in,
          fullCode = "",
          pos = (0, 0),
          result = bool,
          errorMessage = errorMessage,
          children = nodes
        ) -> bool
      }
    }

  def main(args: Array[String]): Unit =
    println("HOWDY")

  def zoom[A, B: Printer](f: A => B, start: Int, end: Int): Zoom[A, B] =
    Zoom.Arr(f).pos(start, end)

  def succeed[A: Printer](a: => A, start: Int, end: Int): Zoom[Any, A] = zoom(_ => a, start, end)

  def equalTo[A](value: A, start: Int, end: Int)(implicit printer: Printer[A]): Zoom[A, Boolean] =
    zoom[A, Boolean](in => in == value, start, end)
      .label(None, renderer = Some(M.result((a: A) => printer(a)) + M.choice("==", "!=") + M.text(printer(value))))

  case class Arr[-A, +B](f: A => B)(implicit printer: Printer[B]) extends Zoom[A, B] {
    override def run(in: A): (Node, B) = {
      val out = f(in)
      (
        Node(
          input = in,
          fullCode = "<CODE>",
          pos = (0, 0),
          result = printer(out),
          errorMessage = "",
          children = List.empty
        ),
        out
      )
    }
  }

  case class AndThen[A, B, C](lhs: Zoom[A, B], rhs: Zoom[B, C]) extends Zoom[A, C] {
    override def run(in: A): (Node, C) = {
      val (nodeB, b) = lhs.run(in)
      val (nodeC, c) = rhs.run(b)
      val node       = nodeB.copy(children = List(nodeC))
      (node, c)
    }
  }

  case class Label[In, Out](
    self: Zoom[In, Out],
    pos: Option[(Int, Int)],
    fullCode: Option[String],
    renderer: Option[MessageDesc[In]]
  ) extends Zoom[In, Out] {

    override def run(in: In): (Node, Out) = {
      val (node, out) = self.run(in)
      node.copy(
        pos = pos.getOrElse(node.pos),
        fullCode = fullCode.getOrElse(node.fullCode),
        errorMessage = renderer.map(renderMessage(_, in)).getOrElse(node.errorMessage)
      ) -> out
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

  private def renderMessage[A](message: MessageDesc[A], a: A) =
    renderToString(message.render(a, false))

}

object AssertExample {
  // company.users.head.name == 3
  // company.users.head.name  >>> Assertion.equalTo("Hello)
  // company.users.head  Assertion.arr[Person, String](_.name)

  // _ == 10

  // foreach
  //   u1.name == bill // true
  //   u2.name == bill
  //   u3.name == bill

  // list.forall(Howdy.length == 10) == true
//  val fullCode                                   = "list.forall(_.length == 10)"
//  val equalToInt: Zoom[Int, Boolean]             = Zoom.equalTo(10).label(fullCode = Some(fullCode))
//  val getLength: Zoom[String, Int]               = Zoom.zoom((_: String).length).label(Some(".length"))
//  val getLengthEqualToTen: Zoom[String, Boolean] = getLength >>> equalToInt
//  val forall: Zoom[Iterable[String], Boolean] =
//    Zoom.forall(getLengthEqualToTen).label(Some("forall(_.length == 10)"), fullCode = Some(fullCode))
//  val list: Zoom[Any, List[String]]             = Zoom.succeed(List("Howdydoody", "FAILURE", "Nice")).label(Some("list"))
//  val alwaysTrue                                = Zoom.zoom((_: Any) => true).label(Some("always true"))
//  val all: Zoom[Any, Boolean]                   = list >>> forall // >>> equalToFalse
//  lazy val equalToFalse: Zoom[Boolean, Boolean] = Zoom.equalTo(true).label(fullCode = Some(fullCode))
//

  def debugNode(node: Node, indent: Int): Unit = {
    println(" " * indent + node.copy(fullCode = "", children = Nil))
    node.children.foreach(debugNode(_, indent + 2))
  }

  val l1                          = List(Some(1))
  val zooooom: Zoom[Any, Boolean] = ??? //assertZoom(l1.head.get == 6)

  def main(args: Array[String]): Unit = {

    val (node, _) = zooooom.run
    debugNode(node, 0)
    println("")
//    println(all)
    println("")
    println(examples.render(node, List.empty, 0, true).mkString("\n"))
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
  private val ex1 = (person.name == "Bill") // && (person.age == 31)

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

  final case class Node(
    input: Any,
    fullCode: String,
    pos: (Int, Int),
    result: Any,
    errorMessage: String,
    children: List[Node]
  ) {

    def withCode(string: String): Node =
      copy(fullCode = string, children = children.map(_.withCode(string)))

    def label = fullCode.substring(pos._1, pos._2)

    def firstErrorMessage: String =
      if (children.isEmpty || errorMessage.trim.nonEmpty) errorMessage
      else
        children.collectFirst { case node if node.firstErrorMessage.nonEmpty => node.firstErrorMessage }.getOrElse("")
  }

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

  def highlight(string: String, start: Int, end: Int): String =
    bold(string.take(start)) + bold(yellow(string.slice(start, end))) + bold(string.drop(end))

  def render(node: Node, acc: List[String], indent: Int, isTop: Boolean = false): List[String] = {
    val spaces = " " * indent
    node.children match {
      case Nil =>
        s"$spaces${red("›")} ${node.errorMessage}" :: s"$spaces${highlight(node.fullCode, node.pos._1, node.pos._2)}" :: acc

      case head :: Nil =>
        render(head, s"$spaces${dim(node.label)} = ${blue(node.result.toString)}" :: acc, indent)

      case children if isTop =>
        children.foldLeft(acc) { (acc, node) =>
          render(node, acc, indent)
        }

      case children =>
        s"$spaces> ${node.errorMessage}" :: node.fullCode ::
//        s"$spaces${node.label} = ${node.result}" ::
          children.foldRight(acc) { (node, acc) =>
//            s"$spaces  > ${node.firstErrorMessage}" :: s"$spaces  ${node.result.toString}" :: acc
            render(node, acc, indent + 2)
          }
    }
  }
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
