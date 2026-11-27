package zio.test.diff

import zio.internal.ansi.AnsiStringOps
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.ConsoleUtils._
import zio.test.PrettyPrint

/**
 * The result of comparing an old value with a new one using a [[Diff]]. It is a
 * tree: [[DiffResult.Nested]] holds the results for the labelled parts of a
 * collection or a record, and the leaves say whether a value is
 * [[DiffResult.Identical]], [[DiffResult.Different]], [[DiffResult.Removed]] or
 * [[DiffResult.Added]].
 *
 * {{{
 * val result = Diff[Map[String, Int]].diff(Map("a" -> 1, "b" -> 2), Map("a" -> 3, "c" -> 4))
 * // Nested(Map,List((Some(a),Different(1,3,None)), (Some(b),Removed(2)), (Some(c),Added(4))))
 *
 * val changed = result.hasDiff
 * // true
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/test/zio-test-diff/ Diff]]
 */
sealed trait DiffResult {
  self =>

  import DiffResult._

  /**
   * Returns `true` when no part of this result has a difference: the negation
   * of `hasDiff`.
   */
  def noDiff: Boolean = !hasDiff

  /**
   * Returns `true` when this result is a `Different`, `Removed` or `Added`, or
   * a `Nested` with at least one field that has a difference. It returns
   * `false` for `Identical` and for a `Nested` with no fields.
   */
  def hasDiff: Boolean =
    self match {
      case Nested(_, fields)  => fields.exists(_._2.hasDiff)
      case Different(_, _, _) => true
      case Removed(_)         => true
      case Added(_)           => true
      case Identical(_)       => false
    }

  /**
   * Renders this result as a string, in the form that smart assertions show in
   * a failure message.
   */
  def render: String = self match {
    case Nested(label, fields) =>
      s"""
$label(
  ${indent(
          fields
            .filter(_._2.hasDiff)
            .map {
              case (Some(label), Different(_, _, Some(custom))) => dim(label + " = ") + indent(custom, label.length + 3)
              case (Some(label), diff) =>
                val color = diff match {
                  case Removed(_) => scala.Console.RED
                  case Added(_)   => scala.Console.GREEN
                  case _          => ""
                }
                dim(color + label + " = ") + diff.render
              case (None, diff) => diff.render
            }
            .mkString(",\n")
        )}
)
         """.trim
    case Different(_, _, Some(custom)) =>
      custom
    case Different(oldValue, newValue, None) =>
      s"${PrettyPrint(oldValue).red} → ${PrettyPrint(newValue).green}"
    case Removed(oldValue) =>
      PrettyPrint(oldValue).red
    case Added(newValue) =>
      PrettyPrint(newValue).green
    case Identical(value) =>
      PrettyPrint(value)
  }
}

/**
 * The cases of [[DiffResult]].
 */
object DiffResult {

  /**
   * The result for a value made of labelled parts, such as a collection, an
   * `Option` or a case class. `label` names the type, and each field pairs an
   * optional label (such as an index or a map key) with the result for that
   * part.
   */
  case class Nested(label: String, fields: List[(Option[String], DiffResult)]) extends DiffResult

  /**
   * The result for two values that differ. When `customRender` is defined,
   * `render` returns it instead of rendering the two values; the `String`
   * instance sets it for the strings it diffs word by word, character by
   * character or line by line.
   */
  case class Different(oldValue: Any, newValue: Any, customRender: Option[String] = None) extends DiffResult

  /**
   * A value present in the old value and missing from the new one, such as a
   * key only in the old map or an element past the end of the new sequence.
   */
  case class Removed(oldValue: Any) extends DiffResult

  /**
   * A value missing from the old value and present in the new one, such as a
   * key only in the new map or an element past the end of the old sequence.
   */
  case class Added(newValue: Any) extends DiffResult

  /**
   * The result for two values that are the same.
   */
  case class Identical(value: Any) extends DiffResult

  /**
   * Indents each line of `string` after the first by `amount` spaces.
   */
  def indent(string: String, amount: Int = 2): String =
    string.split("\n").toList match {
      case head :: tail =>
        (head +: tail.map((" " * amount) + _)).mkString("\n")
      case other => other.mkString("\n")
    }
}
