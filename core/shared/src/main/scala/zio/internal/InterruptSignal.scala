/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import zio.UIO
import zio.IO

/**
 * Represents an interruption signal for a fiber. It keeps track of the
 * interruption status of the fiber, child fibers, and so forth.
 */
private[internal] class InterruptSignal private (
  private var onDone: () => Unit,
  private var parent: InterruptSignal,
  private val children: AtomicReference[Set[FiberContext[_, _]]],
  @volatile private[this] var ownerInterrupted: Boolean
) { self =>

  /**
   * Records a new child, forked from the fiber that owns this signal.
   */
  @tailrec
  final def forkChild(childFiber: FiberContext[_, _]): InterruptSignal = {
    val oldState = children.get

    if (!children.compareAndSet(oldState, oldState + childFiber)) forkChild(childFiber)
    else InterruptSignal.make(() => self.childDone(childFiber), self, new AtomicReference(Set()))
  }

  /**
   * Returns an effect that semantically blocks until all children of the
   * owner of this signal have been successfully interrupted.
   */
  final def interruptChildren: UIO[Unit] =
    children.get.foldLeft[UIO[Any]](IO.unit)(_ *> _.interrupt) *> UIO(children.set(Set.empty))

  /**
   * Determines if the fiber owning this signal has been ownerInterrupted.
   */
  final def isInterrupted: Boolean =
    ownerInterrupted || ((parent ne null) && parent.isInterrupted)

  final def isInterruptedFlat: Boolean =
    ownerInterrupted

  /**
   * Marks the fiber that owns this signal as done.
   */
  final def ownerDone(): Unit =
    if (onDone ne null) {
      onDone()
      onDone = null
    }

  /**
   * Sets the interruption status of the fiber owning this signal.
   */
  final def setInterrupted(flag: Boolean): Unit = ownerInterrupted = flag

  /**
   * Marks the specified child as done. This method has no effect if the
   * specified fiber is not a child of the owner of this signal.
   */
  @tailrec
  private final def childDone(fiber: FiberContext[_, _]): Unit = {
    val oldState = children.get

    if (!children.compareAndSet(oldState, oldState - fiber)) childDone(fiber)
  }
}
private[zio] object InterruptSignal {
  private[zio] final def root(): InterruptSignal =
    make(() => (), null, new AtomicReference(Set()))

  private final def make(
    onDone: () => Unit,
    parentInterrupted: InterruptSignal,
    children: AtomicReference[Set[FiberContext[_, _]]]
  ): InterruptSignal =
    new InterruptSignal(onDone, parentInterrupted, children, false)

  private[zio] def garbageCollect(signal: InterruptSignal): InterruptSignal =
    if (signal eq null) signal
    else if (signal.onDone eq null) {
      // Signal is done and should be garbage collected:
      if (signal.parent eq null) null
      else garbageCollect(signal.parent)
    } else {
      make(signal.onDone, garbageCollect(signal.parent), signal.children)
    }
}
