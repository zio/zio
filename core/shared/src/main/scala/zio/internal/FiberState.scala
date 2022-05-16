/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
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

import zio.Fiber.Status
import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

sealed abstract class FiberState[+E, +A] extends Serializable with Product {
  def suppressed: Cause[Nothing]
  def status: Fiber.Status
  def isInterrupting: Boolean = status.isInterrupting
  def interruptors: Set[FiberId]
  def interruptorsCause: Cause[Nothing] =
    interruptors.foldLeft[Cause[Nothing]](Cause.empty) { case (acc, interruptor) =>
      acc ++ Cause.interrupt(interruptor)
    }
}
object FiberState extends Serializable {
  sealed abstract class CancelerState

  object CancelerState {
    case object Empty                                              extends CancelerState
    case object Pending                                            extends CancelerState
    final case class Registered(asyncCanceler: ZIO[Any, Any, Any]) extends CancelerState
  }

  final case class Executing[E, A](
    status: Fiber.Status,
    observers: List[Callback[Nothing, Exit[E, A]]],
    suppressed: Cause[Nothing],
    interruptors: Set[FiberId],
    asyncCanceler: CancelerState,
    mailbox: UIO[Any]
  ) extends FiberState[E, A]
  final case class Done[E, A](value: Exit[E, A]) extends FiberState[E, A] {
    def suppressed: Cause[Nothing] = Cause.empty
    def status: Fiber.Status       = Status.Done
    def interruptors: Set[FiberId] = Set.empty
  }

  def initial[E, A]: Executing[E, A] =
    Executing[E, A](
      Status.Running(false),
      Nil,
      Cause.empty,
      Set.empty[FiberId],
      CancelerState.Empty,
      null.asInstanceOf[UIO[Any]]
    )
}

import java.util.{HashMap => JavaMap, Set => JavaSet}

final case class FiberSuspension(blockingOn: FiberId, location: ZTraceElement)

object FiberState2 {
  def apply[E, A](location: ZTraceElement, refs: FiberRefs): FiberState2[E, A] = new FiberState2(location, refs)
}
class FiberState2[E, A](location0: ZTraceElement, fiberRefs0: FiberRefs) {
  import FiberStatusIndicator.Status

  val mailbox     = new AtomicReference[UIO[Any]](ZIO.unit)
  val statusState = new FiberStatusState(new AtomicInteger(FiberStatusIndicator.initial))

  var _children  = null.asInstanceOf[JavaSet[FiberContext[_, _]]]
  var fiberRefs  = fiberRefs0
  var observers  = Nil: List[Exit[Nothing, Exit[E, A]] => Unit]
  var suspension = null.asInstanceOf[FiberSuspension]
  val fiberId    = FiberId.unsafeMake(location0)

  @volatile var _exitValue = null.asInstanceOf[Exit[E, A]]

  final def evalOn(effect: zio.UIO[Any], orElse: UIO[Any])(implicit trace: ZTraceElement): UIO[Unit] =
    UIO.suspendSucceed {
      if (addMessage(effect)) ZIO.unit else orElse.unit
    }

  /**
   * Adds a weakly-held reference to the specified fiber inside the children
   * set.
   */
  final def addChild(child: FiberContext[_, _]): Unit = {
    if (_children eq null) {
      _children = Platform.newWeakSet[FiberContext[_, _]]()
    }
    _children.add(child)
  }

  /**
   * Adds a message to the mailbox and returns true if the state is not done.
   * Otherwise, returns false to indicate the fiber cannot accept messages.
   */
  final def addMessage(effect: UIO[Any]): Boolean = {
    @tailrec
    def loop(message: UIO[Any]): Unit = {
      val oldMessages = mailbox.get

      val newMessages =
        if (oldMessages eq ZIO.unit) message else (oldMessages *> message)(ZTraceElement.empty)

      if (!mailbox.compareAndSet(oldMessages, newMessages)) loop(message)
      else ()
    }

    if (statusState.beginAddMessage()) {
      try {
        loop(effect)
        true
      } finally statusState.endAddMessage()
    } else false
  }

  /**
   * Adds an observer to the list of observers.
   */
  final def addObserver(observer: Exit[Nothing, Exit[E, A]] => Unit): Unit =
    observers = observer :: observers

  /**
   * Attempts to place the state of the fiber in interruption, but only if the
   * fiber is currently asynchronously suspended (hence, "async interruption").
   */
  final def attemptAsyncInterrupt(asyncs: Int): Boolean =
    statusState.attemptAsyncInterrupt(asyncs)

  /**
   * Attempts to set the state of the fiber to done. This may fail if there are
   * pending messages in the mailbox, in which case those messages will be
   * returned. This method should only be called by the main loop of the fiber.
   *
   * @return
   *   `null` if the state of the fiber was set to done, or the pending
   *   messages, otherwise.
   */
  final def attemptDone(e: Exit[E, A]): UIO[Any] = {
    _exitValue = e

    if (statusState.attemptDone()) {
      null.asInstanceOf[UIO[Any]]
    } else drainMailbox()
  }

  /**
   * Attempts to place the state of the fiber into running, from a previous
   * suspended state, identified by the async count.
   *
   * Returns `true` if the state was successfully transitioned, or `false` if it
   * cannot be transitioned, because the fiber state was already resumed or even
   * completed.
   */
  final def attemptResume(asyncs: Int): Boolean = {
    val resumed = statusState.attemptResume(asyncs)

    if (resumed) suspension = null

    resumed
  }

  /**
   * Drains the mailbox of all messages. If the mailbox is empty, this will
   * return `ZIO.unit`.
   */
  final def drainMailbox(): UIO[Any] = {
    @tailrec
    def clearMailbox(): UIO[Any] = {
      val oldMailbox = mailbox.get

      if (!mailbox.compareAndSet(oldMailbox, ZIO.unit)) clearMailbox()
      else oldMailbox
    }

    if (statusState.clearMessages()) clearMailbox()
    else ZIO.unit
  }

  /**
   * Changes the state to be suspended.
   */
  final def enterSuspend(): Int =
    statusState.enterSuspend(getFiberRef(FiberRef.interruptible))

  /**
   * Retrieves the exit value of the fiber state, which will be `null` if not
   * currently set.
   */
  final def exitValue(): Exit[E, A] = _exitValue

  /**
   * Retrieves the current number of async suspensions of the fiber, which can
   * be used to uniquely identify each suspeension.
   */
  final def getAsyncs(): Int = statusState.getAsyncs()

  /**
   * Retrieves the state of the fiber ref, or else the specified value.
   */
  final def getFiberRefOrElse[A](fiberRef: FiberRef[A], orElse: => A): A =
    fiberRefs.get(fiberRef).getOrElse(orElse)

  /**
   * Retrieves the state of the fiber ref, or else its initial value.
   */
  final def getFiberRef[A](fiberRef: FiberRef[A]): A =
    fiberRefs.getOrDefault(fiberRef)

  /**
   * Retrieves the interruptibility status of the fiber state.
   */
  final def getInterruptible(): Boolean = getFiberRef(FiberRef.interruptible)

  /**
   * Determines if the fiber state contains messages to process by the fiber run
   * loop. Due to race conditions, if this method returns true, it means only
   * that, if the messages were not drained, there will be some messages at some
   * point later, before the fiber state transitions to done.
   */
  final def hasMessages(): Boolean = {
    val indicator = statusState.getIndicator()

    FiberStatusIndicator.getPendingMessages(indicator) > 0 || FiberStatusIndicator.getMessages(indicator)
  }

  /**
   * Retrieves the location from whence the fiber was forked, which is a way to
   * tie the executing logic of the fiber back to source code locations.
   */
  final def location: ZTraceElement = fiberId.location

  /**
   * Removes the child from the children list.
   */
  final def removeChild(child: FiberContext[_, _]): Unit =
    if (_children ne null) {
      _children.remove(child)
    }

  /**
   * Removes the specified observer from the list of observers.
   */
  final def removeObserver(observer: Exit[Nothing, Exit[E, A]] => Unit): Unit =
    observers = observers.filter(_ ne observer)

  /**
   * Sets the fiber ref to the specified value.
   */
  final def setFiberRef[A](fiberRef: FiberRef[A], value: A): Unit =
    fiberRefs = fiberRefs.updatedAs(fiberId)(fiberRef, value)

  /**
   * Sets the interruptibility status of the fiber to the specified value.
   */
  final def setInterruptible(interruptible: Boolean): Unit =
    setFiberRef(FiberRef.interruptible, interruptible)

  /**
   * Retrieves a snapshot of the status of the fibers.
   */
  final def status(): Fiber.Status = {
    import FiberStatusIndicator.Status

    val indicator = statusState.getIndicator()

    val status       = FiberStatusIndicator.getStatus(indicator)
    val interrupting = FiberStatusIndicator.getInterrupting(indicator)

    if (status == Status.Done) Fiber.Status.Done
    else if (status == Status.Running) Fiber.Status.Running(interrupting)
    else {
      val interruptible = FiberStatusIndicator.getInterruptible(indicator)
      val asyncs        = FiberStatusIndicator.getAsyncs(indicator)
      val blockingOn    = if (suspension eq null) FiberId.None else suspension.blockingOn
      val asyncTrace    = if (suspension eq null) ZTraceElement.empty else suspension.location

      Fiber.Status.Suspended(interrupting, interruptible, asyncs, blockingOn, asyncTrace)
    }
  }
}
