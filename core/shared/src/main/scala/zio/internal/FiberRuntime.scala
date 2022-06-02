package zio.internal

import scala.concurrent._
import scala.annotation.tailrec
import scala.util.control.NoStackTrace

import java.util.{Set => JavaSet}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}

import zio._

class FiberRuntime[E, A](fiberId: FiberId.Runtime, fiberRefs0: FiberRefs) extends Fiber.Runtime.Internal[E, A] {
  self =>
  type Erased = ZIO[Any, Any, Any]

  import ZIO._
  import EvaluationStep._
  import ReifyStack.{AsyncJump, Trampoline, GenerateTrace}

  private var fiberRefs      = fiberRefs0
  private val queue          = new java.util.concurrent.ConcurrentLinkedQueue[FiberMessage]()
  private var inactiveStatus = null.asInstanceOf[Fiber.Status.Suspended]
  private var _children      = null.asInstanceOf[JavaSet[FiberRuntime[_, _]]]
  private var observers      = Nil: List[Exit[E, A] => Unit]
  private val running        = new AtomicBoolean(false)

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

  def id: FiberId.Runtime = fiberId

  def inheritRefs(implicit trace: Trace): UIO[Unit] =
    ZIO.fiberIdWith { parentFiberId =>
      ZIO.getFiberRefs.flatMap { parentFiberRefs =>
        val childFiberRefs   = self.asInstanceOf[FiberRuntime[Any, Any]].unsafeGetFiberRefs()
        val updatedFiberRefs = parentFiberRefs.joinAs(parentFiberId.asInstanceOf[FiberId.Runtime])(childFiberRefs)
        ZIO.unsafeStateful[Any, Nothing, Unit] { (state, _) =>
          state.unsafeSetFiberRefs(updatedFiberRefs)
          ZIO.unit
        }
      }
    }

  final def interruptAsFork(fiberId: FiberId)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed {
      val cause = Cause.interrupt(fiberId).traced(StackTrace(fiberId, Chunk(trace)))

      unsafeAddInterruptedCause(cause)

      val interrupt = ZIO.refailCause(cause)

      unsafeGetInterruptors().foreach { interruptor =>
        interruptor(interrupt)
      }
    }

  final def location: Trace = fiberId.location

  final def poll(implicit trace: Trace): UIO[Option[Exit[E, A]]] =
    ZIO.succeed(Option(self.unsafeExitValue()))

  final def scope: FiberScope = FiberScope.unsafeMake(this.asInstanceOf[FiberRuntime[_, _]])

  final def status(implicit trace: Trace): UIO[zio.Fiber.Status] =
    ask[zio.Fiber.Status]((_, inactiveStatus) => inactiveStatus)

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
        FiberMessage.Stateful((fiber, inactiveStatus) => promise.unsafeDone(ZIO.succeedNow(f(fiber, inactiveStatus))))
      )

      promise.await
    }

  private def assertNonNull(a: Any, message: String, location: Trace): Unit =
    if (a == null) {
      throw new NullPointerException(s"${message}: ${location.toString}")
    }

  private def assertNonNullContinuation(a: Any, location: Trace): Unit =
    assertNonNull(a, "The return value of a success or failure handler must be non-null", location)

  // unsafeGetCurrentExecutor().unsafeSubmitOrThrow { () =>
  //   outerRunLoop(effect, stack)
  // }

  /**
   * Adds a message to be processed by the fiber on the correct thread pool.
   */
  final def tell(message: FiberMessage): Unit = {
    queue.add(message)

    if (running.compareAndSet(false, true)) drainQueue()
  }

  /**
   * Adds a message to be processed by the fiber on the current thread. Note
   * this should only be called from a thread on the correct thread pool.
   */
  final def tellHere(message: FiberMessage): Unit = {
    queue.add(message)

    if (running.compareAndSet(false, true)) drainQueueHere()
  }

  /**
   * Begins execution of the effect associated with this fiber on the current
   * thread. This can be called to "kick off" execution of a fiber after it has
   * been created, in hopes that the effect can be executed synchronously.
   */
  final def start[R](effect: ZIO[R, E, A], interruptible: Boolean): Unit =
    tellHere(FiberMessage.Resume(effect.asInstanceOf[ZIO[Any, Any, Any]], Chunk.empty, interruptible))

  /**
   * Begins execution of the effect associated with this fiber on in the
   * background, and on the correct thread pool. This can be called to "kick
   * off" execution of a fiber after it has been created, in hopes that the
   * effect can be executed synchronously.
   */
  final def startBackground[R](effect: ZIO[R, E, A], interruptible: Boolean): Unit =
    tell(FiberMessage.Resume(effect.asInstanceOf[ZIO[Any, Any, Any]], Chunk.empty, interruptible))

  /**
   * Evaluates a single message on the current thread, while the fiber is
   * suspended. This method should only be called while the fiber is suspended.
   */
  final def evaluateMessage(fiberMessage: FiberMessage): Unit = {
    assert(running.get == true)

    fiberMessage match {
      case FiberMessage.InterruptSignal(cause) => self.unsafeAddInterruptedCause(cause)
      case FiberMessage.GenStackTrace(onTrace) => onTrace(StackTrace(self.id, self.inactiveStatus.stack.map(_.trace)))
      case FiberMessage.Stateful(onFiber) =>
        onFiber(
          self.asInstanceOf[FiberRuntime[Any, Any]],
          if (_exitValue ne null) Fiber.Status.Done else inactiveStatus
        )
      case FiberMessage.Resume(effect, stack, interruptible) =>
        unsafeRemoveInterruptors()
        evaluateEffect(effect, stack, interruptible)
    }
  }

  /**
   * Evaluates an effect until completion, potentially asynchronously.
   */
  final def evaluateEffect(
    effect0: ZIO[Any, Any, Any],
    stack0: Chunk[EvaluationStep],
    interruptible0: Boolean
  ): Unit = {
    assert(running.get == true)

    var effect        = effect0
    var stack         = stack0
    var interruptible = interruptible0

    while (effect ne null) {
      try {
        val exit =
          try Exit.succeed(runLoop(effect, 1000, stack, interruptible).asInstanceOf[A])
          catch {
            case zioError: ZIOError => Exit.failCause(zioError.cause.asInstanceOf[Cause[E]])
          }

        self.unsafeSetDone(exit)

        effect = null
      } catch {
        case trampoline: Trampoline =>
          effect = trampoline.effect
          stack = trampoline.stack.result()
          interruptible = trampoline.interruptible

        case asyncJump: AsyncJump =>
          val nextStack     = asyncJump.stack.result()
          val alreadyCalled = new AtomicBoolean(false)
          val interruptible = asyncJump.interruptible

          // Store the stack inside the heap so it can be leveraged for dumps during inactiveStatus:
          self.inactiveStatus = Fiber.Status.Suspended(nextStack, interruptible, asyncJump.trace, asyncJump.blockingOn)

          lazy val callback: ZIO[Any, Any, Any] => Unit = (effect: ZIO[Any, Any, Any]) => {
            if (alreadyCalled.compareAndSet(false, true)) {
              tell(FiberMessage.Resume(effect, nextStack, interruptible))
            }
          }

          if (interruptible) unsafeAddInterruptor(callback)

          // FIXME: registerCallback throws
          asyncJump.registerCallback(callback)

          effect = null

        case traceGen: GenerateTrace =>
          val nextStack = traceGen.stack.result() // TODO: Don't build it, just iterate over it!
          val builder   = StackTraceBuilder.unsafeMake()

          nextStack.foreach(k => builder += k.trace)

          val trace = StackTrace(self.fiberId, builder.result())

          effect = ZIO.succeed(trace)(Trace.empty)
          stack = nextStack
          interruptible = traceGen.interruptible

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
  }

  /**
   * On the current thread, executes all messages in the fiber's inbox. This
   * method may return before all work is done, in the event the fiber executes
   * an asynchronous operation.
   */
  @tailrec
  final def drainQueueHere(): Unit = {
    assert(running.get == true)

    try {
      while (!queue.isEmpty()) {
        val head = queue.poll()

        evaluateMessage(head)
      }
    } finally {
      running.set(false)
    }

    if (!queue.isEmpty()) {
      if (running.compareAndSet(false, true)) drainQueueHere()
    }
  }

  /**
   * Schedules the execution of all messages in the fiber's inbox on the correct
   * thread pool. This method will return immediately after the scheduling
   * operation is completed, but potentially before such messages have been
   * executed.
   */
  final def drainQueue(): Unit = {
    assert(running.get == true)

    val currentExecutor = self.unsafeGetCurrentExecutor()

    currentExecutor.unsafeSubmitOrThrow(() => drainQueueHere())
  }

  def runLoop(
    effect: ZIO[Any, Any, Any],
    remainingDepth: Int,
    stack: Chunk[ZIO.EvaluationStep],
    interruptible0: Boolean
  ): AnyRef = {
    assert(running.get == true)

    var cur           = effect
    var done          = null.asInstanceOf[AnyRef]
    var stackIndex    = 0
    var interruptible = interruptible0
    var lastTrace     = null.asInstanceOf[Trace] // TODO: Rip out

    if (remainingDepth <= 0) {
      val builder = ChunkBuilder.make[EvaluationStep]()

      builder ++= stack

      throw Trampoline(effect, builder, interruptible)
    }

    while (cur ne null) {
      val nextTrace = cur.trace
      if (nextTrace ne Trace.empty) lastTrace = nextTrace

      while (!queue.isEmpty()) {
        queue.poll() match {
          case FiberMessage.InterruptSignal(cause) =>
            self.unsafeAddInterruptedCause(cause)

            cur = if (interruptible) Refail(cause) else cur

          case FiberMessage.GenStackTrace(onTrace) =>
            val oldCur = cur

            cur = ZIO.trace(Trace.empty).map(onTrace(_)) *> oldCur

          case FiberMessage.Stateful(onFiber) =>
            onFiber(self.asInstanceOf[FiberRuntime[Any, Any]], Fiber.Status.Running(interruptible, lastTrace))

          case FiberMessage.Resume(_, _, _) =>
            throw new IllegalStateException("It is illegal to have multiple concurrent run loops in a single fiber")
        }
      }

      try {
        cur match {
          case effect0: OnSuccessOrFailure[_, _, _, _, _] =>
            val effect = effect0.erase

            try {
              cur = effect.onSuccess(runLoop(effect.first, remainingDepth - 1, Chunk.empty, interruptible))
            } catch {
              case zioError1: ZIOError =>
                cur =
                  try {
                    effect.onFailure(zioError1.cause)
                  } catch {
                    case zioError2: ZIOError => Refail(zioError1.cause.stripFailures ++ zioError2.cause)
                  }

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
                  case k: Continuation[_, _, _, _, _] =>
                    cur = k.erase.onSuccess(value)

                    assertNonNullContinuation(cur, k.trace)

                  case k: ChangeInterruptibility =>
                    interruptible = k.interruptible

                    // TODO: Interruption
                    if (interruptible && unsafeIsInterrupted()) cur = Refail(unsafeGetInterruptedCause())

                  case k: UpdateTrace => if (k.trace ne Trace.empty) lastTrace = k.trace
                }
              }

              if (cur eq null) done = value.asInstanceOf[AnyRef]
            } catch {
              case zioError: ZIOError =>
                cur = Refail(zioError.cause)
            }

          case effect: Async[_, _, _] =>
            throw AsyncJump(effect.registerCallback, ChunkBuilder.make(), interruptible, lastTrace, effect.blockingOn)

          case effect: ChangeInterruptionWithin[_, _, _] =>
            if (effect.newInterruptible && unsafeIsInterrupted()) { // TODO: Interruption
              cur = Refail(unsafeGetInterruptedCause())
            } else {
              val oldInterruptible = interruptible

              interruptible = effect.newInterruptible

              cur =
                try {
                  val value = runLoop(effect.scope(oldInterruptible), remainingDepth - 1, Chunk.empty, interruptible)

                  interruptible = oldInterruptible

                  // TODO: Interruption
                  if (interruptible && unsafeIsInterrupted()) Refail(unsafeGetInterruptedCause())
                  else ZIO.succeed(value)
                } catch {
                  case reifyStack: ReifyStack => reifyStack.changeInterruptibility(oldInterruptible)
                }
            }

          case generateStackTrace: GenerateStackTrace =>
            val builder = ChunkBuilder.make[EvaluationStep]()

            builder += EvaluationStep.UpdateTrace(generateStackTrace.trace)

            throw GenerateTrace(builder, interruptible)

          case stateful: Stateful[_, _, _] =>
            cur = stateful.erase.onState(
              self.asInstanceOf[FiberRuntime[Any, Any]],
              Fiber.Status.Running(interruptible, lastTrace)
            )

          case refail: Refail[_] =>
            var cause = refail.cause.asInstanceOf[Cause[Any]]

            cur = null

            while ((cur eq null) && stackIndex < stack.length) {
              val element = stack(stackIndex)

              stackIndex += 1

              element match {
                case k: Continuation[_, _, _, _, _] =>
                  try {
                    cur = k.erase.onFailure(cause)

                    assertNonNullContinuation(cur, k.trace)
                  } catch {
                    case zioError: ZIOError =>
                      cause = cause.stripFailures ++ zioError.cause
                  }
                case k: ChangeInterruptibility =>
                  interruptible = k.interruptible

                  // TODO: Interruption
                  if (interruptible && unsafeIsInterrupted())
                    cur = Refail(cause.stripFailures ++ unsafeGetInterruptedCause())

                case k: UpdateTrace => if (k.trace ne Trace.empty) lastTrace = k.trace
              }
            }

            if (cur eq null) throw ZIOError(cause)

          case InterruptSignal(cause, trace) =>
            cur = if (interruptible) Refail(cause) else ZIO.unit
        }
      } catch {
        case zioError: ZIOError =>
          throw zioError

        case reifyStack: ReifyStack =>
          if (stackIndex < stack.length) reifyStack.stack ++= stack.drop(stackIndex)

          throw reifyStack

        case interruptedException: InterruptedException =>
          cur = Refail(Cause.die(interruptedException) ++ Cause.interrupt(FiberId.None))

        case throwable: Throwable => // TODO: If non-fatal
          cur = Refail(Cause.die(throwable))
      }
    }

    done
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
    unsafeUpdateFiberRef(FiberRef.interruptors)(_ + interruptor)

  /**
   * Adds an observer to the list of observers.
   */
  final def unsafeAddObserver(observer: Exit[E, A] => Unit): Unit =
    observers = observer :: observers

  final def unsafeDeleteFiberRef(ref: FiberRef[_]): Unit =
    fiberRefs = fiberRefs.remove(ref)

  /**
   * Retrieves the exit value of the fiber state, which will be `null` if not
   * currently set.
   */
  final def unsafeExitValue(): Exit[E, A] = _exitValue

  final def unsafeForeachSupervisor(f: Supervisor[Any] => Unit): Unit =
    fiberRefs.getOrDefault(FiberRef.currentSupervisors).foreach(f)

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

  final def unsafeGetInterruptedCause(): Cause[Nothing] = unsafeGetFiberRef(FiberRef.interruptedCause)

  final def unsafeGetInterruptors(): Set[ZIO[Any, Nothing, Nothing] => Unit] =
    unsafeGetFiberRef(FiberRef.interruptors)

  final def unsafeGetLoggers(): Set[ZLogger[String, Any]] =
    unsafeGetFiberRef(FiberRef.currentLoggers)

  final def unsafeGetReportFatal(): Throwable => Nothing =
    unsafeGetFiberRef(FiberRef.currentReportFatal)

  final def unsafeGetRuntimeFlags(): Set[RuntimeFlag] =
    unsafeGetFiberRef(FiberRef.currentRuntimeFlags)

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
    val contextMap  = fiberRefs.unsafeGefMap() // FIXME: Change Logger to take FiberRefs

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
    fiberRefs = fiberRefs.updatedAs(fiberId)(fiberRef, value)

  final def unsafeSetFiberRefs(fiberRefs0: FiberRefs): Unit =
    this.fiberRefs = fiberRefs0

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
  import java.util.concurrent.atomic.AtomicBoolean

  def apply[E, A](fiberId: FiberId.Runtime, fiberRefs: FiberRefs): FiberRuntime[E, A] =
    new FiberRuntime(fiberId, fiberRefs)

  // FIXME: Make this work
  private[zio] val catastrophicFailure: AtomicBoolean = new AtomicBoolean(false)
}
