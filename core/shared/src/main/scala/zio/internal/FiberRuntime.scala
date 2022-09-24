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

import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.annotation.tailrec

import java.util.{Set => JavaSet}
import java.util.concurrent.atomic.AtomicBoolean

import zio._
import zio.metrics.Metric

final class FiberRuntime[E, A](fiberId: FiberId.Runtime, fiberRefs0: FiberRefs, runtimeFlags0: RuntimeFlags)
    extends Fiber.Runtime.Internal[E, A]
    with FiberRunnable {
  self =>
  type Erased = ZIO[Any, Any, Any]

  import ZIO._
  import ReifyStack.{AsyncJump, Trampoline, GenerateTrace}
  import FiberRuntime.EvaluationSignal

  private var _fiberRefs       = fiberRefs0
  private val queue            = new java.util.concurrent.ConcurrentLinkedQueue[FiberMessage]()
  private var _children        = null.asInstanceOf[JavaSet[FiberRuntime[_, _]]]
  private var observers        = Nil: List[Exit[E, A] => Unit]
  private val running          = new AtomicBoolean(false)
  private var _runtimeFlags    = runtimeFlags0
  private val reifiedStack     = PinchableArray.make[EvaluationStep](-1)
  private var asyncInterruptor = null.asInstanceOf[ZIO[Any, Any, Any] => Any]
  private var asyncTrace       = null.asInstanceOf[Trace]
  private var asyncBlockingOn  = null.asInstanceOf[FiberId]

  if (RuntimeFlags.runtimeMetrics(_runtimeFlags)) {
    Metric.runtime.fibersStarted.unsafe.update(1)(Unsafe.unsafe)
    Metric.runtime.fiberForkLocations.unsafe.update(fiberId.location.toString)(Unsafe.unsafe)
  }

  @volatile private var _exitValue = null.asInstanceOf[Exit[E, A]]

  /**
   * Returns an effect that will contain information computed from the fiber
   * state and status while running on the fiber.
   *
   * This allows the outside world to interact safely with mutable fiber state
   * without locks or immutable data.
   */
  def ask[A](f: Unsafe => (FiberRuntime[_, _], Fiber.Status) => A)(implicit trace: Trace): UIO[A] =
    ZIO.suspendSucceed {
      val promise = zio.Promise.unsafe.make[Nothing, A](fiberId)(Unsafe.unsafe)

      tell(
        FiberMessage.Stateful((fiber, status) =>
          promise.unsafe.done(ZIO.succeedNow(f(Unsafe.unsafe)(fiber, status)))(Unsafe.unsafe)
        )
      )(Unsafe.unsafe)

      promise.await
    }

  def await(implicit trace: Trace): UIO[Exit[E, A]] =
    ZIO.asyncInterrupt[Any, Nothing, Exit[E, A]](
      { k =>
        val cb = (exit: Exit[_, _]) => k(Exit.Success(exit.asInstanceOf[Exit[E, A]]))
        tell(FiberMessage.Stateful { (fiber, _) =>
          if (fiber._exitValue ne null) cb(fiber.exitValue()(Unsafe.unsafe))
          else fiber.addObserver(cb)(Unsafe.unsafe)
        })(Unsafe.unsafe)
        Left(ZIO.succeed(tell(FiberMessage.Stateful { (fiber, _) =>
          fiber.removeObserver(cb)(Unsafe.unsafe)
        })(Unsafe.unsafe)))
      },
      id
    )

  def children(implicit trace: Trace): UIO[Chunk[FiberRuntime[_, _]]] =
    ask(unsafe => (fiber, _) => Chunk.fromJavaIterable(fiber.getChildren()(unsafe)))

  def fiberRefs(implicit trace: Trace): UIO[FiberRefs] =
    ask(unsafe => (fiber, _) => fiber.getFiberRefs()(unsafe))

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

      self.runtimeFlags.flatMap { childRuntimeFlags =>
        // Do not inherit WindDown or Interruption!

        val patch =
          RuntimeFlags.Patch.exclude(
            RuntimeFlags.Patch.exclude(
              RuntimeFlags.diff(parentRuntimeFlags, childRuntimeFlags)
            )(RuntimeFlag.WindDown)
          )(RuntimeFlag.Interruption)

        ZIO.updateRuntimeFlags(patch)
      }
    }

  def interruptAsFork(fiberId: FiberId)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed {
      val cause = Cause.interrupt(fiberId).traced(StackTrace(fiberId, Chunk(trace)))

      tell(FiberMessage.InterruptSignal(cause))(Unsafe.unsafe)
    }

  def location: Trace = fiberId.location

  def poll(implicit trace: Trace): UIO[Option[Exit[E, A]]] =
    ZIO.succeed(Option(self.exitValue()(Unsafe.unsafe)))

  override def run(): Unit =
    drainQueueOnCurrentThread()(Unsafe.unsafe)

  def runtimeFlags(implicit trace: Trace): UIO[RuntimeFlags] =
    ask[RuntimeFlags] { _ => (state, status) =>
      status match {
        case Fiber.Status.Done               => state._runtimeFlags
        case active: Fiber.Status.Unfinished => active.runtimeFlags
      }
    }

  def scope: FiberScope = FiberScope.make(this)

  def status(implicit trace: Trace): UIO[zio.Fiber.Status] =
    ask[zio.Fiber.Status](_ => (_, currentStatus) => currentStatus)

  def trace(implicit trace: Trace): UIO[StackTrace] =
    ZIO.suspendSucceed {
      val promise = zio.Promise.unsafe.make[Nothing, StackTrace](fiberId)(Unsafe.unsafe)

      tell(FiberMessage.GenStackTrace(trace => promise.unsafe.done(ZIO.succeedNow(trace))(Unsafe.unsafe)))(
        Unsafe.unsafe
      )

      promise.await
    }

  /**
   * Adds a weakly-held reference to the specified fiber inside the children
   * set.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private[zio] def addChild(child: FiberRuntime[_, _])(implicit unsafe: Unsafe): Unit =
    getChildren().add(child)

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

  /**
   * Deletes the specified fiber ref.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
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
  private def drainQueueOnCurrentThread()(implicit unsafe: Unsafe): Unit = {
    assert(running.get)

    var evaluationSignal: EvaluationSignal = EvaluationSignal.Continue

    if (RuntimeFlags.currentFiber(_runtimeFlags)) Fiber._currentFiber.set(self)

    try {
      while (evaluationSignal == EvaluationSignal.Continue) {
        evaluationSignal =
          if (queue.isEmpty) EvaluationSignal.Done
          else evaluateMessageWhileSuspended(queue.poll())
      }
    } finally {
      running.set(false)

      if (RuntimeFlags.currentFiber(_runtimeFlags)) Fiber._currentFiber.set(null)
    }

    // Maybe someone added something to the queue between us checking, and us
    // giving up the drain. If so, we need to restart the draining, but only
    // if we beat everyone else to the restart:
    if (!queue.isEmpty && running.compareAndSet(false, true)) {
      if (evaluationSignal == EvaluationSignal.YieldNow) drainQueueLaterOnExecutor()
      else drainQueueOnCurrentThread()
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

    self.getCurrentExecutor().submitOrThrow(self)
  }

  /**
   * Drains the fiber's message queue while the fiber is actively running,
   * returning the next effect to execute, which may be the input effect if no
   * additional effect needs to be executed.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private def drainQueueWhileRunning(
    runtimeFlags: RuntimeFlags,
    lastTrace: Trace,
    cur0: ZIO[Any, Any, Any]
  )(implicit unsafe: Unsafe): ZIO[Any, Any, Any] = {
    var cur = cur0

    while (!queue.isEmpty) {
      val message = queue.poll()

      message match {
        case FiberMessage.InterruptSignal(cause) =>
          processNewInterruptSignal(cause)

          if (RuntimeFlags.interruptible(runtimeFlags)) {
            cur = Exit.Failure(cause)
          }

        case FiberMessage.GenStackTrace(onTrace) =>
          val oldCur = cur

          cur = ZIO
            .stackTrace(Trace.empty)
            .flatMap({ stackTrace =>
              onTrace(stackTrace)
              oldCur
            })(Trace.empty)

        case FiberMessage.Stateful(onFiber) =>
          processStatefulMessage(onFiber, Fiber.Status.Running(runtimeFlags, lastTrace))

        case FiberMessage.Resume(_) =>
          throw new IllegalStateException("It is illegal to have multiple concurrent run loops in a single fiber")

        case FiberMessage.YieldNow =>
          val oldCur = cur

          cur = ZIO.yieldNow(Trace.empty).flatMap(_ => oldCur)(Trace.empty)
      }
    }

    cur
  }

  /**
   * Evaluates an effect until completion, potentially asynchronously.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private def evaluateEffect(
    effect0: ZIO[Any, Any, Any]
  )(implicit unsafe: Unsafe): Exit[E, A] = {
    assert(running.get)

    getSupervisor().onResume(self)

    try {
      // Possible the fiber has been interrupted before it begins. Check here:
      var effect =
        if (RuntimeFlags.interruptible(_runtimeFlags) && isInterrupted())
          Exit.Failure(getInterruptedCause())
        else effect0
      var trampolines = 0
      var finalExit   = null.asInstanceOf[Exit[E, A]]

      while (effect ne null) {
        try {
          val localStack = self.reifiedStack.pinch()

          val exit =
            try {
              Exit.Success(runLoop(effect, 0, localStack, _runtimeFlags).asInstanceOf[A])
            } catch {
              case zioError: ZIOError =>
                Exit.Failure(zioError.cause.asInstanceOf[Cause[E]])
            }

          self._runtimeFlags = RuntimeFlags.enable(_runtimeFlags)(RuntimeFlag.WindDown)

          val interruption = interruptAllChildren()

          if (interruption == null) {
            if (queue.isEmpty) {
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
              tell(FiberMessage.YieldNow)
              tell(FiberMessage.Resume(trampoline.effect))

              effect = null
            } else {
              effect = trampoline.effect
            }

          case AsyncJump =>
            // Terminate this evaluation, async resumption will continue evaluation:
            effect = null

          case GenerateTrace =>
            effect = ZIO.succeedNow(generateStackTrace())

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
      getSupervisor().onSuspend(self)
    }
  }

  /**
   * Evaluates a single message on the current thread, while the fiber is
   * suspended. This method should only be called while evaluation of the
   * fiber's effect is suspended due to an asynchronous operation.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private def evaluateMessageWhileSuspended(fiberMessage: FiberMessage)(implicit unsafe: Unsafe): EvaluationSignal = {
    assert(running.get)

    fiberMessage match {
      case FiberMessage.InterruptSignal(cause) =>
        processNewInterruptSignal(cause)

        if (asyncInterruptor != null) {
          asyncInterruptor(Exit.Failure(cause))
          asyncInterruptor = null
        }

        EvaluationSignal.Continue

      case FiberMessage.GenStackTrace(onTrace) =>
        onTrace(generateStackTrace())

        EvaluationSignal.Continue

      case FiberMessage.Stateful(onFiber) =>
        val status =
          if (_exitValue ne null) Fiber.Status.Done
          else if (self.asyncTrace == null) Fiber.Status.Running(self._runtimeFlags, Trace.empty)
          else Fiber.Status.Suspended(self._runtimeFlags, self.asyncTrace, self.asyncBlockingOn)

        processStatefulMessage(onFiber, status)

        EvaluationSignal.Continue

      case FiberMessage.Resume(nextEffect0) =>
        val nextEffect = nextEffect0.asInstanceOf[ZIO[Any, Any, Any]]

        self.asyncInterruptor = null
        self.asyncTrace = null.asInstanceOf[Trace]
        self.asyncBlockingOn = null

        evaluateEffect(nextEffect)

        EvaluationSignal.Continue

      case FiberMessage.YieldNow => EvaluationSignal.YieldNow
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

    builder += id.location

    StackTrace(self.fiberId, builder.result())
  }

  /**
   * Retrieves the mutable set of children of this fiber.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private[zio] def getChildren()(implicit unsafe: Unsafe): JavaSet[FiberRuntime[_, _]] = {
    if (_children eq null) {
      _children = Platform.newWeakSet[FiberRuntime[_, _]]()
    }
    _children
  }

  /**
   * Retrieves the current executor that effects are executed on.
   *
   * '''NOTE''': This method is safe to invoke on any fiber, but if not invoked
   * on this fiber, then values derived from the fiber's state (including the
   * log annotations and log level) may not be up-to-date.
   */
  private[zio] def getCurrentExecutor()(implicit unsafe: Unsafe): Executor =
    getFiberRef(FiberRef.overrideExecutor).getOrElse(Runtime.defaultExecutor)

  /**
   * Retrieves the state of the fiber ref, or else its initial value.
   *
   * '''NOTE''': This method is safe to invoke on any fiber, but if not invoked
   * on this fiber, then values derived from the fiber's state (including the
   * log annotations and log level) may not be up-to-date.
   */
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

  /**
   * Retrieves all fiber refs of the fiber.
   *
   * '''NOTE''': This method is safe to invoke on any fiber, but if not invoked
   * on this fiber, then values derived from the fiber's state (including the
   * log annotations and log level) may not be up-to-date.
   */
  private[zio] def getFiberRefs()(implicit unsafe: Unsafe): FiberRefs = _fiberRefs

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
    val reportFatal = getReportFatal()(Unsafe.unsafe)
    reportFatal(throwable)
  }

  /**
   * Initiates an asynchronous operation, by building a callback that will
   * resume execution, and then feeding that callback to the registration
   * function, handling error cases and repeated resumptions appropriately.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private def initiateAsync(runtimeFlags: RuntimeFlags, asyncRegister: (ZIO[Any, Any, Any] => Unit) => Any)(implicit
    unsafe: Unsafe
  ): Unit = {
    val alreadyCalled = new AtomicBoolean(false)

    val callback = (effect: ZIO[Any, Any, Any]) => {
      if (alreadyCalled.compareAndSet(false, true)) {
        tell(FiberMessage.Resume(effect))
      }
    }

    if (RuntimeFlags.interruptible(runtimeFlags)) self.asyncInterruptor = callback

    try {
      asyncRegister(callback)
    } catch {
      case throwable: Throwable =>
        if (isFatal(throwable)) handleFatalError(throwable)
        else callback(Exit.Failure(Cause.die(throwable)))
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
    if (sendInterruptSignalToAllChildren()) {
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

  /**
   * Determines if the specified throwable is fatal, based on the fatal errors
   * tracked by the fiber's state.
   *
   * '''NOTE''': This method is safe to invoke on any fiber, but if not invoked
   * on this fiber, then values derived from the fiber's state (including the
   * log annotations and log level) may not be up-to-date.
   */
  private[zio] def isFatal(t: Throwable)(implicit unsafe: Unsafe): Boolean =
    getFiberRef(FiberRef.currentFatal).apply(t)

  /**
   * Determines if the fiber is interrupted.
   *
   * '''NOTE''': This method is safe to invoke on any fiber, but if not invoked
   * on this fiber, then values derived from the fiber's state (including the
   * log annotations and log level) may not be up-to-date.
   */
  private[zio] def isInterrupted()(implicit unsafe: Unsafe): Boolean = !getFiberRef(FiberRef.interruptedCause).isEmpty

  /**
   * Logs using the current set of loggers.
   *
   * '''NOTE''': This method is safe to invoke on any fiber, but if not invoked
   * on this fiber, then values derived from the fiber's state (including the
   * log annotations and log level) may not be up-to-date.
   */
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

  private def processStatefulMessage(onFiber: (FiberRuntime[_, _], Fiber.Status) => Unit, status: Fiber.Status)(implicit
    unsafe: Unsafe
  ): Unit =
    try {
      onFiber(self, status)
    } catch {
      case throwable: Throwable =>
        if (isFatal(throwable)) {
          handleFatalError(throwable)
        } else {
          log(
            () =>
              s"An unexpected error was encountered while processing statefulf iber message with callback ${onFiber}",
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
  private def patchRuntimeFlags(oldRuntimeFlags: RuntimeFlags, patch: RuntimeFlags.Patch): RuntimeFlags = {
    val newRuntimeFlags = RuntimeFlags.patch(patch)(oldRuntimeFlags)

    if (RuntimeFlags.Patch.isEnabled(patch)(RuntimeFlag.CurrentFiber)) {
      Fiber._currentFiber.set(self)
    } else if (RuntimeFlags.Patch.isDisabled(patch)(RuntimeFlag.CurrentFiber)) Fiber._currentFiber.set(null)

    self._runtimeFlags = newRuntimeFlags

    newRuntimeFlags
  }

  /**
   * Processes a new incoming interrupt signal.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private def processNewInterruptSignal(cause: Cause[Nothing])(implicit unsafe: Unsafe): Unit = {
    self.addInterruptedCause(cause)
    sendInterruptSignalToAllChildren()
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
   * The main run-loop for evaluating effects. This method is recursive,
   * utilizing JVM stack space.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private def runLoop(
    effect: ZIO[Any, Any, Any],
    currentDepth: Int,
    localStack: Chunk[ZIO.EvaluationStep],
    runtimeFlags0: RuntimeFlags
  )(implicit unsafe: Unsafe): AnyRef = {
    assert(running.get)

    type Erased         = ZIO[Any, Any, Any]
    type ErasedSuccessK = Any => ZIO[Any, Any, Any]
    type ErasedFailureK = Cause[Any] => ZIO[Any, Any, Any]

    // Note that assigning `cur` as the result of `try` or `if` can cause scalac to box `runtimeFlags` or `lastTrace`.
    var cur          = effect
    var done         = null.asInstanceOf[AnyRef]
    var stackIndex   = 0
    var runtimeFlags = runtimeFlags0
    var lastTrace    = Trace.empty
    var ops          = 0

    if (currentDepth >= 500) {
      self.reifiedStack.ensureCapacity(currentDepth)

      self.reifiedStack ++= localStack

      throw Trampoline(effect, false)
    }

    while (cur ne null) {
      if (RuntimeFlags.opSupervision(runtimeFlags)) {
        self.getSupervisor().onEffect(self, cur)
      }

      val nextTrace = cur.trace
      if (nextTrace ne Trace.empty) lastTrace = nextTrace

      cur = drainQueueWhileRunning(runtimeFlags, lastTrace, cur)

      ops += 1

      if (ops > FiberRuntime.MaxOperationsBeforeYield) {
        ops = 0
        val oldCur = cur
        val trace  = lastTrace
        cur = ZIO.yieldNow(trace).flatMap(_ => oldCur)(trace)
      } else {
        try {
          cur match {

            case effect0: OnSuccess[_, _, _, _] =>
              val effect = effect0.asInstanceOf[OnSuccess[Any, Any, Any, Any]]

              try {
                cur = effect.successK(runLoop(effect.first, currentDepth + 1, Chunk.empty, runtimeFlags))
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
                      runtimeFlags = patchRuntimeFlags(runtimeFlags, k.update)

                      if (RuntimeFlags.interruptible(runtimeFlags) && isInterrupted())
                        cur = Exit.Failure(getInterruptedCause())

                    case k: EvaluationStep.UpdateTrace => if (k.trace ne Trace.empty) lastTrace = k.trace
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
                cur = Exit.Success(runLoop(effect.first, currentDepth + 1, Chunk.empty, runtimeFlags))
              } catch {
                case zioError: ZIOError =>
                  cur = effect.onFailure(zioError.cause)

                case reifyStack: ReifyStack =>
                  self.reifiedStack += effect

                  throw reifyStack
              }

            case effect0: OnSuccessAndFailure[_, _, _, _, _] =>
              val effect = effect0.asInstanceOf[OnSuccessAndFailure[Any, Any, Any, Any, Any]]

              try {
                cur = effect.successK(runLoop(effect.first, currentDepth + 1, Chunk.empty, runtimeFlags))
              } catch {
                case zioError: ZIOError =>
                  cur = effect.failureK(zioError.cause)

                case reifyStack: ReifyStack =>
                  self.reifiedStack += effect

                  throw reifyStack
              }

            case effect: Async[_, _, _] =>
              self.reifiedStack.ensureCapacity(currentDepth)

              self.asyncTrace = lastTrace
              self.asyncBlockingOn = effect.blockingOn()

              initiateAsync(runtimeFlags, effect.registerCallback)

              throw AsyncJump

            case effect: UpdateRuntimeFlagsWithin[_, _, _] =>
              val updateFlags     = effect.update
              val oldRuntimeFlags = runtimeFlags
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
                  runtimeFlags = patchRuntimeFlags(runtimeFlags, updateFlags)

                  // Since we updated the flags, we need to revert them:
                  val revertFlags = RuntimeFlags.diff(newRuntimeFlags, oldRuntimeFlags)

                  try {
                    val value = runLoop(
                      effect.scope(oldRuntimeFlags).asInstanceOf[ZIO[Any, Any, Any]],
                      currentDepth + 1,
                      Chunk.empty,
                      runtimeFlags
                    )

                    // Go backward, on the stack:
                    runtimeFlags = patchRuntimeFlags(runtimeFlags, revertFlags)

                    if (RuntimeFlags.interruptible(runtimeFlags) && isInterrupted())
                      cur = Exit.Failure(getInterruptedCause())
                    else {
                      cur = Exit.Success(value)
                    }
                  } catch {
                    case zioError: ZIOError =>
                      runtimeFlags = patchRuntimeFlags(runtimeFlags, revertFlags)
                      cur = Exit.Failure(zioError.cause)

                    case reifyStack: ReifyStack =>
                      self.reifiedStack += EvaluationStep.UpdateRuntimeFlags(revertFlags) // Go backward, on the heap

                      throw reifyStack
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
                  Fiber.Status.Running(runtimeFlags, lastTrace)
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
                    runtimeFlags = patchRuntimeFlags(runtimeFlags, k.update)

                    if (RuntimeFlags.interruptible(runtimeFlags) && isInterrupted())
                      cur = Exit.Failure(getInterruptedCause())

                  case k: EvaluationStep.UpdateTrace => if (k.trace ne Trace.empty) lastTrace = k.trace
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
                    if (!(RuntimeFlags.interruptible(runtimeFlags) && isInterrupted()))
                      cur = k.failureK.asInstanceOf[ErasedFailureK](cause)
                    else
                      cause = cause.stripFailures // Skipped an error handler which changed E1 => E2, so must discard

                  case k: ZIO.OnFailure[_, _, _, _] =>
                    if (!(RuntimeFlags.interruptible(runtimeFlags) && isInterrupted()))
                      cur = k.failureK.asInstanceOf[ErasedFailureK](cause)
                    else
                      cause = cause.stripFailures // Skipped an error handler which changed E1 => E2, so must discard

                  case k: EvaluationStep.UpdateRuntimeFlags =>
                    runtimeFlags = patchRuntimeFlags(runtimeFlags, k.update)

                    if (RuntimeFlags.interruptible(runtimeFlags) && isInterrupted()) {
                      cur = Exit.Failure(cause ++ getInterruptedCause())
                    }

                  case k: EvaluationStep.UpdateTrace => if (k.trace ne Trace.empty) lastTrace = k.trace
                }
              }

              if (cur eq null) throw ZIOError(cause)

            case updateRuntimeFlags: UpdateRuntimeFlags =>
              runtimeFlags = patchRuntimeFlags(runtimeFlags, updateRuntimeFlags.update)

              // If we are nested inside another recursive call to `runLoop`,
              // then we need pop out to the very top in order to update
              // runtime flags globally:
              if (currentDepth > 0) {
                self.reifiedStack.ensureCapacity(currentDepth)
                throw Trampoline(ZIO.unit, false)
              } else {
                // We are at the top level, no need to update runtime flags
                // globally:
                cur = ZIO.unit
              }

            case iterate0: WhileLoop[_, _, _] =>
              val iterate = iterate0.asInstanceOf[WhileLoop[Any, Any, Any]]

              val check = iterate.check

              try {
                while (check()) {
                  val result = runLoop(iterate.body(), currentDepth + 1, Chunk.empty, runtimeFlags)

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
              self.reifiedStack += EvaluationStep.UpdateTrace(yieldNow.trace)

              throw Trampoline(ZIO.unit, true)
          }
        } catch {
          case zioError: ZIOError =>
            assert(stackIndex >= localStack.length)

            throw zioError

          case reifyStack: ReifyStack =>
            if (stackIndex < localStack.length)
              self.reifiedStack ++= localStack.drop(stackIndex)

            throw reifyStack

          case interruptedException: InterruptedException =>
            cur = Exit.Failure(Cause.die(interruptedException) ++ Cause.interrupt(FiberId.None))

          case throwable: Throwable =>
            if (isFatal(throwable)) {
              cur = handleFatalError(throwable)
            } else {
              cur = ZIO.failCause(Cause.die(throwable))(lastTrace)
            }
        }
      }
    }

    done
  }

  private def sendInterruptSignalToAllChildren()(implicit unsafe: Unsafe): Boolean =
    if (_children == null || _children.isEmpty) false
    else {
      // Initiate asynchronous interruption of all children:
      val iterator = _children.iterator()
      var told     = false

      while (iterator.hasNext) {
        val next = iterator.next()

        if (next ne null) {
          next.tell(FiberMessage.InterruptSignal(Cause.interrupt(id)))

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
            Metric.runtime.fiberFailures.unsafe.update(1)
            cause.foldContext(())(FiberRuntime.fiberFailureTracker)
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
          Metric.runtime.fiberSuccesses.unsafe.update(1)
        }
    }

    _exitValue = e

    if (RuntimeFlags.runtimeMetrics(_runtimeFlags)) {
      val startTimeMillis = fiberId.startTimeMillis
      val endTimeMillis   = java.lang.System.currentTimeMillis()
      val lifetime        = (endTimeMillis - startTimeMillis) / 1000.0

      Metric.runtime.fiberLifetimes.unsafe.update(lifetime)
    }

    reportExitValue(e)

    val iterator = observers.iterator

    while (iterator.hasNext) {
      val observer = iterator.next()

      observer(e)
    }
    observers = Nil
  }

  /**
   * Sets the fiber ref to the specified value.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private[zio] def setFiberRef[A](fiberRef: FiberRef[A], value: A)(implicit unsafe: Unsafe): Unit =
    _fiberRefs = _fiberRefs.updatedAs(fiberId)(fiberRef, value)

  /**
   * Wholesale replaces all fiber refs of this fiber.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private[zio] def setFiberRefs(fiberRefs0: FiberRefs)(implicit unsafe: Unsafe): Unit =
    this._fiberRefs = fiberRefs0

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
      try {
        evaluateEffect(effect.asInstanceOf[ZIO[Any, Any, Any]])
      } finally {
        running.set(false)

        // Because we're special casing `start`, we have to be responsible
        // for spinning up the fiber if there were new messages added to
        // the queue between the completion of the effect and the transition
        // to the not running state.
        if (!queue.isEmpty && running.compareAndSet(false, true)) drainQueueLaterOnExecutor()
      }
    } else {
      tell(FiberMessage.Resume(effect))

      null
    }

  /**
   * Begins execution of the effect associated with this fiber on in the
   * background, and on the correct thread pool. This can be called to "kick
   * off" execution of a fiber after it has been created, in hopes that the
   * effect can be executed synchronously.
   */
  private[zio] def startFork[R](effect: ZIO[R, E, A])(implicit unsafe: Unsafe): Unit =
    tell(FiberMessage.Resume(effect))

  /**
   * Adds a message to be processed by the fiber on the fiber.
   */
  private[zio] def tell(message: FiberMessage)(implicit unsafe: Unsafe): Unit = {
    queue.add(message)

    // Attempt to spin up fiber, if it's not already running:
    if (running.compareAndSet(false, true)) drainQueueLaterOnExecutor()
  }

  /**
   * Updates a fiber ref belonging to this fiber by using the provided update
   * function.
   *
   * '''NOTE''': This method must be invoked by the fiber itself.
   */
  private[zio] def updateFiberRef[A](fiberRef: FiberRef[A])(f: A => A)(implicit unsafe: Unsafe): Unit =
    setFiberRef(fiberRef, f(getFiberRef(fiberRef)))

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
  private[zio] val MaxTrampolinesBeforeYield = 5
  private[zio] val MaxOperationsBeforeYield  = 1024

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

  private val fiberFailureTracker: Cause.Folder[Unit, Any, Unit] =
    new Cause.Folder[Unit, Any, Unit] {
      def empty(context: Unit): Unit = ()
      def failCase(context: Unit, error: Any, stackTrace: StackTrace): Unit =
        Metric.runtime.fiberFailureCauses.unsafe.update(error.getClass.getName)(Unsafe.unsafe)

      def dieCase(context: Unit, t: Throwable, stackTrace: StackTrace): Unit =
        Metric.runtime.fiberFailureCauses.unsafe.update(t.getClass.getName)(Unsafe.unsafe)

      def interruptCase(context: Unit, fiberId: FiberId, stackTrace: StackTrace): Unit = ()
      def bothCase(context: Unit, left: Unit, right: Unit): Unit                       = ()
      def thenCase(context: Unit, left: Unit, right: Unit): Unit                       = ()
      def stacklessCase(context: Unit, value: Unit, stackless: Boolean): Unit          = ()
    }
}
