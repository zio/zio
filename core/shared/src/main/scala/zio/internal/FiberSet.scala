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

import zio.internal.FiberSet.IsAlive
import zio.{Duration, Unsafe}

import java.lang.ref.{ReferenceQueue, WeakReference}
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReferenceArray}

/**
 * A [[FiberSet]] is a lock-free concurrent bag that keeps elements in a striped
 * nursery of strong references, only wrapping them in `WeakReference` when they
 * are evicted from the nursery. Short-lived entries that become dead (per
 * `isAlive`) before eviction never allocate a weak ref at all.
 *
 * The larger the nursery, the fewer weak references are created, at the cost
 * of higher memory usage. `concurrencyLevel` controls striping for the nursery
 * to reduce contention under concurrent `add` calls.
 */
private[zio] final class FiberSet[A <: AnyRef](
  nurseryCapacity: Int,
  concurrencyLevel: Int,
  isAlive: IsAlive[A]
) { self =>

  private[this] val nParts: Int = {
    val raw = FiberSet.nextPow2(concurrencyLevel.max(1))
    raw.min(FiberSet.nextPow2(nurseryCapacity) / 2).max(1)
  }
  private[this] val partMask = nParts - 1

  private[this] val partCap: Int = FiberSet.nextPow2((nurseryCapacity / nParts).max(2))
  private[this] val slotMask    = partCap - 1

  private[this] val slots   = Array.tabulate(nParts)(_ => new AtomicReferenceArray[AnyRef](partCap))
  private[this] val cursors = Array.fill(nParts)(new AtomicInteger(0))

  private[this] val grads  = Platform.newConcurrentSet[WeakReference[A]](nurseryCapacity.max(16))(Unsafe.unsafe)
  private[this] val refQ   = new ReferenceQueue[A]()
  private[this] val gcFlag = new AtomicBoolean(false)

  private[this] val autoGcStarted = new AtomicBoolean(false)

  def withAutoGc(every: Duration): FiberSet[A] = {
    if (autoGcStarted.compareAndSet(false, true))
      FiberSetGc.start(self, every)
    self
  }

  /**
   * Adds a new value, graduating the evicted nursery occupant to long-term
   * storage if it is still alive.
   */
  final def add(a: A): Unit = {
    val p    = ThreadLocalRandom.current().nextInt() & partMask
    val arr  = slots(p)
    val idx  = cursors(p).getAndIncrement() & slotMask
    val prev = arr.getAndSet(idx, a)

    if (prev ne null) {
      val prevA = prev.asInstanceOf[A]
      if (isAlive(prevA))
        grads.add(new WeakReference[A](prevA, refQ))
    }

    if (grads.size() > nParts * partCap) gc(false)
  }

  final def remove(a: A): Boolean = {
    var p = 0
    while (p < nParts) {
      val arr = slots(p)
      var s   = 0
      while (s < partCap) {
        val v = arr.get(s)
        if ((v ne null) && (v eq a)) {
          if (arr.compareAndSet(s, v, null)) return true
        }
        s += 1
      }
      p += 1
    }
    val it = grads.iterator()
    while (it.hasNext) {
      val ref = it.next()
      val v   = ref.get()
      if (v eq a) {
        it.remove()
        return true
      }
    }
    false
  }

  final def gc(): Unit = gc(true)

  final def gc(force: Boolean): Unit = {
    val acquired = gcFlag.compareAndSet(false, true)
    try {
      if (force || acquired) {
        var ref = refQ.poll()
        while (ref ne null) {
          grads.remove(ref)
          ref = refQ.poll()
        }
        val it = grads.iterator()
        while (it.hasNext) {
          val entry = it.next()
          val v     = entry.get()
          if ((v eq null) || !isAlive(v)) it.remove()
        }
      }
    } finally {
      if (acquired) gcFlag.set(false)
    }
  }

  /**
   * Applies `f` to every live element. Dead graduate entries are cleaned up
   * opportunistically during the traversal.
   */
  final def forEach(f: A => Unit): Unit = {
    var p = 0
    while (p < nParts) {
      val arr = slots(p)
      var s   = 0
      while (s < partCap) {
        val v = arr.get(s)
        if (v ne null) {
          val a = v.asInstanceOf[A]
          if (isAlive(a)) f(a)
        }
        s += 1
      }
      p += 1
    }
    val it = grads.iterator()
    while (it.hasNext) {
      val ref = it.next()
      val v   = ref.get()
      if ((v ne null) && isAlive(v)) f(v)
      else it.remove()
    }
  }

  final def iterator: Iterator[A] = new Iterator[A] {
    private var pIdx   = 0
    private var sIdx   = 0
    private var inGrad = false
    private var gradIt: java.util.Iterator[WeakReference[A]] = _
    private var _next: A = prefetch()

    private def prefetch(): A = {
      if (!inGrad) {
        while (pIdx < nParts) {
          val arr = slots(pIdx)
          while (sIdx < partCap) {
            val v = arr.get(sIdx)
            sIdx += 1
            if (v ne null) {
              val a = v.asInstanceOf[A]
              if (isAlive(a)) return a
            }
          }
          sIdx = 0
          pIdx += 1
        }
        inGrad = true
        gradIt = grads.iterator()
      }
      while (gradIt.hasNext) {
        val ref = gradIt.next()
        val v   = ref.get()
        if ((v ne null) && isAlive(v)) return v
        else gradIt.remove()
      }
      null.asInstanceOf[A]
    }

    def hasNext: Boolean = _next ne null

    def next(): A = {
      if (_next eq null)
        throw new NoSuchElementException("next on empty FiberSet iterator")
      val r = _next
      _next = prefetch()
      r
    }
  }

  final def isEmpty: Boolean = {
    var p = 0
    while (p < nParts) {
      val arr = slots(p)
      var s   = 0
      while (s < partCap) {
        val v = arr.get(s)
        if (v ne null) {
          val a = v.asInstanceOf[A]
          if (isAlive(a)) return false
        }
        s += 1
      }
      p += 1
    }
    val it = grads.iterator()
    while (it.hasNext) {
      val ref = it.next()
      val v   = ref.get()
      if ((v ne null) && isAlive(v)) return false
      else it.remove()
    }
    true
  }

  def size: Int = {
    var count = 0
    var p     = 0
    while (p < nParts) {
      val arr = slots(p)
      var s   = 0
      while (s < partCap) {
        if (arr.get(s) ne null) count += 1
        s += 1
      }
      p += 1
    }
    count + grads.size()
  }

  override def toString: String = iterator.mkString("FiberSet(", ", ", ")")
}

private[zio] object FiberSet {

  def apply[A <: AnyRef](
    nurseryCapacity: Int,
    concurrencyLevel: Int = 1,
    isAlive: IsAlive[A] = IsAlive.always
  ): FiberSet[A] = new FiberSet(nurseryCapacity, concurrencyLevel, isAlive)

  /** Specialized Function1 that doesn't cause boxing of the Boolean */
  trait IsAlive[-A] {
    def apply(value: A): Boolean
  }

  object IsAlive {
    val always: IsAlive[Any] = _ => true
  }

  private[internal] def nextPow2(n: Int): Int = {
    if (n <= 1) return 1
    var v = n - 1
    v |= v >> 1
    v |= v >> 2
    v |= v >> 4
    v |= v >> 8
    v |= v >> 16
    v + 1
  }
}
