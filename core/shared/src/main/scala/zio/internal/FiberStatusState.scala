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
  final def attemptAsyncInterrupt(asyncs: Int): Boolean = {
    val oldFlags  = getIndicator()
    val oldAsyncs = FiberStatusIndicator.getAsyncs(oldFlags)
    val oldStatus = FiberStatusIndicator.getStatus(oldFlags)

    if (
      (oldAsyncs != asyncs) || (oldStatus != FiberStatusIndicator.Status.Suspended) || !FiberStatusIndicator
        .getInterruptible(oldFlags)
    ) false
    else {
      val newFlags = FiberStatusIndicator.withStatus(oldFlags, FiberStatusIndicator.Status.Running)

      if (!ref.compareAndSet(oldFlags, newFlags)) attemptAsyncInterrupt(asyncs)
      else true
    }
  }

  @tailrec
  final def attemptDone(): Boolean = {
    val oldFlags    = getIndicator()
    val oldStatus   = FiberStatusIndicator.getStatus(oldFlags)
    val oldMessages = FiberStatusIndicator.getMessages(oldFlags)

    if (oldMessages) false
    else {
      assert(oldStatus != FiberStatusIndicator.Status.Done)

      val newFlags = FiberStatusIndicator.withStatus(oldFlags, FiberStatusIndicator.Status.Done)

      if (!ref.compareAndSet(oldFlags, newFlags)) attemptDone()
      else true
    }
  }

  @tailrec
  final def attemptResume(asyncs: Int): Boolean = {
    val oldFlags  = getIndicator()
    val oldAsyncs = FiberStatusIndicator.getAsyncs(oldFlags)
    val oldStatus = FiberStatusIndicator.getStatus(oldFlags)

    if (asyncs != oldAsyncs || oldStatus != FiberStatusIndicator.Status.Suspended) false
    else {
      val newFlags = FiberStatusIndicator.withStatus(oldFlags, FiberStatusIndicator.Status.Running)

      if (!ref.compareAndSet(oldFlags, newFlags)) attemptResume(asyncs)
      else true
    }
  }

  final def getAsyncs(): Int = FiberStatusIndicator.getAsyncs(getIndicator())

  final def getIndicator(): FiberStatusIndicator = ref.get.asInstanceOf[FiberStatusIndicator]

  final def getInterruptible(): Boolean = FiberStatusIndicator.getInterruptible(getIndicator())

  final def getInterrupting(): Boolean = FiberStatusIndicator.getInterrupting(getIndicator())

  final def getMessages(): Boolean = FiberStatusIndicator.getMessages(getIndicator())

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

  @tailrec
  final def setMessages(messages: Boolean): Unit = {
    val oldFlags = getIndicator()
    val newFlags = FiberStatusIndicator.withMessages(oldFlags, messages)

    if (!ref.compareAndSet(oldFlags, newFlags)) setMessages(messages)
    else ()
  }

  @tailrec
  final def enterSuspend(interruptible: Boolean): Int = {
    val oldFlags  = getIndicator()
    val oldAsyncs = FiberStatusIndicator.getAsyncs(oldFlags)
    val newAsyncs = oldAsyncs + 1
    val newFlags = FiberStatusIndicator.withInterruptible(
      FiberStatusIndicator
        .withAsyncs(FiberStatusIndicator.withStatus(oldFlags, FiberStatusIndicator.Status.Suspended), newAsyncs),
      interruptible
    )

    if (!ref.compareAndSet(oldFlags, newFlags)) enterSuspend(interruptible)
    else newAsyncs
  }
}
object FiberStatusState {
  def unsafeMake(initial: FiberStatusIndicator = FiberStatusIndicator.initial): FiberStatusState = {
    val atomic = new AtomicInteger(initial)

    new FiberStatusState(atomic)
  }
}
