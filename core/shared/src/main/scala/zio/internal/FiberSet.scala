/*
 * Copyright 2022-2024 John A. De Goes and the ZIO Contributors
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

import zio.internal.FiberSet.{IsAlive, WeakRef}

import java.lang.ref.{ReferenceQueue, WeakReference}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.{AtomicInteger, AtomicReferenceArray}

/**
 * A [[FiberSet]] is a lock-free concurrent bag optimized for fiber lifecycle
 * tracking. New entries land in a striped nursery of strong references; they
 * are only wrapped in a [[WeakReference]] when evicted from their nursery slot.
 * Short-lived fibers that are [[remove]]d before eviction never allocate a
 * [[WeakReference]] at all.
 *
 * Stale weak references are reclaimed in small, bounded batches on each [[add]]
 * and [[remove]] call, eliminating the need for a background GC thread and
 * keeping the structure fully compatible with Project Loom virtual threads.
 *
 * The `nurserySize` controls the total strong-reference capacity across all
 * stripes. The `concurrencyLevel` is rounded up to the nearest power of two to
 * determine the stripe count; higher values reduce CAS contention at the cost
 * of slightly more memory.
 */
private[zio] final class FiberSet[A <: AnyRef](
  nurserySize: Int,
  concurrencyLevel: Int,
  isAlive: IsAlive[A]
) {

  private[this] val nStripes: Int   = FiberSet.nextPow2(concurrencyLevel.max(1))
  private[this] val stripeMask: Int = nStripes - 1
  private[this] val stripeSize: Int = FiberSet.nextPow2((nurserySize / nStripes).max(4))
  private[this] val slotMask: Int   = stripeSize - 1

  // Striped nursery: strong refs, no WeakReference allocated for short-lived entries.
  private[this] val nursery: Array[AtomicReferenceArray[AnyRef]] =
    Array.tabulate(nStripes)(_ => new AtomicReferenceArray[AnyRef](stripeSize))
  // Per-stripe write cursors; wrap around via slotMask so each slot is visited
  // in round-robin order, giving every slot a fair chance to drain to long-term.
  private[this] val cursors: Array[AtomicInteger] =
    Array.fill(nStripes)(new AtomicInteger(0))

  // Long-term storage: WeakReferences keyed by identity for O(1) remove.
  private[this] val longTerm: ConcurrentHashMap[WeakRef[A], java.lang.Boolean] =
    new ConcurrentHashMap[WeakRef[A], java.lang.Boolean](stripeSize * nStripes * 2)
  private[this] val refQueue: ReferenceQueue[A] = new ReferenceQueue[A]()

  // Drain at most this many cleared refs per mutation to bound latency.
  private[this] final val DrainBatch = 16
  // Full sweep of long-term storage is triggered when it exceeds this threshold.
  private[this] final val GcThreshold = nStripes * stripeSize

  /**
   * Adds an entry to this set. The entry is placed in a randomly chosen nursery
   * slot, atomically evicting any previous occupant. If the evicted occupant is
   * still alive it is graduated to long-term weak storage. Lock-free.
   */
  final def add(a: A): Unit = {
    drainQueue()
    val tlr    = ThreadLocalRandom.current()
    val stripe = tlr.nextInt() & stripeMask
    val arr    = nursery(stripe)
    val idx    = cursors(stripe).getAndIncrement() & slotMask
    val prev   = arr.getAndSet(idx, a.asInstanceOf[AnyRef])
    if (prev ne null) {
      val evicted = prev.asInstanceOf[A]
      if (isAlive(evicted))
        longTerm.put(new WeakRef[A](evicted, refQueue), java.lang.Boolean.TRUE)
    }
    if (longTerm.size() > GcThreshold) sweepLongTerm()
  }

  /**
   * Removes an entry from this set using identity comparison. The nursery is
   * scanned first; if the entry is not found there it is looked up and removed
   * from long-term storage. Lock-free.
   */
  final def remove(a: A): Unit = {
    drainQueue()
    var s = 0
    while (s < nStripes) {
      val arr = nursery(s)
      var i   = 0
      while (i < stripeSize) {
        if (arr.get(i) eq a) {
          arr.compareAndSet(i, a.asInstanceOf[AnyRef], null)
          return
        }
        i += 1
      }
      s += 1
    }
    longTerm.remove(new WeakRef[A](a, null))
  }

  /**
   * Returns `true` if this set contains no live entries. Weakly consistent.
   */
  final def isEmpty: Boolean = {
    var s = 0
    while (s < nStripes) {
      val arr = nursery(s)
      var i   = 0
      while (i < stripeSize) {
        val raw = arr.get(i)
        if ((raw ne null) && isAlive(raw.asInstanceOf[A])) return false
        i += 1
      }
      s += 1
    }
    val it = longTerm.keySet().iterator()
    while (it.hasNext) {
      val v = it.next().get()
      if ((v ne null) && isAlive(v)) return false
    }
    true
  }

  /**
   * Applies `f` to every live entry currently in this set. Weakly consistent;
   * entries added or removed concurrently may or may not be observed.
   */
  final def forEach(f: A => Unit): Unit = {
    var s = 0
    while (s < nStripes) {
      val arr = nursery(s)
      var i   = 0
      while (i < stripeSize) {
        val raw = arr.get(i)
        if (raw ne null) {
          val a = raw.asInstanceOf[A]
          if (isAlive(a)) f(a)
        }
        i += 1
      }
      s += 1
    }
    val it = longTerm.keySet().iterator()
    while (it.hasNext) {
      val ref = it.next()
      val v   = ref.get()
      if ((v ne null) && isAlive(v)) f(v)
    }
  }

  /**
   * Returns a weakly consistent iterator over live entries. The iterator is
   * backed by a snapshot collected at the moment of the call and will never
   * throw even in the presence of concurrent modifications.
   */
  final def iterator: Iterator[A] = {
    val buf = new java.util.ArrayList[A](GcThreshold)
    forEach(a => buf.add(a))
    import scala.jdk.CollectionConverters._
    buf.iterator().asScala
  }

  /**
   * Returns the approximate number of entries, including entries that may have
   * already been collected by the GC but not yet reclaimed.
   */
  def size: Int = {
    var n = 0
    var s = 0
    while (s < nStripes) {
      val arr = nursery(s)
      var i   = 0
      while (i < stripeSize) {
        if (arr.get(i) ne null) n += 1
        i += 1
      }
      s += 1
    }
    n + longTerm.size()
  }

  override def toString: String = iterator.mkString("FiberSet(", ", ", ")")

  // Drains at most DrainBatch cleared refs from the GC queue, removing each
  // from long-term storage in O(1) via its precomputed identity hash.
  private[this] def drainQueue(): Unit = {
    var n = 0
    var r = refQueue.poll()
    while ((r ne null) && n < DrainBatch) {
      longTerm.remove(r.asInstanceOf[WeakRef[A]])
      n += 1
      r = refQueue.poll()
    }
  }

  // Full scan of long-term storage; triggered when size exceeds GcThreshold.
  private[this] def sweepLongTerm(): Unit = {
    val it = longTerm.keySet().iterator()
    while (it.hasNext) {
      val ref = it.next()
      val v   = ref.get()
      if ((v eq null) || !isAlive(v)) it.remove()
    }
  }
}

private[zio] object FiberSet {

  def apply[A <: AnyRef](
    nurserySize: Int,
    concurrencyLevel: Int = 1,
    isAlive: IsAlive[A] = IsAlive.always
  ): FiberSet[A] = new FiberSet(nurserySize, concurrencyLevel, isAlive)

  /**
   * Specialized predicate for liveness checks. Defined as a trait rather than
   * `Function1` to avoid boxing the `Boolean` return value.
   */
  trait IsAlive[-A] {
    def apply(value: A): Boolean
  }

  object IsAlive {
    val always: IsAlive[Any] = _ => true
  }

  /**
   * A [[WeakReference]] that uses identity semantics for hash and equality. The
   * identity hash is captured at construction time and remains stable after the
   * referent is collected, keeping the entry locatable in the map even after
   * its referent is gone.
   */
  private[internal] final class WeakRef[A <: AnyRef](
    referent: A,
    queue: ReferenceQueue[A]
  ) extends WeakReference[A](referent, queue) {
    private[this] val _hash: Int = System.identityHashCode(referent)

    override def hashCode(): Int = _hash

    override def equals(obj: Any): Boolean = obj match {
      case that: WeakRef[_] =>
        val a = this.get()
        val b = that.get()
        if ((a ne null) && (b ne null)) a eq b else this eq that
      case _ => false
    }
  }

  /**
   * Returns the smallest power of two that is >= `n`, with a minimum of 1.
   */
  private[internal] def nextPow2(n: Int): Int = {
    var v = (n - 1).max(0)
    v |= v >>> 1
    v |= v >>> 2
    v |= v >>> 4
    v |= v >>> 8
    v |= v >>> 16
    v + 1
  }
}
