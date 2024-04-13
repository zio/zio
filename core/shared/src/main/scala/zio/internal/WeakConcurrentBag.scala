package zio.internal

import zio.Unsafe

import java.lang.ref.WeakReference
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Predicate
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
private[zio] class WeakConcurrentBag[A <: AnyRef](nurserySize: Int, isAlive: A => Boolean) {
  private[this] implicit val unsafe: Unsafe = Unsafe.unsafe

  private[this] val poolSize = java.lang.Runtime.getRuntime.availableProcessors
  private[this] val gcStatus = new AtomicBoolean(false)

  val nursery   = new PartitionedRingBuffer[WeakReference[A]](poolSize << 1, nurserySize)
  val graduates = Platform.newConcurrentSet[WeakReference[A]](nursery.capacity)

  /**
   * Adds a new value to the weak concurrent bag, graduating nursery occupants
   * if necessary to make room.
   */
  final def add(a: A): Unit = {
    val rnd = ThreadLocalRandom.current()
    val ref = new WeakReference(a)

    /**
     * NOTE on misses:
     *
     * If we're start missing when offering, we're approaching the nursery
     * capacity limit. There's no reason to try all the underlying partitions as
     * we'll very soon need to GC anyways.
     *
     * Similar, when graduating, if we're missing it means the nursery is almost
     * empty, so no need to keep trying all the partitions
     */
    @tailrec
    def loop(): Unit =
      if (!nursery.offer(ref, rnd, maxMisses = 0)) {
        graduate(rnd, upTo = 500, maxMisses = 0)
        loop()
      }
    loop()
  }

  /**
   * Performs a garbage collection, which consists of traversing long-term
   * storage, identifying dead or GC'd values, and removing them.
   */
  final def gc(): Unit =
    if (gcStatus.compareAndSet(false, true)) {
      graduates.removeIf(gcPredicate)
      gcStatus.set(false)
    }

  private[this] val gcPredicate: Predicate[WeakReference[A]] = { ref =>
    val value = ref.get()
    (value eq null) || !isAlive(value)
  }

  /**
   * Moves all occupants of the nursery into long-term storage in a concurrent
   * set, wrapped in a weak reference to avoid interfering with GC.
   *
   * This method will occassionally perform garbage collection, but only when
   * the size of long-term storage exceeds the nursery size.
   */
  final def graduate(): Unit =
    graduate(ThreadLocalRandom.current(), upTo = Int.MaxValue, maxMisses = Int.MaxValue)

  final private def graduate(random: ThreadLocalRandom, upTo: Int, maxMisses: Int): Unit = {
    var ref = nursery.poll(null, random, maxMisses)
    var i   = 0
    while ((ref ne null) && i < upTo) {
      val value = ref.get()
      if ((value ne null) && isAlive(value)) {
        graduates.add(ref)
      }
      ref = nursery.poll(null, random, maxMisses)
      i += 1
    }

    if (graduates.size() > nurserySize) gc()
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
        if (it.hasNext) {
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
  def apply[A <: AnyRef](capacity: Int, isAlive: A => Boolean = (_: A) => true): WeakConcurrentBag[A] =
    new WeakConcurrentBag(capacity, isAlive)
}
