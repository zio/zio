/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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

import java.util.concurrent.atomic.AtomicReference

/**
 * Specialized multi-producer / single-consumer mailbox for [[FiberRuntime]].
 *
 * The fiber run loop is the only consumer of its mailbox: `poll` and
 * `isEmpty` are always invoked from the thread that currently owns the
 * fiber. Producers (`add`) can come from any thread and are wait-free.
 *
 * Compared to `java.util.concurrent.ConcurrentLinkedQueue`, this
 * implementation:
 *
 *   - performs a single `getAndSet` per enqueue (no CAS-retry loop);
 *   - performs only a single volatile read per `poll` and only a relaxed
 *     read per `isEmpty`, since there is exactly one consumer thread;
 *   - does not maintain auxiliary metrics or a size counter.
 *
 * Algorithm: the lock-free MPSC queue described by Dmitry Vyukov,
 * <https://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue>.
 * The queue is FIFO across all producers, since each producer linearises on
 * the single `head.getAndSet` and the consumer walks the resulting chain in
 * insertion order.
 *
 * The queue exposes one observable race: a producer that has linked itself
 * into the `head` chain may not yet have published its predecessor's
 * `next`. From the consumer's point of view this manifests as `poll`
 * returning `null` while a strict observer would say a message is "in
 * flight". `isEmpty` correspondingly returns `true` whenever `poll` would
 * return `null`: that is, the consumer cannot distinguish a genuinely empty
 * mailbox from one that has a publisher mid-add. The fiber run loop already
 * tolerates this - see `FiberRuntime.drainQueueOnCurrentThread`, which
 * re-checks `isEmpty` and re-enters drain if a producer races with the
 * `running.compareAndSet` transition.
 *
 * @tparam A the element type. The mailbox does not impose a `null`
 *           restriction beyond what callers already do.
 */
private[zio] final class FiberMailbox[A <: AnyRef] {
  import FiberMailbox.Node

  // Initial sentinel node. `tail` always points at the most-recently-consumed
  // node (or, before the first poll, at this sentinel). Reading `tail.next`
  // gives the next message - or `null` if the queue is empty / a producer is
  // mid-publish.
  private[this] val sentinel = new Node[A](null.asInstanceOf[A])
  private[this] val head     = new AtomicReference[Node[A]](sentinel)

  // `tail` is touched only by the single consumer thread; no atomic needed.
  private[this] var tail = sentinel

  /**
   * Enqueue a message. Wait-free; callable from any thread.
   */
  def add(a: A): Unit = {
    val n    = new Node[A](a)
    val prev = head.getAndSet(n)
    // Publish the link with release semantics. Until this store completes, a
    // concurrent consumer observes the missing link as `null` and surfaces
    // the queue as empty - which is exactly what the fiber run loop can
    // tolerate.
    prev.lazySetNext(n)
  }

  /**
   * Dequeue the oldest message, or `null` if the mailbox is observably
   * empty.
   *
   * MUST only be called by the single consumer thread.
   */
  def poll(): A = {
    val next = tail.getNext()
    if (next eq null) {
      null.asInstanceOf[A]
    } else {
      val v = next.value
      // Clear the slot so the GC can collect the message and so any latent
      // re-read sees `null` rather than a stale reference.
      next.value = null.asInstanceOf[A]
      tail = next
      v
    }
  }

  /**
   * @return `true` if the mailbox is observably empty. May report `true`
   *         while a producer is mid-publish; never reports `false` while no
   *         message has been linked.
   */
  def isEmpty: Boolean =
    tail.getNext() eq null
}

private[internal] object FiberMailbox {

  /**
   * Linked node holding a single message and a publisher-visible `next`
   * pointer. The pointer uses an `AtomicReference` so a producer can publish
   * it with release semantics while the consumer reads with acquire
   * semantics, without bringing the rest of the structure under a CAS.
   */
  private final class Node[A](var value: A) {
    private[this] val next = new AtomicReference[Node[A]](null)

    @inline def getNext(): Node[A] = next.get()

    @inline def lazySetNext(n: Node[A]): Unit = next.lazySet(n)
  }
}
