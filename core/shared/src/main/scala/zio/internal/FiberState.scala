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


  object successor {
    type Flags <: Int 
    object Flags {
      def apply(status: Int, epoch: Int, interrupting: Boolean): Flags = 
        (status + (epoch << EpochShift) + ((if (interrupting) 1 else 0) << InterruptingShift)).asInstanceOf[Flags]

      def initial: Flags = apply(Status.Running, 0, false)

      object Status {
        final val Running   = (1 << 0)
        final val Suspended = (1 << 1)
        final val Done      = (1 << 2)
      }
      
      final val StatusSize          = 3 
      final val StatusMask          = (2 ^ StatusSize) << StatusShift
      final val StatusShift         = 0

      final val InterruptingShift   = StatusSize
      final val InterruptingSize    = 1 
      final val InterruptingMask    = (2 ^ InterruptingSize) << InterruptingShift
      final val InterruptingMaskN   = ~InterruptingMask 

      final val InterruptibleShift  = StatusSize + InterruptingSize
      final val InterruptibleSize   = 1 
      final val InterruptibleMask   = (2 ^ InterruptibleSize) << InterruptibleShift
      final val InterruptibleMaskN  = ~InterruptibleMask

      final val EpochShift          = StatusSize + InterruptingSize + InterruptibleSize
      final val EpochSize           = 32 - (StatusSize + InterruptingSize + InterruptibleSize)
      final val EpochMask           = (2 ^ EpochSize) << EpochShift
      final val EpochMaskN          = ~EpochMask

      def getStatus(flags: Flags): Int = (flags & StatusMask) >> StatusShift

      def getEpoch(flags: Flags): Int = (flags & EpochMask) >> EpochShift

      def getInterruptible(flags: Flags): Boolean = (flags & InterruptibleMask) != 0
      
      def getInterrupting(flags: Flags): Boolean = (flags & InterruptingMask) != 0

      def withEpoch(flags: Flags, epoch: Int): Flags = 
        ((epoch << EpochShift) | (flags & EpochMaskN)).asInstanceOf[Flags]

      def withInterruptible(flags: Flags, interruptible: Boolean): Flags = 
        (((if (interruptible) 1 else 0) << InterruptibleShift) | (flags & InterruptibleMaskN)).asInstanceOf[Flags]

      def withInterrupting(flags: Flags, interrupting: Boolean): Flags = 
        (((if (interrupting) 1 else 0) << InterruptingShift) | (flags & InterruptingMaskN)).asInstanceOf[Flags]

      def withStatus(flags: Flags, status: Int): Flags = 
        ((status << EpochShift) | (flags & EpochMaskN)).asInstanceOf[Flags]
    }
    final class FlagsState(val ref: AtomicInteger) {
      @tailrec
      final def asyncInterrupt(epoch: Int): Boolean = {
        val oldFlags  = getFlags
        val oldEpoch  = Flags.getEpoch(oldFlags)
        val oldStatus = Flags.getStatus(oldFlags)

        if ((oldEpoch != epoch) || (oldStatus != Flags.Status.Suspended) || !Flags.getInterruptible(oldFlags)) false 
        else {
          val newFlags = Flags.withEpoch(Flags.withStatus(oldFlags, Flags.Status.Running), oldEpoch + 1)
          
          if (!ref.compareAndSet(oldFlags, newFlags)) asyncInterrupt(epoch)
          else true
        }
      }

      final def getFlags: Flags = ref.get.asInstanceOf[Flags]

      final def getInterrupting(): Boolean = Flags.getInterrupting(getFlags)

      final def getInterruptible(): Boolean = Flags.getInterruptible(getFlags)

      final def getStatus(): Int = Flags.getStatus(getFlags)

      @tailrec
      final def resume(epoch: Int): Boolean = {
        val oldFlags  = getFlags
        val oldEpoch  = Flags.getEpoch(oldFlags)
        val oldStatus = Flags.getStatus(oldFlags)

        if (epoch != oldEpoch || oldStatus != Flags.Status.Suspended) false 
        else {
          val newFlags = Flags.withStatus(oldFlags, Flags.Status.Running)
          
          if (!ref.compareAndSet(oldFlags, newFlags)) resume(epoch)
          else true
        }
      }

      @tailrec 
      final def setInterrupting(): Unit = {
        val oldFlags  = getFlags
        val newFlags  = Flags.withInterrupting(oldFlags, true)

        if (!ref.compareAndSet(oldFlags, newFlags)) setInterrupting()
        else ()
      }

      @tailrec 
      final def suspend(interruptible: Boolean): Int = {
        val oldFlags = getFlags
        val oldEpoch = Flags.getEpoch(oldFlags)
        val newFlags = Flags.withInterruptible(Flags.withEpoch(Flags.withStatus(oldFlags, Flags.Status.Suspended), oldEpoch + 1), interruptible)

        if (!ref.compareAndSet(oldFlags, newFlags)) suspend(interruptible)
        else oldEpoch
      }
    }

    class SuspendedInfo {
      var blockingOn: FiberId = FiberId.None
      var asyncTrace: ZTraceElement = ZTraceElement.empty
    }

    import java.util.{ Set => JavaSet }

    class FiberContext[+E, +A](
      interruptible0: Boolean,
      runtimeConfig0: RuntimeConfig,
      location0: ZTraceElement) {
      val fiberId  = FiberId.unsafeMake()
      val location = location0
      val state    = new AtomicReference(new FiberState.Running[E, A](interruptible0, runtimeConfig0, location0))
    }

    sealed trait FiberState[+E, +A] {
      def tag: FiberState.Tag 
    }
    object FiberState { 
      type Tag <: Int 
      final val Done = 0.asInstanceOf[Tag]
      final val Running = 1.asInstanceOf[Tag]

      class Done[E, A](val exit: Exit[E, A]) extends FiberState[E, A] {
        def tag = Done 
      }
      class Running[+E, +A](
        interruptible0: Boolean,
        runtimeConfig0: RuntimeConfig,
        location0: ZTraceElement
      ) {
        val interruptStatus = StackBool(interruptible0)
        val stack           = Stack[Any => ZIO[Any, Any, Any]]()
        var fiberRefs       = Map.empty[FiberRef[_], Any]
        var suspendedInfo   = null.asInstanceOf[SuspendedInfo]
        var runtimeConfig   = runtimeConfig0
        var _children       = null.asInstanceOf[JavaSet[FiberContext[_, _]]]      
        val mailbox         = new AtomicReference[UIO[Any]](UIO.unit)
        val flagsState      = new FlagsState(new AtomicInteger(Flags.initial))

        final def addInterruptor(fiberId: FiberId): Unit = 
          setRef(FiberRef.interruptors, interruptors + fiberId)

        final def addSuppressed(cause: Cause[Nothing]): Unit = 
          setRef(FiberRef.suppressed, suppressed ++ cause)

        final def evalOn(effect: UIO[Any])(implicit trace: ZTraceElement): Boolean = {
          val oldMailbox = mailbox.get

          if (oldMailbox == null) false
          else {
            val newMailbox = if (oldMailbox eq ZIO.unit) effect else oldMailbox *> effect

            if (!mailbox.compareAndSet(oldMailbox, newMailbox)) evalOn(effect)
            else true
          }
        }

        final def getCurrentExecutor(): Option[Executor] =
          getRefOrElse(FiberRef.currentExecutor, None)

        final def getEnvironment(): ZEnvironment[Any] = 
          getRefOrElse(FiberRef.currentEnvironment, ZEnvironment.empty)

        final def getForkScopeOverride(): Option[ZScope] = 
          getRefOrElse(FiberRef.forkScopeOverride, None)

        final def getRef[A](ref: FiberRef[A]): Option[A] = 
          fiberRefs.get(ref).asInstanceOf[Option[A]]

        final def getRefOrElse[A](ref: FiberRef[A], orElse: A): A = 
          if (fiberRefs.contains(ref)) fiberRefs(ref).asInstanceOf[A]
          else orElse

        final def isDone(): Boolean = flagsState.getStatus() == Flags.Status.Done 

        final def isInterrupting(): Boolean = flagsState.getInterrupting()

        final def isRunning(): Boolean = flagsState.getStatus() == Flags.Status.Running

        final def isSuspended(): Boolean = flagsState.getStatus() == Flags.Status.Suspended 

        final def interruptors: Set[FiberId] = getRefOrElse(FiberRef.interruptors, Set.empty[FiberId])

        final def interruptorsCause: Cause[Nothing] =
          interruptors.foldLeft[Cause[Nothing]](Cause.empty) { case (acc, interruptor) =>
            acc ++ Cause.interrupt(interruptor)
          }

        final def setCurrentExecutor(executor: Executor): Unit = 
          setRef(FiberRef.currentExecutor, Some(executor))

        final def setEnvironment(env: ZEnvironment[Any]): Unit = 
          setRef(FiberRef.currentEnvironment, env)

        final def setRef[A](ref: FiberRef[A], value: A): Unit = {
          fiberRefs = fiberRefs + (ref -> value)
        }

        final def suppressed: Cause[Nothing] = getRefOrElse(FiberRef.suppressed, Cause.empty)

        def tag = Running
      }
    }
  }