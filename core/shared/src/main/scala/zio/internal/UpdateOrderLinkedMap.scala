package zio.internal

import scala.annotation.tailrec
import scala.collection.AbstractIterator
import scala.collection.immutable.{HashMap, VectorBuilder}
import scala.util.hashing.MurmurHash3

final class UpdateOrderLinkedMap[K, +V](
  fields: Vector[Any],
  underlying: HashMap[K, (Int, V)]
) { self =>
  import UpdateOrderLinkedMap._

  val size: Int = underlying.size

  def isEmpty: Boolean = size == 0

  def updated[V1 >: V](key: K, value: V1): UpdateOrderLinkedMap[K, V1] = {
    var fs = fields
    val sz = fs.size

    val existing = underlying.getOrElse(key, null)
    if (existing eq null) {
      new UpdateOrderLinkedMap(fs :+ key, underlying.updated(key, (sz, value)))
    } else if (existing._1 == sz - 1) {
      // If the entry to be added is at the tail of the fields, we can just update the value
      new UpdateOrderLinkedMap(fs, underlying.updated(key, (sz - 1, value)))
    } else if (sz - self.size > 10000) {
      val builder = newBuilder[K, V1]
      builder.addAll(self.iterator)
      builder.addOne(key, value)
      builder.result()
    } else {
      val s = existing._1

      // Calculate next of kin
      val next =
        if (s < sz - 1) fs(s + 1) match {
          case Tombstone(d) => s + d + 1
          case _            => s + 1
        }
        else s + 1

      fs = fs.updated(s, Tombstone(next - s))

      // Calculate first index of preceding tombstone sequence
      val first =
        if (s > 0) {
          fs(s - 1) match {
            case Tombstone(d) if d < 0  => if (s + d >= 0) s + d else 0
            case Tombstone(d) if d == 1 => s - 1
            case Tombstone(d)           => throw new IllegalStateException("tombstone indicate wrong position: " + d)
            case _                      => s
          }
        } else s
      fs = fs.updated(first, Tombstone(next - first))

      // Calculate last index of succeeding tombstone sequence
      val last = next - 1
      if (last != first) {
        fs = fs.updated(last, Tombstone(first - 1 - last))
      }
      new UpdateOrderLinkedMap(fs :+ key, underlying.updated(key, (fields.length, value)))
    }
  }

  def iterator: Iterator[(K, V)] = new AbstractIterator[(K, V)] {
    private[this] val fieldsLength = fields.length
    private[this] var slot         = -1
    private[this] var key: K       = null.asInstanceOf[K]

    @tailrec
    final private[this] def nextValidField(slot: Int): (Int, K) =
      if (slot >= fields.size) (-1, null.asInstanceOf[K])
      else
        fields(slot) match {
          case Tombstone(distance) => nextValidField(slot + distance)
          case k /*: K | Null */   => (slot, k.asInstanceOf[K])
        }

    final private[this] def advance(): Unit = {
      val nextSlot = slot + 1
      if (nextSlot >= fieldsLength) {
        slot = fieldsLength
        key = null.asInstanceOf[K]
      } else {
        nextValidField(nextSlot) match {
          case (-1, _) =>
            slot = fieldsLength
            key = null.asInstanceOf[K]
          case (s, k) =>
            slot = s
            key = k
        }
      }
    }

    advance()

    override def knownSize: Int = self.size

    override def hasNext: Boolean = slot < fieldsLength

    override def next(): (K, V) =
      if (!hasNext) Iterator.empty.next()
      else {
        val result = (key, underlying(key)._2)
        advance()
        result
      }
  }

  def reverseIterator: Iterator[(K, V)] = new AbstractIterator[(K, V)] {
    private[this] var slot   = fields.length
    private[this] var key: K = null.asInstanceOf[K]

    @tailrec
    final private[this] def nextValidField(slot: Int): (Int, K) =
      if (slot < 0) (-1, null.asInstanceOf[K])
      else
        fields(slot) match {
          case Tombstone(d) if d < 0  => nextValidField(slot + d)
          case Tombstone(d) if d == 1 => nextValidField(slot - 1)
          case Tombstone(d)           => throw new IllegalStateException("tombstone indicate wrong position: " + d)
          case k                      => (slot, k.asInstanceOf[K])
        }

    final private[this] def advance(): Unit = {
      val nextSlot = slot - 1
      if (nextSlot < 0) {
        slot = -1
        key = null.asInstanceOf[K]
      } else {
        nextValidField(nextSlot) match {
          case (-1, _) =>
            slot = -1
            key = null.asInstanceOf[K]
          case (s, k) =>
            slot = s
            key = k
        }
      }
    }

    advance()

    override def knownSize: Int = self.size

    override def hasNext: Boolean = slot >= 0

    override def next(): (K, V) =
      if (!hasNext) Iterator.empty.next()
      else {
        val result = (key, underlying(key)._2)
        advance()
        result
      }
  }

  def toList: List[(K, V)] = iterator.toList

  lazy val reversedLazyList: LazyList[(K, V)] = LazyList.from(reverseIterator)

  override def hashCode(): Int = MurmurHash3.orderedHash(iterator)
}

object UpdateOrderLinkedMap {
  private final case class Tombstone(distance: Int)

  private[this] final val EmptyMap: UpdateOrderLinkedMap[Nothing, Nothing] =
    new UpdateOrderLinkedMap[Nothing, Nothing](Vector.empty[Nothing], HashMap.empty[Nothing, (Int, Nothing)])

  def empty[K, V]: UpdateOrderLinkedMap[K, V] = EmptyMap.asInstanceOf[UpdateOrderLinkedMap[K, V]]

  def from[K, V](it: IterableOnce[(K, V)]): UpdateOrderLinkedMap[K, V] = {
    val builder = newBuilder[K, V]
    builder.addAll(it)
    builder.result()
  }

  def newBuilder[K, V]: UpdateOrderLinkedMap.Builder[K, V] = new UpdateOrderLinkedMap.Builder[K, V]

  final class Builder[K, V] { self =>
    private[this] val vectorBuilder                       = new VectorBuilder[K]
    private[this] val mapBuilder                          = HashMap.newBuilder[K, (Int, V)]
    private[this] var aliased: UpdateOrderLinkedMap[K, V] = _

    def clear(): Unit = {
      vectorBuilder.clear()
      mapBuilder.clear()
      aliased = null
    }

    def result(): UpdateOrderLinkedMap[K, V] = {
      if (aliased eq null) {
        aliased = new UpdateOrderLinkedMap(vectorBuilder.result(), mapBuilder.result())
      }
      aliased
    }
    def addOne(key: K, value: V): UpdateOrderLinkedMap.Builder[K, V] = {
      if (aliased ne null) {
        aliased = aliased.updated(key, value)
      } else {
        val vectorSize = vectorBuilder.size
        vectorBuilder.addOne(key)
        mapBuilder.addOne(key, (vectorSize, value))
      }
      this
    }

    def addOne(elem: (K, V)): UpdateOrderLinkedMap.Builder[K, V] = addOne(elem._1, elem._2)

    def addAll(xs: IterableOnce[(K, V)]): UpdateOrderLinkedMap.Builder[K, V] = {
      xs.iterator.foreach(addOne)
      self
    }
  }

}
