package zio.test.diff

import zio.test.diff.Delta.DiffType.{ Change, Delete, Insert }
import zio.test.diff.Delta._

private[diff] case class Delta[T](diffType: DiffType, original: Chunk[T], revised: Chunk[T])

private[diff] object Delta {
  sealed abstract class DiffType
  object DiffType {
    case object Change    extends DiffType
    case object Delete    extends DiffType
    case object Insert    extends DiffType
    case object Unchanged extends DiffType
  }
}

private[diff] object ChangeDelta {
  def apply[T](original: Chunk[T], revised: Chunk[T]): Delta[T] = Delta(Change, original, revised)
}

private[diff] object InsertDelta {
  def apply[T](original: Chunk[T], revised: Chunk[T]): Delta[T] = Delta(Insert, original, revised)
}

private[diff] object DeleteDelta {
  def apply[T](original: Chunk[T], revised: Chunk[T]): Delta[T] = Delta(Delete, original, revised)
}
