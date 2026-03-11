/*
 * Copyright 2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.internal

import zio.Fiber

import java.lang.ref.{ReferenceQueue, WeakReference}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReferenceArray
import java.util.function.Consumer

/**
 * A Loom-friendly, high-performance concurrent weak set optimized for fiber
 * tracking. It uses a zero-allocation fast-path (a striped nursery of strong
 * references) for short-lived fibers, graduating them to a ConcurrentHashMap of
 * WeakReferences only when the nursery overflows.
 *
 * Cleanup is amortized into `add` and `remove` calls to eliminate the need for
 * background GC daemon threads, saving idle CPU cycles.
 */
private[zio] final class FiberSet {
  private val nurserySize = 128
  private val nursery     = new AtomicReferenceArray[Fiber.Runtime[_, _]](nurserySize)

  private val coldStorage = new ConcurrentHashMap[WeakFiberRef, java.lang.Boolean]()
  private val refQueue    = new ReferenceQueue[Fiber.Runtime[_, _]]()

  private val maxAmortizedCleanupPerOp = 10

  def add(fiber: Fiber.Runtime[_, _]): Unit = {
    cleanup()

    // Fast path: Try to place in nursery using a lock-free CAS
    val threadId   = Thread.currentThread().getId
    val startIndex = (threadId % nurserySize).toInt
    var i          = 0
    var added      = false

    while (i < nurserySize && !added) {
      val idx     = (startIndex + i) % nurserySize
      val current = nursery.get(idx)

      if (current == null || !current.isAlive()) {
        if (nursery.compareAndSet(idx, current, fiber)) {
          added = true
        }
      }
      i += 1
    }

    // Slow path: Nursery is full, graduate directly to cold storage
    if (!added) {
      val weakRef = new WeakFiberRef(fiber, refQueue)
      coldStorage.put(weakRef, java.lang.Boolean.TRUE)
    }
  }

  def remove(fiber: Fiber.Runtime[_, _]): Unit = {
    cleanup()

    // Attempt removal from nursery
    var i       = 0
    var removed = false
    while (i < nurserySize && !removed) {
      if (nursery.get(i) eq fiber) {
        nursery.set(i, null)
        removed = true
      }
      i += 1
    }

    // Attempt removal from cold storage
    if (!removed) {
      // Create a dummy ref for removal lookup
      val dummyRef = new WeakFiberRef(fiber, null)
      coldStorage.remove(dummyRef)
    }
  }

  def isEmpty: Boolean = {
    cleanup()
    var i = 0
    while (i < nurserySize) {
      val f = nursery.get(i)
      if (f != null && f.isAlive()) return false
      i += 1
    }
    coldStorage.isEmpty
  }

  def iterator(): Iterator[Fiber.Runtime[_, _]] = new Iterator[Fiber.Runtime[_, _]] {
    private var nurseryIdx = 0
    // Use keySet().iterator() to get the underlying Java iterator from the ConcurrentHashMap
    private val coldIterator                     = coldStorage.keySet().iterator()
    private var nextElement: Fiber.Runtime[_, _] = null

    advance()

    override def hasNext: Boolean = nextElement != null

    override def next(): Fiber.Runtime[_, _] = {
      val current = nextElement
      advance()
      current
    }

    private def advance(): Unit = {
      nextElement = null
      while (nurseryIdx < nurserySize && nextElement == null) {
        val f = nursery.get(nurseryIdx)
        nurseryIdx += 1
        if (f != null && f.isAlive()) nextElement = f
      }

      // coldIterator is a Java iterator, so we use hasNext() with parentheses
      while (nextElement == null && coldIterator.hasNext()) {
        val f = coldIterator.next().get()
        if (f != null && f.isAlive()) nextElement = f
      }
    }
  }

  def forEach(action: Consumer[_ >: Fiber.Runtime[_, _]]): Unit = {
    val it = iterator()
    while (it.hasNext) {
      action.accept(it.next())
    }
  }

  // Amortized threadless cleanup. Drains a small batch from the ReferenceQueue.
  private def cleanup(): Unit = {
    var count = 0
    var ref   = refQueue.poll()
    while (ref != null && count < maxAmortizedCleanupPerOp) {
      coldStorage.remove(ref)
      count += 1
      if (count < maxAmortizedCleanupPerOp) {
        ref = refQueue.poll()
      }
    }
  }

  private class WeakFiberRef(
    fiber: Fiber.Runtime[_, _],
    q: ReferenceQueue[Fiber.Runtime[_, _]]
  ) extends WeakReference[Fiber.Runtime[_, _]](fiber, q) {
    private val hash = System.identityHashCode(fiber)

    override def hashCode(): Int = hash

    override def equals(obj: Any): Boolean = obj match {
      case that: WeakFiberRef =>
        val thisFiber = this.get()
        val thatFiber = that.get()
        if (thisFiber != null && thatFiber != null) thisFiber eq thatFiber
        else this eq that
      case _ => false
    }
  }
}
