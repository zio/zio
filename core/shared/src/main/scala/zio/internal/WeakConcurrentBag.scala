package zio.internal

import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReferenceArray
import scala.annotation.tailrec

/**
 * A [[WeakConcurrentBag]] stores a collection of values that will ultimately be
 * wrapped in a `WeakReference`, if they are survive long enough in a 'nursery'.
 * The structure is optimized for addition, and will achieve zero allocations in
 * the happy path. There is no way to remove values from the bag, as they will
 * be automatically removed assuming they become unreachable.
 *
 * The larger the nursery size, the less weak references will be created,
 * because the more values will die before they are forced out of the nursery.
 * However, larger nursery sizes use more memory, and increase the worst
 * possible performance of the `add` method, which has to do maintenance of the
 * nursery and occassional garbage collection.
 */
private[zio] class WeakConcurrentBag[A](nurserySize: Int, isAlive: A => Boolean) {
  val nursery   = RingBuffer[A](nurserySize)
  val graduates = Platform.newConcurrentSet[WeakReference[A]]()(zio.Unsafe.unsafe)

  /**
   * Adds a new value to the weak concurrent bag, graduating nursery occupants
   * if necessary to make room.
   */
  final def add(a: A): Unit =
    if (!nursery.offer(a)) {
      graduate()
      add(a)
    }

  /**
   * Performs a garbage collection, which consists of traversing long-term
   * storage, identifying dead or GC'd values, and removing them.
   */
  final def gc(): Unit = {
    val iterator = graduates.iterator()

    while (iterator.hasNext()) {
      val weakRef = iterator.next()
      val value   = weakRef.get()

      if (value == null) {
        graduates.remove(weakRef) // TODO: Reuse weakref
      } else {
        if (!isAlive(value)) graduates.remove(weakRef)
      }
    }
  }

  /**
   * Moves all occupants of the nursery into long-term storage in a concurrent
   * set, wrapped in a weak reference to avoid interfering with GC.
   *
   * This method will occassionally perform garbage collection, but only when
   * the size of long-term storage exceeds the nursery size.
   */
  final def graduate(): Unit = {
    var element       = nursery.poll(null.asInstanceOf[A])

    while (element != null) {
      if (isAlive(element)) {
        val weakRef = new WeakReference[A](element)

        graduates.add(weakRef)
      }

      element = nursery.poll(null.asInstanceOf[A])
    }

    if (graduates.size() > nurserySize) {
      gc()
    }
  }

  /**
   * Returns a weakly consistent iterator over the bag. This iterator will never
   * throw exceptions even in the presence of concurrent modifications.
   */
  final def iterator: Iterator[A] = {
    graduate()

    new Iterator[A] {
      val it    = graduates.iterator()
      var _next = prefetch()

      @tailrec
      def prefetch(): A =
        if (it.hasNext()) {
          val next = it.next().get()

          if (next == null) prefetch()
          else next
        } else {
          null.asInstanceOf[A]
        }

      def hasNext() = _next != null

      def next(): A =
        if (_next == null)
          throw new NoSuchElementException("There is no more element in the weak concurrent bag iterator")
        else {
          val result = _next

          _next = prefetch()

          result
        }
    }
  }

  /**
   * Returns the approximate size of the bag.
   */
  def size = graduates.size() + nursery.size()

  override final def toString(): String = iterator.mkString("WeakConcurrentBag(", ",", ")")
}
private[zio] object WeakConcurrentBag {
  def apply[A](capacity: Int, isAlive: A => Boolean = (_: A) => true): WeakConcurrentBag[A] =
    new WeakConcurrentBag(capacity, isAlive)
}
