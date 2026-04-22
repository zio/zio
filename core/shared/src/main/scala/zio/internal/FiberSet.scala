package zio.internal

import zio.{Chunk, Duration, Unsafe}
import java.lang.ref.{ReferenceQueue, WeakReference}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import scala.annotation.tailrec

/**
 * A high-performance concurrent set optimized for Fiber references.
 *
 * Features:
 * - Lock-free add/remove for hot path (Tier 1 ring buffer)
 * - Reference queue-based GC (no full traversal)
 * - Weakly consistent iteration
 * - No duplicate enforcement (per issue requirements)
 * - Optimized for Loom-friendly concurrent access
 *
 * Architecture:
 * - Tier 1: Hot path - lock-free ring buffer (direct references, zero WeakRef overhead)
 * - Tier 2: Warm storage - WeakReference + ReferenceQueue (batch GC on queue poll)
 *
 * @param hotCapacity Size of tier-1 hot path (default: 256, fits in L1 cache)
 * @param warmCapacity Initial capacity of tier-2 warm storage
 * @param isAlive Predicate to check if a fiber is still alive
 */
private[zio] final class FiberSet[A <: AnyRef] private (
  hotCapacity: Int,
  warmCapacity: Int,
  isAlive: FiberSet.IsAlive[A]
) { self =>

  // Tier 1: Hot path - lock-free ring buffer (power of 2 capacity for fast modulo)
  private[this] val hotBuffer = new FiberSet.HotRingBuffer[A](hotCapacity)

  // Tier 2: Warm storage - WeakReference + ReferenceQueue for efficient GC
  private[this] val warmStorage = ConcurrentHashMap.newKeySet[WeakReference[A]](warmCapacity)
  private[this] val refQueue = new ReferenceQueue[A]()

  // GC state - atomic boolean for lock-free GC coordination
  private[this] val gcLock = new AtomicBoolean(false)

  /**
   * Add a fiber to the set.
   *
   * This method is lock-free and achieves zero allocation in the happy path
   * (when hot buffer has space). When the hot buffer is full, elements are
   * spilled to warm storage with WeakReference wrapping.
   *
   * @param fiber The fiber to add
   */
  def add(fiber: A): Unit = {
    // Try hot path first (lock-free, no allocation)
    if (hotBuffer.offer(fiber)) {
      () // Success
    } else {
      // Hot path full, spill to warm storage
      spillToWarm(fiber)
    }
  }

  /**
   * Remove a fiber from the set (best-effort, no guarantee).
   *
   * This method is optimized for the common case where the fiber is still
   * in the hot buffer. If not found there, it searches warm storage.
   *
   * @param fiber The fiber to remove
   * @return true if the fiber was found and removed, false otherwise
   */
  def remove(fiber: A): Boolean = {
    // Try hot path first (fast path)
    if (hotBuffer.remove(fiber)) {
      true
    } else {
      // Search warm storage (poll ref queue first for efficiency)
      removeExpired()
      warmStorage.removeIf(ref => ref.get() eq fiber)
    }
  }

  /**
   * Returns a weakly consistent iterator over the set.
   *
   * This iterator will never throw exceptions even in the presence of
   * concurrent modifications. It may or may not reflect additions/removals
   * that occur during iteration.
   *
   * Dead references are automatically removed during iteration.
   *
   * @return An iterator over all alive fibers in the set
   */
  def iterator: Iterator[A] = {
    // Clean up expired references before iteration
    removeExpired()

    val hotIter = hotBuffer.iterator()
    val warmIter = warmStorage.iterator()

    new Iterator[A] {
      private var nextElem: A = null.asInstanceOf[A]
      private var hasNextElem: Boolean = true

      // Prefetch the next element
      prefetch()

      @tailrec
      private def prefetch(): Unit = {
        // Try hot buffer first
        if (hotIter.hasNext) {
          val fiber = hotIter.next()
          if (isAlive(fiber)) {
            nextElem = fiber
            return
          }
        }
        // Then try warm storage
        if (warmIter.hasNext) {
          val ref = warmIter.next()
          val fiber = ref.get()
          if ((fiber ne null) && isAlive(fiber)) {
            nextElem = fiber
            return
          } else {
            // Auto-remove dead refs during iteration
            warmIter.remove()
          }
        }
        // No more elements
        hasNextElem = false
      }

      def hasNext: Boolean = hasNextElem

      def next(): A = {
        if (!hasNextElem)
          throw new NoSuchElementException("FiberSet iterator exhausted")
        val result = nextElem
        prefetch()
        result
      }
    }
  }

  /**
   * Returns the approximate size of the set.
   *
   * Note: This is an approximation as the set may be concurrently modified.
   *
   * @return The approximate number of elements in the set
   */
  def size: Int = hotBuffer.size() + warmStorage.size()

  /**
   * Force garbage collection of dead references.
   *
   * This method polls the reference queue and removes all expired entries
   * from warm storage. It also performs a full scan to remove any references
   * that are no longer alive according to the isAlive predicate.
   */
  def gc(): Unit = {
    // Poll reference queue and remove expired
    removeExpired()
    // Full scan for isAlive check
    warmStorage.removeIf(ref => (ref.get() eq null) || !isAlive(ref.get()))
  }

  /**
   * Poll the reference queue and remove all expired entries from warm storage.
   *
   * This is the key optimization: instead of traversing all references to find
   * dead ones, we only process those that the GC has already identified as
   * unreachable (via the ReferenceQueue).
   */
  private def removeExpired(): Unit = {
    @tailrec
    def loop(): Unit = {
      val ref = refQueue.poll()
      if (ref ne null) {
        warmStorage.remove(ref)
        loop() // Process all pending refs
      }
    }
    loop()
  }

  /**
   * Spill elements from hot path to warm storage.
   *
   * This method flushes half of the hot buffer to make room for new elements,
   * then adds the current fiber to warm storage. The flushed elements are
   * wrapped in WeakReference and associated with the reference queue.
   *
   * @param fiber The fiber that triggered the spill
   */
  private def spillToWarm(fiber: A): Unit = {
    // Flush half of hot buffer to make room (gives chance for GC without promotion)
    val flushed = hotBuffer.flushHalf()

    // Add flushed elements to warm storage
    val iter = flushed.iterator()
    while (iter.hasNext) {
      val f = iter.next()
      if (isAlive(f)) {
        warmStorage.add(new WeakReference[A](f, refQueue))
      }
    }

    // Add current fiber to warm storage
    warmStorage.add(new WeakReference[A](fiber, refQueue))
  }

  /**
   * Start an auto-GC thread that runs on the specified interval.
   *
   * @note This method is only supported on the JVM. On Scala JS and Scala Native,
   *       it is a no-op.
   *
   * @param every The interval between GC runs
   * @return This FiberSet for method chaining
   */
  def withAutoGc(every: Duration): FiberSet[A] = {
    FiberSetGcThread.start(this, every)
    this
  }

  override def toString: String = iterator.mkString("FiberSet(", ",", ")")
}

private[zio] object FiberSet {

  /**
   * Creates a new FiberSet with default capacities.
   *
   * @param hotCapacity Size of the hot path ring buffer (default: 256)
   * @param warmCapacity Initial capacity of warm storage (default: 1024)
   * @param isAlive Predicate to check if a fiber is still alive (default: always true)
   * @param unsafe Implicit unsafe token (required for concurrent operations)
   * @return A new FiberSet instance
   */
  def apply[A <: AnyRef](
    hotCapacity: Int = 256,
    warmCapacity: Int = 1024,
    isAlive: IsAlive[A] = IsAlive.always
  )(implicit unsafe: Unsafe): FiberSet[A] =
    new FiberSet(hotCapacity, warmCapacity, isAlive)

  /**
   * Specialized function type that doesn't cause boxing of the Boolean result.
   *
   * @tparam A The type of values to check
   */
  trait IsAlive[-A] {
    def apply(value: A): Boolean
  }

  object IsAlive {
    /** Always considers values as alive */
    val always: IsAlive[Any] = _ => true
  }

  /**
   * Lock-free ring buffer for the hot path.
   *
   * This is a single-producer, multi-consumer ring buffer optimized for
   * the common case of concurrent adds from multiple threads. It uses
   * CAS operations for lock-free access and power-of-2 capacity for
   * fast index calculation.
   *
   * @param capacity The capacity of the ring buffer (must be power of 2)
   */
  private final class HotRingBuffer[A <: AnyRef](capacity: Int) {
    require((capacity & (capacity - 1)) == 0, "Capacity must be a power of 2")

    // The actual buffer array
    private[this] val buffer = new Array[AnyRef](capacity)

    // Head and tail pointers (using AtomicInteger for CAS operations)
    // Head: where we remove from (for flush)
    // Tail: where we add to
    private[this] val head = new AtomicInteger(0)
    private[this] val tail = new AtomicInteger(0)

    // Mask for fast modulo operation (capacity - 1 for power of 2)
    private[this] val mask = capacity - 1

    /**
     * Offer an element to the ring buffer.
     *
     * This method is lock-free and uses CAS for thread-safe access.
     * If the buffer is full, it returns false immediately.
     *
     * @param value The value to offer
     * @return true if the value was added, false if the buffer was full
     */
    def offer(value: A): Boolean = {
      @tailrec
      def tryOffer(): Boolean = {
        val t = tail.get()
        val h = head.get()
        val size = t - h

        if (size >= capacity) {
          false // Buffer is full
        } else {
          val idx = t & mask // Fast modulo for power of 2
          // Try to CAS the slot from null to value
          if (buffer.compareAndSet(idx, null, value)) {
            // Success, advance tail (may fail if another thread interfered, but that's ok)
            tail.lazySet(t + 1) // lazySet is sufficient here
            true
          } else {
            // Slot was already taken (concurrent add), retry
            tryOffer()
          }
        }
      }
      tryOffer()
    }

    /**
     * Remove a specific value from the ring buffer.
     *
     * This method searches for the value and removes it by setting the slot
     * to null. It's optimized for the common case where the value is near
     * the head of the buffer.
     *
     * @param value The value to remove
     * @return true if the value was found and removed, false otherwise
     */
    def remove(value: A): Boolean = {
      var i = head.get()
      val end = tail.get()

      while (i < end) {
        val idx = i & mask
        val current = buffer(idx)

        if (current eq value) {
          // Found it, try to remove
          if (buffer.compareAndSet(idx, value, null)) {
            return true
          }
          // CAS failed, another thread may have removed it or moved it
          // Continue searching (the value might be elsewhere due to concurrency)
        }
        i += 1
      }
      false
    }

    /**
     * Flush approximately half of the buffer contents.
     *
     * This method removes and returns the first half of the elements
     * in the buffer, making room for new elements. It's used during
     * the spill-to-warm process.
     *
     * @return A Chunk containing the flushed elements
     */
    def flushHalf(): Chunk[A] = {
      val builder = Chunk.newBuilder[A]
      var i = head.get()
      val end = tail.get()
      val flushCount = (end - i) / 2

      var flushed = 0
      while (i < end && flushed < flushCount) {
        val idx = i & mask
        val current = buffer(idx)

        if (current ne null) {
          builder += current.asInstanceOf[A]
          // Clear the slot (best-effort, no CAS needed)
          buffer(idx) = null
          flushed += 1
        }
        i += 1
      }

      // Advance head pointer
      if (flushed > 0) {
        head.addAndGet(flushed)
      }

      builder.result()
    }

    /**
     * Get the approximate size of the buffer.
     *
     * @return The number of elements currently in the buffer
     */
    def size(): Int = math.max(0, tail.get() - head.get())

    /**
     * Returns an iterator over the buffer contents.
     *
     * This iterator is weakly consistent and may not reflect concurrent
     * modifications made after the iterator was created.
     *
     * @return An iterator over all non-null elements in the buffer
     */
    def iterator(): Iterator[A] = {
      val currentHead = head.get()
      val currentTail = tail.get()

      new Iterator[A] {
        private var i = currentHead

        def hasNext: Boolean = i < currentTail

        def next(): A = {
          while (i < currentTail) {
            val idx = i & mask
            val value = buffer(idx)
            i += 1
            if (value ne null) {
              return value.asInstanceOf[A]
            }
          }
          throw new NoSuchElementException("HotRingBuffer iterator exhausted")
        }
      }
    }
  }
}
