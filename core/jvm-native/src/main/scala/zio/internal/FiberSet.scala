package zio.internal

import zio.internal.FiberSet.IsAlive
import zio.Duration

import java.lang.ref.{ReferenceQueue, WeakReference}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference, AtomicReferenceArray}
import scala.annotation.tailrec

private[zio] object FiberSet {
  private val TOMBSTONE: AnyRef = new AnyRef
  private val MAX_CAPACITY      = 1 << 30

  private def roundToPow2(n: Int): Int = {
    var v = n - 1
    v |= v >> 1; v |= v >> 2; v |= v >> 4; v |= v >> 8; v |= v >> 16
    (v + 1) max 16
  }

  private final class WeakEntry[A <: AnyRef](referent: A, queue: ReferenceQueue[AnyRef], val hash: Int)
      extends WeakReference[A](referent, queue.asInstanceOf[ReferenceQueue[A]])

  /** Specialized Function1 that doesn't cause boxing of the Boolean */
  trait IsAlive[-A] {
    def apply(value: A): Boolean
  }

  object IsAlive {
    val always: IsAlive[Any] = _ => true
  }
}

private[zio] final class FiberSet[A <: AnyRef](
  initialCapacity: Int,
  isAlive: IsAlive[A],
  autoGcEvery: Option[Duration]
) extends FiberSetPlatformSpecific[A](initialCapacity, isAlive, autoGcEvery) {
  import FiberSet.{MAX_CAPACITY, TOMBSTONE, WeakEntry, roundToPow2}

  private[this] val refQueue = new ReferenceQueue[AnyRef]()
  private[this] val tableRef = new AtomicReference[AtomicReferenceArray[AnyRef]](
    new AtomicReferenceArray[AnyRef](roundToPow2(initialCapacity))
  )
  private[this] val count    = new AtomicInteger(0)
  private[this] val gcStatus = new AtomicBoolean(false)

  private[this] val DRAIN_BATCH_CAP = 16

  if (autoGcEvery.isDefined) FiberSetGc.start(this, autoGcEvery.get)

  private[this] def drainRefQueue(): Unit = {
    var i   = 0
    var ref = refQueue.poll().asInstanceOf[WeakEntry[A]]
    while ((ref ne null) && i < DRAIN_BATCH_CAP) {
      clearDeadEntry(ref)
      ref = refQueue.poll().asInstanceOf[WeakEntry[A]]
      i += 1
    }
  }

  private[this] def clearDeadEntry(ref: WeakEntry[A]): Unit = {
    val t     = tableRef.get()
    val start = ref.hash & (t.length() - 1)
    var i     = start
    var done  = false

    while (!done) {
      val slot = t.get(i)
      if (slot eq ref) {
        if (t.compareAndSet(i, ref, null)) count.decrementAndGet()
        done = true
      } else if (slot eq null) {
        done = true
      } else {
        i = (i + 1) & (t.length() - 1)
        if (i == start) done = true
      }
    }
  }

  def add(a: A): Unit = {
    drainRefQueue()

    val entry = new WeakEntry(a, refQueue, System.identityHashCode(a))
    val table = tableRef.get()
    if (count.get() >= table.length() * 3 / 4) maybeResize()

    val t     = tableRef.get()
    val start = entry.hash & (t.length() - 1)
    probe(t, a, entry, start, start)
  }

  @tailrec
  private[this] def probe(t: AtomicReferenceArray[AnyRef], a: A, entry: WeakEntry[A], i: Int, probeStart: Int): Unit = {
    val slot = t.get(i)
    if (slot eq null) {
      if (!t.compareAndSet(i, null, entry)) probe(t, a, entry, i, probeStart)
      else count.incrementAndGet()
    } else if (slot eq TOMBSTONE) {
      if (!t.compareAndSet(i, TOMBSTONE, entry)) probe(t, a, entry, i, probeStart)
      else count.incrementAndGet()
    } else {
      val existing = slot.asInstanceOf[WeakEntry[A]]
      val v        = existing.get()
      if ((v ne null) && (v eq a)) ()
      else {
        val next = (i + 1) & (t.length() - 1)
        if (next == probeStart) {
          maybeResize()
          val resized = tableRef.get()
          val start   = entry.hash & (resized.length() - 1)
          probe(resized, a, entry, start, start)
        } else probe(t, a, entry, next, probeStart)
      }
    }
  }

  def remove(a: A): Unit = {
    drainRefQueue()
    val t     = tableRef.get()
    val start = System.identityHashCode(a) & (t.length() - 1)
    removeProbe(t, a, start, start)
  }

  @tailrec
  private[this] def removeProbe(t: AtomicReferenceArray[AnyRef], a: A, i: Int, probeStart: Int): Unit = {
    val slot = t.get(i)
    if (slot eq null) ()
    else if (slot eq TOMBSTONE) {
      val next = (i + 1) & (t.length() - 1)
      if (next != probeStart) removeProbe(t, a, next, probeStart)
    } else {
      val existing = slot.asInstanceOf[WeakEntry[A]]
      val v        = existing.get()
      if ((v ne null) && (v eq a)) {
        if (!t.compareAndSet(i, existing, TOMBSTONE)) removeProbe(t, a, i, probeStart)
        else count.decrementAndGet()
      } else {
        val next = (i + 1) & (t.length() - 1)
        if (next != probeStart) removeProbe(t, a, next, probeStart)
      }
    }
  }

  def iterator: Iterator[A] = {
    val t = tableRef.get()

    new Iterator[A] {
      private[this] var idx   = 0
      private[this] var _next = prefetch()

      @tailrec
      def prefetch(): A =
        if (idx < t.length()) {
          val slot = t.get(idx)
          idx += 1
          if ((slot eq null) || (slot eq TOMBSTONE)) prefetch()
          else {
            val v = slot.asInstanceOf[WeakEntry[A]].get()
            if ((v ne null) && isAlive(v)) v else prefetch()
          }
        } else {
          null.asInstanceOf[A]
        }

      def hasNext: Boolean = _next ne null

      def next(): A =
        if (_next eq null)
          throw new NoSuchElementException("There is no more element in the FiberSet iterator")
        else {
          val result = _next
          _next = prefetch()
          result
        }
    }
  }

  def isEmpty: Boolean = count.get() <= 0

  def size: Int = count.get()

  def gc(force: Boolean): Unit = {
    val lockAcquired = gcStatus.compareAndSet(false, true)

    // NOTE: try-finally most probably not needed; just being extra cautious not to accidentally lock GC
    try
      if (force || lockAcquired) {
        if (force) {
          var ref = refQueue.poll().asInstanceOf[WeakEntry[A]]
          while (ref ne null) {
            clearDeadEntry(ref)
            ref = refQueue.poll().asInstanceOf[WeakEntry[A]]
          }
        } else {
          drainRefQueue()
        }

        val t = tableRef.get()
        var i = 0
        while (i < t.length()) {
          val slot = t.get(i)
          if ((slot ne null) && (slot ne TOMBSTONE)) {
            val v = slot.asInstanceOf[WeakEntry[A]].get()
            if (v eq null) {
              if (t.compareAndSet(i, slot, null)) count.decrementAndGet()
            }
          } else if (slot eq TOMBSTONE) {
            t.compareAndSet(i, TOMBSTONE, null)
          }
          i += 1
        }
      }
    finally if (lockAcquired) gcStatus.set(false)
  }

  def withAutoGc(every: Duration): FiberSet[A] = this

  private[this] def maybeResize(): Unit = {
    val old = tableRef.get()
    if (old.length() < MAX_CAPACITY) {
      val cap      = old.length() * 2
      val newTable = new AtomicReferenceArray[AnyRef](cap)
      var i        = 0

      while (i < old.length()) {
        val slot = old.get(i)
        if ((slot ne null) && (slot ne TOMBSTONE)) {
          val entry = slot.asInstanceOf[WeakEntry[A]]
          val v     = entry.get()
          if (v ne null) {
            val start = entry.hash & (cap - 1)
            rehashProbe(newTable, entry, start)
          }
        }
        i += 1
      }

      tableRef.compareAndSet(old, newTable)
      // losing threads re-read tableRef on the next add and probe the new table
    }
  }

  @tailrec
  private[this] def rehashProbe(t: AtomicReferenceArray[AnyRef], entry: WeakEntry[A], i: Int): Unit =
    if (t.get(i) ne null) rehashProbe(t, entry, (i + 1) & (t.length() - 1))
    else if (!t.compareAndSet(i, null, entry)) rehashProbe(t, entry, i)
}
