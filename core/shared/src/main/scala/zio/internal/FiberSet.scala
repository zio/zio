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

import zio.{Chunk, Duration, Unsafe}
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.lang.ref.{ReferenceQueue, WeakReference}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import scala.annotation.tailrec

/**
 * A [[FiberSet]] is a high-performance concurrent weak set optimized for fiber
 * tracking in ZIO. It is designed to be Loom-friendly by minimizing lock
 * contention and supporting efficient operations under high concurrency.
 *
 * Key features:
 *   - Lock-free concurrent add via CAS on partitioned ring buffers
 *   - O(1) explicit remove for clean fiber termination
 *   - ReferenceQueue-based cleanup for GC'd entries
 *   - Eventually consistent iteration
 *
 * OPTIMIZATION: The nursery uses simple WeakReference (no hash computation).
 * Hash is only computed when entries graduate to long-term storage.
 */
private[zio] final class FiberSet[A <: AnyRef](
  nurserySize: Int,
  isAlive: FiberSet.IsAlive[A]
) { self =>

  import FiberSet._

  private[this] def nCpu = java.lang.Runtime.getRuntime.availableProcessors()

  // Nursery uses simple WeakReference for zero-overhead adds (no hash computation)
  private[this] val nursery           = new PartitionedRingBuffer[WeakReference[A]](nCpu * 4, nurserySize, roundToPow2 = true)
  private[this] val nurseryActualSize = nursery.capacity

  // Graduated entries use Entry with pre-computed hash for O(1) removal
  private[this] val graduates = new ConcurrentHashMap[Entry[A], Entry[A]](nurseryActualSize * 2)

  // Reference queue for automatic cleanup of GC'd graduated entries
  private[this] val refQueue = new ReferenceQueue[A]()

  // GC/cleanup status flags
  private[this] val gcStatus       = new AtomicBoolean(false)
  private[this] val cleanerStarted = new AtomicBoolean(false)

  /**
   * Schedules a background cleaner thread that periodically polls the reference
   * queue and removes cleared entries from graduated storage.
   */
  def withAutoCleaner(every: Duration): FiberSet[A] = {
    if (cleanerStarted.compareAndSet(false, true)) {
      FiberSetCleaner.start(self, refQueue, every)
    }
    self
  }

  /**
   * Adds a new value to the fiber set. This operation is lock-free.
   *
   * OPTIMIZED: Uses simple WeakReference in nursery (no hash computation). Hash
   * is only computed when entry graduates to long-term storage.
   */
  final def add(a: A): Unit = {
    // Hot path: simple WeakReference, no hash computation
    val ref = new WeakReference[A](a)

    val flushed = maybeFlushAndOffer(ref)

    if (flushed.nonEmpty) {
      addToGraduates(flushed)
      if (graduates.size() > nurseryActualSize) gc(false)
    }
  }

  /**
   * Removes a value from the fiber set. O(1) removal for graduated entries.
   */
  final def remove(a: A): Boolean = {
    val probe = new ProbeEntry[A](a)
    val entry = graduates.remove(probe)
    if (entry ne null) {
      entry.clear()
      true
    } else {
      false
    }
  }

  /**
   * Cleans up entries that have been garbage collected via ReferenceQueue.
   */
  final def pollRefQueue(): Unit = {
    var ref = refQueue.poll()
    while (ref ne null) {
      ref match {
        case entry: Entry[A @unchecked] =>
          graduates.remove(entry)
        case _ => // Nursery refs don't use ref queue
      }
      ref = refQueue.poll()
    }
  }

  /**
   * Performs garbage collection by removing dead entries from graduated
   * storage.
   */
  final def gc(): Unit = gc(true)

  final def gc(force: Boolean): Unit = {
    val lockAcquired = gcStatus.compareAndSet(false, true)

    try {
      if (force || lockAcquired) {
        pollRefQueue()

        val iter = graduates.values().iterator()
        while (iter.hasNext) {
          val entry = iter.next()
          val value = entry.get()
          if ((value eq null) || !isAlive(value)) {
            iter.remove()
          }
        }
      }
    } finally {
      if (lockAcquired) gcStatus.set(false)
    }
  }

  /**
   * Forces all nursery entries to graduate to long-term storage.
   */
  final def graduate(): Unit = {
    flushNurseryToGraduates()
    if (graduates.size() > nurseryActualSize) gc(false)
  }

  private def flushNurseryToGraduates(): Unit = {
    val partitions = nursery.partitionIterator

    while (partitions.hasNext) {
      val partition = partitions.next()
      addToGraduates(partition.pollUpTo(partition.capacity))
    }
  }

  private def maybeFlushAndOffer(ref: WeakReference[A]): Chunk[WeakReference[A]] = {
    val queue = nursery.randomPartition(ThreadLocalRandom.current())
    if (!queue.offer(ref)) {
      val flushed = queue.pollUpTo(queue.capacity >> 1)
      if (queue.offer(ref)) flushed else flushed :+ ref
    } else Chunk.empty
  }

  private def addToGraduates(chunk: Chunk[WeakReference[A]]): Unit = {
    var i    = 0
    val iter = chunk.chunkIterator
    while (iter.hasNextAt(i)) {
      val ref   = iter.nextAt(i)
      val value = ref.get()
      if ((value ne null) && isAlive(value)) {
        // Cold path: create Entry with hash only when graduating
        val entry = new Entry[A](value, refQueue)
        graduates.put(entry, entry)
      }
      i += 1
    }
  }

  /**
   * Returns a weakly consistent iterator over the fiber set.
   */
  final def iterator: Iterator[A] = {
    pollRefQueue()
    flushNurseryToGraduates()

    new Iterator[A] {
      private[this] val it    = graduates.values().iterator()
      private[this] var _next = prefetch()

      @tailrec
      private def prefetch(): A =
        if (it.hasNext) {
          val entry = it.next()
          val value = entry.get()

          if ((value eq null) || !isAlive(value)) {
            it.remove()
            prefetch()
          } else value
        } else {
          null.asInstanceOf[A]
        }

      def hasNext: Boolean = _next ne null

      def next(): A = {
        if (_next eq null) {
          throw new NoSuchElementException("No more elements in FiberSet iterator")
        }
        val result = _next
        _next = prefetch()
        result
      }
    }
  }

  /**
   * Returns true if the fiber set is empty.
   */
  def isEmpty: Boolean = graduates.isEmpty && nursery.isEmpty()

  /**
   * Iterates over all live entries in the fiber set.
   */
  def forEach(f: A => Unit): Unit = {
    val iter = iterator
    while (iter.hasNext) {
      f(iter.next())
    }
  }

  /**
   * Returns the approximate size of the fiber set.
   */
  def size: Int = graduates.size() + nursery.size()

  override def toString: String = iterator.mkString("FiberSet(", ", ", ")")
}

private[zio] object FiberSet {

  def apply[A <: AnyRef](capacity: Int, isAlive: IsAlive[A] = IsAlive.always): FiberSet[A] =
    new FiberSet[A](capacity, isAlive)

  /**
   * Specialized Function1 that doesn't cause boxing of the Boolean.
   */
  trait IsAlive[-A] {
    def apply(value: A): Boolean
  }

  object IsAlive {
    val always: IsAlive[Any] = _ => true
  }

  /**
   * Entry for graduated storage with pre-computed identity hash.
   */
  private[internal] final class Entry[A <: AnyRef](
    referent: A,
    queue: ReferenceQueue[A]
  ) extends WeakReference[A](referent, queue) {

    private[this] val identityHash: Int = System.identityHashCode(referent)

    override def hashCode(): Int = identityHash

    override def equals(obj: Any): Boolean = obj match {
      case other: Entry[_] =>
        val thisRef  = this.get()
        val otherRef = other.get()
        if ((thisRef ne null) && (otherRef ne null)) {
          thisRef eq otherRef
        } else {
          this eq other
        }
      case probe: ProbeEntry[_] =>
        val thisRef = this.get()
        (thisRef ne null) && (thisRef eq probe.value)
      case _ => false
    }
  }

  /**
   * Probe entry for O(1) lookups without holding weak reference.
   */
  private[internal] final class ProbeEntry[A <: AnyRef](val value: A) {
    private[this] val identityHash: Int = System.identityHashCode(value)

    override def hashCode(): Int = identityHash

    override def equals(obj: Any): Boolean = obj match {
      case entry: Entry[_] =>
        val entryRef = entry.get()
        (entryRef ne null) && (entryRef eq value)
      case other: ProbeEntry[_] =>
        value eq other.value
      case _ => false
    }
  }
}
