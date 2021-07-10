package zio.test

import zio.Chunk
import zio.test.Diff.{dim, green, red}
import zio.test.internal.myers.{Action, MyersDiff}

sealed trait DiffResult {
  self =>

  import DiffResult._

  def noDiff: Boolean = !hasDiff

  def hasDiff: Boolean =
    self match {
      case Recursive(_, fields) => fields.exists(_._2.hasDiff)
      case Different(_, _, _)   => true
      case Removed(_)           => true
      case Added(_)             => true
      case Identical(_)         => false
    }

  def render: String = self match {
    case Recursive(label, fields) =>
      scala.Console.RESET + s"""
$label(
  ${fields
        .filter(_._2.hasDiff)
        .map {
          case (Some(label), diff) => dim(label + " = ") + DiffResult.indent(diff.render)
          case (None, diff)        => DiffResult.indent(diff.render)
        }
        .mkString(",\n  ")}
)
         """.trim
    case Different(_, _, Some(custom)) =>
      custom
    case Different(oldValue, newValue, None) =>
      s"${red(PrettyPrint(oldValue))} → ${green(PrettyPrint(newValue))}"
    case Removed(oldValue) =>
      red(PrettyPrint(oldValue))
    case Added(newValue) =>
      green(PrettyPrint(newValue))
    case Identical(value) =>
      PrettyPrint(value)
  }
}

object DiffResult {
  case class Recursive(label: String, fields: Seq[(Option[String], DiffResult)]) extends DiffResult

  case class Different(oldValue: Any, newValue: Any, customRender: Option[String] = None) extends DiffResult

  case class Removed(oldValue: Any) extends DiffResult

  case class Added(newValue: Any) extends DiffResult

  case class Identical(value: Any) extends DiffResult

  def indent(string: String): String =
    string.split("\n").toList match {
      case head :: tail =>
        (head +: tail.map("  " + _)).mkString("\n")
    }
}

trait Diff[-A] {
  def diff(x: A, y: A): DiffResult

  def isLowPriority: Boolean = false
}

object Diff extends LowPriDiff {

  def render[A: Diff](oldValue: A, newValue: A): String =
    (oldValue diffed newValue).render

  def caseClassDiff(typeName: String, paramDiffs: List[(String, DiffResult)]): DiffResult =
    DiffResult.Recursive(typeName, paramDiffs.map { case (s, v) => Some(s) -> v })

  implicit final class DiffOps[A](private val self: A)(implicit diff: Diff[A]) {
    def diffed(that: A): DiffResult = diff.diff(self, that)
  }

  def red[A](string: A): String   = scala.Console.RED + string + scala.Console.RESET
  def dim[A](string: A): String   = "\u001b[2m" + string + scala.Console.RESET
  def green[A](string: A): String = scala.Console.GREEN + string + scala.Console.RESET

  implicit def mapDiff[K, V: Diff]: Diff[Map[K, V]] = (x: Map[K, V], y: Map[K, V]) => {
    val fields =
      (x.keySet ++ y.keySet).toList.map { key =>
        (x.get(key), y.get(key)) match {
          case (Some(oldValue), Some(newValue)) =>
            Some(key.toString) -> (oldValue diffed newValue)
          case (Some(oldValue), None) =>
            Some(key.toString) -> DiffResult.Removed(oldValue)
          case (None, Some(newValue)) =>
            Some(key.toString) -> DiffResult.Added(newValue)
          case (None, None) =>
            throw new Error("THIS IS IMPOSSIBLE.")
        }
      }

    DiffResult.Recursive("Map", fields)
  }

  implicit def listDiff[A: Diff]: Diff[List[A]] = (x: List[A], y: List[A]) => {
    def safeGet(list: List[A], idx: Int): Option[A] =
      Option.when(idx < list.length)(list(idx))

    val fields =
      (0 until (x.length max y.length)).map { idx =>
        (safeGet(x, idx), safeGet(y, idx)) match {
          case (Some(oldValue), Some(newValue)) =>
            Some(idx.toString) -> (oldValue diffed newValue)
          case (Some(oldValue), None) =>
            Some(idx.toString) -> DiffResult.Removed(oldValue)
          case (None, Some(newValue)) =>
            Some(idx.toString) -> DiffResult.Added(newValue)
          case (None, None) =>
            throw new Error("THIS IS IMPOSSIBLE.")
        }
      }

    DiffResult.Recursive("List", fields)
  }

  implicit def setDiff[A: Diff]: Diff[Set[A]] = (x: Set[A], y: Set[A]) => {
    val removed = x -- y
    val added   = y -- x

    val fields: List[(Option[String], DiffResult)] =
      (removed.map(None -> DiffResult.Removed(_)) ++ added.map(None -> DiffResult.Added(_))).toList

    DiffResult.Recursive("Set", fields)
  }

  implicit val stringDiff: Diff[String] = (x: String, y: String) => {
    val actions0 = MyersDiff.diff(x, y).actions

    val actions = actions0
      .foldLeft[Chunk[Action]](Chunk.empty) {
        case (Action.Delete(s1) +: tail, Action.Delete(s2)) =>
          Action.Delete(s1 + s2) +: tail
        case (Action.Insert(s1) +: tail, Action.Insert(s2)) =>
          Action.Insert(s1 + s2) +: tail
        case (Action.Keep(s1) +: tail, Action.Keep(s2)) =>
          Action.Keep(s1 + s2) +: tail
        case (acc, action) => action +: acc
      }
      .reverse

    val isIdentical = actions.count {
      case Action.Keep(_) => false
      case _              => true
    } == 0

    if (isIdentical) {
      DiffResult.Identical(x)
    } else if (
      actions0.count {
        case Action.Keep(_) => true
        case _              => false
      } < 30
    ) {
      DiffResult.Different(x, y)
    } else {
      val custom = scala.Console.RESET + actions.map {
        case Action.Delete(s) => red("-" + s)
        case Action.Insert(s) => green("+" + s)
        case Action.Keep(s)   => s
      }.mkString("")

      DiffResult.Different(x, y, Some(custom))
    }
  }

}

trait LowPriDiff {

  implicit val primitiveDiff: Diff[AnyVal] = (x: AnyVal, y: AnyVal) =>
    if (x == y) DiffResult.Identical(x)
    else DiffResult.Different(x, y)
}
