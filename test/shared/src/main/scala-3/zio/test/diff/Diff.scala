package zio.test.diff

import java.lang.reflect.{Array => JArray}

import zio.internal.ansi.AnsiStringOps
import zio.{Chunk, NonEmptyChunk}
import zio.test.{ConsoleUtils, ErrorMessage => M, PrettyPrint}
import zio.test.internal.OptionalImplicit

trait Diff[-A] { self =>
  def diff(x: A, y: A): DiffResult

  final def contramap[B](f: B => A): Diff[B] =
    (x, y) => self.diff(f(x), f(y))

  def isLowPriority: Boolean = false
}

object Diff extends DiffInstances {
  def apply[A](implicit diff: Diff[A]): Diff[A] = diff

  def render[A: Diff](oldValue: A, newValue: A): String =
    (oldValue diffed newValue).render

  private[test] def renderAssertionFailure[A](
    expected: A,
    actual: A
  )(implicit diff: OptionalImplicit[Diff[A]]): zio.test.ErrorMessage =
    renderAssertionFailure(expected, actual, diff.value)

  private[test] def renderAssertionFailure[A](
    expected: A,
    actual: A,
    diff: Option[Diff[A]]
  ): zio.test.ErrorMessage =
    diff match {
      case Some(diff) if !diff.isLowPriority =>
        renderAssertionFailure(expected, actual, diff.diff(expected, actual))
      case _ =>
        M.pretty(actual) + M.equals + M.pretty(expected)
    }

  private[test] def renderRuntimeAssertionFailure(
    expected: Any,
    actual: Any
  ): zio.test.ErrorMessage =
    runtimeDiff(expected, actual) match {
      case Some(diffResult) => renderAssertionFailure(expected, actual, diffResult)
      case None             => M.pretty(actual) + M.equals + M.pretty(expected)
    }

  private[test] def hasRuntimeDiff(expected: Any, actual: Any): Boolean =
    runtimeDiff(expected, actual).isDefined

  private def arrayElements(array: AnyRef): Vector[Any] = {
    val length = JArray.getLength(array)
    Vector.tabulate(length)(index => JArray.get(array, index))
  }

  private def renderAssertionFailure(
    expected: Any,
    actual: Any,
    diffResult: DiffResult
  ): zio.test.ErrorMessage =
    diffResult match {
      case DiffResult.Different(_, _, None) =>
        M.pretty(actual) + M.equals + M.pretty(expected)
      case diffResult =>
        M.choice("There was no difference", "There was a difference") ++
          M.custom(ConsoleUtils.underlined("Expected")) ++
          M.custom(PrettyPrint(expected)) ++
          M.custom(
            ConsoleUtils.underlined("Diff") +
              s" ${scala.Console.RED}-expected ${scala.Console.GREEN}+obtained".faint
          ) ++
          M.custom(scala.Console.RESET + diffResult.render)
    }

  private def runtimeDiff(expected: Any, actual: Any): Option[DiffResult] =
    (expected, actual) match {
      case (left: String, right: String) =>
        Some(stringDiff.diff(left, right))
      case (left: Option[_], right: Option[_]) =>
        Some(optionDiff[Any](anyDiff[Any]).diff(left.asInstanceOf[Option[Any]], right.asInstanceOf[Option[Any]]))
      case (left: List[_], right: List[_]) =>
        Some(listDiff[Any](anyDiff[Any]).diff(left.asInstanceOf[List[Any]], right.asInstanceOf[List[Any]]))
      case (left: Vector[_], right: Vector[_]) =>
        Some(vectorDiff[Any](anyDiff[Any]).diff(left.asInstanceOf[Vector[Any]], right.asInstanceOf[Vector[Any]]))
      case (left: Chunk[_], right: Chunk[_]) =>
        Some(chunkDiff[Any](anyDiff[Any]).diff(left.asInstanceOf[Chunk[Any]], right.asInstanceOf[Chunk[Any]]))
      case (left: NonEmptyChunk[_], right: NonEmptyChunk[_]) =>
        Some(
          nonEmptyChunk[Any](anyDiff[Any]).diff(left.asInstanceOf[NonEmptyChunk[Any]], right.asInstanceOf[NonEmptyChunk[Any]])
        )
      case (left: Array[_], right: Array[_]) =>
        Some(seqDiff[Any](anyDiff[Any]).diff(arrayElements(left), arrayElements(right)))
      case (left: Seq[_], right: Seq[_]) =>
        Some(seqDiff[Any](anyDiff[Any]).diff(left.asInstanceOf[Seq[Any]], right.asInstanceOf[Seq[Any]]))
      case (left: Set[_], right: Set[_]) =>
        Some(setDiff[Any](anyDiff[Any]).diff(left.asInstanceOf[Set[Any]], right.asInstanceOf[Set[Any]]))
      case (left: Map[_, _], right: Map[_, _]) =>
        Some(
          mapDiff[Any, Any](anyDiff[Any]).diff(left.asInstanceOf[Map[Any, Any]], right.asInstanceOf[Map[Any, Any]])
        )
      case _ =>
        None
    }

  implicit final class DiffOps[A](private val self: A)(implicit diff: Diff[A]) {
    def diffed(that: A): DiffResult = diff.diff(self, that)
  }
}
