/*
 * Copyright 2020-2024 John A. De Goes and the ZIO Contributors
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

import zio.{Fiber, Unsafe}

import java.lang.ref.{ReferenceQueue, WeakReference}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference, AtomicReferenceArray}
import java.util.function.Predicate
import scala.annotation.tailrec

/**
 * A lock-free, Project Loom-friendly concurrent weak set/bag specialized for
 * storing active fibers without `synchronized` monitors or lock contention.
 *
 * Newly added elements reside in a fixed-size atomic nursery array using strong
 * references so short-lived fibers that terminate or are removed before slot
 * eviction allocate zero `WeakReference` wrappers. Surviving elements that are
 * evicted from a nursery slot (or explicitly graduated) move to a concurrent
 * long-term set backed by identity `WeakReference` entries and a
 * `ReferenceQueue` for O(1) garbage-collection cleanup.
 */
private[zio] final class FiberSet[A <: AnyRef](
  nurserySize: Int = FiberSet.DefaultNurserySize,
  isAlive: WeakConcurrentBag.IsAlive[A] = FiberSet.DefaultIsAlive
) { self =>
  import FiberSet._

  private[this] val nurseryCapacity: Int =
    RingBuffer.nextPow2(Math.max(2, nurserySize))

  private[this] val nurseryMask: Int =
    nurseryCapacity - 1

  private[this] val nursery: AtomicReferenceArray[A] =
    new AtomicReferenceArray[A](nurseryCapacity)

  private[this] val graduatesRef: AtomicReference[java.util.Set[WeakEntry[A]]] =
    new AtomicReference[java.util.Set[WeakEntry[A]]](null)

  private[this] val refQueue: ReferenceQueue[A] =
    new ReferenceQueue[A]()

  private[this] val gcStatus: AtomicBoolean =
    new AtomicBoolean(false)

  private[this] val notAlivePredicate: Predicate[WeakEntry[A]] =
    new Predicate[WeakEntry[A]] {
      override def test(entry: WeakEntry[A]): Boolean = {
        val value = entry.get()
        (value eq null) || !isAlive(value)
      }
    }

  @inline
  private[this] def bucketIndex(a: A): Int = {
    val h = System.identityHashCode(a)
    (h ^ (h >>> 16)) & nurseryMask
  }

  private[this] def getOrCreateGraduates(): java.util.Set[WeakEntry[A]] = {
    var g = graduatesRef.get()
    if (g eq null) {
      val created = Platform.newConcurrentSet[WeakEntry[A]](nurseryCapacity * 2)(Unsafe.unsafe)
      if (graduatesRef.compareAndSet(null, created)) g = created
      else g = graduatesRef.get()
    }
    g
  }

  private[this] def expungeStaleEntries(g: java.util.Set[WeakEntry[A]]): Unit =
    if (g ne null) {
      var polled = refQueue.poll()
      var count  = 0
      while ((polled ne null) && count < 64) {
        g.remove(polled)
        polled = refQueue.poll()
        count += 1
      }
    }

  private[this] def expungeAllStaleEntries(g: java.util.Set[WeakEntry[A]]): Unit =
    if (g ne null) {
      var polled = refQueue.poll()
      while (polled ne null) {
        g.remove(polled)
        polled = refQueue.poll()
      }
    }

  /**
   * Adds an element to the set without acquiring any monitor locks.
   */
  final def add(a: A): Boolean = {
    if ((a eq null) || !isAlive(a)) return false

    val idx = bucketIndex(a)
    val cur = nursery.get(idx)

    if (cur eq a) return false

    val g0 = graduatesRef.get()
    if (g0 ne null) {
      expungeStaleEntries(g0)
      if (g0.contains(new WeakEntry[A](a))) return false
    }

    if (((cur eq null) || !isAlive(cur)) && nursery.compareAndSet(idx, cur, a)) return true

    val evicted = nursery.getAndSet(idx, a)
    if ((evicted ne null) && (evicted ne a) && isAlive(evicted)) {
      val g = getOrCreateGraduates()
      g.add(new WeakEntry[A](evicted, refQueue))
      if (g.size() > nurseryCapacity) gc(false)
    }
    true
  }

  /**
   * Removes an element from the set if present.
   */
  final def remove(a: A): Boolean = {
    if (a eq null) return false

    val idx          = bucketIndex(a)
    var removedNurs  = false
    var continueLoop = true

    while (continueLoop) {
      val cur = nursery.get(idx)
      if (cur eq a) {
        if (nursery.compareAndSet(idx, a, null.asInstanceOf[A])) {
          removedNurs = true
          continueLoop = false
        }
      } else continueLoop = false
    }

    val g = graduatesRef.get()
    val removedGrad =
      if (g ne null) {
        expungeStaleEntries(g)
        g.remove(new WeakEntry[A](a))
      } else false

    removedNurs || removedGrad
  }

  /**
   * Returns true if the set contains the specified live element.
   */
  final def contains(a: A): Boolean = {
    if ((a eq null) || !isAlive(a)) return false

    val idx = bucketIndex(a)
    if (nursery.get(idx) eq a) true
    else {
      val g = graduatesRef.get()
      (g ne null) && g.contains(new WeakEntry[A](a))
    }
  }

  /**
   * Moves all live occupants of the nursery into weak long-term storage so
   * unreachable suspended fibers can be garbage collected by the JVM.
   */
  final def graduate(): Unit = {
    var i = 0
    while (i < nurseryCapacity) {
      val evicted = nursery.getAndSet(i, null.asInstanceOf[A])
      if ((evicted ne null) && isAlive(evicted)) {
        val g = getOrCreateGraduates()
        g.add(new WeakEntry[A](evicted, refQueue))
      }
      i += 1
    }
    val g = graduatesRef.get()
    if ((g ne null) && g.size() > nurseryCapacity) gc(false)
  }

  /**
   * Cleans up garbage-collected and dead references in long-term storage.
   */
  final def gc(): Unit = gc(true)

  final def gc(force: Boolean): Unit = {
    var i = 0
    while (i < nurseryCapacity) {
      val cur = nursery.get(i)
      if ((cur ne null) && !isAlive(cur)) nursery.compareAndSet(i, cur, null.asInstanceOf[A])
      i += 1
    }
    val g = graduatesRef.get()
    if (g ne null) {
      expungeAllStaleEntries(g)
      val lockAcquired = gcStatus.compareAndSet(false, true)
      try if (force || lockAcquired) g.removeIf(notAlivePredicate)
      finally if (lockAcquired) gcStatus.set(false)
    }
  }

  /**
   * Clears all elements from both the nursery and long-term storage.
   */
  final def clear(): Unit = {
    var i = 0
    while (i < nurseryCapacity) {
      nursery.set(i, null.asInstanceOf[A])
      i += 1
    }
    val g = graduatesRef.get()
    if (g ne null) {
      g.clear()
      expungeAllStaleEntries(g)
    }
  }

  /**
   * Returns true if the set contains no live elements.
   */
  final def isEmpty: Boolean = {
    var i = 0
    while (i < nurseryCapacity) {
      val cur = nursery.get(i)
      if (cur ne null) {
        if (isAlive(cur)) return false
        else nursery.compareAndSet(i, cur, null.asInstanceOf[A])
      }
      i += 1
    }
    val g = graduatesRef.get()
    if (g eq null) true
    else {
      expungeStaleEntries(g)
      val it = g.iterator()
      while (it.hasNext) {
        val value = it.next().get()
        if ((value eq null) || !isAlive(value)) it.remove()
        else return false
      }
      true
    }
  }

  /**
   * Applies the given function to each live element in the set.
   */
  final def forEach(f: A => Unit): Unit = {
    var i = 0
    while (i < nurseryCapacity) {
      val cur = nursery.get(i)
      if (cur ne null) {
        if (isAlive(cur)) f(cur)
        else nursery.compareAndSet(i, cur, null.asInstanceOf[A])
      }
      i += 1
    }
    val g = graduatesRef.get()
    if (g ne null) {
      expungeStaleEntries(g)
      val it = g.iterator()
      while (it.hasNext) {
        val entry = it.next()
        val value = entry.get()
        if ((value eq null) || !isAlive(value)) it.remove()
        else if (nursery.get(bucketIndex(value)) ne value) f(value)
      }
    }
  }

  /**
   * Returns a weakly-consistent iterator over all live elements in the set.
   */
  final def iterator(): Iterator[A] = new Iterator[A] {
    private[this] var nurseryIdx: Int = 0
    private[this] val gradIter: java.util.Iterator[WeakEntry[A]] = {
      val g = graduatesRef.get()
      if (g ne null) {
        expungeStaleEntries(g)
        g.iterator()
      } else null
    }
    private[this] var _next: A = prefetch()

    @tailrec
    private def prefetch(): A =
      if (nurseryIdx < nurseryCapacity) {
        val idx = nurseryIdx
        val cur = nursery.get(idx)
        nurseryIdx += 1
        if (cur ne null) {
          if (isAlive(cur)) cur
          else {
            nursery.compareAndSet(idx, cur, null.asInstanceOf[A])
            prefetch()
          }
        } else prefetch()
      } else if ((gradIter ne null) && gradIter.hasNext) {
        val entry = gradIter.next()
        val value = entry.get()
        if ((value eq null) || !isAlive(value)) {
          gradIter.remove()
          prefetch()
        } else if (nursery.get(bucketIndex(value)) eq value) prefetch()
        else value
      } else null.asInstanceOf[A]

    override def hasNext: Boolean = _next ne null

    override def next(): A =
      if (_next eq null)
        throw new NoSuchElementException("There is no more element in the FiberSet iterator")
      else {
        val res = _next
        _next = prefetch()
        res
      }
  }

  /**
   * Returns the approximate number of live elements in the set.
   */
  final def size: Int = {
    var count = 0
    forEach(_ => count += 1)
    count
  }
}

private[zio] object FiberSet {
  final val DefaultNurserySize: Int = 16

  val DefaultIsAlive: WeakConcurrentBag.IsAlive[Any] = WeakConcurrentBag.IsAlive.always

  val FiberRuntimeIsAlive: WeakConcurrentBag.IsAlive[Fiber.Runtime[_, _]] =
    new WeakConcurrentBag.IsAlive[Fiber.Runtime[_, _]] {
      override def apply(value: Fiber.Runtime[_, _]): Boolean = value.isAlive()
    }

  def forFibers(nurserySize: Int = DefaultNurserySize): FiberSet[Fiber.Runtime[_, _]] =
    new FiberSet[Fiber.Runtime[_, _]](nurserySize, FiberRuntimeIsAlive)

  private[internal] final class WeakEntry[A <: AnyRef](
    referent: A,
    queue: ReferenceQueue[A]
  ) extends WeakReference[A](referent, queue) {
    val hash: Int = System.identityHashCode(referent)

    def this(referent: A) = this(referent, null)

    override def hashCode(): Int = hash

    override def equals(that: Any): Boolean =
      that match {
        case other: WeakEntry[_] =>
          if (this eq other) true
          else if (this.hash != other.hash) false
          else {
            val thisRef  = this.get()
            val otherRef = other.get()
            (thisRef ne null) && (thisRef eq otherRef)
          }
        case _ => false
      }
  }
}
