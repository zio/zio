/*
 * Copyright 2024 John A. De Goes and the ZIO Contributors
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

import zio.{Duration, Fiber, FiberId, Unsafe}

import java.lang.ref.{ReferenceQueue, WeakReference}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import java.util.{Map => JMap}
import scala.annotation.tailrec

/**
 * A weak, concurrently updatable collection specialized for ZIO runtime fibers.
 *
 * The collection is keyed by `FiberId`, not by the fiber itself, so the backing
 * map never keeps fibers strongly reachable. Values are weak references backed
 * by a reference queue, which lets the structure remove collected fibers
 * without scanning the whole map on every operation.
 */
private[zio] final class FiberSet private (initialCapacity: Int) { self =>

  import FiberSet._

  private[this] val fibers: JMap[FiberId, Node] =
    Platform.newConcurrentMap[FiberId, Node](initialCapacity)(Unsafe)
  private[this] val queue   = new ReferenceQueue[Fiber.Runtime[_, _]]
  private[this] val opCount = new AtomicInteger(0)
  private[this] val autoGc  = new AtomicBoolean(false)

  def add(fiber: Fiber.Runtime[_, _]): Boolean =
    if ((fiber eq null) || !fiber.isAlive()) false
    else {
      drainQueuePeriodically()
      fibers.put(fiber.id, new Node(fiber.id, fiber, queue)) eq null
    }

  def remove(fiber: Fiber.Runtime[_, _]): Boolean =
    if (fiber eq null) false
    else {
      drainQueuePeriodically()
      fibers.remove(fiber.id) ne null
    }

  def clear(): Unit =
    fibers.clear()

  def gc(): Unit =
    drainQueue()

  /**
   * Schedules periodic cleanup of references that have already been cleared by
   * the garbage collector.
   *
   * This is used by the global root-fiber set so it does not require calls to
   * `Fiber.roots` to shed cleared entries. On Scala.js this is a no-op.
   */
  def withAutoGc(every: Duration): FiberSet = {
    if (autoGc.compareAndSet(false, true)) {
      FiberSetGc.start(self, every)
    }
    self
  }

  def isEmpty: Boolean =
    !iterator.hasNext

  def size: Int = {
    drainQueue()

    var size = 0
    val it   = fibers.values().iterator()
    while (it.hasNext) {
      val node  = it.next()
      val fiber = node.get()
      if ((fiber ne null) && fiber.isAlive()) size += 1
      else removeNode(node)
    }
    size
  }

  def iterator: Iterator[Fiber.Runtime[_, _]] = {
    drainQueue()

    new Iterator[Fiber.Runtime[_, _]] {
      private[this] val it        = fibers.values().iterator()
      private[this] var nextFiber = findNext()

      @tailrec
      private def findNext(): Fiber.Runtime[_, _] =
        if (it.hasNext) {
          val node  = it.next()
          val fiber = node.get()
          if ((fiber ne null) && fiber.isAlive()) fiber
          else {
            removeNode(node)
            findNext()
          }
        } else null.asInstanceOf[Fiber.Runtime[_, _]]

      def hasNext: Boolean =
        nextFiber ne null

      def next(): Fiber.Runtime[_, _] =
        if (nextFiber eq null) throw new NoSuchElementException("FiberSet iterator is empty")
        else {
          val fiber = nextFiber
          nextFiber = findNext()
          fiber
        }
    }
  }

  def foreach[U](f: Fiber.Runtime[_, _] => U): Unit =
    iterator.foreach(f)

  private def drainQueuePeriodically(): Unit =
    if ((opCount.incrementAndGet() & CleanupMask) == 0) drainQueue()

  private def drainQueue(): Unit = {
    var node = queue.poll().asInstanceOf[Node]
    while (node ne null) {
      removeNode(node)
      node = queue.poll().asInstanceOf[Node]
    }
  }

  private def removeNode(node: Node): Unit = {
    val _ = fibers.remove(node.fiberId)
    ()
  }
}

private[zio] object FiberSet {
  private final val DefaultInitialCapacity = 1024
  private final val CleanupMask            = 63

  def apply(initialCapacity: Int = DefaultInitialCapacity): FiberSet =
    new FiberSet(initialCapacity)

  private final class Node(
    val fiberId: FiberId,
    fiber: Fiber.Runtime[_, _],
    queue: ReferenceQueue[Fiber.Runtime[_, _]]
  ) extends WeakReference[Fiber.Runtime[_, _]](fiber, queue)
}
