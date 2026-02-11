/*
 * Copyright 2017-2026 John A. De Goes and the ZIO Contributors
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

import java.util.concurrent.atomic.AtomicReferenceArray
import java.util.concurrent.ConcurrentHashMap
import java.lang.ref.{ReferenceQueue, WeakReference}

/**
 * A specialized concurrent weak set for tracking fibers in ZIO.
 *
 * It uses a "Hot/Cold" stratified strategy to minimize overhead:
 *   1. **Hot**: A fixed-size `AtomicReferenceArray` (ring buffer) that holds
 *      *strong* references.
 *      - New fibers are added here first using a lock-free CAS.
 *      - This path is zero-allocation in the steady state (when finding an
 *        empty slot).
 *      - Uses bitwise masking for probing indices (capacity must be power of
 *        2).
 *   2. **Cold**: A `ConcurrentHashMap` of `WeakReference`s.
 *      - When the Hot buffer is full or upon eviction, fibers are moved here.
 *      - Uses `WeakReference` to allow GC of substantial number of idle fibers.
 *   3. **GC**: Uses a `ReferenceQueue` to clean up dead fibers from the Cold
 *      map.
 *
 * This structure is designed to be "Loom-friendly" by avoiding `synchronized`
 * blocks and minimizing contention.
 */
final class FiberSet[A <: AnyRef] private () {
  import FiberSet._

  // Hot buffer: Strong references to recently added fibers. 1024 gives less collisions.
  // MUST BE A POWER OF 2 for bitwise masking optimization.
  private[this] val hotCapacity = 1024
  private[this] val hotMask     = hotCapacity - 1
  private[this] val hot         = new AtomicReferenceArray[A](hotCapacity)

  // Cold storage: Weak references to older fibers.
  // We use Boolean as value (Dummy).
  private[this] val cold = new ConcurrentHashMap[WeakFiberRef[A], java.lang.Boolean]()

  // Reference queue for cleaning up dead fibers from Cold storage.
  private[this] val referenceQueue = new ReferenceQueue[A]()

  /**
   * Adds a fiber to the set.
   */
  def add(fiber: A): Unit = {
    val hash = java.lang.System.identityHashCode(fiber)

    // 0. Opportunistic GC of cold storage (Sampled for performance)
    // Only check queue 1/128 times to avoid 'poll' overhead on every add.
    if ((hash & 0x7f) == 0) gc()

    // 1. Try to insert into Hot buffer
    val index = hash & hotMask

    // Attempt 1: Check primary slot
    val existing = hot.get(index)
    if (existing == null) {
      if (hot.compareAndSet(index, null.asInstanceOf[A], fiber)) return
    } else if (existing.asInstanceOf[AnyRef] eq fiber.asInstanceOf[AnyRef]) {
      return // Already present
    }

    // Attempt 2: Eviction strategy at primary index (simplest for O(1))
    // Move 'existing' to Cold, put 'fiber' in Hot.
    // We need a loop to handle race conditions.

    var curr = hot.get(index)
    while (true) {
      if (curr == null) {
        if (hot.compareAndSet(index, null.asInstanceOf[A], fiber)) return
        curr = hot.get(index) // Retry
      } else if (curr.asInstanceOf[AnyRef] eq fiber.asInstanceOf[AnyRef]) {
        return
      } else {
        // Evict 'curr' to Cold (allocates WeakRef)
        cold.put(new WeakFiberRef(curr, referenceQueue), java.lang.Boolean.TRUE)
        // Try to replace 'curr' with 'fiber'
        if (hot.compareAndSet(index, curr, fiber)) return
        // If CAS failed, reload curr and retry
        curr = hot.get(index)
      }
    }
  }

  /**
   * Removes a fiber from the set.
   */
  def remove(fiber: A): Unit = {
    // 1. Check Hot buffer
    val hash  = java.lang.System.identityHashCode(fiber)
    val index = hash & hotMask

    val inSlot = hot.get(index)
    if (inSlot.asInstanceOf[AnyRef] eq fiber.asInstanceOf[AnyRef]) {
      // Found in Hot! Remove it.
      // We don't need to check Cold, because `add` ensures a fiber is in Hot OR Cold (mostly).
      // Even if it was in Cold due to race, checking Hot first and returning is the "Fast Path".
      // We prioritize Zero Allocation here.
      if (hot.compareAndSet(index, fiber, null.asInstanceOf[A])) {
        return
      }
      // If CAS failed, someone else changed the slot (maybe evicted it, or removed it).
      // We should fall through to check Cold, just in case it was evicted.
    }

    // 2. Remove from Cold (allocates WeakRef lookup key)
    val key = new WeakFiberRef(fiber, null.asInstanceOf[ReferenceQueue[A]])
    cold.remove(key)
  }

  /**
   * Cleans up dead fibers from the Cold storage.
   */
  def gc(): Unit = {
    var ref = referenceQueue.poll()
    while (ref != null) {
      cold.remove(ref)
      ref = referenceQueue.poll()
    }
  }

  /**
   * Checks if the set contains the specified fiber.
   */
  def contains(fiber: A): Boolean = {
    // 1. Check Hot buffer
    val hash   = java.lang.System.identityHashCode(fiber)
    val index  = hash & hotMask
    val inSlot = hot.get(index)

    if (inSlot != null && (inSlot.asInstanceOf[AnyRef] eq fiber.asInstanceOf[AnyRef])) {
      return true
    }

    // 2. Check Cold storage
    // We strictly use `containsKey` with a transient WeakRef.
    // Note: Creating a WeakRef here is an allocation, but unavoidable for Cold lookup.
    // However, fast-path is checking Hot (Zero Allocation).
    val key = new WeakFiberRef(fiber, null.asInstanceOf[ReferenceQueue[A]])
    cold.containsKey(key)
  }

  /**
   * Clears the set.
   */
  def clear(): Unit = {
    // 1. Clear Hot
    var i = 0
    while (i < hotCapacity) {
      hot.set(i, null.asInstanceOf[A])
      i += 1
    }

    // 2. Clear Cold
    cold.clear()

    // 3. Drain Queue
    while (referenceQueue.poll() != null) {}
  }

  /**
   * Iterates over all fibers in the set (Weakly Consistent).
   *
   * @return
   *   An iterator of strong references to currently active fibers.
   */
  def iterator: java.util.Iterator[A] = {
    // Optimized: Use single ArrayList to avoid intermediate allocations
    // Estimate size: Hot capacity + current Cold size (padded)
    val sizeEstimate = hotCapacity + cold.size()
    val list         = new java.util.ArrayList[A](sizeEstimate)

    // 1. Snapshot Hot
    var i = 0
    while (i < hotCapacity) {
      val f = hot.get(i)
      if (f != null) list.add(f)
      i += 1
    }

    // 2. Snapshot Cold (alive ones)
    val it = cold.keySet().iterator()
    while (it.hasNext) {
      val ref = it.next()
      val f   = ref.get()
      if (f != null) list.add(f)
    }

    list.iterator()
  }

  /**
   * Applies the function `f` to each element in the set.
   *
   * Optimized to maximize performance and minimize allocations. Unlike
   * `iterator`, this does not allocate an intermediate collection.
   */
  def foreach(f: A => Unit): Unit = {
    // 1. Iterate Hot (Fast, no alloc)
    var i = 0
    while (i < hotCapacity) {
      val fiber = hot.get(i)
      if (fiber != null) f(fiber)
      i += 1
    }

    // 2. Iterate Cold
    val it = cold.keySet().iterator()
    while (it.hasNext) {
      val ref   = it.next()
      val fiber = ref.get()
      if (fiber != null) f(fiber)
    }
  }

  /**
   * Adds all specified fibers to the set.
   */
  def addAll(fibers: Iterable[A]): Unit =
    fibers.foreach(add)

  /**
   * Removes all fibers that satisfy the predicate `p`.
   *
   * This is more efficient than filtering via iterator as it operates directly
   * on the internal structures.
   */
  def removeIf(p: A => Boolean): Unit = {
    // 1. Scan Hot
    var i = 0
    while (i < hotCapacity) {
      val fiber = hot.get(i)
      if (fiber != null && p(fiber)) {
        // Try to verify it's still there and remove it
        if (hot.compareAndSet(i, fiber, null.asInstanceOf[A])) {
          // Success
        } else {
          // Race condition: slot changed.
          // If it changed to null, we're good.
          // If it changed to another fiber, we should check that fiber (retry logic)
          // But strict `removeIf` guarantees for concurrent sets are weak.
          // We'll accept best-effort here or retry.
          // Let's check the new value in the next iteration or fall through.
          // For simplicity in a weak set, we accept the race (item might stay).
          // But we can check new value:
          val reload = hot.get(i)
          if (reload != null && p(reload)) {
            hot.compareAndSet(i, reload, null.asInstanceOf[A])
          }
        }
      }
      i += 1
    }

    // 2. Scan Cold
    val it = cold.keySet().iterator()
    while (it.hasNext) {
      val ref   = it.next()
      val fiber = ref.get()
      if (fiber != null && p(fiber)) {
        it.remove()
      }
    }
  }

  def size: Int = {
    // Approximate size
    var count = 0
    var i     = 0
    while (i < hotCapacity) {
      if (hot.get(i) != null) count += 1
      i += 1
    }
    count + cold.size()
  }

  override def toString: String =
    s"FiberSet(hotSize=${hotSize}, coldSize=${cold.size()})"

  private def hotSize: Int = {
    var count = 0
    var i     = 0
    while (i < hotCapacity) {
      if (hot.get(i) != null) count += 1
      i += 1
    }
    count
  }
}

object FiberSet {
  def make[A <: AnyRef](): FiberSet[A] = new FiberSet[A]()

  private class WeakFiberRef[A](fiber: A, q: ReferenceQueue[A]) extends WeakReference[A](fiber, q) {

    private[this] val hash = java.lang.System.identityHashCode(fiber)

    override def hashCode(): Int = hash

    override def equals(obj: Any): Boolean = {
      if (this eq obj.asInstanceOf[AnyRef]) return true
      if (obj == null || getClass != obj.getClass) return false

      val other = obj.asInstanceOf[WeakFiberRef[A]]
      // Check if referents are identical (fast path)
      val r1 = this.get()
      val r2 = other.get()

      if (r1 != null && r2 != null) {
        // If both are alive, they are equal iff they refer to the same object
        return r1.asInstanceOf[AnyRef] eq r2.asInstanceOf[AnyRef]
      }

      // If one or both are cleared, we strictly assume they are NOT equal unless they are the same wrapper object (checked above).
      // This matches ConcurrentHashMap's behavior for keys: if the key is effectively gone, we can't reliably equate it
      // to another key unless we track the referent identity separately (which we don't, other than hash).
      // However, for `remove(key)`, we constructed a `new WeakFiberRef(fiber)`.
      // If the `cold` map contains a cleared entry, `r2` will be null. `r1` (our lookup key) will be non-null.
      // They will return false. This is correct: we can't remove an already-cleared entry by looking up the live fiber.
      // The GC mechanism handles cleared entries.
      false
    }
  }
}
