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

  def size: Int = underlying.size

  def isEmpty: Boolean = size == 0

  def updated[V1 >: V](key: K, value: V1): UpdateOrderLinkedMap[K, V1] = {
    val existing = underlying.getOrElse(key, null)
    if (existing eq null) {
      new UpdateOrderLinkedMap(self.fields :+ key, underlying.updated(key, (self.fields.size, value)))
    } else if (existing._1 == self.fields.size - 1) {
      // If the entry to be added is at the tail of the fields, we can just update the value
      new UpdateOrderLinkedMap(self.fields, underlying.updated(key, (self.fields.size - 1, value)))
    } else {
      var fs     = fields
      val oldIdx = existing._1

      // Calculate next of kin
      val next = fs(oldIdx + 1) match {
        case Tombstone(d) => oldIdx + d + 1
        case _            => oldIdx + 1
      }

      // Calculate first index of preceding tombstone sequence
      val first =
        if (oldIdx > 0) {
          fs(oldIdx - 1) match {
            case Tombstone(d) if d < 0  => if (oldIdx + d >= 0) oldIdx + d else 0
            case Tombstone(d) if d == 1 => oldIdx - 1
            case Tombstone(d)           => throw new IllegalStateException("tombstone indicate wrong position: " + d)
            case _                      => oldIdx
          }
        } else oldIdx

      // Calculate last index of succeeding tombstone sequence
      val last = next - 1

      fs = fs.updated(first, Tombstone(next - first))
      if (last != first) {
        fs = fs.updated(last, Tombstone(first - 1 - last))
      }
      if (oldIdx != first && oldIdx != last) {
        fs = fs.updated(oldIdx, Tombstone(next - oldIdx))
      }

      new UpdateOrderLinkedMap(fs :+ key, underlying.updated(key, (fields.length, value)))
    }
  }

  def iterator: Iterator[(K, V)] = iteratorLz.iterator

  private[this] lazy val iteratorLz: LzList[(K, V)] = {
    val it = iterator0
    def loop(): LzList[(K, V)] =
      if (it.hasNext) LzList(it.next(), loop()) else LzList.empty
    loop()
  }

  private[this] def iterator0: Iterator[(K, V)] = new AbstractIterator[(K, V)] {
    private[this] val fieldsLength = fields.length
    private[this] var slot         = -1

    @tailrec
    final private[this] def findNextKey(nextSlot: Int): K =
      if (nextSlot >= fieldsLength) {
        slot = fieldsLength
        null.asInstanceOf[K]
      } else
        fields(nextSlot) match {
          case Tombstone(d) => findNextKey(nextSlot + d)
          case k =>
            slot = nextSlot
            k.asInstanceOf[K]
        }

    override def hasNext: Boolean = slot < fieldsLength - 1

    override def next(): (K, V) =
      if (!hasNext) Iterator.empty.next()
      else {
        val key = findNextKey(slot + 1)
        (key, underlying(key)._2)
      }
  }

  def reverseIterator: Iterator[(K, V)] = reverseIteratorLz.iterator

  private[this] lazy val reverseIteratorLz: LzList[(K, V)] = {
    val it = reverseIterator0
    def loop(): LzList[(K, V)] =
      if (it.hasNext) LzList(it.next(), loop()) else LzList.empty

    loop()
  }

  private def reverseIterator0: Iterator[(K, V)] = new AbstractIterator[(K, V)] {
    private[this] var slot = fields.length

    @tailrec
    final private[this] def findNextKey(nextSlot: Int): K =
      if (nextSlot < 0) {
        slot = -1
        null.asInstanceOf[K]
      } else {
        fields(nextSlot) match {
          case Tombstone(d) if d < 0  => findNextKey(nextSlot + d)
          case Tombstone(d) if d == 1 => findNextKey(nextSlot - 1)
          case Tombstone(d)           => throw new IllegalStateException("tombstone indicate wrong position: " + d)
          case k =>
            slot = nextSlot
            k.asInstanceOf[K]
        }
      }

    override def hasNext: Boolean = slot > 0

    override def next(): (K, V) =
      if (!hasNext) Iterator.empty.next()
      else {
        val key    = findNextKey(slot - 1)
        val result = (key, underlying(key)._2)
        result
      }
  }

  def toList: List[(K, V)] = iterator.toList

  override def hashCode(): Int = MurmurHash3.orderedHash(iterator)
}

object UpdateOrderLinkedMap {
  private final case class Tombstone(distance: Int)

  private[this] final val EmptyMap: UpdateOrderLinkedMap[Nothing, Nothing] =
    new UpdateOrderLinkedMap[Nothing, Nothing](Vector.empty[Nothing], HashMap.empty[Nothing, (Int, Nothing)])

  def empty[K, V]: UpdateOrderLinkedMap[K, V] = EmptyMap.asInstanceOf[UpdateOrderLinkedMap[K, V]]

  def from[K, V](it: Iterable[(K, V)]): UpdateOrderLinkedMap[K, V] = {
    val builder = newBuilder[K, V]
    builder.addAll(it)
    builder.result()
  }

  def newBuilder[K, V]: UpdateOrderLinkedMap.Builder[K, V] = new UpdateOrderLinkedMap.Builder[K, V]

  final class Builder[K, V] { self =>
    private[this] val vectorBuilder                       = new VectorBuilder[K]
    private[this] val mapBuilder                          = HashMap.newBuilder[K, (Int, V)]
    private[this] var aliased: UpdateOrderLinkedMap[K, V] = _
    private[this] var size                                = 0

    def clear(): Unit = {
      vectorBuilder.clear()
      mapBuilder.clear()
      aliased = null
      size = 0
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
        val vectorSize = size
        vectorBuilder += key
        mapBuilder += ((key, (vectorSize, value)))
        size += 1
      }
      this
    }

    def addOne(elem: (K, V)): UpdateOrderLinkedMap.Builder[K, V] = addOne(elem._1, elem._2)

    def addAll(xs: Iterable[(K, V)]): UpdateOrderLinkedMap.Builder[K, V] = {
      xs.iterator.foreach(addOne)
      self
    }
  }

  private sealed trait LzList[+A] {
    def head: A
    def tail: LzList[A]

    final def isEmpty: Boolean = this eq LzList.Empty

    final def iterator = new AbstractIterator[A] {
      private[this] var current: LzList[A] = LzList.this

      override def hasNext: Boolean = !current.isEmpty

      override def next(): A = {
        val result = current.head
        current = current.tail
        result
      }
    }
  }

  private object LzList {
    def apply[A](head: => A, tail: => LzList[A]): LzList[A] =
      new Cons(() => head, () => tail)

    def empty[A]: LzList[A] = Empty

    private case object Empty extends LzList[Nothing] {
      def head: Nothing         = throw new NoSuchElementException("head of empty list")
      def tail: LzList[Nothing] = throw new NoSuchElementException("tail of empty list")
    }

    private final class Cons[A](private val _head: () => A, private val _tail: () => LzList[A]) extends LzList[A] {
      @transient lazy val head: A         = _head()
      @transient lazy val tail: LzList[A] = _tail()
    }
  }
}
