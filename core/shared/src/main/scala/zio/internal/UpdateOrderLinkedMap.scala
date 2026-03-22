/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.internal

import zio.BuildInfo
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.annotation.tailrec
import scala.collection.AbstractIterator
import scala.collection.immutable.{HashMap, VectorBuilder}
import scala.collection.mutable.ListBuffer
import scala.util.hashing.MurmurHash3

/**
 * This Map is optimized for use with ZEnvironment.
 *
 * '''DO NOT''' use for any other purposes.
 */
private[zio] final class UpdateOrderLinkedMap[K, +V] private (
  fields: Vector[Any],
  underlying: Map[K, UpdateOrderLinkedMap.Entry[V]]
) extends Serializable { self =>
  import UpdateOrderLinkedMap._

  def size: Int = underlying.size

  def isEmpty: Boolean = underlying.isEmpty

  def keySet: Set[K] = underlying.keySet

  def updated[V1 >: V](key: K, value: V1): UpdateOrderLinkedMap[K, V1] = {
    val existing = underlying.getOrElse(key, null)
    if (existing eq null) {
      new UpdateOrderLinkedMap(fields :+ key, underlying.updated(key, Entry(fields.size, value)))
    } else if (existing.idx == fields.size - 1) {
      // If the entry to be added is at the tail of the fields, we can just update the value
      new UpdateOrderLinkedMap(fields, underlying.updated(key, existing.copy(value = value)))
    } else {
      var fs     = fields
      val oldIdx = existing.idx

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

      new UpdateOrderLinkedMap(fs :+ key, underlying.updated(key, Entry(fs.length, value))).maybeReindex()
    }
  }

  /**
   * Rebuilds the underlying vector and map, removing tombstones and reindexing
   * the elements, but only if the number of dead elements exceeds 10000.
   *
   * This should never happen, but we add it as a safeguard against memory leaks
   * due to weird usage patterns.
   */
  private def maybeReindex(): UpdateOrderLinkedMap[K, V] =
    if (self.fields.size - size > 10000) fromUnsafe(iterator0)
    else self

  def iterator: Iterator[(K, V)] =
    if (underlying.size == 1) {
      val kv = underlying.head
      Iterator.single((kv._1, kv._2.value))
    } else iteratorLz.iterator

  @transient
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
      fields(nextSlot) match {
        case Tombstone(d) => findNextKey(nextSlot + d)
        case k =>
          slot = nextSlot
          k.asInstanceOf[K]
      }

    override final def hasNext: Boolean = slot < fieldsLength - 1

    override final def next(): (K, V) =
      if (!hasNext) Iterator.empty.next()
      else {
        val key = findNextKey(slot + 1)
        (key, underlying(key).value)
      }
  }

  def reverseIterator: Iterator[(K, V)] =
    if (underlying.size == 1) {
      val kv = underlying.head
      Iterator.single((kv._1, kv._2.value))
    } else reverseIteratorLz.iterator

  @transient
  private[this] lazy val reverseIteratorLz: LzList[(K, V)] = {
    val it = reverseIterator0
    def loop(): LzList[(K, V)] =
      if (it.hasNext) LzList(it.next(), loop()) else LzList.empty

    loop()
  }

  private def reverseIterator0: Iterator[(K, V)] = new AbstractIterator[(K, V)] {
    private[this] var slot      = fields.length
    private[this] var remaining = underlying.size

    @tailrec
    final private[this] def findNextKey(nextSlot: Int): K =
      fields(nextSlot) match {
        case Tombstone(d) if d < 0  => findNextKey(nextSlot + d)
        case Tombstone(d) if d == 1 => findNextKey(nextSlot - 1)
        case Tombstone(d)           => throw new IllegalStateException("tombstone indicate wrong position: " + d)
        case k =>
          remaining -= 1
          slot = nextSlot
          k.asInstanceOf[K]
      }

    override final def hasNext: Boolean = remaining > 0

    override final def next(): (K, V) =
      if (!hasNext) Iterator.empty.next()
      else {
        val key    = findNextKey(slot - 1)
        val result = (key, underlying(key).value)
        result
      }
  }

  def toList: List[(K, V)] = iterator.toList

  override def hashCode(): Int = MurmurHash3.orderedHash(iterator)
}

private[zio] object UpdateOrderLinkedMap {
  private final case class Tombstone(distance: Int)
  private final case class Entry[+V](idx: Int, value: V)

  private[this] final val EmptyMap: UpdateOrderLinkedMap[Nothing, Nothing] =
    new UpdateOrderLinkedMap[Nothing, Nothing](Vector.empty[Nothing], Map.empty[Nothing, Entry[Nothing]])

  def empty[K, V]: UpdateOrderLinkedMap[K, V] = EmptyMap.asInstanceOf[UpdateOrderLinkedMap[K, V]]

  def fromMap[K, V](map: Map[K, V]): UpdateOrderLinkedMap[K, V] = fromUnsafe(map.iterator)

  def single[K, V](key: K, value: V): UpdateOrderLinkedMap[K, V] =
    new UpdateOrderLinkedMap(
      Vector.empty[K] :+ key, // More efficient than `Vector(key)`
      new Map.Map1(key, Entry(0, value))
    )

  /**
   * Keys in the iterator '''MUST be unique'''!
   */
  private def fromUnsafe[K, V](it: Iterator[(K, V)]): UpdateOrderLinkedMap[K, V] = {
    val vectorBuilder = new VectorBuilder[K]
    val mapBuilder    = HashMap.newBuilder[K, Entry[V]]
    var i             = 0
    while (it.hasNext) {
      val kv = it.next()
      val k  = kv._1
      val v  = kv._2
      vectorBuilder += k
      mapBuilder += ((k, Entry(i, v)))
      i += 1
    }
    new UpdateOrderLinkedMap(vectorBuilder.result(), mapBuilder.result())
  }

  def newBuilder[K, V]: UpdateOrderLinkedMap.Builder[K, V] = new UpdateOrderLinkedMap.Builder[K, V]

  final class Builder[K, V] { self =>
    private[this] val entries = ListBuffer.empty[(K, V)]

    /**
     * Adds a single element to the builder.
     *
     * '''NOTE''': The elements added to this builder MUST be unique!
     */
    def addOne(elem: (K, V)): UpdateOrderLinkedMap.Builder[K, V] = {
      entries += elem
      this
    }

    def clear(): Unit =
      entries.clear()

    def result(): UpdateOrderLinkedMap[K, V] = {
      val entries = this.entries
      entries.length match {
        case 0 =>
          empty
        case 1 =>
          val head = entries.toList.head // faster than calling `entries.head`
          single(head._1, head._2)
        case _ =>
          // NOTE: The compiler removes this assertion in release mode
          assert(
            BuildInfo.optimizationsEnabled || entries.map(_._1).distinct.size == entries.size,
            "Entries added to UpdateOrderLinkedMap.Builder were not unique"
          )
          fromUnsafe(entries.toList.iterator)
      }
    }
  }

  private sealed trait LzList[+A] { self =>
    protected def head: A
    protected def tail: LzList[A]

    final def isEmpty: Boolean = this eq LzList.Empty

    final def iterator: Iterator[A] = new AbstractIterator[A] {
      private[this] var current: LzList[A] = self

      override def hasNext: Boolean = !current.isEmpty

      override def next(): A = {
        // Never call `tail` before `head`!
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
      protected def head: Nothing         = throw new NoSuchElementException("head of empty list")
      protected def tail: LzList[Nothing] = throw new NoSuchElementException("tail of empty list")
    }

    private final class Cons[A](_head: () => A, _tail: () => LzList[A]) extends LzList[A] {
      @transient protected lazy val head: A         = _head()
      @transient protected lazy val tail: LzList[A] = _tail()
    }
  }
}
