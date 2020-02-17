package zio.test.diff

import zio.test.diff.Delta.TYPE
import zio.test.diff.meyers.MyersDiff

trait StringDiffer {
  def diff(actual: String, expected: String): Vector[DiffElement]
}

object StringDiffer {
  private val differ = new MyersDiff[Char]()
  val default: StringDiffer = (actual: String, expected: String) => {
    val patch = differ.diff(actual.toVector, expected.toVector)
    patch.deltas.map { d =>
      d.`type` match {
        case TYPE.UNCHANGED => DiffElement.Unchanged(toString(d.original))
        case TYPE.CHANGE    => DiffElement.Changed(toString(d.original), toString(d.revised))
        case TYPE.INSERT    => DiffElement.Inserted(toString(d.revised))
        case TYPE.DELETE    => DiffElement.Deleted(toString(d.original))
      }
    }
  }
  private def toString(chars: Chunk[Char]) = new String(chars.elements.toArray)
}

sealed trait DiffElement
object DiffElement {
  case class Unchanged(text: String)                   extends DiffElement
  case class Changed(actual: String, expected: String) extends DiffElement
  case class Inserted(text: String)                    extends DiffElement
  case class Deleted(text: String)                     extends DiffElement
}
