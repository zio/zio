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

import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.lang.ref.{ReferenceQueue, WeakReference}
import java.util.concurrent.ConcurrentHashMap
import java.util.{AbstractSet, Iterator => JIterator}

/**
 * A Loom-friendly, concurrent, weak-reference set for tracking [[FiberRuntime]]
 * children.
 *
 * Replaces the previous
 * `Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap))` on
 * the JVM with a [[ConcurrentHashMap]]-backed implementation that avoids the
 * global ''synchronized'' monitor. On Project Loom, a ''synchronized'' block
 * pins the carrier thread and prevents virtual-thread scheduling; this
 * implementation uses only the internal striped locks of [[ConcurrentHashMap]],
 * which are Loom-compatible.
 *
 * ==Design==
 *
 * Elements are stored as [[IdentityWeakKey]] instances:
 * {{{
 *   ConcurrentHashMap[ IdentityWeakKey[A], java.lang.Boolean ]
 * }}}
 * Each key holds a [[WeakReference]] to the element and records the element's
 * [[System.identityHashCode]] at insertion time. When the GC collects the
 * element the key is enqueued in a [[ReferenceQueue]]; the next call to
 * [[add]], [[size]], [[isEmpty]], or [[iterator]] drains that queue and removes
 * the corresponding dead keys from the map ([[expunge]]).
 *
 * ==Thread safety==
 *
 * All mutations delegate to [[ConcurrentHashMap]]; individual operations are
 * linearisable. [[expunge]] is not synchronized so, under contention, two
 * threads may both try to remove the same dead key — this is harmless because
 * [[ConcurrentHashMap.remove]] is idempotent.
 *
 * ==Eventual consistency of [[isEmpty]]==
 *
 * Like [[FiberMailbox.isEmpty]], this method is ''eventually consistent'':
 * a concurrent [[add]] that has not yet completed may cause a transient false
 * positive. Callers (i.e. [[FiberRuntime]]) must tolerate false negatives.
 */
private[zio] final class FiberSet[A <: AnyRef] extends AbstractSet[A] {

  private val backing  = new ConcurrentHashMap[IdentityWeakKey[A], java.lang.Boolean]()
  private val refQueue = new ReferenceQueue[A]()

  // -------------------------------------------------------------------------
  // AbstractSet contract
  // -------------------------------------------------------------------------

  override def add(a: A): Boolean = {
    if (a eq null) throw new NullPointerException("FiberSet does not accept null elements")
    expunge()
    backing.put(new IdentityWeakKey[A](a, refQueue), java.lang.Boolean.TRUE) eq null
  }

  override def size(): Int = {
    expunge()
    backing.size()
  }

  override def isEmpty: Boolean = {
    expunge()
    backing.isEmpty
  }

  override def iterator(): JIterator[A] = {
    expunge()
    new JIterator[A] {
      private val inner  = backing.keySet().iterator()
      private var _next  = fetchNext()

      private def fetchNext(): A = {
        while (inner.hasNext) {
          val v = inner.next().get()
          if (v ne null) return v
          else inner.remove()
        }
        null.asInstanceOf[A]
      }

      override def hasNext: Boolean = _next ne null
      override def next(): A = {
        if (_next eq null) throw new java.util.NoSuchElementException
        val result = _next
        _next = fetchNext()
        result
      }
    }
  }

  // -------------------------------------------------------------------------
  // GC cleanup
  // -------------------------------------------------------------------------

  /**
   * Drains GC'd entries from the [[ReferenceQueue]] and removes them from the
   * backing map. Called automatically before any structural operation.
   */
  private def expunge(): Unit = {
    var ref = refQueue.poll()
    while (ref ne null) {
      backing.remove(ref)
      ref = refQueue.poll()
    }
  }
}

/**
 * An identity-based [[WeakReference]] key for use inside [[ConcurrentHashMap]].
 *
 * Two [[IdentityWeakKey]] instances are ''equal'' iff their referents are the
 * same object (reference equality), falling back to [[AnyRef]] reference
 * equality when one or both referents have been GC'd (so that dead keys can
 * still be removed via [[ConcurrentHashMap.remove]] from [[FiberSet.expunge]]).
 *
 * The hash code is fixed at construction time to the identity hash of the
 * referent so that the key remains usable after the referent is collected.
 */
private[internal] final class IdentityWeakKey[A <: AnyRef](ref: A, queue: ReferenceQueue[A])
    extends WeakReference[A](ref, queue) {

  private val _hash: Int = System.identityHashCode(ref)

  override def hashCode(): Int = _hash

  override def equals(obj: Any): Boolean = obj match {
    case other: IdentityWeakKey[_] =>
      val a = get()
      val b = other.get()
      if ((a ne null) && (b ne null)) a eq b  // both alive: compare by identity
      else this eq other                        // at least one dead: fall back to ref equality
    case _ => false
  }
}
