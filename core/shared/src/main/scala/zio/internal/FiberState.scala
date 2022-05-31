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
import java.util.{Set => JavaSet}
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

final case class FiberSuspension(blockingOn: FiberId, location: Trace)

sealed trait FiberMessage
object FiberMessage {
  final case class InterruptSignal(cause: Cause[Nothing]) extends FiberMessage
  final case class GenStackTrace(onTrace: StackTrace => Unit) extends FiberMessage
  final case class Stateful(onFiber: RuntimeFiber[Any, Any] => Unit) extends FiberMessage
  final case class Resume(zio: ZIO[Any, Any, Any], stack: Chunk[ZIO.EvaluationStep]) extends FiberMessage
}

abstract class FiberState[E, A](fiberId0: FiberId.Runtime, fiberRefs0: FiberRefs) extends Fiber.Runtime.Internal[E, A] {
  private[zio] val queue       = new java.util.concurrent.ConcurrentLinkedQueue[FiberMessage]()
  private[zio] val statusState = new FiberStatusState(new AtomicInteger(FiberStatusIndicator.initial))

  private var _children  = null.asInstanceOf[JavaSet[RuntimeFiber[_, _]]]
  private var fiberRefs  = fiberRefs0
  private var observers  = Nil: List[Exit[E, A] => Unit]
  private var suspension = null.asInstanceOf[FiberSuspension]
  private[zio] var asyncStack = Chunk.empty[ZIO.EvaluationStep]

  protected val fiberId = fiberId0

  @volatile private var _exitValue = null.asInstanceOf[Exit[E, A]]


  final def scope: FiberScope = FiberScope.unsafeMake(this.asInstanceOf[RuntimeFiber[_, _]])

  /**
   * Adds a weakly-held reference to the specified fiber inside the children
   * set.
   */
  final def unsafeAddChild(child: RuntimeFiber[_, _]): Unit = {
    if (_children eq null) {
      _children = Platform.newWeakSet[RuntimeFiber[_, _]]()
    }
    _children.add(child)
    ()
  }

  /**
   * Adds an interruptor to the set of interruptors that are interrupting this
   * fiber.
   */
  final def unsafeAddInterruptedCause(cause: Cause[Nothing]): Unit = {
    val oldSC = unsafeGetFiberRef(FiberRef.interruptedCause)

    unsafeSetFiberRef(FiberRef.interruptedCause, oldSC ++ cause)
  }

  final def unsafeAddInterruptor(interruptor: ZIO[Any, Nothing, Nothing] => Unit): Unit = 
    ???    

  /**
   * Adds an observer to the list of observers.
   */
  final def unsafeAddObserver(observer: Exit[E, A] => Unit): Unit =
    observers = observer :: observers

  /**
   * Attempts to place the state of the fiber in interruption, but only if the
   * fiber is currently asynchronously suspended (hence, "async interruption").
   */
  final def unsafeAttemptAsyncInterrupt(): Boolean =
    statusState.attemptAsyncInterrupt()

  /**
   * Attempts to set the state of the fiber to done. This may fail if there are
   * pending messages in the mailbox, in which case those messages will be
   * returned. If the method succeeds, it will notify any listeners, who are
   * waiting for the exit value of the fiber.
   *
   * This method should only be called by the main runLoop of the fiber.
   *
   * @return
   *   `null` if the state of the fiber was set to done, or the pending
   *   messages, otherwise.
   */
  final def unsafeAttemptDone(e: Exit[E, A]): Unit = {
    println(s"\nfiber ${fiberId.threadName} attempting done with ${e}")

    _exitValue = e

    if (statusState.attemptDone()) {
      println(s"\nfiber ${fiberId.threadName} done, notifying ${observers.size} observers")
      val iterator = observers.iterator

      while (iterator.hasNext) {
        val observer = iterator.next()

        try observer(e)
        catch {
          case t: Throwable => println(s"Error notifying observer: ${t}"); throw t
        }
      }
      observers = Nil
      println(s"fiber ${fiberId.threadName} done notifying observers with ${e}")
      null.asInstanceOf[UIO[Any]]
    } else {
      println(s"fiber ${fiberId.threadName} not done yet, have work to do")
    }
  }

  /**
   * Places the state of the fiber into running, from a previous suspended
   * state.
   */
  final def unsafeAttemptResume(): Unit = {
    statusState.attemptResume()

    suspension = null // FIXME

    ()
  }

  final def unsafeDeleteFiberRef(ref: FiberRef[_]): Unit =
    fiberRefs = fiberRefs.remove(ref)


  /**
   * Changes the state to be suspended.
   */
  final def unsafeEnterSuspend(): Unit =
    statusState.enterSuspend()

  final def unsafeEvalOn[A](f: RuntimeFiber[Any, Any] => Unit): Unit =
    queue.add(FiberMessage.Stateful(f)) // TODO: Resume

  /**
   * Retrieves the exit value of the fiber state, which will be `null` if not
   * currently set.
   */
  final def unsafeExitValue(): Exit[E, A] = _exitValue

  final def unsafeForeachSupervisor(f: Supervisor[Any] => Unit): Unit =
    fiberRefs.getOrDefault(FiberRef.currentSupervisors).foreach(f)

  final def unsafeGetChildren(): JavaSet[RuntimeFiber[_, _]] = {
    if (_children eq null) {
      _children = Platform.newWeakSet[RuntimeFiber[_, _]]()
    }
    _children
  }

  final def unsafeGetCurrentExecutor(): Executor = unsafeGetFiberRef(FiberRef.currentExecutor)

  /**
   * Retrieves the state of the fiber ref, or else the specified value.
   */
  final def unsafeGetFiberRefOrElse[A](fiberRef: FiberRef[A], orElse: => A): A =
    fiberRefs.get(fiberRef).getOrElse(orElse)

  /**
   * Retrieves the state of the fiber ref, or else its initial value.
   */
  final def unsafeGetFiberRef[A](fiberRef: FiberRef[A]): A =
    fiberRefs.getOrDefault(fiberRef)

  final def unsafeGetFiberRefOption[A](fiberRef: FiberRef[A]): Option[A] =
    fiberRefs.get(fiberRef)

  final def unsafeGetFiberRefs(): FiberRefs = fiberRefs

  /**
   * Retrieves the interruptibility status of the fiber state.
   */
  final def unsafeGetInterruptible(): Boolean = statusState.getInterruptible()

  final def unsafeGetInterruptedCause(): Cause[Nothing] = unsafeGetFiberRef(FiberRef.interruptedCause)

  final def unsafeGetInterruptors(): Set[ZIO[Any, Nothing, Nothing] => Unit] = 
    unsafeGetFiberRef(FiberRef.interruptors)

  final def unsafeGetLoggers(): Set[ZLogger[String, Any]] =
    unsafeGetFiberRef(FiberRef.currentLoggers)

  final def unsafeGetReportFatal(): Throwable => Nothing =
    unsafeGetFiberRef(FiberRef.currentReportFatal)

  final def unsafeGetRuntimeFlags(): Set[RuntimeFlag] =
    unsafeGetFiberRef(FiberRef.currentRuntimeFlags)

  /**
   * Determines if the fiber state contains messages to process by the fiber run
   * runLoop. Due to race conditions, if this method returns true, it means only
   * that, if the messages were not drained, there will be some messages at some
   * point later, before the fiber state transitions to done.
   */
  final def unsafeHasMessages(): Boolean = {
    val indicator = statusState.getIndicator()

    FiberStatusIndicator.getPendingMessages(indicator) > 0 || FiberStatusIndicator.getMessages(indicator)
  }

  final def unsafeIsDone(): Boolean = {
    val indicator = statusState.getIndicator()

    FiberStatusIndicator.getStatus(indicator) == FiberStatusIndicator.Status.Done
  }

  final def unsafeIsFatal(t: Throwable): Boolean =
    unsafeGetFiberRef(FiberRef.currentFatal).exists(_.isAssignableFrom(t.getClass))

  final def unsafeIsInterrupted(): Boolean = !unsafeGetFiberRef(FiberRef.interruptedCause).isEmpty

  final def unsafeIsRunning(): Boolean = {
    val indicator = statusState.getIndicator()

    FiberStatusIndicator.getStatus(indicator) == FiberStatusIndicator.Status.Running
  }

  final def unsafeIsSuspended(): Boolean = {
    val indicator = statusState.getIndicator()

    FiberStatusIndicator.getStatus(indicator) == FiberStatusIndicator.Status.Suspended
  }

  final def unsafeLog(
    message: () => String,
    cause: Cause[Any],
    overrideLogLevel: Option[LogLevel],
    trace: Trace
  ): Unit = {
    val logLevel =
      if (overrideLogLevel.isDefined) overrideLogLevel.get
      else unsafeGetFiberRef(FiberRef.currentLogLevel)

    val spans       = unsafeGetFiberRef(FiberRef.currentLogSpan)
    val annotations = unsafeGetFiberRef(FiberRef.currentLogAnnotations)
    val loggers     = unsafeGetLoggers()
    val contextMap  = fiberRefs.unsafeGefMap() // FIXME: Change Logger to take FiberRefs

    loggers.foreach { logger =>
      logger(trace, fiberId, logLevel, message, cause, contextMap, spans, annotations)
    }
  }

  /**
   * Removes the child from the children list.
   */
  final def unsafeRemoveChild(child: RuntimeFiber[_, _]): Unit =
    if (_children ne null) {
      _children.remove(child)
      ()
    }

  final def unsafeRemoveInterruptor(interruptor: ZIO[Any, Nothing, Nothing] => Unit): Unit = {
    ???
  }

  /**
   * Removes the specified observer from the list of observers.
   */
  final def unsafeRemoveObserver(observer: Exit[E, A] => Unit): Unit =
    observers = observers.filter(_ ne observer)

  /**
   * Sets the fiber ref to the specified value.
   */
  final def unsafeSetFiberRef[A](fiberRef: FiberRef[A], value: A): Unit =
    fiberRefs = fiberRefs.updatedAs(fiberId)(fiberRef, value)

  final def unsafeSetFiberRefs(fiberRefs: FiberRefs): Unit =
    this.fiberRefs = fiberRefs

  /**
   * Sets the interruptibility status of the fiber to the specified value.
   */
  final def unsafeSetInterruptible(interruptible: Boolean): Unit =
    statusState.setInterruptible(interruptible)

  /**
   * Retrieves a snapshot of the status of the fibers.
   */
  final def unsafeGetStatus(): zio.Fiber.Status = {
    import FiberStatusIndicator.Status

    val indicator = statusState.getIndicator()

    val status       = FiberStatusIndicator.getStatus(indicator)
    val interrupting = FiberStatusIndicator.getInterrupting(indicator)

    if (status == Status.Done) zio.Fiber.Status.Done
    else if (status == Status.Running) zio.Fiber.Status.Running(interrupting)
    else {
      val interruptible = FiberStatusIndicator.getInterruptible(indicator)
      val blockingOn    = if (suspension eq null) FiberId.None else suspension.blockingOn
      val asyncTrace    = if (suspension eq null) Trace.empty else suspension.location

      zio.Fiber.Status.Suspended(interrupting, interruptible, blockingOn, asyncTrace)
    }
  }
}
