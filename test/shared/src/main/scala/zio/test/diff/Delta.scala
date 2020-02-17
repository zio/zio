package zio.test.diff

import zio.test.diff.Delta.TYPE.{ CHANGE, DELETE, INSERT }
import zio.test.diff.Delta._

private[diff] case class Delta[T](`type`: TYPE, original: Chunk[T], revised: Chunk[T])

private[diff] object Delta {
  sealed abstract class TYPE
  object TYPE {
    case object CHANGE    extends TYPE
    case object DELETE    extends TYPE
    case object INSERT    extends TYPE
    case object UNCHANGED extends TYPE
  }
}

private[diff] object ChangeDelta {
  def apply[T](original: Chunk[T], revised: Chunk[T]): Delta[T] = Delta(CHANGE, original, revised)
}

private[diff] object InsertDelta {
  def apply[T](original: Chunk[T], revised: Chunk[T]): Delta[T] = Delta(INSERT, original, revised)
}

private[diff] object DeleteDelta {
  def apply[T](original: Chunk[T], revised: Chunk[T]): Delta[T] = Delta(DELETE, original, revised)
}
