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
    def apply(status: Int, asyncs: Int, interrupting: Boolean): Flags =
      (status + (asyncs << AsyncsShift) + ((if (interrupting) 1 else 0) << InterruptingShift)).asInstanceOf[Flags]

    def initial: Flags = apply(Status.Running, 0, false)

    object Status {
      final val Running   = (1 << 0)
      final val Suspended = (1 << 1)
      final val Done      = (1 << 2)
    }

    final val StatusSize  = 3
    final val StatusMask  = (2 ^ StatusSize) << StatusShift
    final val StatusShift = 0

    final val InterruptingShift = StatusSize
    final val InterruptingSize  = 1
    final val InterruptingMask  = (2 ^ InterruptingSize) << InterruptingShift
    final val InterruptingMaskN = ~InterruptingMask

    final val InterruptibleShift = StatusSize + InterruptingSize
    final val InterruptibleSize  = 1
    final val InterruptibleMask  = (2 ^ InterruptibleSize) << InterruptibleShift
    final val InterruptibleMaskN = ~InterruptibleMask

    final val AsyncsShift = StatusSize + InterruptingSize + InterruptibleSize
    final val AsyncsSize  = 32 - (StatusSize + InterruptingSize + InterruptibleSize)
    final val AsyncsMask  = (2 ^ AsyncsSize) << AsyncsShift
    final val AsyncsMaskN = ~AsyncsMask

    def getStatus(flags: Flags): Int = (flags & StatusMask) >> StatusShift

    def getAsyncs(flags: Flags): Int = (flags & AsyncsMask) >> AsyncsShift

    def getInterruptible(flags: Flags): Boolean = (flags & InterruptibleMask) != 0

    def getInterrupting(flags: Flags): Boolean = (flags & InterruptingMask) != 0

    def withAsyncs(flags: Flags, asyncs: Int): Flags =
      ((asyncs << AsyncsShift) | (flags & AsyncsMaskN)).asInstanceOf[Flags]

    def withInterruptible(flags: Flags, interruptible: Boolean): Flags =
      (((if (interruptible) 1 else 0) << InterruptibleShift) | (flags & InterruptibleMaskN)).asInstanceOf[Flags]

    def withInterrupting(flags: Flags, interrupting: Boolean): Flags =
      (((if (interrupting) 1 else 0) << InterruptingShift) | (flags & InterruptingMaskN)).asInstanceOf[Flags]

    def withStatus(flags: Flags, status: Int): Flags =
      ((status << AsyncsShift) | (flags & AsyncsMaskN)).asInstanceOf[Flags]
  }
  final class FlagsState(val ref: AtomicInteger) extends AnyVal {
    @tailrec
    final def asyncInterrupt(asyncs: Int): Boolean = {
      val oldFlags  = getFlags
      val oldAsyncs = Flags.getAsyncs(oldFlags)
      val oldStatus = Flags.getStatus(oldFlags)

      if ((oldAsyncs != asyncs) || (oldStatus != Flags.Status.Suspended) || !Flags.getInterruptible(oldFlags)) false
      else {
        val newFlags = Flags.withAsyncs(Flags.withStatus(oldFlags, Flags.Status.Running), oldAsyncs + 1)

        if (!ref.compareAndSet(oldFlags, newFlags)) asyncInterrupt(asyncs)
        else true
      }
    }

    final def getAsyncs(): Int = Flags.getAsyncs(getFlags)

    final def getFlags: Flags = ref.get.asInstanceOf[Flags]

    final def getInterrupting(): Boolean = Flags.getInterrupting(getFlags)

    final def getInterruptible(): Boolean = Flags.getInterruptible(getFlags)

    final def getStatus(): Int = Flags.getStatus(getFlags)

    @tailrec
    final def resume(asyncs: Int): Boolean = {
      val oldFlags  = getFlags
      val oldAsyncs = Flags.getAsyncs(oldFlags)
      val oldStatus = Flags.getStatus(oldFlags)

      if (asyncs != oldAsyncs || oldStatus != Flags.Status.Suspended) false
      else {
        val newFlags = Flags.withStatus(oldFlags, Flags.Status.Running)

        if (!ref.compareAndSet(oldFlags, newFlags)) resume(asyncs)
        else true
      }
    }

    @tailrec
    final def setInterrupting(): Unit = {
      val oldFlags = getFlags
      val newFlags = Flags.withInterrupting(oldFlags, true)

      if (!ref.compareAndSet(oldFlags, newFlags)) setInterrupting()
      else ()
    }

    @tailrec
    final def suspend(interruptible: Boolean): Int = {
      val oldFlags  = getFlags
      val oldAsyncs = Flags.getAsyncs(oldFlags)
      val newFlags = Flags.withInterruptible(
        Flags.withAsyncs(Flags.withStatus(oldFlags, Flags.Status.Suspended), oldAsyncs + 1),
        interruptible
      )

      if (!ref.compareAndSet(oldFlags, newFlags)) suspend(interruptible)
      else oldAsyncs
    }
  }

  import java.util.{HashMap => JavaMap, Set => JavaSet}

  class FiberState[E, A](
    interruptible0: Boolean,
    runtimeConfig0: RuntimeConfig,
    location0: ZTraceElement
  ) {
    val fiberId         = FiberId.unsafeMake(location0)
    val interruptStatus = StackBool(interruptible0)
    val stack           = Stack[ZIO.TracedCont[Any, Any, Any, Any]]()
    var _fiberRefs      = null.asInstanceOf[JavaMap[FiberRef.Runtime[_], Any]]
    var runtimeConfig   = runtimeConfig0
    var _children       = null.asInstanceOf[JavaSet[FiberContext[_, _]]]
    val mailbox         = new AtomicReference[UIO[Any]](ZIO.unit)
    val flagsState      = new FlagsState(new AtomicInteger(Flags.initial))

    final def await(implicit trace: ZTraceElement): UIO[Exit[E, A]] =
      ZIO.asyncInterrupt[Any, Nothing, Exit[E, A]](
        { k =>
          val observer = (x: Exit[Nothing, Exit[E, A]]) => k(ZIO.done(x))

          if (unsafeEvalOn(UIO(unsafeAddObserver(observer)))) {
            Left(evalOn(ZIO.succeed(unsafeRemoveObserver(observer)), ZIO.unit))
          } else {
            Right(ZIO.succeedNow(unsafeGetDone()))
          }
        },
        fiberId
      )

    final def children(implicit trace: ZTraceElement): UIO[Chunk[Fiber.Runtime[_, _]]] =
      evalOnZIO(
        UIO {
          val chunkBuilder = ChunkBuilder.make[Fiber.Runtime[_, _]](_children.size)

          val iterator = _children.iterator()

          while (iterator.hasNext()) {
            chunkBuilder += iterator.next()
          }

          chunkBuilder.result()
        },
        UIO(Chunk.empty)
      )

    private final def childSet: JavaSet[FiberContext[_, _]] = {
      if (_children eq null) {
        _children = Platform.newWeakSet[FiberContext[_, _]]()
      }
      _children
    }

    final def eval(effect: zio.UIO[Any])(implicit trace: ZTraceElement): UIO[Unit] =
      evalOn(effect, effect).unit

    final def evalOn(effect: zio.UIO[Any], orElse: UIO[Any])(implicit trace: ZTraceElement): UIO[Unit] =
      UIO.suspendSucceed {
        if (unsafeEvalOn(effect)) ZIO.unit else orElse.unit
      }

    // TODO: Delete
    def evalOnZIO[R, E2, A2](effect: ZIO[R, E2, A2], orElse: ZIO[R, E2, A2])(implicit
      trace: ZTraceElement
    ): ZIO[R, E2, A2] =
      for {
        r <- ZIO.environment[R]
        p <- Promise.make[E2, A2]
        _ <- evalOn(effect.provideEnvironment(r).intoPromise(p), orElse.provideEnvironment(r).intoPromise(p))
        a <- p.await
      } yield a

    def evalZIO[R, E2, A2](effect: ZIO[R, E2, A2])(implicit
      trace: ZTraceElement
    ): ZIO[R, E2, A2] = evalOnZIO(effect, effect)

    private final def fiberRefs: JavaMap[FiberRef.Runtime[_], Any] = {
      if (_fiberRefs eq null) {
        _fiberRefs = new JavaMap[FiberRef.Runtime[_], Any]
      }
      _fiberRefs
    }

    final def getRef[A](ref: FiberRef.Runtime[A])(implicit trace: ZTraceElement): UIO[A] =
      evalZIO(UIO(unsafeGetRefOrInitial(ref)))

    final def id: FiberId.Runtime = fiberId

    final def inheritRefs(implicit trace: ZTraceElement): UIO[Unit] =
      eval {
        UIO.suspendSucceed {
          import scala.collection.JavaConverters._

          if (_fiberRefs eq null) ZIO.unit
          else {
            val locals = _fiberRefs.asScala

            if (locals.isEmpty) UIO.unit
            else
              UIO.foreachDiscard(locals) { case (fiberRef, value) =>
                val ref = fiberRef.asInstanceOf[FiberRef.Runtime[Any]]
                ref.update(old => ref.join(old, value))
              }
          }
        }
      }

    final def interruptAs(fiberId: FiberId)(implicit trace: ZTraceElement): UIO[Exit[E, A]] =
      ??? // TODO

    final def location: ZTraceElement = fiberId.location

    final def poll(implicit trace: ZTraceElement): UIO[Option[Exit[E, A]]] =
      UIO(unsafePoll())

    final def run(nextEffect: ZIO[_, _, _]): Unit =
      runUntil(nextEffect, unsafeGetExecutor().yieldOpCount)

    /**
     * The main evaluator loop for the fiber. For purely synchronous effects,
     * this will run either to completion, or for the specified maximum
     * operation count. For effects with asynchronous callbacks, the loop will
     * proceed no further than the first asynchronous boundary.
     */
    final def runUntil(nextEffect0: ZIO[_, _, _], maxOpCount: Int): Unit =
      ???

    override final def toString(): String =
      s"FiberContext($fiberId)"

    final def scope: ZScope = ??? // TODO: ZScope.unsafeMake(self)

    final def status(implicit trace: ZTraceElement): UIO[Fiber.Status2] =
      evalOnZIO(UIO(unsafeGetStatus()), UIO(Fiber.Status2.Done))

    final def trace(implicit trace0: ZTraceElement): UIO[ZTrace] =
      UIO(unsafeCaptureTrace(Nil))

    final def unsafeAddInterruptor(fiberId: FiberId): Unit =
      unsafeSetRef(FiberRef.interruptors, unsafeGetInterruptors() + fiberId)

    final def unsafeAddObserver(k: Exit[Nothing, Exit[E, A]] => Unit): Unit =
      unsafeSetListeners(k :: unsafeGetListeners())

    // TODO: Rename due to async nature of this method
    final def unsafeAddObserverMaybe(k: Exit[Nothing, Exit[E, A]] => Unit): Exit[E, A] =
      if (unsafeEvalOn(UIO(unsafeAddObserver(k))(ZTraceElement.empty))) null.asInstanceOf[Exit[E, A]]
      else unsafeGetDone()

    final def unsafeAddSuppressed(cause: Cause[Nothing]): Unit =
      unsafeSetRef(FiberRef.suppressed, unsafeGetSuppressed() ++ cause)

    final def unsafeCaptureTrace(prefix: List[ZTraceElement]): ZTrace = {
      val builder = StackTraceBuilder.unsafeMake()

      prefix.foreach(builder += _)
      stack.foreach(k => builder += k.trace)

      ZTrace(fiberId, builder.result())
    }

    final def unsafeClearSuppressed(): Cause[Nothing] = {
      val suppressed = unsafeGetRefOrInitial(FiberRef.suppressed)

      unsafeDeleteRef(FiberRef.suppressed)

      suppressed
    }

    final def unsafeDeleteRef(ref: FiberRef[_]): Unit =
      fiberRefs.remove(ref)

    final def unsafeDisableInterrupting(): Unit =
      interruptStatus.push(false)

    @tailrec
    final def unsafeDrainMailbox(): UIO[Any] = {
      val message = mailbox.get

      if (!mailbox.compareAndSet(message, ZIO.unit)) unsafeDrainMailbox()
      else message
    }

    final def unsafeEvalOn(effect: UIO[Any]): Boolean = {
      val oldMailbox = mailbox.get

      if (oldMailbox == null) false
      else {
        val newMailbox =
          if (oldMailbox eq ZIO.unit) effect
          else oldMailbox.flatMap(_ => effect)(ZTraceElement.empty)

        if (!mailbox.compareAndSet(oldMailbox, newMailbox)) unsafeEvalOn(effect)
        else true
      }
    }

    final def unsafeGetAsyncs(): Int =
      flagsState.getAsyncs()

    final def unsafeGetCurrentExecutor(): Option[Executor] =
      unsafeGetRefOrElse(FiberRef.currentExecutor, None)

    private def unsafeGetDescriptor(implicit trace: ZTraceElement): Fiber.Descriptor2 =
      Fiber.Descriptor2(
        fiberId,
        unsafeGetStatus(),
        unsafeGetInterruptors(),
        InterruptStatus.fromBoolean(unsafeIsInterruptible()),
        unsafeGetExecutor(),
        fiberRefs.containsKey(FiberRef.currentExecutor),
        scope
      )

    final def unsafeGetDone(): Exit[E, A] =
      unsafeGetRefOrElse(FiberRef.exit, Exit.empty).asInstanceOf[Exit[E, A]]

    final def unsafeGetEnvironment(): ZEnvironment[Any] =
      unsafeGetRefOrElse(FiberRef.currentEnvironment, ZEnvironment.empty)

    private def unsafeGetExecutor(): zio.Executor =
      unsafeGetRefOrInitial(FiberRef.currentExecutor).getOrElse(runtimeConfig.executor)

    final def unsafeGetForkScopeOverride(): Option[ZScope] =
      unsafeGetRefOrInitial(FiberRef.forkScopeOverride)

    final def unsafeGetInterruptors(): Set[FiberId] = unsafeGetRefOrElse(FiberRef.interruptors, Set.empty[FiberId])

    final def unsafeGetInterruptorsCause: Cause[Nothing] =
      unsafeGetInterruptors().foldLeft[Cause[Nothing]](Cause.empty) { case (acc, interruptor) =>
        acc ++ Cause.interrupt(interruptor)
      }

    final def unsafeGetListeners(): List[Exit[Nothing, Exit[E, A]] => Unit] =
      unsafeGetRefOrElse(FiberRef.listeners, Nil).asInstanceOf[List[Exit[Nothing, Exit[E, A]] => Unit]]

    final def unsafeGetRef[A](ref: FiberRef.Runtime[A]): Option[A] =
      if (fiberRefs.containsKey(ref)) Some(fiberRefs.get(ref).asInstanceOf[A])
      else None

    final def unsafeGetRefOrInitial[A](ref: FiberRef.Runtime[A]): A =
      if (fiberRefs.containsKey(ref)) fiberRefs.get(ref).asInstanceOf[A]
      else ref.initial

    final def unsafeGetRefOrElse[A](ref: FiberRef.Runtime[A], orElse: A): A =
      if (fiberRefs.containsKey(ref)) fiberRefs.get(ref).asInstanceOf[A]
      else orElse

    final def unsafeGetStatus(): Fiber.Status2 =
      if (unsafeIsDone()) Fiber.Status2.Done
      else
        Fiber.Status2.Running(
          unsafeIsInterruptible(),
          unsafeIsInterrupting(),
          unsafeGetAsyncs(),
          if (unsafeIsSuspended()) Some(unsafeGetRefOrInitial(FiberRef.suspension))
          else None
        )

    final def unsafeGetSuppressed(): Cause[Nothing] = unsafeGetRefOrInitial(FiberRef.suppressed)

    final def unsafeIsDone(): Boolean = flagsState.getStatus() == Flags.Status.Done

    final def unsafeIsInterruptible(): Boolean = interruptStatus.peekOrElse(true)

    final def unsafeIsInterrupting(): Boolean = flagsState.getInterrupting()

    final def unsafeIsRunning(): Boolean = flagsState.getStatus() == Flags.Status.Running

    final def unsafeIsSuspended(): Boolean = flagsState.getStatus() == Flags.Status.Suspended

    final def unsafeLog(tag: LightTypeTag, message: () => Any)(implicit trace: ZTraceElement): Unit = {
      val logLevel = unsafeGetRefOrInitial(FiberRef.currentLogLevel)
      val spans    = unsafeGetRefOrInitial(FiberRef.currentLogSpan)

      unsafeLogForEach(tag) { logger =>
        logger(trace, fiberId, logLevel, message, ???, spans, location) // FIXME
      }
    }

    final def unsafeLog(
      tag: LightTypeTag,
      message: () => Any,
      overrideLogLevel: Option[LogLevel],
      overrideRef1: FiberRef.Runtime[_] = null,
      overrideValue1: AnyRef = null,
      trace: ZTraceElement
    ): Unit = {
      val logLevel = overrideLogLevel match {
        case Some(level) => level
        case _           => unsafeGetRefOrInitial(FiberRef.currentLogLevel)
      }

      val spans = unsafeGetRefOrInitial(FiberRef.currentLogSpan)

      val contextMap =
        if (overrideRef1 ne null) {
          val map: Map[FiberRef.Runtime[_], AnyRef] = ??? // FIXME

          if (overrideValue1 eq null) map - overrideRef1
          else map.updated(overrideRef1, overrideValue1)
        } else ??? // FIXME

      unsafeLogForEach(tag) { logger =>
        logger(trace, fiberId, logLevel, message, contextMap, spans, location)
      }
    }

    final def unsafeLogForEach(tag: LightTypeTag)(f: ZLogger[Any, Any] => Unit): Unit = {
      val loggers = runtimeConfig.loggers.getAllDynamic(tag)

      loggers.foreach(logger => f(logger.asInstanceOf[ZLogger[Any, Any]]))
    }

    final def unsafePoll(): Option[Exit[E, A]] =
      if (unsafeIsDone()) Some(unsafeGetDone())
      else None

    final def unsafeRemoveObserver(k: Exit[Nothing, Exit[E, A]] => Unit): Unit =
      unsafeSetListeners(unsafeGetListeners().filter(_ ne k))

    final def unsafeReportUnhandled(v: Exit[E, A], trace: ZTraceElement): Unit = v match {
      case Exit.Failure(cause) =>
        try {
          unsafeLog(ZLogger.causeTag, () => cause, ZIO.someDebug, trace = trace)
        } catch {
          case t: Throwable =>
            if (runtimeConfig.fatal(t)) {
              runtimeConfig.reportFatal(t)
            } else {
              println("An exception was thrown by a logger:")
              t.printStackTrace
            }
        }
      case _ =>
    }

    final def unsafeRunLater(zio: ZIO[_, _, _]): Unit =
      if (stack.isEmpty) unsafeGetExecutor().unsafeSubmitAndYieldOrThrow(() => run(zio))
      else unsafeGetExecutor().unsafeSubmitOrThrow(() => run(zio))

    final def unsafeSetCurrentExecutor(executor: Executor): Unit =
      unsafeSetRef(FiberRef.currentExecutor, Some(executor))

    final def unsafeSetDone(exit: Exit[E, A]): Unit =
      unsafeSetRef(FiberRef.exit, exit)

    final def unsafeSetEnvironment(env: ZEnvironment[Any]): Unit =
      unsafeSetRef(FiberRef.currentEnvironment, env)

    final def unsafeSetInterrupting(): Unit =
      flagsState.setInterrupting()

    final def unsafeSetListeners(ls: List[Exit[Nothing, Exit[E, A]] => Unit]): Unit =
      unsafeSetRef(FiberRef.listeners, ls.asInstanceOf[List[Exit[Nothing, Exit[Any, Any]] => Unit]])

    final def unsafeSetRef[A](ref: FiberRef.Runtime[A], value: A): Unit =
      fiberRefs.put(ref, value)
  }
}
