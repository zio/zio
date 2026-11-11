/*
 * Copyright 2017-2024 John A. De Goes and the ZIO contributors
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

import zio.Fiber

import java.lang.ref.{ReferenceQueue, WeakReference}
import java.util.{AbstractSet, Iterator => JavaIterator, NoSuchElementException}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

/**
 * A Loom-friendly, lock-free concurrent weak set specialized for tracking
 * `Fiber.Runtime[_, _]` instances (such as fiber children and root fibers).
 *
 * Unlike `Collections.synchronizedSet(new WeakHashMap())`, `FiberSet` never
 * acquires object monitor locks (`synchronized`), preventing virtual thread
 * pinning on Project Loom.
 *
 * Fibers that terminate normally are deterministically removed via `remove`,
 * while fibers that become unreachable while suspended forever are collected
 * by the JVM garbage collector and lazily expunged via an internal
 * `ReferenceQueue` without requiring full-table scans.
 */
private[zio] final class FiberSet(initialCapacity: Int = 16) extends AbstractSet[Fiber.Runtime[_, _]] {
  import FiberSet._

  private[this] val queue     = new ReferenceQueue[Fiber.Runtime[_, _]]()
  private[this] val backing   = ConcurrentHashMap.newKeySet[FiberRefNode](initialCapacity)
  private[this] val opCounter = new AtomicInteger(0)

  /**
   * Drains unreachable weak references enqueued by the garbage collector.
   */
  final def expungeStaleEntries(): Unit = {
    var ref = queue.poll()
    while (ref ne null) {
      backing.remove(ref.asInstanceOf[FiberRefNode])
      ref = queue.poll()
    }
  }

  @inline private def maybeExpunge(): Unit = {
    if ((opCounter.incrementAndGet() & ExpungeMask) == 0) {
      expungeStaleEntries()
    }
  }

  override def add(fiber: Fiber.Runtime[_, _]): Boolean = {
    if ((fiber eq null) || !fiber.isAlive()) false
    else {
      maybeExpunge()
      backing.add(new FiberRefNode(fiber, queue))
    }
  }

  def addAll(fibers: Iterable[Fiber.Runtime[_, _]]): Unit = {
    maybeExpunge()
    val it = fibers.iterator
    while (it.hasNext) {
      val f = it.next()
      if ((f ne null) && f.isAlive()) {
        backing.add(new FiberRefNode(f, queue))
      }
    }
  }

  override def remove(o: Any): Boolean =
    o match {
      case fiber: Fiber.Runtime[_, _] =>
        maybeExpunge()
        backing.remove(new LookupKey(fiber))
      case _ =>
        false
    }

  override def contains(o: Any): Boolean =
    o match {
      case fiber: Fiber.Runtime[_, _] =>
        backing.contains(new LookupKey(fiber))
      case _ =>
        false
    }

  override def clear(): Unit = {
    backing.clear()
    while (queue.poll() ne null) {}
  }

  override def isEmpty: Boolean = {
    expungeStaleEntries()
    !iterator().hasNext
  }

  override def size(): Int = {
    expungeStaleEntries()
    backing.size()
  }

  /**
   * Performs a full GC sweep of dead or collected fibers.
   */
  final def gc(): Unit = {
    expungeStaleEntries()
    val it = backing.iterator()
    while (it.hasNext) {
      val node  = it.next()
      val fiber = node.get()
      if ((fiber eq null) || !fiber.isAlive()) {
        it.remove()
      }
    }
  }

  override def forEach(action: Consumer[_ >: Fiber.Runtime[_, _]]): Unit = {
    expungeStaleEntries()
    val it = backing.iterator()
    while (it.hasNext) {
      val node  = it.next()
      val fiber = node.get()
      if ((fiber eq null) || !fiber.isAlive()) {
        it.remove()
      } else {
        action.accept(fiber)
      }
    }
  }

  override def iterator(): JavaIterator[Fiber.Runtime[_, _]] = {
    expungeStaleEntries()
    new JavaIterator[Fiber.Runtime[_, _]] {
      private[this] val underlying                = backing.iterator()
      private[this] var nextFiber: Fiber.Runtime[_, _] = advance()

      private def advance(): Fiber.Runtime[_, _] = {
        while (underlying.hasNext) {
          val node  = underlying.next()
          val fiber = node.get()
          if ((fiber eq null) || !fiber.isAlive()) {
            underlying.remove()
          } else {
            return fiber
          }
        }
        null
      }

      override def hasNext: Boolean = nextFiber ne null

      override def next(): Fiber.Runtime[_, _] = {
        val current = nextFiber
        if (current eq null) throw new NoSuchElementException("FiberSet iterator exhausted")
        nextFiber = advance()
        current
      }

      override def remove(): Unit =
        underlying.remove()
    }
  }

  def scalaIterator: Iterator[Fiber.Runtime[_, _]] =
    new Iterator[Fiber.Runtime[_, _]] {
      private[this] val jIterator = self.iterator()
      override def hasNext: Boolean = jIterator.hasNext
      override def next(): Fiber.Runtime[_, _] = jIterator.next()
    }

  private def self = this
}

private[zio] object FiberSet {
  private final val ExpungeMask = 63

  def apply(initialCapacity: Int = 16): FiberSet =
    new FiberSet(initialCapacity)

  private final class FiberRefNode(
    referent: Fiber.Runtime[_, _],
    queue: ReferenceQueue[Fiber.Runtime[_, _]]
  ) extends WeakReference[Fiber.Runtime[_, _]](referent, queue) {
    val hash: Int = System.identityHashCode(referent)

    override def hashCode(): Int = hash

    override def equals( that: Any): Boolean =
      if (this eq that.asInstanceOf[AnyRef]) true
      else {
        that match {
          case other: FiberRefNode =>
            if (this.hash != other.hash) false
            else {
              val a = this.get()
              (a ne null) && (a eq other.get())
            }
          case lookup: LookupKey =>
            (this.hash == lookup.hash) && (this.get() eq lookup.target)
          case _ =>
            false
        }
      }
  }

  private final class LookupKey(val target: Fiber.Runtime[_, _]) {
    val hash: Int = System.identityHashCode(target)

    override def hashCode(): Int = hash

    override def equals(that: Any): Boolean =
      that match {
        case node: FiberRefNode =>
          (this.hash == node.hash) && (this.target eq node.get())
        case other: LookupKey =>
          this.target eq other.target
        case _ =>
          false
      }
  }
}
