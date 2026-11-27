package zio.test.diff

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.diff.Diff.DiffOps
import zio.test.internal.myers.MyersDiff
import zio.{Chunk, NonEmptyChunk}

import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.util.Try

/**
 * The [[Diff]] instances that the `Diff` companion object provides, for `String`, `Map`, `Option`, `List`, `Vector`, `Chunk`, `NonEmptyChunk`, `Array`, `ArrayBuffer`, `ListBuffer`, `Seq`, `Set` and `Nothing`. [[LowPriDiff]] adds a fallback for `AnyVal` types.
 */
trait DiffInstances extends LowPriDiff {

  /**
   * Compares two strings, choosing how to diff them from their inputs:
   *
   *   - Equal strings give `Identical`, and a `null` against a non-`null` string gives a `Different` with no custom rendering.
   *   - Two single-line strings give a `Different` with no custom rendering when either is shorter than 20 characters. Otherwise they are diffed word by word when both have more than one word and fewer than 12 characters per word, and character by character otherwise.
   *   - When either string has more than one line, they are diffed line by line. Strings whose lines are equal after splitting on newlines, such as `"a\nb"` and `"a\nb\n"`, give `Identical`.
   */
  implicit val stringDiff: Diff[String] = { (x: String, y: String) =>
    if (x == y) {
      DiffResult.Identical(x)
    } else if ((x == null && y != null) || (y == null && x != null)) {
      DiffResult.Different(x, y)
    } else if (x.split("\n").length <= 1 && y.split("\n").length <= 1) {
      if (x.length < 20 || y.length < 20)
        DiffResult.Different(x, y)
      else {
        val xWordCount       = x.split("\\s+").length
        val yWordCount       = y.split("\\s+").length
        val xWordToCharRatio = Try(x.length / xWordCount.toDouble).getOrElse(0.0)
        val yWordToCharRatio = Try(y.length / yWordCount.toDouble).getOrElse(0.0)

        if (xWordCount > 1 && yWordCount > 1 && xWordToCharRatio < 12 && yWordToCharRatio < 12) {
          // Diffing per word
          DiffResult.Different(x, y, Some(MyersDiff.diffWords(x, y).renderLine))
        } else {
          // Diffing per char
          DiffResult.Different(x, y, Some(MyersDiff.diffChars(x, y).renderLine))
        }
      }
    } else {
      val result = MyersDiff.diff(x, y)
      if (result.isIdentical)
        DiffResult.Identical(x)
      else
        DiffResult.Different(x, y, Some(result.toString))
    }
  }

  /**
   * Compares two maps key by key. The result is a `Nested` labelled `Map` with one field per key in either map, labelled with the key's `toString`: the values' diff for a key in both maps, `Removed` for a key only in `x`, and `Added` for a key only in `y`.
   */
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

    DiffResult.Nested("Map", fields)
  }

  /**
   * Compares two options. Two `Some` values give a `Nested` labelled `Some` holding the diff of their contents, a `Some` against a `None` (in either order) gives `Different`, and two `None` values give `Identical`.
   */
  implicit def optionDiff[A: Diff]: Diff[Option[A]] = (x: Option[A], y: Option[A]) =>
    (x, y) match {
      case (Some(x), Some(y)) => DiffResult.Nested("Some", List(None -> (x diffed y)))
      case (Some(x), _)       => DiffResult.Different(Some(x), None)
      case (_, Some(y))       => DiffResult.Different(None, Some(y))
      case _                  => DiffResult.Identical(None)
    }

  /**
   * Compares two lists index by index, as `mkSeqDiff` does, giving a `Nested` labelled `List`.
   */
  implicit def listDiff[A: Diff]: Diff[List[A]] = mkSeqDiff("List")(identity(_))

  /**
   * Compares two vectors index by index, as `mkSeqDiff` does, giving a `Nested` labelled `Vector`.
   */
  implicit def vectorDiff[A: Diff]: Diff[Vector[A]] = mkSeqDiff("Vector")(identity(_))

  /**
   * Compares two chunks index by index, as `mkSeqDiff` does, giving a `Nested` labelled `Chunk`.
   */
  implicit def chunkDiff[A: Diff]: Diff[Chunk[A]] = mkSeqDiff("Chunk")(identity(_))

  /**
   * Compares two non-empty chunks index by index, as `mkSeqDiff` does, giving a `Nested` labelled `NonEmptyChunk`.
   */
  implicit def nonEmptyChunk[A: Diff]: Diff[NonEmptyChunk[A]] = mkSeqDiff("NonEmptyChunk")(identity(_))

  /**
   * Compares two arrays index by index, as `mkSeqDiff` does, giving a `Nested` labelled `Array`.
   */
  implicit def arrayDiff[A: Diff]: Diff[Array[A]] = mkSeqDiff("Array")((_: Array[A]).toIndexedSeq)

  /**
   * Compares two `ArrayBuffer`s index by index, as `mkSeqDiff` does, giving a `Nested` labelled `ArrayBuffer`.
   */
  implicit def arrayBufferDiff[A: Diff]: Diff[ArrayBuffer[A]] =
    mkSeqDiff("ArrayBuffer")((_: ArrayBuffer[A]).toIndexedSeq)

  /**
   * Compares two `ListBuffer`s index by index, as `mkSeqDiff` does, giving a `Nested` labelled `ListBuffer`.
   */
  implicit def listBufferDiff[A: Diff]: Diff[ListBuffer[A]] = mkSeqDiff("ListBuffer")((_: ListBuffer[A]).toIndexedSeq)

  /**
   * Compares two sequences index by index, as `mkSeqDiff` does, giving a `Nested` labelled `Seq`.
   */
  implicit def seqDiff[A: Diff]: Diff[Seq[A]] = mkSeqDiff("Seq")(identity(_))

  /**
   * Builds a `Diff[F[A]]` that converts both values to a `Seq` with `f` and compares the elements at the same index. The result is a `Nested` labelled `name` with one field per index, labelled with the index: the elements' diff where both values have an element, `Removed` for an index only in `x`, and `Added` for an index only in `y`. Elements are paired by position only: an inserted element is not detected as an insertion, and it shifts the pairing of each element after it.
   */
  def mkSeqDiff[F[_], A: Diff](name: String)(f: F[A] => Seq[A]): Diff[F[A]] = (x0: F[A], y0: F[A]) => {
    val x                                          = f(x0)
    val y                                          = f(y0)
    def safeGet(list: Seq[A], idx: Int): Option[A] = list.lift(idx)

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

    DiffResult.Nested(name, fields.toList)
  }

  /**
   * Compares two sets by membership. The result is a `Nested` labelled `Set` with an unlabelled `Removed` for each element only in `x` and an unlabelled `Added` for each element only in `y`. Elements in both sets are left out, and the `Diff[A]` of the elements is not used.
   */
  implicit def setDiff[A: Diff]: Diff[Set[A]] = (x: Set[A], y: Set[A]) => {
    val removed = x -- y
    val added   = y -- x

    val fields: List[(Option[String], DiffResult)] =
      (removed.map(None -> DiffResult.Removed(_)) ++ added.map(None -> DiffResult.Added(_))).toList

    DiffResult.Nested("Set", fields)
  }

  /**
   * The `Diff` instance for `Nothing`, which has no values to compare.
   */
  implicit val nothingDiff: Diff[Nothing] =
    (x: Nothing, _: Nothing) => DiffResult.Identical(x)
}

/**
 * Fallback [[Diff]] instances that compare values with `==`, with a lower implicit priority than those of [[DiffInstances]].
 */
trait LowPriDiff {

  /**
   * Compares two `AnyVal` values, such as `Int`, `Double` or `Boolean` values, with `==`: `Identical` when they are equal, and `Different` with no custom rendering otherwise. The instance is low priority (see `Diff.isLowPriority`), so smart assertions do not show its result.
   */
  implicit def anyValDiff[A <: AnyVal]: Diff[A] = anyDiff[A]

  /**
   * Returns a low-priority `Diff[A]` that compares with `==`: `Identical` when the values are equal, and `Different` with no custom rendering otherwise. It is not implicit.
   */
  def anyDiff[A]: Diff[A] = new Diff[A] {
    override def diff(x: A, y: A): DiffResult =
      if (x == y) DiffResult.Identical(x)
      else DiffResult.Different(x, y)

    override def isLowPriority: Boolean = true
  }
}
