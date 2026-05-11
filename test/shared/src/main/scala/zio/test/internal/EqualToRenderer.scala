package zio.test.internal

import zio.{Chunk, NonEmptyChunk}
import zio.internal.ansi.AnsiStringOps
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.diff.{Diff, DiffResult}
import zio.test.{ConsoleUtils, ErrorMessage => M, PrettyPrint}

import java.lang.reflect.{Array => JArray}
import scala.collection.mutable.{ArrayBuffer, ListBuffer}

private[test] object EqualToRenderer {

  def render(
    actual: Any,
    expected: Any,
    result: Boolean
  )(fallback: => zio.test.ErrorMessage): zio.test.ErrorMessage =
    if (result) {
      fallback
    } else {
      runtimeDiff(expected, actual).fold(fallback)(renderDiff(_, expected, fallback))
    }

  def render[A](
    actual: => A,
    expected: => A,
    result: Boolean,
    diff: OptionalImplicit[Diff[A]]
  )(fallback: => zio.test.ErrorMessage): zio.test.ErrorMessage =
    diff.value match {
      case Some(diff) if !diff.isLowPriority && !result =>
        try {
          renderDiff(diff.diff(expected, actual), expected, fallback)
        } catch {
          case _: ClassCastException => fallback
        }
      case _ =>
        fallback
    }

  private def renderDiff(
    diffResult: DiffResult,
    expected: => Any,
    fallback: => zio.test.ErrorMessage
  ): zio.test.ErrorMessage =
    diffResult match {
      case DiffResult.Different(_, _, None) =>
        fallback
      case diffResult =>
        M.choice("There was no difference", "There was a difference") ++
          M.custom(ConsoleUtils.underlined("Expected")) ++
          M.custom(PrettyPrint(expected)) ++
          M.custom(
            ConsoleUtils.underlined("Diff") + s" ${scala.Console.RED}-expected ${scala.Console.GREEN}+obtained".faint
          ) ++
          M.custom(scala.Console.RESET + diffResult.render)
    }

  private def arrayElements(array: AnyRef): Vector[Any] = {
    val length = JArray.getLength(array)
    Vector.tabulate(length)(index => JArray.get(array, index))
  }

  private def runtimeDiff(expected: Any, actual: Any): Option[DiffResult] =
    (expected, actual) match {
      case (left: AnyRef, right: AnyRef) if left.getClass.isArray && right.getClass.isArray =>
        Some(
          Diff
            .mkSeqDiff[Vector, Any]("Array")(identity)(Diff.anyDiff[Any])
            .diff(arrayElements(left), arrayElements(right))
        )
      case (left: String, right: String) =>
        Some(Diff.stringDiff.diff(left, right))
      case (left: List[_], right: List[_]) =>
        Some(Diff.listDiff[Any](Diff.anyDiff[Any]).diff(left, right))
      case (left: Vector[_], right: Vector[_]) =>
        Some(Diff.vectorDiff[Any](Diff.anyDiff[Any]).diff(left, right))
      case (left: Chunk[_], right: Chunk[_]) =>
        Some(Diff.chunkDiff[Any](Diff.anyDiff[Any]).diff(left.asInstanceOf[Chunk[Any]], right.asInstanceOf[Chunk[Any]]))
      case (left: NonEmptyChunk[_], right: NonEmptyChunk[_]) =>
        Some(
          Diff
            .nonEmptyChunk[Any](Diff.anyDiff[Any])
            .diff(left.asInstanceOf[NonEmptyChunk[Any]], right.asInstanceOf[NonEmptyChunk[Any]])
        )
      case (left: ArrayBuffer[_], right: ArrayBuffer[_]) =>
        Some(
          Diff
            .arrayBufferDiff[Any](Diff.anyDiff[Any])
            .diff(left.asInstanceOf[ArrayBuffer[Any]], right.asInstanceOf[ArrayBuffer[Any]])
        )
      case (left: ListBuffer[_], right: ListBuffer[_]) =>
        Some(
          Diff
            .listBufferDiff[Any](Diff.anyDiff[Any])
            .diff(left.asInstanceOf[ListBuffer[Any]], right.asInstanceOf[ListBuffer[Any]])
        )
      case (left: Seq[_], right: Seq[_]) =>
        Some(Diff.seqDiff[Any](Diff.anyDiff[Any]).diff(left, right))
      case (left: Option[_], right: Option[_]) =>
        Some(Diff.optionDiff[Any](Diff.anyDiff[Any]).diff(left, right))
      case (left: Map[_, _], right: Map[_, _]) =>
        Some(
          Diff
            .mapDiff[Any, Any](Diff.anyDiff[Any])
            .diff(left.asInstanceOf[Map[Any, Any]], right.asInstanceOf[Map[Any, Any]])
        )
      case (left: Set[_], right: Set[_]) =>
        Some(Diff.setDiff[Any](Diff.anyDiff[Any]).diff(left.asInstanceOf[Set[Any]], right.asInstanceOf[Set[Any]]))
      case _ =>
        None
    }
}
