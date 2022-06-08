package zio.internal

import scala.concurrent._
import scala.annotation.tailrec
import scala.util.control.NoStackTrace

import java.util.{Set => JavaSet}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}

import zio._

class FiberRuntime[E, A](fiberId: FiberId.Runtime, fiberRefs0: FiberRefs, runtimeFlags0: RuntimeFlags)
    extends Fiber.Runtime.Internal[E, A]
    with FiberRunnable {
  self =>
  type Erased = ZIO[Any, Any, Any]

  import ZIO._
  import ReifyStack.{AsyncJump, Trampoline, GenerateTrace}
  import FiberRuntime.EvaluationSignal

  private var _fiberRefs      = fiberRefs0
  private val queue           = new java.util.concurrent.ConcurrentLinkedQueue[FiberMessage]()
  private var suspendedStatus = null.asInstanceOf[Fiber.Status.Suspended]
  private var _children       = null.asInstanceOf[JavaSet[FiberRuntime[_, _]]]
  private var observers       = Nil: List[Exit[E, A] => Unit]
  private val running         = new AtomicBoolean(false)
  private var _runtimeFlags   = runtimeFlags0

  @volatile private var _exitValue = null.asInstanceOf[Exit[E, A]]

  def await(implicit trace: Trace): UIO[Exit[E, A]] =
    ZIO.async[Any, Nothing, Exit[E, A]] { cb =>
      tell(FiberMessage.Stateful { (fiber, _) =>
        if (fiber._exitValue ne null) cb(ZIO.succeed(fiber.unsafeExitValue().asInstanceOf[Exit[E, A]]))
        else fiber.unsafeAddObserver(exit => cb(ZIO.succeed(exit.asInstanceOf[Exit[E, A]])))
      })
    }

  def children(implicit trace: Trace): UIO[Chunk[FiberRuntime[_, _]]] =
    ask((fiber, _) => Chunk.fromJavaIterable(fiber.unsafeGetChildren()))

  def fiberRefs(implicit trace: Trace): UIO[FiberRefs] =
    ask((fiber, _) => fiber.unsafeGetFiberRefs())

  def id: FiberId.Runtime = fiberId

  def inheritRefs(implicit trace: Trace): UIO[Unit] =
    ZIO.unsafeStateful[Any, Nothing, Unit] { (parentFiber, parentStatus) =>
      val parentFiberId      = parentFiber.id
      val parentFiberRefs    = parentFiber.unsafeGetFiberRefs()
      val parentRuntimeFlags = parentStatus.runtimeFlags

      val childFiberRefs   = self.unsafeGetFiberRefs() // Inconsistent snapshot
      val updatedFiberRefs = parentFiberRefs.joinAs(parentFiberId)(childFiberRefs)

      parentFiber.unsafeSetFiberRefs(updatedFiberRefs)

      for {
        childRuntimeFlags <- self.runtimeFlags
        // Do not inherit WindDown or Interruption!
        patch =
          parentRuntimeFlags.diff(childRuntimeFlags).exclude(RuntimeFlag.WindDown).exclude(RuntimeFlag.Interruption)
        _ <- ZIO.updateRuntimeFlags(patch)
      } yield ()
    }

  final def interruptAsFork(fiberId: FiberId)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed(unsafeInterruptAsFork(fiberId))

  final def unsafeInterruptAsFork(fiberId: FiberId)(implicit trace: Trace): Unit = {
    val cause = Cause.interrupt(fiberId).traced(StackTrace(fiberId, Chunk(trace)))

    tell(FiberMessage.InterruptSignal(cause))
  }

  final def location: Trace = fiberId.location

  final def poll(implicit trace: Trace): UIO[Option[Exit[E, A]]] =
    ZIO.succeed(Option(self.unsafeExitValue()))

  final def runtimeFlags(implicit trace: Trace): UIO[RuntimeFlags] =
    ask[RuntimeFlags] { (state, status) =>
      status match {
        case Fiber.Status.Done           => state._runtimeFlags
        case active: Fiber.Status.Active => active.runtimeFlags
      }
    }

  final def scope: FiberScope = FiberScope.unsafeMake(this.asInstanceOf[FiberRuntime[_, _]])

  final def status(implicit trace: Trace): UIO[zio.Fiber.Status] =
    ask[zio.Fiber.Status]((_, suspendedStatus) => suspendedStatus)

  def trace(implicit trace: Trace): UIO[StackTrace] =
    ZIO.suspendSucceed {
      val promise = zio.Promise.unsafeMake[Nothing, StackTrace](fiberId)

      tell(FiberMessage.GenStackTrace(trace => promise.unsafeDone(ZIO.succeedNow(trace))))

      promise.await
    }

  def ask[A](f: (FiberRuntime[Any, Any], Fiber.Status) => A)(implicit trace: Trace): UIO[A] =
    ZIO.suspendSucceed {
      val promise = zio.Promise.unsafeMake[Nothing, A](fiberId)

      tell(
        FiberMessage.Stateful((fiber, suspendedStatus) => promise.unsafeDone(ZIO.succeedNow(f(fiber, suspendedStatus))))
      )

      promise.await
    }

  private def assertNonNull(a: Any, message: String, location: Trace): Unit =
    if (a == null) {
      throw new NullPointerException(s"${message}: ${location.toString}")
    }

  private def assertNonNullContinuation(a: Any, location: Trace): Unit =
    assertNonNull(a, "The return value of a success or failure handler must be non-null", location)

  /**
   * Adds a message to be processed by the fiber on the correct thread pool.
   */
  final def tell(message: FiberMessage): Unit = {
    queue.add(message)

    if (running.compareAndSet(false, true)) drainQueueLaterOnExecutor()
  }

  /**
   * Adds a message to be processed by the fiber on the current thread. Note
   * this should only be called from a thread on the correct thread pool.
   */
  final def tellHere(message: FiberMessage): Unit = {
    queue.add(message)

    if (running.compareAndSet(false, true)) drainQueueOnCurrentThread()
  }

  /**
   * Begins execution of the effect associated with this fiber on the current
   * thread. This can be called to "kick off" execution of a fiber after it has
   * been created, in hopes that the effect can be executed synchronously.
   */
  final def start[R](effect: ZIO[R, E, A]): Unit =
    tellHere(FiberMessage.Resume(effect.asInstanceOf[ZIO[Any, Any, Any]], Chunk.empty))

  /**
   * Begins execution of the effect associated with this fiber on in the
   * background, and on the correct thread pool. This can be called to "kick
   * off" execution of a fiber after it has been created, in hopes that the
   * effect can be executed synchronously.
   */
  final def startBackground[R](effect: ZIO[R, E, A]): Unit =
    tell(FiberMessage.Resume(effect.asInstanceOf[ZIO[Any, Any, Any]], Chunk.empty))

  /**
   * Evaluates a single message on the current thread, while the fiber is
   * suspended. This method should only be called while the fiber is suspended.
   */
  final def evaluateMessage(fiberMessage: FiberMessage): EvaluationSignal = {
    assert(running.get == true)

    fiberMessage match {
      case FiberMessage.InterruptSignal(cause) =>
        self.unsafeAddInterruptedCause(cause)

        val interrupt = ZIO.refailCause(cause)

        unsafeGetInterruptors().foreach { interruptor =>
          interruptor(interrupt)
        }
        EvaluationSignal.Continue

      case FiberMessage.GenStackTrace(onTrace) =>
        onTrace(StackTrace(self.id, self.suspendedStatus.stack.map(_.trace)))
        EvaluationSignal.Continue

      case FiberMessage.Stateful(onFiber) =>
        onFiber(
          self.asInstanceOf[FiberRuntime[Any, Any]],
          if (_exitValue ne null) Fiber.Status.Done else suspendedStatus
        )
        EvaluationSignal.Continue

      case FiberMessage.Resume(effect, stack) =>
        unsafeRemoveInterruptors()
        evaluateEffect(effect, stack)
    }
  }

  /**
   * Evaluates an effect until completion, potentially asynchronously.
   */
  final def evaluateEffect(
    effect0: ZIO[Any, Any, Any],
    stack0: Chunk[EvaluationStep]
  ): EvaluationSignal = {
    assert(running.get == true)

    var effect      = effect0
    var stack       = stack0
    var signal      = EvaluationSignal.Continue: EvaluationSignal
    var trampolines = 0
    var ops         = 0

    while (effect ne null) {
      try {
        val exit =
          try {
            Exit.succeed(runLoop(effect, 0, stack, _runtimeFlags).asInstanceOf[A])
          } catch {
            case zioError: ZIOError =>
              Exit.failCause(zioError.cause.asInstanceOf[Cause[E]])
          }

        val interruption = unsafeInterruptAllChildren()

        if (interruption == null) {
          self.unsafeSetDone(exit)

          effect = null
        } else {
          _runtimeFlags = _runtimeFlags.enable(RuntimeFlag.WindDown)

          effect = interruption *> ZIO.done(exit)
          stack = Chunk.empty
        }
      } catch {
        case trampoline: Trampoline =>
          trampolines = trampolines + 1

          if (!trampoline.forceYield && (trampolines < FiberRuntime.MaxTrampolinesBeforeYield)) {
            effect = trampoline.effect
            stack = trampoline.stack.result()
          } else {
            tell(FiberMessage.Resume(trampoline.effect, trampoline.stack.result()))

            effect = null
            signal = EvaluationSignal.Yield
          }

        case asyncJump: AsyncJump =>
          val nextStack     = asyncJump.stack.result()
          val alreadyCalled = new AtomicBoolean(false)

          // Store the stack & runtime flags inside the heap so we can access this information during suspension:
          self.suspendedStatus = Fiber.Status.Suspended(nextStack, _runtimeFlags, asyncJump.trace, asyncJump.blockingOn)

          val callback: ZIO[Any, Any, Any] => Unit = (effect: ZIO[Any, Any, Any]) => {
            if (alreadyCalled.compareAndSet(false, true)) {
              tell(FiberMessage.Resume(effect, nextStack))
            }
          }

          if (_runtimeFlags.interruptible) unsafeAddInterruptor(callback)

          // FIXME: registerCallback throws
          asyncJump.registerCallback(callback)

          effect = null

        case traceGen: GenerateTrace =>
          val nextStack = traceGen.stack.result()
          val builder   = StackTraceBuilder.unsafeMake()

          nextStack.foreach(k => builder += k.trace)

          val trace = StackTrace(self.fiberId, builder.result())

          effect = ZIO.succeed(trace)(Trace.empty)
          stack = nextStack

        case t: Throwable => // FIXME: Non-fatal
          // No error should escape to this level.
          self.unsafeLog(
            () => s"An unhandled error was encountered while executing ${id.threadName}",
            Cause.die(t),
            ZIO.someError,
            Trace.empty
          )

          effect = null
      }
    }

    signal
  }

  override final def run(): Unit = drainQueueOnCurrentThread()

  /**
   * On the current thread, executes all messages in the fiber's inbox. This
   * method may return before all work is done, in the event the fiber executes
   * an asynchronous operation.
   */
  @tailrec
  final def drainQueueOnCurrentThread(): Unit = {
    assert(running.get == true)

    var evaluationSignal: EvaluationSignal = EvaluationSignal.Continue

    if (_runtimeFlags.currentFiber) Fiber._currentFiber.set(self)

    try {
      while (evaluationSignal == EvaluationSignal.Continue) {
        evaluationSignal =
          if (queue.isEmpty()) EvaluationSignal.Done
          else evaluateMessage(queue.poll())
      }
    } finally {
      running.set(false)

      if (_runtimeFlags.currentFiber) Fiber._currentFiber.set(null)
    }

    // Maybe someone added something to the queue between us checking, and us
    // giving up the drain. If so, we need to restart the draining, but only
    // if we beat everyone else to the restart:
    if (!queue.isEmpty() && running.compareAndSet(false, true)) {
      if (evaluationSignal == EvaluationSignal.Yield) drainQueueLaterOnExecutor()
      else drainQueueOnCurrentThread()
    }
  }

  /**
   * Schedules the execution of all messages in the fiber's inbox on the correct
   * thread pool. This method will return immediately after the scheduling
   * operation is completed, but potentially before such messages have been
   * executed.
   */
  final def drainQueueLaterOnExecutor(): Unit = {
    assert(running.get == true)

    val currentExecutor = self.unsafeGetCurrentExecutor()

    currentExecutor.unsafeSubmitOrThrow(self)
  }

  final def unsafeInterruptAllChildren(): UIO[Any] =
    if (_children == null || _children.isEmpty) null
    else {
      // Initiate asynchronous interruption of all children:
      var iterator = _children.iterator()

      while (iterator.hasNext()) {
        val next = iterator.next()

        next.tell(FiberMessage.InterruptSignal(Cause.interrupt(id)))
      }

      iterator = _children.iterator()

      _children = null

      // Now await all children to finish:
      ZIO
        .whileLoop(iterator)(_.hasNext) { iterator =>
          val next = iterator.next()

          if (next != null) next.await else ZIO.unit
        }
    }

  def runLoop(
    effect: ZIO[Any, Any, Any],
    currentDepth: Int,
    stack: Chunk[ZIO.EvaluationStep],
    runtimeFlags0: RuntimeFlags
  ): AnyRef = {
    assert(running.get == true)

    var cur          = effect
    var done         = null.asInstanceOf[AnyRef]
    var stackIndex   = 0
    var runtimeFlags = runtimeFlags0
    var lastTrace    = Trace.empty
    var ops          = 0

    if (currentDepth >= 500) {
      val builder = ChunkBuilder.make[EvaluationStep]()

      builder ++= stack

      // Save runtime flags to heap:
      self._runtimeFlags = runtimeFlags

      throw Trampoline(effect, builder, false)
    }

    while (cur ne null) {
      val nextTrace = cur.trace
      if (nextTrace ne Trace.empty) lastTrace = nextTrace

      while (!queue.isEmpty()) {
        queue.poll() match {
          case FiberMessage.InterruptSignal(cause) =>
            self.unsafeAddInterruptedCause(cause)

            cur = if (runtimeFlags.interruptible) Refail(cause) else cur

          case FiberMessage.GenStackTrace(onTrace) =>
            val oldCur = cur

            cur = ZIO.trace(Trace.empty).map(onTrace(_)) *> oldCur

          case FiberMessage.Stateful(onFiber) =>
            onFiber(self.asInstanceOf[FiberRuntime[Any, Any]], Fiber.Status.Running(runtimeFlags, lastTrace))

          case FiberMessage.Resume(_, _) =>
            throw new IllegalStateException("It is illegal to have multiple concurrent run loops in a single fiber")
        }
      }

      ops += 1

      if (ops > FiberRuntime.MaxOperationsBeforeYield) {
        ops = 0
        val oldCur = cur
        cur = ZIO.yieldNow(lastTrace) *> oldCur
      } else {
        try {
          cur match {
            case effect0: OnSuccessOrFailure[_, _, _, _, _] =>
              val effect = effect0.erase

              cur =
                try {
                  effect.onSuccess(runLoop(effect.first, currentDepth + 1, Chunk.empty, runtimeFlags))
                } catch {
                  case zioError: ZIOError => effect.onFailure(zioError.cause)

                  case reifyStack: ReifyStack => reifyStack.addContinuation(effect)
                }

            case effect: Sync[_] =>
              try {
                val value = effect.eval()

                cur = null

                while ((cur eq null) && stackIndex < stack.length) {
                  val element = stack(stackIndex)

                  stackIndex += 1

                  element match {
                    case k: EvaluationStep.Continuation[_, _, _, _, _] =>
                      cur = k.erase.onSuccess(value)

                      assertNonNullContinuation(cur, k.trace)

                    case k: EvaluationStep.UpdateRuntimeFlags =>
                      runtimeFlags = k.update(runtimeFlags)

                      respondToNewRuntimeFlags(k.update)

                      if (runtimeFlags.interruptible && unsafeIsInterrupted())
                        cur = Refail(unsafeGetInterruptedCause())

                    case k: EvaluationStep.UpdateTrace => if (k.trace ne Trace.empty) lastTrace = k.trace
                  }
                }

                if (cur eq null) done = value.asInstanceOf[AnyRef]
              } catch {
                case zioError: ZIOError =>
                  cur = Refail(zioError.cause)
              }

            case effect: Async[_, _, _] =>
              // Save runtime flags to heap:
              self._runtimeFlags = runtimeFlags

              throw AsyncJump(effect.registerCallback, ChunkBuilder.make(), lastTrace, effect.blockingOn)

            case effect: UpdateRuntimeFlagsWithin[_, _, _] =>
              val updateFlags     = effect.update
              val oldRuntimeFlags = runtimeFlags
              val newRuntimeFlags = updateFlags(oldRuntimeFlags)

              cur = if (newRuntimeFlags == oldRuntimeFlags) {
                // No change, short circuit:
                effect.scope(oldRuntimeFlags)
              } else {
                // One more chance to short circuit: if we're immediately going to interrupt.
                // Interruption will cause immediate reversion of the flag, so as long as we
                // "peek ahead", there's no need to set them to begin with.
                if (newRuntimeFlags.interruptible && unsafeIsInterrupted()) {
                  Refail(unsafeGetInterruptedCause())
                } else {
                  // Impossible to short circuit, so record the changes:
                  runtimeFlags = newRuntimeFlags

                  respondToNewRuntimeFlags(updateFlags)

                  // Since we updated the flags, we need to revert them:
                  val revertFlags = newRuntimeFlags.diff(oldRuntimeFlags)

                  try {
                    val value = runLoop(effect.scope(oldRuntimeFlags), currentDepth + 1, Chunk.empty, runtimeFlags)

                    // Go backward, on the stack stack:
                    runtimeFlags = revertFlags(runtimeFlags)

                    respondToNewRuntimeFlags(revertFlags)

                    if (runtimeFlags.interruptible && unsafeIsInterrupted())
                      Refail(unsafeGetInterruptedCause())
                    else ZIO.succeed(value)
                  } catch {
                    case reifyStack: ReifyStack =>
                      reifyStack.updateRuntimeFlags(revertFlags) // Go backward, on the heap
                  }
                }
              }

            case generateStackTrace: GenerateStackTrace =>
              // Save runtime flags to heap:
              self._runtimeFlags = runtimeFlags

              val builder = ChunkBuilder.make[EvaluationStep]()

              builder += EvaluationStep.UpdateTrace(generateStackTrace.trace)

              throw GenerateTrace(builder)

            case stateful: Stateful[_, _, _] =>
              cur = stateful.erase.onState(
                self.asInstanceOf[FiberRuntime[Any, Any]],
                Fiber.Status.Running(runtimeFlags, lastTrace)
              )

            case refail: Refail[_] =>
              val cause = refail.cause.asInstanceOf[Cause[Any]]

              cur = null

              while ((cur eq null) && stackIndex < stack.length) {
                val element = stack(stackIndex)

                stackIndex += 1

                element match {
                  case k: EvaluationStep.Continuation[_, _, _, _, _] =>
                    cur = k.erase.onFailure(cause)

                  case k: EvaluationStep.UpdateRuntimeFlags =>
                    runtimeFlags = k.update(runtimeFlags)

                    respondToNewRuntimeFlags(k.update)

                    if (runtimeFlags.interruptible && unsafeIsInterrupted()) {
                      cur = Refail(cause ++ unsafeGetInterruptedCause())
                    }

                  case k: EvaluationStep.UpdateTrace => if (k.trace ne Trace.empty) lastTrace = k.trace
                }
              }

              if (cur eq null) {
                // Save runtime flags to heap:
                self._runtimeFlags = runtimeFlags

                throw ZIOError(cause)
              }

            case updateRuntimeFlags: UpdateRuntimeFlags =>
              val updateFlags     = updateRuntimeFlags.update
              val oldRuntimeFlags = runtimeFlags

              runtimeFlags = updateFlags(runtimeFlags)

              respondToNewRuntimeFlags(updateFlags)

              // If we are nested inside another recursive call to `runLoop`,
              // then we need pop out to the very top in order to update
              // runtime flags globally:
              if (currentDepth > 0) {
                // Save runtime flags to heap:
                self._runtimeFlags = runtimeFlags

                throw Trampoline(ZIO.unit, ChunkBuilder.make[EvaluationStep](), false)
              } else {
                // We are at the top level, no need to update runtime flags
                // globally:
                cur = ZIO.unit
              }

            case iterate0: WhileLoop[_, _, _] =>
              val iterate = iterate0.asInstanceOf[WhileLoop[Any, Any, Any]]

              val state = iterate.create()
              val check = iterate.check

              try {
                while (check(state)) {
                  runLoop(iterate.process(state), currentDepth + 1, Chunk.empty, runtimeFlags)
                }

                cur = ZIO.succeed(state)
              } catch {
                case reifyStack: ReifyStack =>
                  val continuation = EvaluationStep.Continuation.fromSuccess((_: Any) => iterate.withCreate(state))

                  reifyStack.addContinuation(continuation)

                  throw reifyStack
              }

            case yieldNow: ZIO.YieldNow => // TODO: Trace
              throw Trampoline(ZIO.unit, ChunkBuilder.make[EvaluationStep](), true)
          }
        } catch {
          case zioError: ZIOError =>
            throw zioError

          case reifyStack: ReifyStack =>
            if (stackIndex < stack.length) reifyStack.stack ++= stack.drop(stackIndex)

            throw reifyStack

          case interruptedException: InterruptedException =>
            cur = Refail(Cause.die(interruptedException) ++ Cause.interrupt(FiberId.None))

          case throwable: Throwable =>
            cur = if (unsafeIsFatal(throwable)) {
              FiberRuntime.catastrophicFailure.set(true)
              throwable.printStackTrace()
              throw throwable
            } else Refail(Cause.die(throwable))
        }
      }
    }

    done
  }

  def respondToNewRuntimeFlags(patch: RuntimeFlags.Patch): Unit =
    if (patch.isEnabled(RuntimeFlag.CurrentFiber)) {
      Fiber._currentFiber.set(self)
    } else if (patch.isDisabled(RuntimeFlag.CurrentFiber)) Fiber._currentFiber.set(null)

  /**
   * Adds an interruptor to the set of interruptors that are interrupting this
   * fiber.
   */
  final def unsafeAddInterruptedCause(cause: Cause[Nothing]): Unit = {
    val oldSC = unsafeGetFiberRef(FiberRef.interruptedCause)

    unsafeSetFiberRef(FiberRef.interruptedCause, oldSC ++ cause)
  }

  final def unsafeAddInterruptor(interruptor: ZIO[Any, Nothing, Nothing] => Unit): Unit =
    unsafeUpdateFiberRef(FiberRef.interruptors)(_ + interruptor)

  /**
   * Adds an observer to the list of observers.
   */
  final def unsafeAddObserver(observer: Exit[E, A] => Unit): Unit =
    observers = observer :: observers

  final def unsafeDeleteFiberRef(ref: FiberRef[_]): Unit =
    _fiberRefs = _fiberRefs.delete(ref)

  /**
   * Retrieves the exit value of the fiber state, which will be `null` if not
   * currently set.
   */
  final def unsafeExitValue(): Exit[E, A] = _exitValue

  final def unsafeForeachSupervisor(f: Supervisor[Any] => Unit): Unit =
    _fiberRefs.getOrDefault(FiberRef.currentSupervisors).foreach(f)

  final def unsafeGetCurrentExecutor(): Executor =
    unsafeGetFiberRef(FiberRef.overrideExecutor).getOrElse(Runtime.defaultExecutor)

  /**
   * Retrieves the state of the fiber ref, or else the specified value.
   */
  final def unsafeGetFiberRefOrElse[A](fiberRef: FiberRef[A], orElse: => A): A =
    _fiberRefs.get(fiberRef).getOrElse(orElse)

  /**
   * Retrieves the state of the fiber ref, or else its initial value.
   */
  final def unsafeGetFiberRef[A](fiberRef: FiberRef[A]): A =
    _fiberRefs.getOrDefault(fiberRef)

  final def unsafeGetFiberRefOption[A](fiberRef: FiberRef[A]): Option[A] =
    _fiberRefs.get(fiberRef)

  final def unsafeGetFiberRefs(): FiberRefs = _fiberRefs

  final def unsafeGetInterruptedCause(): Cause[Nothing] = unsafeGetFiberRef(FiberRef.interruptedCause)

  final def unsafeGetInterruptors(): Set[ZIO[Any, Nothing, Nothing] => Unit] =
    unsafeGetFiberRef(FiberRef.interruptors)

  final def unsafeGetLoggers(): Set[ZLogger[String, Any]] =
    unsafeGetFiberRef(FiberRef.currentLoggers)

  final def unsafeGetReportFatal(): Throwable => Nothing =
    unsafeGetFiberRef(FiberRef.currentReportFatal)

  final def unsafeIsFatal(t: Throwable): Boolean =
    unsafeGetFiberRef(FiberRef.currentFatal).exists(_.isAssignableFrom(t.getClass))

  final def unsafeIsInterrupted(): Boolean = !unsafeGetFiberRef(FiberRef.interruptedCause).isEmpty

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
    val contextMap  = _fiberRefs.unsafeGefMap() // FIXME: Change Logger to take FiberRefs

    loggers.foreach { logger =>
      logger(trace, fiberId, logLevel, message, cause, contextMap, spans, annotations)
    }
  }

  final def unsafeRemoveInterruptors(): Unit =
    unsafeSetFiberRef(FiberRef.interruptors, Set.empty[ZIO[Any, Nothing, Nothing] => Unit])

  /**
   * Sets the fiber ref to the specified value.
   */
  final def unsafeSetFiberRef[A](fiberRef: FiberRef[A], value: A): Unit =
    _fiberRefs = _fiberRefs.updatedAs(fiberId)(fiberRef, value)

  final def unsafeSetFiberRefs(fiberRefs0: FiberRefs): Unit =
    this._fiberRefs = fiberRefs0

  /**
   * Adds a weakly-held reference to the specified fiber inside the children
   * set.
   */
  final def unsafeAddChild(child: FiberRuntime[_, _]): Unit =
    unsafeGetChildren().add(child)

  final def unsafeSetDone(e: Exit[E, A]): Unit = {
    _exitValue = e

    val iterator = observers.iterator

    while (iterator.hasNext) {
      val observer = iterator.next()

      observer(e)
    }
    observers = Nil
  }

  final def unsafeGetChildren(): JavaSet[FiberRuntime[_, _]] = {
    if (_children eq null) {
      _children = Platform.newWeakSet[FiberRuntime[_, _]]()
    }
    _children
  }

  /**
   * Removes the child from the children list.
   */
  final def unsafeRemoveChild(child: FiberRuntime[_, _]): Unit =
    if (_children ne null) {
      _children.remove(child)
      ()
    }

  /**
   * Removes the specified observer from the list of observers.
   */
  final def unsafeRemoveObserver(observer: Exit[E, A] => Unit): Unit =
    observers = observers.filter(_ ne observer)

  final def unsafeUpdateFiberRef[A](fiberRef: FiberRef[A])(f: A => A): Unit =
    unsafeSetFiberRef(fiberRef, f(unsafeGetFiberRef(fiberRef)))
}

object FiberRuntime {
  val MaxTrampolinesBeforeYield = 5
  val MaxOperationsBeforeYield  = 1024

  sealed trait EvaluationSignal
  object EvaluationSignal {
    case object Continue extends EvaluationSignal
    case object Yield    extends EvaluationSignal
    case object Done     extends EvaluationSignal
  }
  import java.util.concurrent.atomic.AtomicBoolean

  def apply[E, A](fiberId: FiberId.Runtime, fiberRefs: FiberRefs, runtimeFlags: RuntimeFlags): FiberRuntime[E, A] =
    new FiberRuntime(fiberId, fiberRefs, runtimeFlags)

  // FIXME: Make this work
  private[zio] val catastrophicFailure: AtomicBoolean = new AtomicBoolean(false)
}
