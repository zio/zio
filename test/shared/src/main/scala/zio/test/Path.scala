package zio.test

/**
 *  - History: List[Any]
 *  - Failure: BoolAlgebra[(Message, History)]
 *  - company.users.forall(user => user.name == "Bill" && user.age == 12) == (1 == 3)
 *  - company.users.forall____________________
 *    - Jim != "Bill"
 *    - .name = "Jim
 *    - User("Jim")
 *  - Either[Failure, A]
 */

sealed trait Path { self =>
  def ++(that: Path): Path =
    (self, that) match {
      case (Path.Empty, b)                => b
      case (a, Path.Empty)                => a
      case (Path.Many(t1), Path.Many(t2)) => Path.Many(::(t1.head, t1.tail ++ t2))
      case (Path.Many(t1), b)             => Path.Many(::(t1.head, t1.tail :+ b))
      case (a, Path.Many(t2))             => Path.Many(::(a, t2))
      case (a, b)                         => Path.Many(::(a, List(b)))
    }

  def mark(isSuccess: Boolean): Path =
    self match {
      case Path.Mark(path, _) => Path.Mark(path, isSuccess)
      case path               => Path.Mark(path, isSuccess)
    }

  def succeed: Path = mark(true)
  def fail: Path    = mark(false)
}

object Path {
  def nested(label: String, value: Any, child: Path): Path = Nested(label, value, child)

  def info(label: String): Path = Info(label)

  case class Mark(path: Path, isSuccess: Boolean) extends Path
  // _ == 2
  // Info("== 2")
  case class Info(label: String) extends Path

  // user.age == 2
  // res2 = Nested(".age", 3, Info("== 2"))
  // Nested("user", User(3), res2)
  case class Nested(label: String, value: Any, child: Path) extends Path

  // RoseTree?
  // user.name == "Bill" && user.age == 12
  // user.name == "Bill" || user.age == 12
  case class Many(failures: ::[Path]) extends Path

//  And(_, Nested("4", 4, Info(" == 12")))
  // user.name == "Bill" && 4 == 12
  // jimFail = Both()
  // Both(jimFail, joeFail)
  // Many(Many(Both), Many(Both))

//  Jim != Bill
//  4 != 12

//  Joe != Bill
//  4 != 12

  // Nested("users", List(), Nested("forall", false, Many(Nested("user", User("bill", 4), Many(...)))))
  // company.users.forall(user => user.name == "Bill" && 4 == 12)

  case object Empty extends Path

  def empty: Path = Empty
}
