package zio.test.diff

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.diff.Diff.DiffOps
import zio.test.internal.myers.MyersDiff
import zio.{Chunk, NonEmptyChunk}

import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.util.Try

trait Diff[A] { self =>
  def diff(x: A, y: A): DiffResult

  final def contramap[B](f: B => A): Diff[B] = new Diff[B] {
    override def diff(x: B, y: B): DiffResult = self.diff(f(x), f(y))
  }

  def isLowPriority: Boolean = false
}

object Diff extends DiffInstances {
  def apply[A](implicit diff: Diff[A]): Diff[A] = diff

  def render[A: Diff](oldValue: A, newValue: A): String =
    (oldValue diffed newValue).render

  implicit final class DiffOps[A](private val self: A)(implicit diff: Diff[A]) {
    def diffed(that: A): DiffResult = diff.diff(self, that)
  }
}

trait DiffInstances extends LowPriDiff {

  /**
   * The default String renderer attempts to clever render the resulting diff
   * according to its inputs.
   *
   *   - Short strings are rendered as: "this" was not equal to "something else"
   *   - Single line strings are determined (based on word to length ratio) to
   *     be either structured word-based sentences or less structured dat.
   *     - Sentence-like strings are diffed per word
   *     - Unstructured strings are diffed per character
   *   - Multi-line strings are diffed per line
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

  implicit def optionDiff[A: Diff]: Diff[Option[A]] = (x: Option[A], y: Option[A]) =>
    (x, y) match {
      case (Some(x), Some(y)) => DiffResult.Nested("Some", List(None -> (x diffed y)))
      case (Some(x), _)       => DiffResult.Different(Some(x), None)
      case (_, Some(y))       => DiffResult.Different(None, Some(y))
      case _                  => DiffResult.Identical(None)
    }

  implicit def listDiff[A: Diff]: Diff[List[A]]               = mkSeqDiff("List")(identity(_))
  implicit def vectorDiff[A: Diff]: Diff[Vector[A]]           = mkSeqDiff("Vector")(identity(_))
  implicit def chunkDiff[A: Diff]: Diff[Chunk[A]]             = mkSeqDiff("Chunk")(identity(_))
  implicit def nonEmptyChunk[A: Diff]: Diff[NonEmptyChunk[A]] = mkSeqDiff("NonEmptyChunk")(identity(_))
  implicit def arrayDiff[A: Diff]: Diff[Array[A]]             = mkSeqDiff("Array")((_: Array[A]).toIndexedSeq)
  implicit def arrayBufferDiff[A: Diff]: Diff[ArrayBuffer[A]] =
    mkSeqDiff("ArrayBuffer")((_: ArrayBuffer[A]).toIndexedSeq)
  implicit def listBufferDiff[A: Diff]: Diff[ListBuffer[A]] = mkSeqDiff("ListBuffer")((_: ListBuffer[A]).toIndexedSeq)
  implicit def seqDiff[A: Diff]: Diff[Seq[A]]               = mkSeqDiff("Seq")(identity(_))

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

  implicit def setDiff[A: Diff]: Diff[Set[A]] = (x: Set[A], y: Set[A]) => {
    val removed = x -- y
    val added   = y -- x

    val fields: List[(Option[String], DiffResult)] =
      (removed.map(None -> DiffResult.Removed(_)) ++ added.map(None -> DiffResult.Added(_))).toList

    DiffResult.Nested("Set", fields)
  }

  implicit val nothingDiff: Diff[Nothing] =
    (x: Nothing, _: Nothing) => DiffResult.Identical(x)
}

trait LowPriDiff {

  implicit def anyValDiff[A <: AnyVal]: Diff[A] = anyDiff[A]

  def anyDiff[A]: Diff[A] = new Diff[A] {
    override def diff(x: A, y: A): DiffResult =
      if (x == y) DiffResult.Identical(x)
      else DiffResult.Different(x, y)

    override def isLowPriority: Boolean = true
  }
}
