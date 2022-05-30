/*
 * Copyright 2022 John A. De Goes and the ZIO Contributors
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

import scala.annotation.tailrec
import java.util.concurrent.atomic.AtomicInteger

final class FiberStatusState(val ref: AtomicInteger) extends AnyVal {
  @tailrec
  final def attemptAsyncInterrupt(): Boolean = {
    val oldFlags  = getIndicator()
    val oldStatus = FiberStatusIndicator.getStatus(oldFlags)

    if ((oldStatus != FiberStatusIndicator.Status.Suspended) || !FiberStatusIndicator.getInterruptible(oldFlags)) false
    else {
      val newFlags = FiberStatusIndicator.withStatus(oldFlags, FiberStatusIndicator.Status.Running)

      if (!ref.compareAndSet(oldFlags, newFlags)) attemptAsyncInterrupt()
      else true
    }
  }

  @tailrec
  final def attemptDone(): Boolean = {
    val oldFlags  = getIndicator()
    val oldStatus = FiberStatusIndicator.getStatus(oldFlags)
    val hasMessages =
      FiberStatusIndicator.getPendingMessages(oldFlags) > 0 ||
        FiberStatusIndicator.getMessages(oldFlags)

    if (hasMessages) false
    else {
      assert(oldStatus != FiberStatusIndicator.Status.Done)

      val newFlags = FiberStatusIndicator.withStatus(oldFlags, FiberStatusIndicator.Status.Done)

      if (!ref.compareAndSet(oldFlags, newFlags)) attemptDone()
      else true
    }
  }

  @tailrec
  final def attemptResume(): Unit = {
    val oldFlags  = getIndicator()
    val oldStatus = FiberStatusIndicator.getStatus(oldFlags)

    assert(FiberStatusIndicator.getStatus(oldFlags) == FiberStatusIndicator.Status.Running)

    val newFlags = FiberStatusIndicator.withStatus(oldFlags, FiberStatusIndicator.Status.Running)

    if (!ref.compareAndSet(oldFlags, newFlags)) attemptResume()
    else ()
  }

  @tailrec
  final def beginAddMessage(): Boolean = {
    val oldFlags           = getIndicator()
    val oldStatus          = FiberStatusIndicator.getStatus(oldFlags)
    val oldPendingMessages = FiberStatusIndicator.getPendingMessages(oldFlags)

    val newFlags =
      FiberStatusIndicator.withPendingMessages(oldFlags, oldPendingMessages + 1)

    if (oldStatus == FiberStatusIndicator.Status.Done) false
    else if (!ref.compareAndSet(oldFlags, newFlags)) beginAddMessage()
    else true
  }

  @tailrec
  final def endAddMessage(): Unit = {
    val oldFlags           = getIndicator()
    val oldPendingMessages = FiberStatusIndicator.getPendingMessages(oldFlags)

    val newFlags =
      FiberStatusIndicator.withMessages(
        FiberStatusIndicator.withPendingMessages(oldFlags, oldPendingMessages - 1),
        true
      )

    if (!ref.compareAndSet(oldFlags, newFlags)) endAddMessage()
    else ()
  }

  final def getIndicator(): FiberStatusIndicator = ref.get.asInstanceOf[FiberStatusIndicator]

  final def getInterruptible(): Boolean = FiberStatusIndicator.getInterruptible(getIndicator())

  final def getInterrupting(): Boolean = FiberStatusIndicator.getInterrupting(getIndicator())

  @tailrec
  final def clearMessages(): Boolean = {
    val oldFlags           = getIndicator()
    val oldPendingMessages = FiberStatusIndicator.getPendingMessages(oldFlags)
    val oldMessages        = FiberStatusIndicator.getMessages(oldFlags)
    val hasMessages        = oldPendingMessages > 0 || oldMessages

    val newFlags = FiberStatusIndicator.withMessages(oldFlags, false)

    if (!ref.compareAndSet(oldFlags, newFlags)) clearMessages()
    else hasMessages
  }

  @tailrec
  final def enterSuspend(): Unit = {
    val oldFlags = getIndicator()
    val newFlags =
      FiberStatusIndicator.withStatus(oldFlags, FiberStatusIndicator.Status.Suspended)

    assert(FiberStatusIndicator.getStatus(oldFlags) == FiberStatusIndicator.Status.Running)

    if (!ref.compareAndSet(oldFlags, newFlags)) enterSuspend()
    else ()
  }

  final def getStatus(): FiberStatusIndicator.Status = FiberStatusIndicator.getStatus(getIndicator())

  @tailrec
  final def setInterruptible(interruptible: Boolean): Unit = {
    val oldFlags = getIndicator()
    val newFlags = FiberStatusIndicator.withInterruptible(oldFlags, interruptible)

    if (!ref.compareAndSet(oldFlags, newFlags)) setInterruptible(interruptible)
    else ()
  }

  @tailrec
  final def setInterrupting(interrupting: Boolean): Unit = {
    val oldFlags = getIndicator()
    val newFlags = FiberStatusIndicator.withInterrupting(oldFlags, interrupting)

    if (!ref.compareAndSet(oldFlags, newFlags)) setInterrupting(interrupting)
    else ()
  }
}
object FiberStatusState {
  def unsafeMake(initial: FiberStatusIndicator = FiberStatusIndicator.initial): FiberStatusState = {
    val atomic = new AtomicInteger(initial)

    new FiberStatusState(atomic)
  }
}
