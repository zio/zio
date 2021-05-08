package zio.test

import zio.duration
import zio.test.FailureRenderer.FailureMessage._

import scala.annotation.tailrec

// Result >>> (Result && Result)

case class Result[+A](path: Path, value: Either[Throwable, A]) { self =>
  def mark(implicit ev: A <:< Boolean): Result[Boolean] =
    Result(path.mark(value.contains(true)), value.map(ev))

  def nested(label: String, v: Any): Result[A] =
    copy(path = Path.nested(label, v, path))

  def &&(that: Result[Boolean])(implicit ev: A <:< Boolean): Result[Boolean] = {
    val result = value.flatMap(a => that.value.map(_ && a))
    Result(path ++ that.path, result)
  }

  // ().throws.isSubtypeOf[MyError]

  // Zoom[Any, false] ->
  // Zoom[Any, false]

  def flatMap[B](that: A => Result[B]): Result[B] =
    value match {
      case Left(value) => Result(path, Left(value))
      case Right(a) =>
        val resultB = that(a)
        resultB.value match {
          case Left(error) => Result(path ++ resultB.path, Left(error))
//          case Right(value) => Result(trace ++ resultB.trace, Right(value))
          case Right(b) => Result(resultB.path, Right(b)).nested("flatmap", a)
        }
    }
}

object Result {
  def succeed[A](a: A, label: String): Result[A] = Result(Path.info(label), Right(a))
  def succeed[A](a: A): Result[A]                = Result(Path.empty, Right(a))
}

trait Zoom[-In, +Out] { self =>
  def run(in: In): Result[Out]

  def printPath(path: Path, succeeded: Boolean, indent: Int): Unit = path match {
    case Path.Mark(path, isSuccess) if succeeded != isSuccess =>
      printPath(path, succeeded, indent)
    case Path.Info(label) =>
      println(" " * indent + s"RESULT: $label")
    case Path.Nested(label, value, child) =>
      println(" " * indent + s"$label = $value")
      printPath(child, succeeded, indent)
    case Path.Many(paths) =>
      paths.foreach { path =>
        printPath(path, succeeded, indent + 2)
      }
    case _ =>
  }

  def run(implicit ev: Any <:< In, ev2: Out <:< Boolean): Result[Boolean] = {
    val result = run(()).asInstanceOf[Result[Boolean]]
    result.value match {
      case Left(exception) =>
        println(s"OH NO $exception")
      case Right(success) =>
        printPath(result.path, success, 0)
    }
    result
  }

  def andThen[Out2](that: Zoom[Out, Out2]): Zoom[In, Out2] = { in =>
    self.run(in).flatMap(that.run)
  }

  def >>>[Out2](that: Zoom[Out, Out2]): Zoom[In, Out2] = self andThen that

  def &&[In1 <: In](that: Zoom[In1, Boolean])(implicit ev: Out <:< Boolean): Zoom[In1, Boolean] = { in =>
    self.run(in) && that.run(in)
  }
}

object Zoom {
  def forall[A](za: Zoom[A, Boolean]): Zoom[scala.Iterable[A], Boolean] = { as =>
    val result = as.map(za.run)
    result.foldLeft(Result.succeed(true)) { case (acc, a) =>
      acc && a.mark
    }
  }

  def zoom[A, B](f: A => B): Zoom[A, B] = { a =>
    Result
      .succeed(f(a))
      .nested("function", a)
  }

  def equalTo[A](value: A): Zoom[A, Boolean] = a => Result.succeed(a == value, s"== $value")

  def succeed[A](a: => A): Zoom[Any, A] = _ => Result.succeed(a)
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

  // list.forall(_.length == 10)
  val equalToInt: Zoom[Int, Boolean]             = Zoom.equalTo(10)
  val getLength: Zoom[String, Int]               = Zoom.zoom(_.length)
  val getLengthEqualToTen: Zoom[String, Boolean] = getLength >>> equalToInt
  val forall: Zoom[Iterable[String], Boolean]    = Zoom.forall(getLengthEqualToTen)
  val list: Zoom[Any, List[String]]              = Zoom.succeed(List("Howdydoody", "FAILURE", "Nice"))

  val all: Zoom[Any, Boolean] = list >>> forall

  def main(args: Array[String]): Unit =
    println(all.run)

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

  val zx1: Zoom[Any, Boolean] =
    Zoom.succeed(person) >>> Zoom.zoom(_.name) >>> Zoom.zoom(_ == "Bill") //.label("== 'Bill'")

  final case class Node(
    input: Any,
    fullCode: String,
    label: String,
    result: Any,
    errorMessage: String,
    children: List[Node]
  ) {
    def firstErrorMessage: String =
      if (children.isEmpty || errorMessage.trim.nonEmpty) errorMessage
      else
        children.collectFirst { case node if node.firstErrorMessage.nonEmpty => node.firstErrorMessage }.getOrElse("")
  }

  lazy val andNode = Node((), "", "&&", false, "", List(personNode, schoolNode))

  lazy val personNode  = Node((), "", "person", person, "", List(nameNode))
  lazy val nameNode    = Node(person, "", ".name", "Bob", "", List(equalToNode))
  lazy val equalToNode = Node("Bob", "person.name == 'Bill'", " == 'Bill'", false, "Bob != Bill", List())

  // school.students[.forall(_.age > 10)]
  case class Student(age: Int)
  case class School(students: List[Student])
  val jimmy             = Student(12)
  val carl              = Student(18)
  val sam               = Student(7)
  val grob              = Student(9)
  val school            = School(List(jimmy, carl, sam, grob))
  lazy val schoolNode   = Node((), "", "school", school, "", List(studentsNode))
  lazy val studentsNode = Node(school, "", ".students", school.students, "", List(forallNode))
  lazy val forallNode =
    Node(
      school.students,
      "school.students.forall(_.age > 10)",
      "forall",
      false,
      "2 elements failed",
      List(samNode, grobNode)
    )
  lazy val samNode             = Node((), "", "_", sam, "", List(samAgeNode))
  lazy val samAgeNode          = Node(sam, "", "_.age", sam.age, "", List(samGreaterThanNode))
  lazy val samGreaterThanNode  = Node(sam.age, "_.age > 10", "> 10", false, "7 is not greater than 10", List())
  lazy val grobNode            = Node((), "", "_", grob, "", List(grobAgeNode))
  lazy val grobAgeNode         = Node(grob, "", "_.age", grob.age, "", List(grobGreaterThanNode))
  lazy val grobGreaterThanNode = Node(grob.age, "_.age > 10", "> 10", false, "9 is not greater than 10", List())

  //  @tailrec
  def render[A](node: Node, acc: List[String], indent: Int, isTop: Boolean = false): List[String] = {
    val spaces = " " * indent
    node.children match {
      case Nil =>
        //                 highlight(node.fullCode, node.startPos, node.endPos)
        s"$spaces> ${node.errorMessage}" :: s"$spaces${node.fullCode}" :: acc

      case head :: Nil =>
        render(head, s"$spaces${node.label} = ${node.result}" :: acc, indent)

      case children if isTop =>
        children.foldLeft(acc) { (acc, node) =>
          render(node, acc, indent)
        }

      case children =>
        s"$spaces> ${node.errorMessage}" :: node.fullCode ::
//        s"$spaces${node.label} = ${node.result}" ::
          children.foldRight(acc) { (node, acc) =>
            s"$spaces  > ${node.firstErrorMessage}" :: s"$spaces  ${node.result.toString}" :: acc
//            render(node, acc, indent + 2)
          }
    }
  }

  /**
   * - people are named certain things
   * > ERROR: AGE ACCESS DISALLOWED
   * school.students[.forall(_.age > 10)]
   *   src/Interesting.scala:12
   *
   * > 2 elements failed the predicate
   * school.students[.forall(_.age > 10)]
   *   > 8 is not greater than 10
   *   > 7 is not greater than 10
   * .ages = List(12, 8, 18, 7)
   * school = School(List(12, 8, 18, 7))
   *
   * > Bob != Bill
   * person.name == 'Bill'
   * .name = Bob
   * person = Person(Bobo,82)
   */

  def main(args: Array[String]): Unit =
    println(render(schoolNode, List.empty, 0, true).mkString("\n"))
//  println(render(andNode, List.empty).mkString("\n"))

  // person.name == "Bill"

  /**
   * > "Bobo" != "Bill"
   * person.name == "Bill"
   * .name = "Bobo"
   * person = Person("Bobo", 82)
   *
   * > 82 != 18
   * person.age == 18
   * .age = 82
   * person = Person("Bobo", 82)
   *
   * > The predicate failed for 3 children
   *  people [.forall(_.name == "Bill")]
   *     > Joe != Bill
   *     _ = Person("Joe")
   *     > Jim != Bill
   *     _ = Person("Joe")
   * .people = List(...)
   * .people = List(...)
   */
//  Node("&&", List(Node("", List(Node("", List.empty)))))

  // Translate AST into reified description
  // Evaluate reified description and translate into result type
  // render result type
}
