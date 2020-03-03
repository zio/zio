package zio.test.diff

import zio.test.diff.Delta.DiffType
import zio.test.diff.meyers.MyersDiff

trait StringDiffer {
  def diff(actual: String, expected: String): DiffResult
}

object StringDiffer {
  private val differ = new MyersDiff[Char]()
  val default: StringDiffer = (actual: String, expected: String) =>
    DiffResult {
      val patch = differ.diff(expected.toVector, actual.toVector)
      patch.deltas.map { d =>
        d.diffType match {
          case DiffType.Unchanged => DiffComponent.Unchanged(toString(d.original))
          case DiffType.Change    => DiffComponent.Changed(toString(d.original), toString(d.revised))
          case DiffType.Insert    => DiffComponent.Inserted(toString(d.revised))
          case DiffType.Delete    => DiffComponent.Deleted(toString(d.original))
        }
      }
    }
  private def toString(chars: Chunk[Char]) = new String(chars.elements.toArray)
}
