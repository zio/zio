/*
 * Copyright 2024-2026 ZIO Contributors
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

import java.lang.ref.ReferenceQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicLong, AtomicReference}
import scala.collection.mutable

/**
 * Stratified Epoch Collector (SEC) - High-performance concurrent weak set for fibers.
 *
 * == Design Insight ==
 * Most fibers are "mayflies" (short-lived, <100ms). Only "vampires" (suspended forever)
 * need weak references for GC eligibility. SEC defers weak reference allocation until
 * epoch rotation, so mayflies NEVER allocate a WeakReference.
 *
 * == Architecture ==
 * - Active epoch: stores strong refs in atomic array (zero allocation on add)
 * - Rotation: batch-converts survivors to weak refs (CleanupRef)
 * - Archives: bounded by carry-forward rehoming (never lose live fibers)
 * - ReferenceQueue: O(1) cleanup when GC collects dead fibers
 *
 * == Invariants ==
 * I1: Slot contents by epoch state
 *     - ACTIVE: Fiber | null
 *     - ROTATING: Fiber | CleanupRef | null (transitional)
 *     - ARCHIVED: CleanupRef | null
 *
 * I2: Locator findability
 *     If fiber._setEpochId >= 0, epoch is discoverable and slot references fiber
 *
 * I3: Publication order
 *     Store fiber in slot BEFORE updating fiber's locator fields
 *
 * I4: Epoch discoverability
 *     epochMap.put(oldEpoch) BEFORE activeEpoch.set(newEpoch)
 *
 * I5: Carry-forward before drop
 *     Never remove epoch from tracking without rehoming live fibers first
 *
 * @param capacity slots per epoch (tune based on workload)
 */
final class FiberSet(capacity: Int = FiberSet.DefaultCapacity) {
  import FiberSet._

  // === Core State ===
  
  /** Current active epoch - strong refs, lock-free adds */
  private[this] val activeEpoch: AtomicReference[Epoch] = 
    new AtomicReference(new Epoch(nextEpochId(), capacity))

  /** O(1) epoch lookup by ID (includes ROTATING epochs per I4) */
  private[this] val epochMap: ConcurrentHashMap[Long, Epoch] = 
    new ConcurrentHashMap()

  /** Archived epochs in reverse-chronological order */
  private[this] val archives: mutable.ListBuffer[Epoch] = 
    mutable.ListBuffer.empty

  /** Track archive count (ConcurrentLinkedDeque.size() is O(n)) */
  private[this] val archiveCount: AtomicInteger = 
    new AtomicInteger(0)

  /** GC notification queue for dead weak refs */
  private[this] val cleanupQueue: ReferenceQueue[FiberSetRef] = 
    new ReferenceQueue()

  /** Single-winner guard for carry-forward maintenance */
  private[this] val retireInProgress: AtomicBoolean = 
    new AtomicBoolean(false)

  // ============================================================
  // PUBLIC API
  // ============================================================

  /**
   * Add a fiber to this set. O(1), zero allocations.
   *
   * After this call returns, the fiber is visible to foreach() and
   * can be removed via remove().
   *
   * Thread-safe: may be called concurrently from multiple threads.
   */
  def add(fiber: FiberSetRef): Unit = {
    var done = false
    while (!done) {
      val epoch = activeEpoch.get()
      
      if (epoch.state.get() == ACTIVE) {
        val idx = epoch.nextIndex.getAndIncrement()
        
        if (idx >= capacity) {
          // Epoch full, attempt rotation
          tryRotate(epoch)
        } else {
          // I3: Store slot FIRST, then update locator
          epoch.slots.set(idx, fiber)
          fiber._setEpochId = epoch.id
          fiber._setIndex = idx
          done = true
        }
      }
      // If not ACTIVE, retry with new activeEpoch
    }
  }

  /**
   * Remove a fiber from this set. O(1) expected.
   *
   * Thread-safe. Handles concurrent rotation and carry-forward gracefully.
   * If removal cannot be confirmed after bounded retries, leaves locator
   * intact for eventual GC-based cleanup.
   *
   * INVARIANT: Must not be called until add() has returned for this fiber.
   */
  def remove(fiber: FiberSetRef): Unit = {
    val eid = fiber._setEpochId
    val idx = fiber._setIndex
    
    // Not in any set
    if (eid < 0L) return
    
    var attempts = 0
    while (attempts < MaxRemoveRetries) {
      val active = activeEpoch.get()
      val epoch = if (active.id == eid) active else epochMap.get(eid)
      
      if (epoch == null) {
        // Epoch fully retired (post-carry-forward) - fiber is gone
        clearLocator(fiber)
        return
      }
      
      epoch.state.get() match {
        case ACTIVE =>
          // Slot contains Fiber directly
          if (epoch.slots.compareAndSet(idx, fiber, null)) {
            clearLocator(fiber)
            return
          }
          // CAS failed - retry (concurrent remove or rotation claimed it)
          
        case ROTATING | ARCHIVED =>
          val entry = epoch.slots.get(idx)
          
          if (entry == null) {
            // Already removed
            clearLocator(fiber)
            return
          }
          
          entry match {
            case f: FiberSetRef if f eq fiber =>
              // Rare: still Fiber during ROTATING transition
              if (epoch.slots.compareAndSet(idx, f, null)) {
                clearLocator(fiber)
                return
              }
              
            case ref: CleanupRef =>
              val held = ref.get()
              if (held == null || (held eq fiber)) {
                if (epoch.slots.compareAndSet(idx, ref, null)) {
                  clearLocator(fiber)
                  return
                }
              } else {
                // Different fiber in slot (rehomed) - our fiber moved
                clearLocator(fiber)
                return
              }
              
            case _ =>
              // Slot contains different fiber - ours was rehomed
              clearLocator(fiber)
              return
          }
          
        case other =>
          // Unknown state - defensive spin
          ()
      }
      
      attempts += 1
    }
    
    // Exhausted retries - leave locator intact for eventual GC cleanup
    // This is safe: fiber will be cleaned via ReferenceQueue when it dies
  }

  /**
   * Iterate over all fibers in this set. Eventually consistent.
   *
   * May observe fibers concurrently being added/removed. Will not throw
   * ConcurrentModificationException. May briefly observe "ghost" entries
   * (terminated fibers not yet cleaned up).
   *
   * Performs bounded maintenance (queue drain) on each call.
   */
  def foreach(f: FiberSetRef => Unit): Unit = {
    // Amortized cleanup
    drainQueue(DrainBatchSize)
    
    // Active epoch: strong refs
    val active = activeEpoch.get()
    val activeSize = math.min(active.nextIndex.get(), capacity)
    var i = 0
    while (i < activeSize) {
      val entry = active.slots.get(i)
      entry match {
        case fiber: FiberSetRef => f(fiber)
        case _ => // null or CleanupRef (transient during ROTATING)
      }
      i += 1
    }
    
    // Archives: weak refs
    val archiveList = archives.synchronized { archives.toList }
    val archiveIter = archiveList.iterator
    while (archiveIter.hasNext) {
      val archive = archiveIter.next()
      var i = 0
      while (i < capacity) {
        val entry = archive.slots.get(i)
        entry match {
          case ref: CleanupRef =>
            val fiber = ref.get()
            if (fiber != null) f(fiber)
          case fiber: FiberSetRef =>
            // Transient: observing ROTATING epoch
            f(fiber)
          case _ => // null
        }
        i += 1
      }
    }
  }

  /**
   * Approximate size (for testing/debugging only).
   * Not guaranteed to be accurate under concurrency.
   */
  def sizeApprox: Int = {
    var count = 0
    foreach(_ => count += 1)
    count
  }

  // ============================================================
  // INTERNAL: ROTATION
  // ============================================================

  /**
   * Attempt to rotate oldEpoch to archived status.
   * Single-winner via CAS on epoch state.
   *
   * Order is critical (I4):
   * 1. CAS state to ROTATING
   * 2. Publish to epochMap (so remove() can find it)
   * 3. Swap activeEpoch
   * 4. Convert survivors strong→weak
   * 5. Set state to ARCHIVED
   * 6. Push to archives
   * 7. Enforce cap via carry-forward
   */
  private[this] def tryRotate(oldEpoch: Epoch): Unit = {
    // Single-winner: only one thread rotates this epoch
    if (!oldEpoch.state.compareAndSet(ACTIVE, ROTATING)) return
    
    // I4: Publish BEFORE swap so remove() can always find ROTATING epochs
    epochMap.put(oldEpoch.id, oldEpoch)
    
    // Create and publish new active epoch
    val newEpoch = new Epoch(nextEpochId(), capacity)
    activeEpoch.set(newEpoch)
    
    // Convert survivors: claim strong ref, install weak ref
    var i = 0
    while (i < capacity) {
      val entry = oldEpoch.slots.getAndSet(i, null)
      entry match {
        case fiber: FiberSetRef =>
          val ref = new CleanupRef(fiber, cleanupQueue, oldEpoch.id, i)
          oldEpoch.slots.set(i, ref)
        case _ =>
          // null or already processed - skip
      }
      i += 1
    }
    
    // Mark as archived and add to archive list
    oldEpoch.state.set(ARCHIVED)
    archives.synchronized { archives.prepend(oldEpoch) }
    val count = archiveCount.incrementAndGet()
    
    // Enforce memory cap via carry-forward (I5)
    if (count > MaxArchives) {
      retireOldestWithCarryForward()
    }
  }

  // ============================================================
  // INTERNAL: CARRY-FORWARD (Memory Bounding)
  // ============================================================

  /**
   * Retire the oldest archived epoch after rehoming any live fibers.
   *
   * This is the key correctness guarantee (I5): we NEVER drop an epoch
   * while it might still be the only membership record for live fibers.
   *
   * Single-winner guarded to prevent duplicate carry-forward work.
   */
  private[this] def retireOldestWithCarryForward(): Unit = {
    // Single-winner guard
    if (!retireInProgress.compareAndSet(false, true)) return
    
    try {
      while (archiveCount.get() > MaxArchives) {
        val old = archives.synchronized {
          if (archives.nonEmpty) {
            val last = archives.last
            archives.remove(archives.size - 1)
            Some(last)
          } else None
        }
        old.foreach { epoch =>
          // Scan and rehome live fibers
          var i = 0
          while (i < capacity) {
            val entry = epoch.slots.get(i)
            entry match {
              case ref: CleanupRef =>
                val fiber = ref.get()
                if (fiber != null && !fiber.isTerminated) {
                  // Claim slot and rehome
                  if (epoch.slots.compareAndSet(i, ref, null)) {
                    addRehome(fiber)
                  }
                }
              case _ => // null or Fiber (shouldn't happen in ARCHIVED)
            }
            i += 1
          }

          // Safe to retire: all live fibers rehomed
          epochMap.remove(epoch.id)
          archiveCount.decrementAndGet()
        }
      }
    } finally {
      retireInProgress.set(false)
    }
  }

  /**
   * Rehome a fiber from a retired epoch into the current active epoch.
   *
   * Uses I3 ordering (store-then-locator). Does NOT invoke retirement
   * logic to avoid nested maintenance stacks.
   */// Thread.onSpinWait() // Not available in all platforms
        
  private[this] def addRehome(fiber: FiberSetRef): Unit = {
    var done = false
    while (!done) {
      val epoch = activeEpoch.get()
      
      if (epoch.state.get() == ACTIVE) {
        val idx = epoch.nextIndex.getAndIncrement()
        
        if (idx >= capacity) {
          // Epoch full - trigger rotation but DON'T recurse into retire
          tryRotate(epoch)
          // Loop will retry with new epoch
        } else {
          // I3: Store slot FIRST, then update locator
          epoch.slots.set(idx, fiber)
          fiber._setEpochId = epoch.id
          fiber._setIndex = idx
          done = true
        }
      }
      // If not ACTIVE, retry
    }
  }

  // ============================================================
  // INTERNAL: QUEUE CLEANUP
  // ============================================================

  /**
   * Drain ReferenceQueue for GC'd fibers. O(1) per dequeue.
   *
   * Bounded to prevent iteration stalls. Tolerates retired epochs
   * (simply ignores refs from epochs no longer in epochMap).
   */
  private[this] def drainQueue(max: Int): Unit = {
    var count = 0
    while (count < max) {
      val ref = cleanupQueue.poll()
      if (ref == null) return
      
      ref match {
        case cr: CleanupRef =>
          val epoch = epochMap.get(cr.epochId)
          // Tolerate retired epochs (I5 ensures live fibers were rehomed)
          if (epoch != null) {
            epoch.slots.compareAndSet(cr.slotIndex, cr, null)
          }
        case _ =>
          // Unexpected ref type - ignore
      }
      count += 1
    }
  }

  // ============================================================
  // INTERNAL: UTILITIES
  // ============================================================

  @inline private[this] def clearLocator(fiber: FiberSetRef): Unit = {
    fiber._setEpochId = -1L
    fiber._setIndex = -1
  }
}

object FiberSet {
  // === Epoch States ===
  private[internal] final val ACTIVE   = 0
  private[internal] final val ROTATING = 1
  private[internal] final val ARCHIVED = 2

  // === Tuning Constants ===
  
  /** Slots per epoch. Balance rotation frequency vs conversion cost. */
  final val DefaultCapacity = 4096
  
  /** Maximum archived epochs before carry-forward. Memory bound. */
  final val MaxArchives = 8
  
  /** Queue polls per foreach. Amortizes cleanup. */
  final val DrainBatchSize = 32
  
  /** Remove retries before falling back to GC cleanup. */
  final val MaxRemoveRetries = 8

  // === Global Epoch Counter ===
  private val epochCounter = new AtomicLong(0L)
  
  private[internal] def nextEpochId(): Long = epochCounter.getAndIncrement()

  // === Factory Methods ===
  
  /** Create a FiberSet with default capacity (512 slots/epoch). */
  def apply(): FiberSet = new FiberSet()
  
  /** Create a FiberSet with custom capacity. */
  def apply(capacity: Int): FiberSet = new FiberSet(capacity)
}
