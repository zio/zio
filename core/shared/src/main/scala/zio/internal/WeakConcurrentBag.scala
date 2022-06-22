package zio.internal

import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReferenceArray
import scala.annotation.tailrec

/**
 * A [[WeakConcurrentBag]] stores a collection of values, each wrapped in a
 * `WeakReference`. The structure is optimized for addition, and will achieve
 * zero allocations in the happy path (aside from the allocation of the
 * `WeakReference`, which is unavoidable). To remove a value from the bag, it is
 * sufficient to clear the corresponding weak reference, at which point the weak
 * reference will be removed from the bag during the next garbage collection.
 *
 * Garbage collection happens regularly during the `add` operation. Assuming
 * uniform distribution of hash codes of values added to the bag, the chance of
 * garbage collection occurring during an `add` operation is 1/n, where `n` is
 * the capacity of the table backing the bag.
 */
private[zio] class WeakConcurrentBag[A](tableSize: Int) {
  import zio.internal.FastList._

  private[this] val contents: AtomicReferenceArray[List[WeakReference[A]]] = new AtomicReferenceArray(tableSize)

  /**
   * Adds the specified value to the concurrent bag, returning a `WeakReference`
   * that wraps the value.
   */
  final def add(value: A): WeakReference[A] = {
    val hashCode = value.hashCode.abs
    val bucket   = hashCode % tableSize

    @tailrec
    def loop(newRef: WeakReference[A]): WeakReference[A] = {
      val oldValue = contents.get(bucket)
      val newValue = newRef :: oldValue

      if (!contents.compareAndSet(bucket, oldValue, newValue)) loop(newRef)
      else newRef
    }

    if (bucket == 0) gc()

    loop(new WeakReference[A](value))
  }

  /**
   * Performs garbage collection, removing any empty weak references.
   */
  final def gc(): Unit = {
    val predicate: WeakReference[A] => Boolean =
      ref => (ref ne null) && (ref.get() != null)

    (0 until tableSize).foreach { bucket =>
      val oldValue = contents.get(bucket)

      if (!oldValue.forall(predicate)) {
        val newValue = oldValue.filter(predicate)

        contents.compareAndSet(bucket, oldValue, newValue)
      }
    }
  }

  /**
   * Returns a weakly consistent iterator over the bag. This iterator will never
   * throw exceptions even in the presence of concurrent modifications.
   */
  final def iterator: Iterator[A] =
    new Iterator[A] {
      var _currentBucket = 0
      var _currentList   = List.empty[WeakReference[A]]
      var _nextElement   = null.asInstanceOf[A]

      prefetchNext()

      override def hasNext: Boolean = _nextElement != null

      override def next(): A = {
        val value = _nextElement

        if (value == null) throw new NoSuchElementException()
        else prefetchNext()

        value
      }

      private def prefetchNext(): Unit = {
        val bucketCount = tableSize

        var nextElement   = null.asInstanceOf[A]
        var currentList   = _currentList
        var currentBucket = _currentBucket

        while ((currentBucket < bucketCount || currentList.nonEmpty) && (nextElement == null)) {
          if (currentList.isEmpty) {
            currentList = contents.get(currentBucket)
            currentBucket = currentBucket + 1
          } else {
            nextElement = currentList.head.get()
            currentList = currentList.tail
          }
        }

        _nextElement = nextElement
        _currentList = currentList
        _currentBucket = currentBucket
      }
    }

  /**
   * Returns the size of the bag. Due to concurrent modification, this is only
   * an estimate. Note this operation is O(n.max(m)), where n is the number of
   * elements in the collection, and m is the table size.
   */
  final def size: Int =
    (0 until tableSize).foldLeft(0) { case (sum, bucket) =>
      sum + contents.get(bucket).size
    }

  override final def toString(): String = iterator.mkString("WeakConcurrentBag(", ",", ")")
}
private[zio] object WeakConcurrentBag {
  def apply[A](tableSize: Int): WeakConcurrentBag[A] = new WeakConcurrentBag(tableSize)
}
