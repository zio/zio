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
  def apply[E, A](refs: FiberRefs): FiberState2[E, A] = new FiberState2(refs)
}
class FiberState2[E, A](fiberRefs0: FiberRefs) {
  import FiberStatusIndicator.Status

  val mailbox     = new AtomicReference[UIO[Any]](ZIO.unit)
  val statusState = new FiberStatusState(new AtomicInteger(FiberStatusIndicator.initial))

  var _children  = null.asInstanceOf[JavaSet[FiberContext[_, _]]]
  var fiberRefs  = fiberRefs0
  var observers  = List.empty[Exit[Nothing, Exit[E, A]] => Unit]
  var suspension = null.asInstanceOf[FiberSuspension]

  @volatile var _exitValue = null.asInstanceOf[Exit[E, A]]

  final def evalOn(effect: zio.UIO[Any], orElse: UIO[Any])(implicit trace: ZTraceElement): UIO[Unit] =
    UIO.suspendSucceed {
      if (addMessage(effect)) ZIO.unit else orElse.unit
    }

  final def addChild(child: FiberContext[_, _]): Unit = {
    if (_children eq null) {
      _children = Platform.newWeakSet[FiberContext[_, _]]()
    }
    _children.add(child)
  }

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

object successor {

  // class FiberState[E, A](
  //   interruptible0: Boolean,
  //   runtimeConfig0: RuntimeConfig,
  //   location0: ZTraceElement
  // ) {
  //   val fiberId         = FiberId.unsafeMake(location0)
  //   val interruptStatus = StackBool(interruptible0)
  //   val stack           = Stack[ZIO.TracedCont[Any, Any, Any, Any]]()
  //   var _fiberRefs      = null.asInstanceOf[JavaMap[FiberRef[_], Any]]
  //   var runtimeConfig   = runtimeConfig0
  //   var _children       = null.asInstanceOf[JavaSet[FiberContext[_, _]]]
  //   val mailbox         = new AtomicReference[UIO[Any]](ZIO.unit)
  //   val flagsState      = new FiberStatusState(new AtomicInteger(FiberStatusIndicator.initial))
  //   var exit            = null.asInstanceOf[Exit[E, A]]
  //   var suppressed      = null.asInstanceOf[Cause[Nothing]]

  //   final def await(implicit trace: ZTraceElement): UIO[Exit[E, A]] =
  //     ZIO.asyncInterrupt[Any, Nothing, Exit[E, A]](
  //       { k =>
  //         val observer = (x: Exit[Nothing, Exit[E, A]]) => k(ZIO.done(x))

  //         if (unsafeEvalOn(ZIO.succeed(unsafeAddObserver(observer)))) {
  //           Left(evalOn(ZIO.succeed(unsafeRemoveObserver(observer)), ZIO.unit))
  //         } else {
  //           Right(ZIO.succeedNow(unsafeGetDone()))
  //         }
  //       },
  //       fiberId
  //     )

  //   final def children(implicit trace: ZTraceElement): UIO[Chunk[Fiber.Runtime[_, _]]] =
  //     evalOnZIO(
  //       ZIO.succeed {
  //         val chunkBuilder = ChunkBuilder.make[Fiber.Runtime[_, _]](_children.size)

  //         val iterator = _children.iterator()

  //         while (iterator.hasNext()) {
  //           chunkBuilder += iterator.next()
  //         }

  //         chunkBuilder.result()
  //       },
  //       ZIO.succeed(Chunk.empty)
  //     )

  //   private final def childSet: JavaSet[FiberContext[_, _]] = {
  //     if (_children eq null) {
  //       _children = Platform.newWeakSet[FiberContext[_, _]]()
  //     }
  //     _children
  //   }

  //   final def eval(effect: zio.UIO[Any])(implicit trace: ZTraceElement): UIO[Unit] =
  //     evalOn(effect, effect).unit

  //   final def evalOn(effect: zio.UIO[Any], orElse: UIO[Any])(implicit trace: ZTraceElement): UIO[Unit] =
  //     UIO.suspendSucceed {
  //       if (unsafeEvalOn(effect)) ZIO.unit else orElse.unit
  //     }

  //   // TODO: Delete
  //   def evalOnZIO[R, E2, A2](effect: ZIO[R, E2, A2], orElse: ZIO[R, E2, A2])(implicit
  //     trace: ZTraceElement
  //   ): ZIO[R, E2, A2] =
  //     for {
  //       r <- ZIO.environment[R]
  //       p <- Promise.make[E2, A2]
  //       _ <- evalOn(effect.provideEnvironment(r).intoPromise(p), orElse.provideEnvironment(r).intoPromise(p))
  //       a <- p.await
  //     } yield a

  //   def evalZIO[R, E2, A2](effect: ZIO[R, E2, A2])(implicit
  //     trace: ZTraceElement
  //   ): ZIO[R, E2, A2] = evalOnZIO(effect, effect)

  //   private final def fiberRefs: JavaMap[FiberRef[_], Any] = {
  //     if (_fiberRefs eq null) {
  //       _fiberRefs = new JavaMap[FiberRef[_], Any]
  //     }
  //     _fiberRefs
  //   }

  //   final def getRef[A](ref: FiberRef[A])(implicit trace: ZTraceElement): UIO[A] =
  //     evalZIO(ZIO.succeed(unsafeGetRefOrInitial(ref)))

  //   final def id: FiberId.Runtime = fiberId

  //   final def inheritRefs(implicit trace: ZTraceElement): UIO[Unit] =
  //     eval {
  //       UIO.suspendSucceed {
  //         import scala.collection.JavaConverters._

  //         if (_fiberRefs eq null) ZIO.unit
  //         else {
  //           val locals = _fiberRefs.asScala

  //           if (locals.isEmpty) UIO.unit
  //           else
  //             UIO.foreachDiscard(locals) { case (fiberRef, value) =>
  //               val ref = fiberRef.asInstanceOf[FiberRef[Any]]
  //               ref.update(old => ref.join(old, value))
  //             }
  //         }
  //       }
  //     }

  //   final def interruptAs(fiberId: FiberId)(implicit trace: ZTraceElement): UIO[Exit[E, A]] =
  //     ??? // TODO

  //   final def location: ZTraceElement = fiberId.location

  //   final def poll(implicit trace: ZTraceElement): UIO[Option[Exit[E, A]]] =
  //     ZIO.succeed(unsafePoll())

  //   final def run(nextEffect: ZIO[_, _, _]): Unit =
  //     runUntil(nextEffect, unsafeGetExecutor().yieldOpCount)

  //   /**
  //    * The main evaluator loop for the fiber. For purely synchronous effects,
  //    * this will run either to completion, or for the specified maximum
  //    * operation count. For effects with asynchronous callbacks, the loop will
  //    * proceed no further than the first asynchronous boundary.
  //    */
  //   final def runUntil(nextEffect0: ZIO[_, _, _], maxOpCount: Int): Unit =
  //     ???

  //   override final def toString(): String =
  //     s"FiberContext($fiberId)"

  //   final def status(implicit trace: ZTraceElement): UIO[Fiber.Status] =
  //     evalOnZIO(ZIO.succeed(unsafeGetStatus()), ZIO.succeed(Fiber.Status.Done))

  //   final def trace(implicit trace0: ZTraceElement): UIO[ZTrace] =
  //     ZIO.succeed(unsafeCaptureTrace(Nil))

  //   final def unsafeAddInterruptor(fiberId: FiberId): Unit =
  //     unsafeSetRef(FiberRef.interruptors, unsafeGetInterruptors() + fiberId)

  //   final def unsafeAddObserver(k: Exit[Nothing, Exit[E, A]] => Unit): Unit =
  //     unsafeSetListeners(k :: unsafeGetListeners())

  //   // TODO: Rename due to async nature of this method
  //   final def unsafeAddObserverMaybe(k: Exit[Nothing, Exit[E, A]] => Unit): Exit[E, A] =
  //     if (unsafeEvalOn(ZIO.succeed(unsafeAddObserver(k))(ZTraceElement.empty))) null.asInstanceOf[Exit[E, A]]
  //     else unsafeGetDone()

  //   final def unsafeAddSuppressed(cause: Cause[Nothing]): Unit = {
  //     suppressed = suppressed ++ cause
  //   }

  //   final def unsafeCaptureTrace(prefix: List[ZTraceElement]): ZTrace = {
  //     val builder = StackTraceBuilder.unsafeMake()

  //     prefix.foreach(builder += _)
  //     stack.foreach(k => builder += k.trace)

  //     ZTrace(fiberId, builder.result())
  //   }

  //   final def unsafeClearSuppressed(): Cause[Nothing] = {
  //     val suppressed = unsafeGetRefOrInitial(FiberRef.suppressed)

  //     unsafeDeleteRef(FiberRef.suppressed)

  //     suppressed
  //   }

  //   final def unsafeDeleteRef(ref: FiberRef[_]): Unit =
  //     fiberRefs.remove(ref)

  //   final def unsafeDisableInterrupting(): Unit =
  //     interruptStatus.push(false)

  //   @tailrec
  //   final def unsafeDrainMailbox(): UIO[Any] = {
  //     val message = mailbox.get

  //     if (!mailbox.compareAndSet(message, ZIO.unit)) unsafeDrainMailbox()
  //     else message
  //   }

  //   final def unsafeEvalOn(effect: UIO[Any]): Boolean = {
  //     val oldMailbox = mailbox.get

  //     if (oldMailbox == null) false
  //     else {
  //       val newMailbox =
  //         if (oldMailbox eq ZIO.unit) effect
  //         else oldMailbox.flatMap(_ => effect)(ZTraceElement.empty)

  //       if (!mailbox.compareAndSet(oldMailbox, newMailbox)) unsafeEvalOn(effect)
  //       else true
  //     }
  //   }

  //   final def unsafeGetAsyncs(): Int =
  //     flagsState.getAsyncs()

  //   final def unsafeGetCurrentExecutor(): Option[Executor] =
  //     unsafeGetRefOrElse(FiberRef.currentExecutor, None)

  //   private def unsafeGetDescriptor(implicit trace: ZTraceElement): Fiber.Descriptor2 =
  //     Fiber.Descriptor2(
  //       fiberId,
  //       unsafeGetStatus(),
  //       unsafeGetInterruptors(),
  //       InterruptStatus.fromBoolean(unsafeIsInterruptible()),
  //       unsafeGetExecutor(),
  //       fiberRefs.containsKey(FiberRef.currentExecutor)
  //     )

  //   final def unsafeGetDone(): Exit[E, A] =
  //     unsafeGetRefOrElse(FiberRef.exit, Exit.empty).asInstanceOf[Exit[E, A]]

  //   final def unsafeGetEnvironment(): ZEnvironment[Any] =
  //     unsafeGetRefOrElse(FiberRef.currentEnvironment, ZEnvironment.empty)

  //   private def unsafeGetExecutor(): zio.Executor =
  //     unsafeGetRefOrInitial(FiberRef.currentExecutor).getOrElse(runtimeConfig.executor)

  //   final def unsafeGetForkScopeOverride(): Option[FiberScope] =
  //     unsafeGetRefOrInitial(FiberRef.forkScopeOverride)

  //   final def unsafeGetInterruptors(): Set[FiberId] = unsafeGetRefOrElse(FiberRef.interruptors, Set.empty[FiberId])

  //   final def unsafeGetInterruptorsCause: Cause[Nothing] =
  //     unsafeGetInterruptors().foldLeft[Cause[Nothing]](Cause.empty) { case (acc, interruptor) =>
  //       acc ++ Cause.interrupt(interruptor)
  //     }

  //   final def unsafeGetListeners(): List[Exit[Nothing, Exit[E, A]] => Unit] =
  //     unsafeGetRefOrElse(FiberRef.listeners, Nil).asInstanceOf[List[Exit[Nothing, Exit[E, A]] => Unit]]

  //   final def unsafeGetRef[A](ref: FiberRef[A]): Option[A] =
  //     if (fiberRefs.containsKey(ref)) Some(fiberRefs.get(ref).asInstanceOf[A])
  //     else None

  //   final def unsafeGetRefOrInitial[A](ref: FiberRef[A]): A =
  //     if (fiberRefs.containsKey(ref)) fiberRefs.get(ref).asInstanceOf[A]
  //     else ref.initial

  //   final def unsafeGetRefOrElse[A](ref: FiberRef[A], orElse: A): A =
  //     if (fiberRefs.containsKey(ref)) fiberRefs.get(ref).asInstanceOf[A]
  //     else orElse

  //   final def unsafeGetStatus(): Fiber.Status =
  //     if (unsafeIsDone()) Fiber.Status.Done
  //     else
  //       Fiber.Status.Running(
  //         unsafeIsInterrupting()
  //       )

  //   final def unsafeGetSuppressed(): Cause[Nothing] = unsafeGetRefOrInitial(FiberRef.suppressed)

  //   final def unsafeIsDone(): Boolean = flagsState.getStatus() == FiberStatusIndicator.Status.Done

  //   final def unsafeIsInterruptible(): Boolean = interruptStatus.peekOrElse(true)

  //   final def unsafeIsInterrupting(): Boolean = flagsState.getInterrupting()

  //   final def unsafeIsRunning(): Boolean = flagsState.getStatus() == FiberStatusIndicator.Status.Running

  //   final def unsafeIsSuspended(): Boolean = flagsState.getStatus() == FiberStatusIndicator.Status.Suspended

  //   final def unsafeLog(
  //     message: () => String,
  //     trace: ZTraceElement,
  //     cause: () => Cause[Any] = null,
  //     overrideLogLevel: Option[LogLevel] = None,
  //     overrideRef1: FiberRef[_] = null,
  //     overrideValue1: AnyRef = null
  //   ): Unit = {
  //     val logLevel =
  //       if (overrideLogLevel.isDefined) overrideLogLevel.get else unsafeGetRefOrInitial(FiberRef.currentLogLevel)
  //     val spans       = unsafeGetRefOrInitial(FiberRef.currentLogSpan)
  //     val annotations = unsafeGetRefOrInitial(FiberRef.currentLogAnnotations)

  //     val contextMap =
  //       if (overrideRef1 ne null) {
  //         val map: Map[FiberRef[_], AnyRef] = ??? // FIXME

  //         if (overrideValue1 eq null) map - overrideRef1
  //         else map.updated(overrideRef1, overrideValue1)
  //       } else ??? // FIXME

  //     runtimeConfig.logger(
  //       trace,
  //       fiberId,
  //       logLevel,
  //       message,
  //       if (cause eq null) Cause.empty else cause(),
  //       contextMap,
  //       spans,
  //       annotations
  //     ) // FIXME
  //   }

  //   final def unsafePoll(): Option[Exit[E, A]] =
  //     if (unsafeIsDone()) Some(unsafeGetDone())
  //     else None

  //   final def unsafeRemoveObserver(k: Exit[Nothing, Exit[E, A]] => Unit): Unit =
  //     unsafeSetListeners(unsafeGetListeners().filter(_ ne k))

  //   final def unsafeReportUnhandled(v: Exit[E, A], trace: ZTraceElement): Unit = v match {
  //     case Exit.Failure(cause) =>
  //       try {
  //         unsafeLog(() => "Fiber failed with an unhandled error", trace, () => cause, ZIO.someDebug)
  //       } catch {
  //         case t: Throwable =>
  //           if (runtimeConfig.fatal(t)) {
  //             runtimeConfig.reportFatal(t)
  //           } else {
  //             println("An exception was thrown by a logger:")
  //             t.printStackTrace
  //           }
  //       }
  //     case _ =>
  //   }

  //   final def unsafeRunLater(zio: ZIO[_, _, _]): Unit =
  //     if (stack.isEmpty) unsafeGetExecutor().unsafeSubmitAndYieldOrThrow(() => run(zio))
  //     else unsafeGetExecutor().unsafeSubmitOrThrow(() => run(zio))

  //   final def unsafeSetCurrentExecutor(executor: Executor): Unit =
  //     unsafeSetRef(FiberRef.currentExecutor, Some(executor))

  //   final def unsafeSetDone(exit: Exit[E, A]): Unit =
  //     unsafeSetRef(FiberRef.exit, exit)

  //   final def unsafeSetEnvironment(env: ZEnvironment[Any]): Unit =
  //     unsafeSetRef(FiberRef.currentEnvironment, env)

  //   final def unsafeSetInterrupting(): Unit =
  //     flagsState.setInterrupting()

  //   final def unsafeSetListeners(ls: List[Exit[Nothing, Exit[E, A]] => Unit]): Unit =
  //     unsafeSetRef(FiberRef.listeners, ls.asInstanceOf[List[Exit[Nothing, Exit[Any, Any]] => Unit]])

  //   final def unsafeSetRef[A](ref: FiberRef[A], value: A): Unit =
  //     fiberRefs.put(ref, value)
  // }
}
