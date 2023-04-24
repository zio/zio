/*
 * Copyright 2022-2023 John A. De Goes and the ZIO Contributors
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

import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.annotation.tailrec

import java.util.{Set => JavaSet}
import java.util.concurrent.atomic.AtomicBoolean

import zio._
import zio.metrics.{Metric, MetricLabel}
import java.nio.channels.ClosedByInterruptException

final class FiberRuntime[E, A](fiberId: FiberId.Runtime, fiberRefs0: FiberRefs, runtimeFlags0: RuntimeFlags)
    extends Fiber.Runtime.Internal[E, A]
    with FiberRunnable {
  self =>
  type Erased = ZIO[Any, Any, Any]

  import ZIO._
  import ReifyStack.{AsyncJump, Trampoline, GenerateTrace}
  import FiberRuntime.EvaluationSignal

  private var _lastTrace      = fiberId.location
  private var _fiberRefs      = fiberRefs0
  private var _runtimeFlags   = runtimeFlags0
  private var _blockingOn     = FiberRuntime.notBlockingOn
  private var _asyncContWith  = null.asInstanceOf[ZIO[Any, Any, Any] => Any]
  private val running         = new AtomicBoolean(false)
  private val inbox           = new java.util.concurrent.ConcurrentLinkedQueue[FiberMessage]()
  private var _children       = null.asInstanceOf[JavaSet[Fiber.Runtime[_, _]]]
  private var observers       = Nil: List[Exit[E, A] => Unit]
  private val reifiedStack    = PinchableArray.make[EvaluationStep](-1)
  private var runningExecutor = null.asInstanceOf[Executor]
  private var _greenThread    = null.asInstanceOf[Thread]

  if (RuntimeFlags.runtimeMetrics(_runtimeFlags)) {
    val tags = getFiberRef(FiberRef.currentTags)(Unsafe.unsafe)
    Metric.runtime.fibersStarted.unsafe.update(1, tags)(Unsafe.unsafe)
    Metric.runtime.fiberForkLocations.unsafe.update(fiberId.location.toString, tags)(Unsafe.unsafe)
  }

  @volatile private var _exitValue = null.asInstanceOf[Exit[E, A]]

  def await(implicit trace: Trace): UIO[Exit[E, A]] =
    ZIO.suspendSucceed {
      if (self._exitValue ne null) Exit.succeed(self._exitValue)
      else
        ZIO.asyncInterrupt[Any, Nothing, Exit[E, A]](
          { k =>
            val cb = (exit: Exit[_, _]) => k(Exit.Success(exit.asInstanceOf[Exit[E, A]]))
            tell(FiberMessage.Stateful { (fiber, _) =>
              if (fiber._exitValue ne null) cb(fiber._exitValue)
              else fiber.addObserver(cb)(Unsafe.unsafe)
            })(Unsafe.unsafe)
            Left(ZIO.succeed(tell(FiberMessage.Stateful { (fiber, _) =>
              fiber.removeObserver(cb)(Unsafe.unsafe)
            })(Unsafe.unsafe)))
          },
          id
        )
    }

  def children(implicit trace: Trace): UIO[Chunk[Fiber.Runtime[_, _]]] =
    ZIO.succeed {
      val childs = _children
      if (childs == null) Chunk.empty
      else
        zio.internal.Sync(childs) {
          Chunk.fromJavaIterable(childs)
        }
    }

  def fiberRefs(implicit trace: Trace): UIO[FiberRefs] = ZIO.succeed(_fiberRefs)

  def id: FiberId.Runtime = fiberId

  def inheritAll(implicit trace: Trace): UIO[Unit] =
    ZIO.withFiberRuntime[Any, Nothing, Unit] { (parentFiber, parentStatus) =>
      implicit val unsafe = Unsafe.unsafe

      val parentFiberId      = parentFiber.id
      val parentFiberRefs    = parentFiber.getFiberRefs()
      val parentRuntimeFlags = parentStatus.runtimeFlags

      val childFiberRefs   = self.getFiberRefs() // Inconsistent snapshot
      val updatedFiberRefs = parentFiberRefs.joinAs(parentFiberId)(childFiberRefs)

      parentFiber.setFiberRefs(updatedFiberRefs)

      val updatedRuntimeFlags = parentFiber.getFiberRef(FiberRef.currentRuntimeFlags)
      // Do not inherit WindDown or Interruption!

      val patch =
        RuntimeFlags.Patch.exclude(
          RuntimeFlags.Patch.exclude(
            RuntimeFlags.diff(parentRuntimeFlags, updatedRuntimeFlags)
          )(RuntimeFlag.WindDown)
        )(RuntimeFlag.Interruption)

      ZIO.updateRuntimeFlags(patch)
    }

  def interruptAsFork(fiberId: FiberId)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed {
      val cause = Cause.interrupt(fiberId).traced(StackTrace(fiberId, Chunk(trace)))

      tell(FiberMessage.InterruptSignal(cause))(Unsafe.unsafe)
    }

  override def greenThread(implicit trace: Trace): UIO[Option[Thread]] = ZIO.succeed(Option(_greenThread))

  def location: Trace = fiberId.location

  def poll(implicit trace: Trace): UIO[Option[Exit[E, A]]] =
    ZIO.succeed(Option(self.exitValue()(Unsafe.unsafe)))

  override def run(): Unit =
    drainQueueOnCurrentThread(0)(Unsafe.unsafe)

  override def run(depth: Int): Unit =
    drainQueueOnCurrentThread(depth)(Unsafe.unsafe)

  def runtimeFlags(implicit trace: Trace): UIO[RuntimeFlags] =
    ZIO.succeed(_runtimeFlags)

  def setGreenThread(thread: Thread): Unit = {
    _greenThread = thread
    if (thread ne null) thread.setName(fiberId.threadName)
  }

  lazy val scope: FiberScope = FiberScope.make(this)

  def status(implicit trace: Trace): UIO[zio.Fiber.Status] =
    ZIO.succeed(getStatus()(Unsafe.unsafe))

  def trace(implicit trace: Trace): UIO[StackTrace] =
    ZIO.suspendSucceed {
      val greenThread = _greenThread

      if (greenThread ne null) {
        implicit val unsafe = Unsafe.unsafe
        Exit.succeed(StackTrace.unsafe.fromThread(fiberId, greenThread))
      } else
        ZIO.async[Any, Nothing, StackTrace] { k =>
          tell(FiberMessage.GenStackTrace(trace => k(Exit.succeed(trace))))(
            Unsafe.unsafe
          )
        }
    }

  private[zio] def addChild(child: Fiber.Runtime[_, _])(implicit unsafe: Unsafe): Unit =
    if (isAlive()) {
      getChildren().add(child)

      if (isInterrupted())
        child.tellInterrupt(getInterruptedCause())
    } else {
      child.tellInterrupt(getInterruptedCause())
    }

  /**
   * Adds an interruptor to the set of interruptors that are interrupting this
   * fiber.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private def addInterruptedCause(cause: Cause[Nothing])(implicit unsafe: Unsafe): Unit = {
    val oldSC = getFiberRef(FiberRef.interruptedCause)

    setFiberRef(FiberRef.interruptedCause, oldSC ++ cause)
  }

  /**
   * Adds an observer to the list of observers.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private[zio] def addObserver(observer: Exit[E, A] => Unit)(implicit unsafe: Unsafe): Unit =
    if (_exitValue ne null) observer(_exitValue)
    else observers = observer :: observers

  private[zio] def deleteFiberRef(ref: FiberRef[_])(implicit unsafe: Unsafe): Unit =
    _fiberRefs = _fiberRefs.delete(ref)

  /**
   * On the current thread, executes all messages in the fiber's inbox. This
   * method may return before all work is done, in the event the fiber executes
   * an asynchronous operation.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  @tailrec
  private def drainQueueOnCurrentThread(depth: Int)(implicit unsafe: Unsafe): Unit = {
    assert(running.get)

    var evaluationSignal: EvaluationSignal = EvaluationSignal.Continue
    var previousFiber                      = null.asInstanceOf[FiberRuntime[_, _]]
    try {
      if (RuntimeFlags.currentFiber(_runtimeFlags)) {
        val previousFiber = Fiber._currentFiber.get()
        Fiber._currentFiber.set(self)
      }

      while (evaluationSignal == EvaluationSignal.Continue) {
        evaluationSignal =
          if (inbox.isEmpty) EvaluationSignal.Done
          else evaluateMessageWhileSuspended(depth, inbox.poll())
      }
    } finally {
      running.set(false)

      if ((previousFiber ne null) || RuntimeFlags.currentFiber(_runtimeFlags)) Fiber._currentFiber.set(previousFiber)
    }

    // Maybe someone added something to the inbox between us checking, and us
    // giving up the drain. If so, we need to restart the draining, but only
    // if we beat everyone else to the restart:
    if (!inbox.isEmpty && running.compareAndSet(false, true)) {
      if (evaluationSignal == EvaluationSignal.YieldNow) drainQueueLaterOnExecutor()
      else drainQueueOnCurrentThread(depth)
    }
  }

  /**
   * Schedules the execution of all messages in the fiber's inbox on the correct
   * thread pool. This method will return immediately after the scheduling
   * operation is completed, but potentially before such messages have been
   * executed.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private def drainQueueLaterOnExecutor()(implicit unsafe: Unsafe): Unit = {
    assert(running.get)

    runningExecutor = self.getCurrentExecutor()
    runningExecutor.submitOrThrow(self)
  }

  /**
   * Drains the fiber's message inbox while the fiber is actively running,
   * returning the next effect to execute, which may be the input effect if no
   * additional effect needs to be executed.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private def drainQueueWhileRunning(
    cur0: ZIO[Any, Any, Any]
  )(implicit unsafe: Unsafe): ZIO[Any, Any, Any] = {
    var cur = cur0

    while (!inbox.isEmpty) {
      val message = inbox.poll()

      message match {
        case FiberMessage.InterruptSignal(cause) =>
          processNewInterruptSignal(cause)

          if (isInterruptible()) {
            cur = Exit.Failure(cause)
          }

        case FiberMessage.GenStackTrace(onTrace) =>
          val oldCur = cur

          // TODO: This can be clobbered in the event of InterruptSignal above.
          cur = ZIO
            .stackTrace(Trace.empty)
            .flatMap({ stackTrace =>
              onTrace(stackTrace)
              oldCur
            })(Trace.empty)

        case FiberMessage.Stateful(onFiber) =>
          processStatefulMessage(onFiber)

        case FiberMessage.Resume(_) =>
          throw new IllegalStateException("It is illegal to have multiple concurrent run loops in a single fiber")

        case FiberMessage.YieldNow =>
        // Ignore yield message
      }
    }

    cur
  }

  /**
   * Drains the fiber's message inbox immediately after initiating an async
   * operation, returning the continuation of the async operation, if available,
   * or null, otherwise.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private def drainQueueAfterAsync()(implicit
    unsafe: Unsafe
  ): ZIO[Any, Any, Any] = {
    var resumption: ZIO[Any, Any, Any] = null

    var message                      = inbox.poll()
    var leftover: List[FiberMessage] = Nil

    while (message ne null) {
      message match {
        case FiberMessage.InterruptSignal(cause) =>
          processNewInterruptSignal(cause)

        case FiberMessage.Stateful(onFiber) =>
          processStatefulMessage(onFiber)

        case FiberMessage.Resume(nextEffect0) =>
          assert(resumption eq null)

          resumption = nextEffect0.asInstanceOf[ZIO[Any, Any, Any]]

        case FiberMessage.YieldNow =>

        case message =>
          leftover = message :: leftover
      }

      message = inbox.poll()
    }

    if (leftover ne Nil) {
      leftover.foreach(inbox.offer)
    }

    resumption
  }

  /**
   * Evaluates an effect until completion, potentially asynchronously.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private def evaluateEffect(
    initialDepth: Int,
    effect0: ZIO[Any, Any, Any]
  )(implicit unsafe: Unsafe): Exit[E, A] = {
    assert(running.get)

    self._asyncContWith = null
    self._blockingOn = FiberRuntime.notBlockingOn

    updateLastTrace(effect0.trace)

    val supervisor = getSupervisor()

    if (supervisor ne Supervisor.none) supervisor.onResume(self)

    try {
      var effect      = effect0
      var trampolines = 0
      var finalExit   = null.asInstanceOf[Exit[E, A]]

      while (effect ne null) {
        try {
          // Possible the fiber has been interrupted at a start or trampoline
          // boundary. Check here or else we'll miss the opportunity to cancel:
          if (shouldInterrupt()) {
            effect = Exit.Failure(getInterruptedCause())
          }

          val localStack = self.reifiedStack.pinch()

          val exit =
            try {
              Exit.Success(runLoop(effect, initialDepth, localStack).asInstanceOf[A])
            } catch {
              case zioError: ZIOError =>
                Exit.Failure(zioError.cause.asInstanceOf[Cause[E]])
            }

          self._runtimeFlags = RuntimeFlags.enable(_runtimeFlags)(RuntimeFlag.WindDown)

          val interruption = interruptAllChildren()

          if (interruption == null) {
            if (inbox.isEmpty) {
              finalExit = exit

              // No more messages to process, so we will allow the fiber to end life:
              self.setExitValue(exit)
            } else {
              // There are messages, possibly added by the final op executed by
              // the fiber. To be safe, we should execute those now before we
              // allow the fiber to end life:
              tell(FiberMessage.Resume(exit))
            }

            effect = null
          } else {
            effect = interruption.flatMap(_ => exit)(id.location)
          }
        } catch {
          case trampoline: Trampoline =>
            trampolines = trampolines + 1

            if (
              (trampolines >= FiberRuntime.MaxTrampolinesBeforeYield || trampoline.forceYield) && RuntimeFlags
                .cooperativeYielding(_runtimeFlags)
            ) {
              tell(FiberMessage.YieldNow) // Signal to the outer loop to give us a break!
              tell(FiberMessage.Resume(trampoline.effect))

              effect = null
            } else {
              effect = trampoline.effect
            }

          case AsyncJump =>
            // Terminate this evaluation, async resumption will continue evaluation:
            effect = null

          case GenerateTrace =>
            trampolines += 1

            if (
              (trampolines >= FiberRuntime.MaxTrampolinesBeforeYield) && RuntimeFlags.cooperativeYielding(_runtimeFlags)
            ) {
              tell(FiberMessage.YieldNow) // Signal to the outer loop to give us a break!
              tell(FiberMessage.Resume(Exit.succeed(generateStackTrace())))

              effect = null
            } else {
              effect = Exit.succeed(generateStackTrace())
            }

          case t: Throwable =>
            if (isFatal(t)) {
              handleFatalError(t)
            } else {
              val death = Cause.die(t)

              // No error should escape to this level.
              self.log(
                () => s"An unhandled error was encountered on fiber ${id.threadName}, created at ${id.location}.",
                death,
                ZIO.someError,
                id.location
              )

              effect = null
            }
        }
      }

      finalExit
    } finally {
      val supervisor = getSupervisor()

      if (supervisor ne Supervisor.none) supervisor.onSuspend(self)
    }
  }

  /**
   * Evaluates a single message on the current thread, while the fiber is
   * suspended. This method should only be called while evaluation of the
   * fiber's effect is suspended due to an asynchronous operation.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private def evaluateMessageWhileSuspended(depth: Int, fiberMessage: FiberMessage)(implicit
    unsafe: Unsafe
  ): EvaluationSignal = {
    assert(running.get)

    fiberMessage match {
      case FiberMessage.InterruptSignal(cause) =>
        processNewInterruptSignal(cause)

        EvaluationSignal.Continue

      case FiberMessage.GenStackTrace(onTrace) =>
        onTrace(generateStackTrace())

        EvaluationSignal.Continue

      case FiberMessage.Stateful(onFiber) =>
        processStatefulMessage(onFiber)

        EvaluationSignal.Continue

      case FiberMessage.Resume(nextEffect0) =>
        val nextEffect = nextEffect0.asInstanceOf[ZIO[Any, Any, Any]]

        evaluateEffect(depth, nextEffect)

        EvaluationSignal.Continue

      case FiberMessage.YieldNow =>
        // Break from the loop because we're required to yield now:
        EvaluationSignal.YieldNow
    }
  }

  /**
   * Retrieves the exit value of the fiber state, which will be `null` if not
   * currently set.
   *
   * This method may be invoked on any fiber.
   */
  private[zio] def exitValue()(implicit unsafe: Unsafe): Exit[E, A] = _exitValue

  /**
   * Generates a full stack trace from the reified stack.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private def generateStackTrace(): StackTrace = {
    val builder = StackTraceBuilder.make()(Unsafe.unsafe)

    self.reifiedStack.foreach(k => builder += k.trace)

    builder += id.location // TODO: Allow parent traces?

    StackTrace(self.fiberId, builder.result())
  }

  /**
   * Retrieves the mutable set of children of this fiber.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private[zio] def getChildren()(implicit unsafe: Unsafe): JavaSet[Fiber.Runtime[_, _]] = {
    if (_children eq null) {
      _children = Platform.newConcurrentWeakSet[Fiber.Runtime[_, _]]()
    }
    _children
  }

  private[zio] def getCurrentExecutor()(implicit unsafe: Unsafe): Executor =
    getFiberRef(FiberRef.overrideExecutor) match {
      case None        => Runtime.defaultExecutor
      case Some(value) => value
    }

  def getGreenThread()(implicit unsafe: Unsafe): Thread = _greenThread

  private[zio] def getFiberRef[A](fiberRef: FiberRef[A])(implicit unsafe: Unsafe): A =
    _fiberRefs.getOrDefault(fiberRef)

  /**
   * Retrieves the state of the fiber ref, or else the specified value.
   */
  private[zio] def getFiberRefOrElse[A](fiberRef: FiberRef[A], orElse: => A)(implicit unsafe: Unsafe): A =
    _fiberRefs.get(fiberRef).getOrElse(orElse)

  /**
   * Retrieves the value of the specified fiber ref, or `None` if this fiber is
   * not storing a value for the fiber ref.
   *
   * '''NOTE''': This method is safe to invoke on any fiber, but if not invoked
   * on this fiber, then values derived from the fiber's state (including the
   * log annotations and log level) may not be up-to-date.
   */
  private[zio] def getFiberRefOption[A](fiberRef: FiberRef[A])(implicit unsafe: Unsafe): Option[A] =
    _fiberRefs.get(fiberRef)

  private[zio] def getFiberRefs()(implicit unsafe: Unsafe): FiberRefs = {
    setFiberRef(FiberRef.currentRuntimeFlags, _runtimeFlags)
    _fiberRefs
  }

  /**
   * Retrieves the interrupted cause of the fiber, which will be `Cause.empty`
   * if the fiber has not been interrupted.
   *
   * '''NOTE''': This method is safe to invoke on any fiber, but if not invoked
   * on this fiber, then values derived from the fiber's state (including the
   * log annotations and log level) may not be up-to-date.
   */
  private[zio] def getInterruptedCause()(implicit unsafe: Unsafe): Cause[Nothing] = getFiberRef(
    FiberRef.interruptedCause
  )

  /**
   * Retrieves the logger that the fiber uses to log information.
   *
   * '''NOTE''': This method is safe to invoke on any fiber, but if not invoked
   * on this fiber, then values derived from the fiber's state (including the
   * log annotations and log level) may not be up-to-date.
   */
  private[zio] def getLoggers()(implicit unsafe: Unsafe): Set[ZLogger[String, Any]] =
    getFiberRef(FiberRef.currentLoggers)

  /**
   * Retrieves the function the fiber used to report fatal errors.
   *
   * '''NOTE''': This method is safe to invoke on any fiber, but if not invoked
   * on this fiber, then values derived from the fiber's state (including the
   * log annotations and log level) may not be up-to-date.
   */
  private[zio] def getReportFatal()(implicit unsafe: Unsafe): Throwable => Nothing =
    getFiberRef(FiberRef.currentReportFatal)

  private[zio] def getRunningExecutor()(implicit unsafe: Unsafe): Option[Executor] =
    if (runningExecutor eq null) None else Some(runningExecutor)

  private[zio] def getStatus()(implicit unsafe: Unsafe): Fiber.Status =
    if (_exitValue ne null) Fiber.Status.Done
    else {
      val greenThread = _greenThread

      if (greenThread ne null) {
        // We map the green thread's state to the fiber status to accurately capture
        // suspension information:
        val state = greenThread.getState()

        if (state == Thread.State.NEW)
          Fiber.Status.Suspended(self._runtimeFlags, fiberId.location, FiberId.None) // Unstarted
        else if (state == Thread.State.RUNNABLE) Fiber.Status.Running(self._runtimeFlags, _lastTrace)
        // else if (state == Thread.State.TERMINATED) {
        //   Fiber.Status.Suspended(self._runtimeFlags, fiberId.location, FiberId.None)
        // }
        else
          Fiber.Status.Suspended(
            self._runtimeFlags,
            _lastTrace,
            _blockingOn()
          )
      } else {
        if (_asyncContWith ne null) Fiber.Status.Suspended(self._runtimeFlags, _lastTrace, _blockingOn())
        else Fiber.Status.Running(self._runtimeFlags, _lastTrace)
      }
    }

  /**
   * Retrieves the current supervisor the fiber uses for supervising effects.
   *
   * '''NOTE''': This method is safe to invoke on any fiber, but if not invoked
   * on this fiber, then values derived from the fiber's state (including the
   * log annotations and log level) may not be up-to-date.
   */
  private[zio] def getSupervisor()(implicit unsafe: Unsafe): Supervisor[Any] =
    getFiberRef(FiberRef.currentSupervisor)

  /**
   * Handles a fatal error.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private def handleFatalError(throwable: Throwable): Nothing = {
    FiberRuntime.catastrophicFailure.set(true)
    val errorReporter = getReportFatal()(Unsafe.unsafe)
    errorReporter(throwable)
  }

  /**
   * Initiates an asynchronous operation, by building a callback that will
   * resume execution, and then feeding that callback to the registration
   * function, handling error cases and repeated resumptions appropriately.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private def initiateAsync(
    asyncRegister: (ZIO[Any, Any, Any] => Unit) => ZIO[Any, Any, Any]
  )(implicit
    unsafe: Unsafe
  ): ZIO[Any, Any, Any] = {
    val alreadyCalled = new AtomicBoolean(false)

    val callback = (effect: ZIO[Any, Any, Any]) => {
      if (alreadyCalled.compareAndSet(false, true)) {
        tell(FiberMessage.Resume(effect))
      }
    }

    if (isInterruptible()) self._asyncContWith = callback
    else self._asyncContWith = FiberRuntime.IgnoreContinuation

    try {
      val sync = asyncRegister(callback)

      if (sync ne null) {
        if (alreadyCalled.compareAndSet(false, true)) {
          self._asyncContWith = null
          self._blockingOn = FiberRuntime.notBlockingOn
          sync
        } else {
          log(
            () =>
              s"Async operation attempted synchronous resumption, but its callback was already invoked; synchronous value will be discarded",
            Cause.empty,
            ZIO.someError,
            id.location
          )

          null.asInstanceOf[ZIO[Any, Any, Any]]
        }
      } else null.asInstanceOf[ZIO[Any, Any, Any]]
    } catch {
      case throwable: Throwable =>
        if (isFatal(throwable)) handleFatalError(throwable)
        else callback(Exit.Failure(Cause.die(throwable)))

        null.asInstanceOf[ZIO[Any, Any, Any]]
    }
  }

  /**
   * Interrupts all children of the current fiber, returning an effect that will
   * await the exit of the children. This method will return null if the fiber
   * has no children.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private def interruptAllChildren()(implicit unsafe: Unsafe): UIO[Any] =
    if (sendInterruptSignalToAllChildren(_children)) {
      val iterator = _children.iterator()

      _children = null

      val body = () => {
        val next = iterator.next()

        if (next != null) next.await(id.location) else ZIO.unit
      }

      // Now await all children to finish:
      ZIO
        .whileLoop(iterator.hasNext)(body())(_ => ())(id.location)
    } else null

  private[zio] def isAlive()(implicit unsafe: Unsafe): Boolean =
    _exitValue eq null

  private[zio] def isDone()(implicit unsafe: Unsafe): Boolean =
    _exitValue ne null

  /**
   * Determines if the fiber is interrupted.
   *
   * '''NOTE''': This method is safe to invoke on any fiber, but if not invoked
   * on this fiber, then values derived from the fiber's state (including the
   * log annotations and log level) may not be up-to-date.
   */
  private[zio] def isInterrupted()(implicit unsafe: Unsafe): Boolean = {
    val interruptedCause = getFiberRef(FiberRef.interruptedCause)

    interruptedCause.nonEmpty || {
      if (Thread.interrupted()) {
        addInterruptedCause(Cause.interrupt(FiberId.None))

        true
      } else false
    }
  }

  private[zio] def isInterruptible()(implicit unsafe: Unsafe): Boolean =
    RuntimeFlags.interruptible(_runtimeFlags)

  private[zio] def log(
    message: () => String,
    cause: Cause[Any],
    overrideLogLevel: Option[LogLevel],
    trace: Trace
  )(implicit unsafe: Unsafe): Unit = {
    val logLevel =
      if (overrideLogLevel.isDefined) overrideLogLevel.get
      else getFiberRef(FiberRef.currentLogLevel)

    val spans       = getFiberRef(FiberRef.currentLogSpan)
    val annotations = getFiberRef(FiberRef.currentLogAnnotations)
    val loggers     = getLoggers()
    val contextMap  = getFiberRefs()

    loggers.foreach { logger =>
      logger(trace, fiberId, logLevel, message, cause, contextMap, spans, annotations)
    }
  }

  private def processStatefulMessage(onFiber: (FiberRuntime[_, _], Fiber.Status) => Unit)(implicit
    unsafe: Unsafe
  ): Unit =
    try {
      onFiber(self, getStatus())
    } catch {
      case throwable: Throwable =>
        if (isFatal(throwable)) {
          handleFatalError(throwable)
        } else {
          log(
            () =>
              s"An unexpected error was encountered while processing stateful fiber message with callback ${onFiber}",
            Cause.die(throwable),
            ZIO.someError,
            id.location
          )
        }
    }

  /**
   * Takes the current runtime flags, patches them to return the new runtime
   * flags, and then makes any changes necessary to fiber state based on the
   * specified patch.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private def patchRuntimeFlags(patch: RuntimeFlags.Patch): Unit = {
    if (RuntimeFlags.Patch.isEnabled(patch)(RuntimeFlag.CurrentFiber)) {
      Fiber._currentFiber.set(self)
    } else if (RuntimeFlags.Patch.isDisabled(patch)(RuntimeFlag.CurrentFiber)) Fiber._currentFiber.set(null)

    _runtimeFlags = RuntimeFlags.patch(patch)(_runtimeFlags)
  }

  /**
   * Processes a new incoming interrupt signal.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private def processNewInterruptSignal(cause: Cause[Nothing])(implicit unsafe: Unsafe): Unit = {
    self.addInterruptedCause(cause)
    self.sendInterruptSignalToAllChildren(_children)

    val k = self._asyncContWith

    if ((k ne null) && (k ne FiberRuntime.notBlockingOn)) {
      k(Exit.Failure(cause))
    }
  }

  /**
   * Removes a child from the set of children belonging to this fiber.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private[zio] def removeChild(child: FiberRuntime[_, _])(implicit unsafe: Unsafe): Unit =
    if (_children ne null) {
      _children.remove(child)
      ()
    }

  /**
   * Removes the specified observer from the list of observers that will be
   * notified when the fiber exits.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private[zio] def removeObserver(observer: Exit[E, A] => Unit)(implicit unsafe: Unsafe): Unit =
    observers = observers.filter(_ ne observer)

  /**
   * Begins execution of the effect associated with this fiber on in the
   * background, and on the correct thread pool. This can be called to "kick
   * off" execution of a fiber after it has been created, in hopes that the
   * effect can be executed synchronously.
   */
  private[zio] def resume(effect: ZIO[_, E, A])(implicit unsafe: Unsafe): Unit =
    tell(FiberMessage.Resume(effect))

  /**
   * The main run-loop for evaluating effects. This method is recursive,
   * utilizing JVM stack space.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private def runLoop(
    effect: ZIO[Any, Any, Any],
    currentDepth: Int,
    localStack: Chunk[ZIO.EvaluationStep]
  )(implicit unsafe: Unsafe): AnyRef = {
    assert(running.get)

    type Erased         = ZIO[Any, Any, Any]
    type ErasedSuccessK = Any => ZIO[Any, Any, Any]
    type ErasedFailureK = Cause[Any] => ZIO[Any, Any, Any]

    // Note that assigning `cur` as the result of `try` or `if` can cause Scalac to box local variables.
    var cur        = effect
    var done       = null.asInstanceOf[AnyRef]
    var stackIndex = 0
    var ops        = 0

    if (currentDepth >= FiberRuntime.MaxDepthBeforeTrampoline) {
      self.reifiedStack.ensureCapacity(currentDepth)

      self.reifiedStack ++= localStack

      throw Trampoline(effect, false)
    }

    while (cur ne null) {
      if (RuntimeFlags.opSupervision(_runtimeFlags)) {
        self.getSupervisor().onEffect(self, cur)
      }

      updateLastTrace(cur.trace)

      cur = drainQueueWhileRunning(cur)

      ops += 1

      if ((_greenThread eq null) && ops > FiberRuntime.MaxOperationsBeforeYield) {
        ops = 0
        val oldCur = cur
        val trace  = _lastTrace
        cur = ZIO.YieldNow(trace, true).flatMap(_ => oldCur)(trace)
      } else {
        try {
          cur match {

            case effect0: OnSuccess[_, _, _, _] =>
              val effect = effect0.asInstanceOf[OnSuccess[Any, Any, Any, Any]]

              try {
                cur = effect.successK(runLoop(effect.first, currentDepth + 1, Chunk.empty))
              } catch {
                case zioError: ZIOError =>
                  cur = Exit.Failure(zioError.cause)

                case reifyStack: ReifyStack =>
                  self.reifiedStack += effect

                  throw reifyStack
              }

            case effect: Sync[_] =>
              try {
                // Keep this in sync with Exit.Success
                val value = effect.eval()

                cur = null

                while ((cur eq null) && stackIndex < localStack.length) {
                  val element = localStack(stackIndex)

                  stackIndex += 1

                  element match {
                    case k: ZIO.OnSuccess[_, _, _, _] =>
                      cur = k.successK.asInstanceOf[ErasedSuccessK](value)

                    case k: ZIO.OnSuccessAndFailure[_, _, _, _, _] =>
                      cur = k.successK.asInstanceOf[ErasedSuccessK](value)

                    case k: ZIO.OnFailure[_, _, _, _] => ()

                    case k: EvaluationStep.UpdateRuntimeFlags =>
                      patchRuntimeFlags(k.update)

                      if (shouldInterrupt())
                        cur = Exit.Failure(getInterruptedCause())

                    case k: EvaluationStep.UpdateTrace => updateLastTrace(k.trace)
                  }
                }

                if (cur eq null) done = value.asInstanceOf[AnyRef]
              } catch {
                case zioError: ZIOError =>
                  cur = zioError.toEffect(effect.trace)
                case throwable: Throwable =>
                  if (isFatal(throwable)) {
                    cur = handleFatalError(throwable)
                  } else {
                    cur = ZIO.failCause(Cause.die(throwable))(effect.trace)
                  }
              }

            case effect0: OnFailure[_, _, _, _] =>
              val effect = effect0.asInstanceOf[OnFailure[Any, Any, Any, Any]]

              try {
                cur = Exit.Success(runLoop(effect.first, currentDepth + 1, Chunk.empty))
              } catch {
                case zioError: ZIOError =>
                  if (shouldInterrupt()) {
                    cur = Exit.failCause(zioError.cause.stripFailures)
                  } else {
                    cur = effect.failureK(zioError.cause)
                  }

                case reifyStack: ReifyStack =>
                  self.reifiedStack += effect

                  throw reifyStack
              }

            case effect0: OnSuccessAndFailure[_, _, _, _, _] =>
              val effect = effect0.asInstanceOf[OnSuccessAndFailure[Any, Any, Any, Any, Any]]

              try {
                cur = effect.successK(runLoop(effect.first, currentDepth + 1, Chunk.empty))
              } catch {
                case zioError: ZIOError =>
                  if (shouldInterrupt()) {
                    cur = Exit.failCause(zioError.cause.stripFailures)
                  } else {
                    cur = effect.failureK(zioError.cause)
                  }

                case reifyStack: ReifyStack =>
                  self.reifiedStack += effect

                  throw reifyStack
              }

            case effect: Async[_, _, _] =>
              self._blockingOn = effect.blockingOn

              if (_greenThread ne null) {
                val oneShot = OneShot.make[ZIO[Any, Any, Any]]

                try {
                  val result = effect.registerCallback(oneShot.set(_))

                  if (result ne null) cur = result
                  else {
                    var retry = true
                    while (retry) {
                      try {
                        cur = oneShot.get()

                        retry = false
                      } catch {
                        case _: InterruptedException if !isInterruptible() => ()
                      }
                    }
                  }
                } catch {
                  case t: Throwable if !isFatal(t) && oneShot.isSet() =>
                    log(
                      () =>
                        s"An error from a callback registration is being ignored because it provided a resumption before failing: ${t
                          .getMessage()}",
                      Cause.die(t),
                      ZIO.someWarning,
                      effect.trace
                    )

                    cur = oneShot.get()
                }
              } else {
                self.reifiedStack.ensureCapacity(currentDepth)

                cur = initiateAsync(effect.registerCallback)

                while (cur eq null) {
                  cur = drainQueueAfterAsync()

                  if (cur eq null) {
                    if (!stealWork(currentDepth)) throw AsyncJump
                  }
                }

                if (shouldInterrupt()) {
                  cur = Exit.failCause(getInterruptedCause())
                }
              }

            case effect: UpdateRuntimeFlagsWithin[_, _, _] =>
              val updateFlags     = effect.update
              val oldRuntimeFlags = _runtimeFlags
              val newRuntimeFlags = RuntimeFlags.patch(updateFlags)(oldRuntimeFlags)

              if (newRuntimeFlags == oldRuntimeFlags) {
                // No change, short circuit:
                cur = effect.scope(oldRuntimeFlags).asInstanceOf[ZIO[Any, Any, Any]]
              } else {
                // One more chance to short circuit: if we're immediately going to interrupt.
                // Interruption will cause immediate reversion of the flag, so as long as we
                // "peek ahead", there's no need to set them to begin with.
                if (RuntimeFlags.interruptible(newRuntimeFlags) && isInterrupted()) {
                  cur = Exit.Failure(getInterruptedCause())
                } else {
                  // Impossible to short circuit, so record the changes:
                  patchRuntimeFlags(updateFlags)

                  // Since we updated the flags, we need to revert them:
                  val revertFlags = RuntimeFlags.diff(newRuntimeFlags, oldRuntimeFlags)

                  try {
                    val value = runLoop(
                      effect.scope(oldRuntimeFlags).asInstanceOf[ZIO[Any, Any, Any]],
                      currentDepth + 1,
                      Chunk.empty
                    )

                    // Go backward, on the stack:
                    patchRuntimeFlags(revertFlags)

                    if (shouldInterrupt())
                      cur = Exit.Failure(getInterruptedCause())
                    else {
                      cur = Exit.Success(value)
                    }
                  } catch {
                    case zioError: ZIOError =>
                      patchRuntimeFlags(revertFlags)
                      cur = Exit.Failure(zioError.cause)

                    case reifyStack: ReifyStack =>
                      self.reifiedStack += EvaluationStep.UpdateRuntimeFlags(revertFlags) // Go backward, on the heap

                      throw reifyStack

                    case throwable: Throwable =>
                      // Non-recoverable or fatal error:
                      patchRuntimeFlags(revertFlags)

                      throw throwable
                  }
                }
              }

            case generateStackTrace: GenerateStackTrace =>
              self.reifiedStack += EvaluationStep.UpdateTrace(generateStackTrace.trace)

              throw GenerateTrace

            case stateful: Stateful[_, _, _] =>
              try {
                cur = stateful.erase.onState(
                  self.asInstanceOf[FiberRuntime[Any, Any]],
                  Fiber.Status.Running(_runtimeFlags, _lastTrace)
                )
              } catch {
                case zioError: ZIOError =>
                  cur = zioError.toEffect(stateful.trace)
              }

            case success: Exit.Success[_] =>
              // Keep this in sync with Sync
              val value = success.value

              cur = null

              while ((cur eq null) && stackIndex < localStack.length) {
                val element = localStack(stackIndex)

                stackIndex += 1

                element match {
                  case k: ZIO.OnSuccess[_, _, _, _] =>
                    cur = k.successK.asInstanceOf[ErasedSuccessK](value)

                  case k: ZIO.OnSuccessAndFailure[_, _, _, _, _] =>
                    cur = k.successK.asInstanceOf[ErasedSuccessK](value)

                  case k: ZIO.OnFailure[_, _, _, _] =>

                  case k: EvaluationStep.UpdateRuntimeFlags =>
                    patchRuntimeFlags(k.update)

                    if (shouldInterrupt())
                      cur = Exit.Failure(getInterruptedCause())

                  case k: EvaluationStep.UpdateTrace => updateLastTrace(k.trace)
                }
              }

              if (cur eq null) done = value.asInstanceOf[AnyRef]

            case failure: Exit.Failure[_] =>
              var cause = failure.cause.asInstanceOf[Cause[Any]]

              cur = null

              while ((cur eq null) && stackIndex < localStack.length) {
                val element = localStack(stackIndex)

                stackIndex += 1

                element match {
                  case k: ZIO.OnSuccess[_, _, _, _] => ()

                  case k: ZIO.OnSuccessAndFailure[_, _, _, _, _] =>
                    if (shouldInterrupt())
                      cause = cause.stripFailures // Skipped an error handler which changed E1 => E2, so must discard
                    else
                      cur = k.failureK.asInstanceOf[ErasedFailureK](cause)

                  case k: ZIO.OnFailure[_, _, _, _] =>
                    if (shouldInterrupt())
                      cause = cause.stripFailures // Skipped an error handler which changed E1 => E2, so must discard
                    else
                      cur = k.failureK.asInstanceOf[ErasedFailureK](cause)

                  case k: EvaluationStep.UpdateRuntimeFlags =>
                    patchRuntimeFlags(k.update)

                    if (shouldInterrupt()) {
                      cur = Exit.Failure(cause ++ getInterruptedCause())
                    }

                  case k: EvaluationStep.UpdateTrace => updateLastTrace(k.trace)
                }
              }

              if (cur eq null) throw ZIOError(cause)

            case updateRuntimeFlags: UpdateRuntimeFlags =>
              patchRuntimeFlags(updateRuntimeFlags.update)

              if (shouldInterrupt()) {
                cur = Exit.Failure(getInterruptedCause())
              } else {
                cur = ZIO.unit
              }

            case iterate0: WhileLoop[_, _, _] =>
              val iterate = iterate0.asInstanceOf[WhileLoop[Any, Any, Any]]

              val check = iterate.check

              try {
                while (check()) {
                  val result = runLoop(iterate.body(), currentDepth + 1, Chunk.empty)

                  iterate.process(result)
                }

                cur = ZIO.unit
              } catch {
                case zioError: ZIOError =>
                  cur = Exit.Failure(zioError.cause)

                case reifyStack: ReifyStack =>
                  self.reifiedStack +=
                    EvaluationStep.Continuation.fromSuccess({ (element: Any) =>
                      iterate.process(element)
                      iterate
                    })(iterate.trace)

                  throw reifyStack
              }

            case yieldNow: ZIO.YieldNow =>
              // Green threads never yield:
              if ((_greenThread eq null) && (yieldNow.forceAsync || !stealWork(currentDepth))) {
                throw Trampoline(ZIO.unit, true)
              } else {
                cur = ZIO.unit
              }
          }
        } catch {
          case zioError: ZIOError =>
            assert(stackIndex >= localStack.length)

            throw zioError

          case reifyStack: ReifyStack =>
            if (stackIndex < localStack.length)
              self.reifiedStack ++= localStack.drop(stackIndex)

            throw reifyStack

          // TODO: ClosedByInterruptException (but Scala.js??)
          case interruptedException: InterruptedException =>
            // It's possible we're being interrupted because we're running on a green thread,
            // in which case the full interruption cause is already stored in the queue, and
            // all we have to do is grab it from there. So we form a temporary interruption effect
            // that may be discarded by `drainQueueWhileRunning` if it's not needed and replaced by
            // the real interruption effect:
            cur = drainQueueWhileRunning(Exit.Failure(Cause.interrupt(FiberId.None) ++ Cause.die(interruptedException)))

          case throwable: Throwable =>
            if (isFatal(throwable)) {
              cur = handleFatalError(throwable)
            } else {
              val trace = _lastTrace
              cur = ZIO.failCause(Cause.die(throwable))(trace)
            }
        }
      }
    }

    done
  }

  private def sendInterruptSignalToAllChildrenConcurrently()(implicit unsafe: Unsafe): Boolean = {
    val c = _children

    if (c ne null) {
      internal.Sync(c) {
        sendInterruptSignalToAllChildren(c)
      }
    } else false
  }

  private def sendInterruptSignalToAllChildren(
    children: JavaSet[Fiber.Runtime[_, _]]
  )(implicit unsafe: Unsafe): Boolean =
    if (children == null || children.isEmpty) false
    else {
      // Initiate asynchronous interruption of all children:
      val iterator = children.iterator()
      var told     = false
      val cause    = Cause.interrupt(fiberId)

      while (iterator.hasNext) {
        val next = iterator.next()

        if (next ne null) {
          next.tellInterrupt(cause)

          told = true
        }
      }

      told
    }

  /**
   * Sets the done value for the fiber. This may be done only a single time in
   * the life of the fiber. This method will also update metrics and notify
   * observers of the done value.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private def setExitValue(e: Exit[E, A])(implicit unsafe: Unsafe): Unit = {
    def reportExitValue(v: Exit[E, A])(implicit unsafe: Unsafe): Unit = v match {
      case Exit.Failure(cause) =>
        try {
          if (!cause.isInterruptedOnly) {
            log(
              () => s"Fiber ${fiberId.threadName} did not handle an error",
              cause,
              getFiberRef(FiberRef.unhandledErrorLogLevel),
              id.location
            )
          }

          if (RuntimeFlags.runtimeMetrics(_runtimeFlags)) {
            val tags = getFiberRef(FiberRef.currentTags)
            Metric.runtime.fiberFailures.unsafe.update(1, tags)
            cause.foldContext(tags)(FiberRuntime.fiberFailureTracker)
          }
        } catch {
          case throwable: Throwable =>
            if (isFatal(throwable)) {
              handleFatalError(throwable)
            } else {
              println("An exception was thrown by a logger:")
              throwable.printStackTrace()
            }
        }
      case _ =>
        if (RuntimeFlags.runtimeMetrics(_runtimeFlags)) {
          val tags = getFiberRef(FiberRef.currentTags)
          Metric.runtime.fiberSuccesses.unsafe.update(1, tags)
        }
    }

    _exitValue = e

    if (RuntimeFlags.runtimeMetrics(_runtimeFlags)) {
      val startTimeMillis = fiberId.startTimeMillis
      val endTimeMillis   = java.lang.System.currentTimeMillis()
      val lifetime        = (endTimeMillis - startTimeMillis) / 1000.0

      val tags = getFiberRef(FiberRef.currentTags)
      Metric.runtime.fiberLifetimes.unsafe.update(lifetime, tags)
    }

    reportExitValue(e)

    // ensure we notify observers in the same order they subscribed to us
    val iterator = observers.reverse.iterator

    while (iterator.hasNext) {
      val observer = iterator.next()

      observer(e)
    }
    observers = Nil
  }

  private[zio] def setFiberRef[A](fiberRef: FiberRef[A], value: A)(implicit unsafe: Unsafe): Unit =
    _fiberRefs = _fiberRefs.updatedAs(fiberId)(fiberRef, value)

  private[zio] def setFiberRefs(fiberRefs0: FiberRefs)(implicit unsafe: Unsafe): Unit =
    this._fiberRefs = fiberRefs0

  private[zio] def shouldInterrupt()(implicit unsafe: Unsafe): Boolean = isInterruptible() && isInterrupted()

  /**
   * Begins execution of the effect associated with this fiber on the current
   * thread. This can be called to "kick off" execution of a fiber after it has
   * been created, in hopes that the effect can be executed synchronously.
   *
   * This is not the normal way of starting a fiber, but it is useful when the
   * express goal of executing the fiber is to synchronously produce its exit.
   */
  private[zio] def start[R](effect: ZIO[R, E, A])(implicit unsafe: Unsafe): Exit[E, A] =
    if (running.compareAndSet(false, true)) {
      var previousFiber = null.asInstanceOf[Fiber.Runtime[_, _]]
      try {
        if (RuntimeFlags.currentFiber(_runtimeFlags)) {
          previousFiber = Fiber._currentFiber.get()
          Fiber._currentFiber.set(self)
        }

        evaluateEffect(0, effect.asInstanceOf[ZIO[Any, Any, Any]])
      } finally {
        if ((previousFiber ne null) || RuntimeFlags.currentFiber(_runtimeFlags)) Fiber._currentFiber.set(previousFiber)

        running.set(false)

        // Because we're special casing `start`, we have to be responsible
        // for spinning up the fiber if there were new messages added to
        // the inbox between the completion of the effect and the transition
        // to the not running state.
        if (!inbox.isEmpty && running.compareAndSet(false, true)) drainQueueLaterOnExecutor()
      }
    } else {
      tell(FiberMessage.Resume(effect))

      null
    }

  private[zio] def startSuspended()(implicit unsafe: Unsafe): ZIO[_, E, A] => Any = {
    val alreadyCalled = new AtomicBoolean(false)
    val callback = (effect: ZIO[_, E, A]) => {
      if (alreadyCalled.compareAndSet(false, true)) {
        tell(FiberMessage.Resume(effect))
      }
    }

    self._asyncContWith = callback.asInstanceOf[ZIO[Any, Any, Any] => Any]
    self._blockingOn = FiberRuntime.notBlockingOn

    callback
  }

  private def stealWork(depth: Int): Boolean = false

  /**
   * Attempts to steal work from the current executor, buying some time before
   * this fiber has to asynchronously suspend. Work stealing is only productive
   * if there is "sufficient" space left on the stack, since otherwise, the
   * stolen work would itself immediately trampoline, defeating the potential
   * gains of work stealing.
   */
  private def stealWork(depth0: Int, flags: RuntimeFlags)(implicit unsafe: Unsafe): Boolean = {
    val depth = depth0 + FiberRuntime.WorkStealingSafetyMargin

    val stolen =
      RuntimeFlags.workStealing(flags) && depth < FiberRuntime.MaxWorkStealingDepth && getCurrentExecutor().stealWork(
        depth + FiberRuntime.WorkStealingSafetyMargin
      )

    if (stolen) {
      // After work stealing, we have to do this:
      if (RuntimeFlags.currentFiber(flags)) Fiber._currentFiber.set(self)
    }

    stolen
  }

  /**
   * Adds a message to be processed by the fiber on the fiber.
   */
  private[zio] def tell(message: FiberMessage)(implicit unsafe: Unsafe): Unit = {
    inbox.add(message)

    // Attempt to spin up fiber, if it's not already running:
    if (running.compareAndSet(false, true)) drainQueueLaterOnExecutor()
    else {

      val greenThread = _greenThread

      if (greenThread ne null) {
        message match {
          case FiberMessage.InterruptSignal(cause) =>
            // We may be blocked and therefore we cannot rely on the message to interrupt the children.
            // So we do a slower concurrent signal to the children here:
            sendInterruptSignalToAllChildrenConcurrently()

            // A little racy, since the fiber may no longer be interruptible by the time we
            // perform the interruption:
            if (isInterruptible()) {
              greenThread.interrupt()
            } else {
              // If the fiber has changed between the check and the interrupt (and now the fiber is
              // interruptible, whereas before when we checked, it was not interruptible), then that
              // means its run loop is active, which means it's had a chance to process the message
              // added to the queue before we performed the check.
            }

          case _ => ()
        }
      }
    }
  }

  private[zio] def tellAddChild(child: Fiber.Runtime[_, _])(implicit unsafe: Unsafe): Unit =
    tell(FiberMessage.Stateful((parentFiber, _) => parentFiber.addChild(child)))

  private[zio] def tellInterrupt(cause: Cause[Nothing])(implicit unsafe: Unsafe): Unit =
    tell(FiberMessage.InterruptSignal(cause))

  /**
   * Updates a fiber ref belonging to this fiber by using the provided update
   * function.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private[zio] def updateFiberRef[A](fiberRef: FiberRef[A])(f: A => A)(implicit unsafe: Unsafe): Unit =
    setFiberRef(fiberRef, f(getFiberRef(fiberRef)))

  private[zio] def updateLastTrace(newTrace: Trace): Unit =
    if ((newTrace ne null) && (newTrace ne Trace.empty)) _lastTrace = newTrace

  def unsafe: UnsafeAPI =
    new UnsafeAPI {
      def addObserver(observer: Exit[E, A] => Unit)(implicit unsafe: Unsafe): Unit =
        self.addObserver(observer)

      def deleteFiberRef(ref: FiberRef[_])(implicit unsafe: Unsafe): Unit =
        self.deleteFiberRef(ref)

      def getFiberRefs()(implicit unsafe: Unsafe): FiberRefs =
        self.getFiberRefs()

      def removeObserver(observer: Exit[E, A] => Unit)(implicit unsafe: Unsafe): Unit =
        self.removeObserver(observer)
    }
}

object FiberRuntime {
  private[zio] final val MaxTrampolinesBeforeYield = 5
  private[zio] final val MaxOperationsBeforeYield  = 1024 * 10
  private[zio] final val MaxDepthBeforeTrampoline  = 300
  private[zio] final val MaxWorkStealingDepth      = 150
  private[zio] final val WorkStealingSafetyMargin  = 50

  private[zio] final val IgnoreContinuation: Any => Unit = _ => ()

  private[zio] sealed trait EvaluationSignal
  private[zio] object EvaluationSignal {
    case object Continue extends EvaluationSignal
    case object YieldNow extends EvaluationSignal
    case object Done     extends EvaluationSignal
  }
  import java.util.concurrent.atomic.AtomicBoolean

  def apply[E, A](fiberId: FiberId.Runtime, fiberRefs: FiberRefs, runtimeFlags: RuntimeFlags): FiberRuntime[E, A] =
    new FiberRuntime(fiberId, fiberRefs, runtimeFlags)

  private[zio] val catastrophicFailure: AtomicBoolean = new AtomicBoolean(false)

  private val fiberFailureTracker: Cause.Folder[Set[MetricLabel], Any, Unit] =
    new Cause.Folder[Set[MetricLabel], Any, Unit] {
      def empty(context: Set[MetricLabel]): Unit = ()
      def failCase(context: Set[MetricLabel], error: Any, stackTrace: StackTrace): Unit =
        Metric.runtime.fiberFailureCauses.unsafe.update(error.getClass.getName, context)(Unsafe.unsafe)

      def dieCase(context: Set[MetricLabel], t: Throwable, stackTrace: StackTrace): Unit =
        Metric.runtime.fiberFailureCauses.unsafe.update(t.getClass.getName, context)(Unsafe.unsafe)

      def interruptCase(context: Set[MetricLabel], fiberId: FiberId, stackTrace: StackTrace): Unit = ()
      def bothCase(context: Set[MetricLabel], left: Unit, right: Unit): Unit                       = ()
      def thenCase(context: Set[MetricLabel], left: Unit, right: Unit): Unit                       = ()
      def stacklessCase(context: Set[MetricLabel], value: Unit, stackless: Boolean): Unit          = ()
    }

  private val notBlockingOn: () => FiberId = () => FiberId.None
}
