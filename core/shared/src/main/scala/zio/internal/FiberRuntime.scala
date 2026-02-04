/*
 * Copyright 2022-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.internal

import zio.Exit.{Failure, Success}
import zio._
import zio.internal.SpecializationHelpers.SpecializeInt
import zio.metrics.{Metric, MetricLabel}
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.AtomicBoolean
import java.util.{Set => JavaSet}
import scala.annotation.tailrec

final class FiberRuntime[E, A](fiberId: FiberId.Runtime, fiberRefs0: FiberRefs, runtimeFlags0: RuntimeFlags)
    extends Fiber.Runtime.Internal[E, A]
    with FiberRunnable {
  self =>
  type Erased = ZIO.Erased

  import FiberRuntime._
  import ZIO._

  private var _lastTrace      = fiberId.location
  private var _fiberRefs      = fiberRefs0
  private var _runtimeFlags   = runtimeFlags0
  private var _blockingOn     = FiberRuntime.notBlockingOn
  private var _asyncContWith  = null.asInstanceOf[AsyncContWith]
  private val running         = new AtomicBoolean(false)
  
  private val inbox           = new FiberMailbox() 
  
  private var _children       = null.asInstanceOf[JavaSet[Fiber.Runtime[_, _]]]
  private var observers       = Nil: List[Exit[E, A] => Unit]
  private var runningExecutor = null.asInstanceOf[Executor]
  private var _stack          = null.asInstanceOf[Array[Continuation]]
  private var _stackSize      = 0
  private var _isInterrupted  = false

  private var _forksSinceYield = 0

  private[zio] def shouldYieldBeforeFork(): Boolean =
    if (RuntimeFlags.cooperativeYielding(_runtimeFlags)) {
      _forksSinceYield += 1
      _forksSinceYield >= FiberRuntime.MaxForksBeforeYield
    } else false

  if (RuntimeFlags.runtimeMetrics(_runtimeFlags)) {
    val tags = getFiberRef(FiberRef.currentTags)
    Metric.runtime.fibersStarted.unsafe.update(1, tags)(Unsafe)
    Metric.runtime.fiberForkLocations.unsafe.update(fiberId.location.toString, tags)(Unsafe)
  }

  @volatile private var _exitValue = null.asInstanceOf[Exit[E, A]]

  def await(implicit trace: Trace): UIO[Exit[E, A]] =
    ZIO.suspendSucceed(awaitUnsafe)

  @inline
  private[this] def awaitUnsafe(implicit trace: Trace): UIO[Exit[E, A]] = {
    val exitValue = self._exitValue
    if (exitValue ne null) Exit.succeed(exitValue)
    else
      ZIO.asyncInterrupt[Any, Nothing, Exit[E, A]](
        { k =>
          val cb = (exit: Exit[_, _]) => k(Exit.Success(exit.asInstanceOf[Exit[E, A]]))
          unsafe.addObserver(cb)(Unsafe)
          Left(ZIO.succeed(unsafe.removeObserver(cb)(Unsafe)))
        },
        id
      )
  }

  private[this] def childrenChunk(children: java.util.Set[Fiber.Runtime[?, ?]]): Chunk[Fiber.Runtime[_, _]] =
    if (children eq null) Chunk.empty
    else {
      val bldr = Chunk.newBuilder[Fiber.Runtime[_, _]]
      children.forEach { child =>
        if ((child ne null) && child.isAlive())
          bldr.addOne(child)
      }
      bldr.result()
    }

  def children(implicit trace: Trace): UIO[Chunk[Fiber.Runtime[_, _]]] =
    ZIO.succeed(self.childrenChunk(_children))

  def fiberRefs(implicit trace: Trace): UIO[FiberRefs] = ZIO.succeed(_fiberRefs)

  def id: FiberId.Runtime = fiberId

  def inheritAll(implicit trace: Trace): UIO[Unit] =
    ZIO.withFiberRuntime[Any, Nothing, Unit] { (parentFiber, parentStatus) =>
      val parentFiberId      = parentFiber.id
      val parentFiberRefs    = parentFiber.getFiberRefs()
      val parentRuntimeFlags = parentStatus.runtimeFlags
      val childFiberRefs     = self.getFiberRefs()

      val updatedFiberRefs = parentFiberRefs.joinAs(parentFiberId)(childFiberRefs)
      if (updatedFiberRefs ne parentFiberRefs) {
        parentFiber.setFiberRefs(updatedFiberRefs)

        val updatedRuntimeFlags = updatedFiberRefs.getRuntimeFlags(Unsafe)

        val patch = FiberRuntime.patchExcludeNonInheritable(RuntimeFlags.diff(parentRuntimeFlags, updatedRuntimeFlags))
        ZIO.updateRuntimeFlags(patch)
      } else {
        Exit.unit
      }
    }

  override def interruptAs(fiberId: FiberId)(implicit trace: Trace): UIO[Exit[E, A]] =
    ZIO.suspendSucceed {
      val exit = _exitValue
      if (exit ne null) Exit.succeed(exit)
      else {
        val cause = Cause.interrupt(fiberId, StackTrace(self.fiberId, Chunk.single(trace)))
        inbox.offer(FiberMessage.InterruptSignal(cause))

        if (running.compareAndSet(false, true)) {
          val executor = getCurrentExecutor()
          if (executor.isCurrentThreadInExecutor) drainQueueOnCurrentThread(0)
          else drainQueueLaterOnExecutor(false)
        }

        awaitUnsafe(trace)
      }
    }

  def interruptAsFork(fiberId: FiberId)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed {
      val cause = Cause.interrupt(fiberId, StackTrace(self.fiberId, Chunk.single(trace)))

      tell(FiberMessage.InterruptSignal(cause))
    }

  def location: Trace = fiberId.location

  def poll(implicit trace: Trace): UIO[Option[Exit[E, A]]] =
    ZIO.succeed(Option(self.exitValue()))

  override def run(): Unit =
    drainQueueOnCurrentThread(0)

  override def run(depth: Int): Unit =
    drainQueueOnCurrentThread(depth)

  def runtimeFlags(implicit trace: Trace): UIO[RuntimeFlags] =
    ZIO.succeed(_runtimeFlags)

  lazy val scope: FiberScope = FiberScope.make(this)

  def status(implicit trace: Trace): UIO[zio.Fiber.Status] =
    ZIO.succeed(getStatus())

  def trace(implicit trace: Trace): UIO[StackTrace] =
    ZIO.succeed {
      generateStackTrace()
    }

  private[zio] def addChild(child: Fiber.Runtime[_, _]): Unit =
    if (child.isAlive()) {
      if (isAlive()) {
        getChildren().add(child)

        if (shouldInterrupt())
          child.tellInterrupt(getInterruptedCause())
      } else {
        child.tellInterrupt(getInterruptedCause())
      }
    }

  private[zio] def addChildren(children: Iterable[Fiber.Runtime[_, _]]): Unit = {
    val iter = children.iterator
    if (isAlive()) {
      val childs = getChildren()

      if (shouldInterrupt()) {
        val cause = getInterruptedCause()
        while (iter.hasNext) {
          val child = iter.next()
          if (child.isAlive()) {
            childs.add(child)
            child.tellInterrupt(cause)
          }
        }
      } else {
        while (iter.hasNext) {
          val child = iter.next()
          if (child.isAlive())
            childs.add(child)
        }
      }
    } else {
      val cause = getInterruptedCause()
      while (iter.hasNext) {
        val child = iter.next()
        if (child.isAlive())
          child.tellInterrupt(cause)
      }
    }
  }

  private def addInterruptedCause(cause: Cause[Nothing]): Unit = {
    val oldSC = getFiberRef(FiberRef.interruptedCause)

    _isInterrupted = true
    setFiberRef(FiberRef.interruptedCause, oldSC ++ cause)
  }

  private[zio] def addObserver(observer: Exit[E, A] => Unit)(implicit unsafe: Unsafe): Unit = {
    val exitValue = _exitValue
    if (exitValue ne null) observer(exitValue)
    else observers = observer :: observers
  }

  private[zio] def deleteFiberRef(ref: FiberRef[_]): Unit =
    _fiberRefs = _fiberRefs.delete(ref)

  @tailrec
  private def drainQueueOnCurrentThread(depth: Int): Unit = {
    assert(DisableAssertions || running.get)

    var evaluationSignal: EvaluationSignal = EvaluationSignal.Continue
    try {
      if (RuntimeFlags.currentFiber(_runtimeFlags)) {
        Fiber._currentFiber.set(self)
      }

      while (evaluationSignal == EvaluationSignal.Continue) {
        evaluationSignal = {
          val message = inbox.poll()
          if (message == null) EvaluationSignal.Done
          else evaluateMessageWhileSuspended(depth, message)
        }
      }
    } finally {
      running.set(false)
    }

    if (!inbox.isEmpty && running.compareAndSet(false, true)) {
      if (evaluationSignal == EvaluationSignal.YieldNow) drainQueueLaterOnExecutor(true)
      else drainQueueOnCurrentThread(depth)
    }
  }

  private def drainQueueLaterOnExecutor(attemptResumptionOnSameThread: Boolean): Unit = {
    assert(DisableAssertions || running.get)

    runningExecutor = self.getCurrentExecutor()

    if (attemptResumptionOnSameThread)
      runningExecutor.submitAndYieldOrThrow(self)(Unsafe)
    else
      runningExecutor.submitOrThrow(self)(Unsafe)
  }

  private def drainQueueWhileRunning(cur0: ZIO.Erased): ZIO.Erased = {
    var cur     = cur0
    var message = inbox.poll()

    while (message ne null) {
      message match {
        case FiberMessage.Stateful(onFiber) =>
          processStatefulMessage(onFiber)

        case FiberMessage.InterruptSignal(cause) =>
          updateLastTrace(cur.trace)
          processNewInterruptSignal(cause)

          if (isInterruptible()) {
            cur = Exit.Failure(cause)
          }

        case _ =>
          assert(DisableAssertions, "It is illegal to have multiple concurrent run loops in a single fiber")
      }

      message = inbox.poll()
    }

    cur
  }

  private def drainQueueAfterAsync(): ZIO.Erased = {
    var resumption: ZIO.Erased = null

    var message = inbox.poll()

    while (message ne null) {
      message match {
        case FiberMessage.InterruptSignal(cause) =>
          processNewInterruptSignal(cause)

        case FiberMessage.Stateful(onFiber) =>
          processStatefulMessage(onFiber)

        case FiberMessage.Resume(nextEffect0) =>
          assert(DisableAssertions || (resumption eq null))

          resumption = nextEffect0.asInstanceOf[ZIO.Erased]

        case _ =>
          assert(DisableAssertions)

      }

      message = inbox.poll()
    }

    resumption
  }

  private def ensureStackCapacity(size: Int): Unit = {
    val stack       = _stack
    val stackLength = stack.length

    if (stackLength < size) {
      val newSize = if ((size & (size - 1)) == 0) size else Integer.highestOneBit(size) << 1

      val newStack = new Array[Continuation](newSize)

      java.lang.System.arraycopy(stack, 0, newStack, 0, stackLength)

      _stack = newStack
    }
    ()
  }

  private def evaluateEffect(
    initialDepth: Int,
    effect0: ZIO.Erased
  ): Exit[E, A] = {
    assert(DisableAssertions || running.get)

    self._asyncContWith = AsyncContWith.`null`
    self._blockingOn = FiberRuntime.notBlockingOn

    updateLastTrace(effect0.trace)

    val supervisor = getSupervisor()

    if (supervisor ne Supervisor.none) supervisor.onResume(self)(Unsafe)
    if (_stack eq null) _stack = new Array[Continuation](FiberRuntime.InitialStackSize)

    try {
      var effect    = effect0
      var finalExit = null.asInstanceOf[Exit[E, A]]

      while (effect ne null) {
        try {
          if (shouldInterrupt()) {
            effect = Exit.Failure(getInterruptedCause())
          }

          val exit =
            runLoop(effect, 0, _stackSize, initialDepth, 0).asInstanceOf[Exit[E, A]]

          if (exit eq null) {
            _forksSinceYield = 0
            effect = null
          } else {
            self._runtimeFlags = RuntimeFlags.enable(_runtimeFlags)(RuntimeFlag.WindDown)

            val interruption = interruptAllChildren()

            if (interruption eq null) {
              if (inbox.isEmpty) {
                finalExit = exit

                if (supervisor ne Supervisor.none) supervisor.onEnd(finalExit, self)(Unsafe)
                self.setExitValue(exit)
              } else {
                tell(FiberMessage.Resume(exit))
              }

              effect = null
            } else {
              effect = interruption.flatMap(_ => exit)(id.location)
            }
          }
        } catch {
          case ex if nonFatal(ex) =>
            effect = ZIO.failCause(Cause.die(ex))(_lastTrace)
          case fatal =>
            effect = handleFatalError(fatal)
        }
      }

      finalExit
    } finally {
      gcStack()

      val supervisor = getSupervisor()

      if (supervisor ne Supervisor.none) supervisor.onSuspend(self)(Unsafe)
    }
  }

  private def evaluateMessageWhileSuspended(depth: Int, fiberMessage: FiberMessage): EvaluationSignal = {
    assert(DisableAssertions || running.get)

    fiberMessage match {
      case FiberMessage.InterruptSignal(cause) =>
        processNewInterruptSignal(cause)

        EvaluationSignal.Continue

      case FiberMessage.Stateful(onFiber) =>
        processStatefulMessage(onFiber)

        EvaluationSignal.Continue

      case FiberMessage.Resume(nextEffect0) =>
        val nextEffect = nextEffect0.asInstanceOf[ZIO.Erased]

        val exit = evaluateEffect(depth, nextEffect)
        if (exit eq null) EvaluationSignal.YieldNow
        else EvaluationSignal.Continue

      case _ =>
        assert(DisableAssertions)
        EvaluationSignal.YieldNow
    }
  }

  private[zio] def exitValue(): Exit[E, A] = _exitValue

  private[zio] def generateStackTrace(): StackTrace = {
    val builder = stackTraceBuilderPool.get()

    val stack = _stack
    val size  = _stackSize 

    var last = _lastTrace
    builder += last

    try {
      if (stack ne null) {
        var i = (if (stack.length < size) stack.length else size) - 1

        while (i >= 0) {
          val k = stack(i)
          if (k ne null) { 
            val trace = k.trace
            if (trace ne last) {
              last = trace
              builder += trace
            }
            i -= 1
          }
        }
      }

      val loc = id.location
      if (loc ne last)
        builder += loc 

      StackTrace(self.fiberId, builder.result())
    } finally {
      builder.clear()
    }
  }

  private def getChildren(): JavaSet[Fiber.Runtime[_, _]] = {
    var children = _children
    if (children eq null) {
      children = Platform.newConcurrentWeakSet[Fiber.Runtime[_, _]]()(Unsafe)
      _children = children
    }
    children
  }

  private[zio] def getCurrentExecutor(): Executor =
    getFiberRefOrNull(FiberRef.overrideExecutor) match {
      case Some(value) => value
      case _           => Runtime.defaultExecutor
    }

  private[zio] def getFiberRef[A](fiberRef: FiberRef[A]): A =
    _fiberRefs.getOrDefault(fiberRef)

  private[zio] def getFiberRefOrNull[A](fiberRef: FiberRef[A]): A =
    _fiberRefs.getOrNull(fiberRef)

  private[zio] def getFiberRefOrElse[A](fiberRef: FiberRef[A], orElse: => A): A =
    _fiberRefs.getOrNull(fiberRef) match {
      case null => orElse
      case a    => a
    }

  private[zio] def getFiberRefOption[A](fiberRef: FiberRef[A]): Option[A] =
    _fiberRefs.get(fiberRef)

  private[zio] def getFiberRefs(updateRuntimeFlagsWithin: Boolean): FiberRefs = {
    val refs = _fiberRefs
    if (updateRuntimeFlagsWithin) {
      val flags0  = FiberRuntime.excludeNonInheritable(_runtimeFlags)
      val newRefs = _fiberRefs.updateRuntimeFlags(fiberId)(flags0)
      if (newRefs ne refs) _fiberRefs = newRefs
      newRefs
    } else {
      refs
    }
  }

  private[zio] def getInterruptedCause(): Cause[Nothing] = getFiberRef(
    FiberRef.interruptedCause
  )

  private[zio] def getLoggers(): Set[ZLogger[String, Any]] =
    getFiberRef(FiberRef.currentLoggers)

  private[zio] def getReportFatal(): Throwable => Nothing =
    getFiberRef(FiberRef.currentReportFatal)

  private[zio] def getRunningExecutor(): Option[Executor] =
    if (runningExecutor eq null) None else Some(runningExecutor)

  private[zio] def getStatus(): Fiber.Status =
    if (_exitValue ne null) Fiber.Status.Done
    else if (running.get()) Fiber.Status.Running(self._runtimeFlags, _lastTrace)
    else Fiber.Status.Suspended(self._runtimeFlags, _lastTrace, _blockingOn())

  private[zio] def getSupervisor(): Supervisor[Any] =
    getFiberRef(FiberRef.currentSupervisor)

  private def handleFatalError(throwable: Throwable): Nothing = {
    FiberRuntime.catastrophicFailure.set(true)
    val errorReporter = getReportFatal()
    errorReporter(throwable)
  }

  private def initiateAsync(
    asyncRegister: (ZIO.Erased => Unit) => Either[ZIO.Erased, ZIO.Erased]
  ): ZIO.Erased = {
    val callback = new AsyncContWith.Callback(self)
    var value    = null.asInstanceOf[Either[ZIO.Erased, ZIO.Erased]]

    try {
      value = asyncRegister(callback)
    } catch {
      case ex if nonFatal(ex) => callback(Exit.Failure(Cause.die(ex)))
      case fatal              => handleFatalError(fatal)
    }

    value match {
      case Left(onInterrupt) =>
        if (isInterruptible()) self._asyncContWith = AsyncContWith(callback, onInterrupt)

      case Right(value) if value ne null =>
        if (callback.compareAndSet(false, true)) {
          return value
        }
        log(
          FiberRuntime.syncResumptionErrorMessage,
          Cause.empty,
          ZIO.someError,
          id.location
        )

      case _ =>
        if (isInterruptible()) self._asyncContWith = AsyncContWith(callback)
    }

    null
  }

  private def interruptAllChildren(): UIO[Any] =
    if (sendInterruptSignalToAllChildren(_children)) {
      val iterator = _children.iterator()
      _children = null

      var curr: Fiber.Runtime[_, _] = null

      def skip() = {
        var next: Fiber.Runtime[_, _] = null
        while (iterator.hasNext && (next eq null)) {
          next = iterator.next()
          if ((next ne null) && !next.isAlive())
            next = null
        }
        curr = next
      }

      skip()

      if (null ne curr) {
        ZIO
          .whileLoop(null ne curr)(curr.await(id.location))(_ => skip())(id.location)
      } else null
    } else null

  private[zio] def isAlive(): Boolean =
    _exitValue eq null

  private[zio] def isDone(): Boolean =
    _exitValue ne null

  private[zio] def hasChildrenAlive(implicit trace: Trace): UIO[Boolean] =
    ZIO.withFiberRuntime[Any, Nothing, Boolean] { (parent, _) =>
      if (parent.id == self.id) Exit.boolean(hasChildrenAliveUnsafe)
      else if (_exitValue ne null) Exit.`false`
      else {
        ZIO.async { cb =>
          tell(FiberMessage.Stateful { state =>
            val res = Exit.boolean(state.hasChildrenAliveUnsafe)
            cb(res)
          })
        }
      }
    }

  private def hasChildrenAliveUnsafe: Boolean = {
    val children0 = _children
    if ((children0 eq null) || (_exitValue ne null)) false
    else {
      val it = children0.iterator()
      while (it.hasNext) {
        val child = it.next()
        if ((child ne null) && child.isAlive()) return true
      }
      false
    }
  }

  private[zio] def isInterrupted(): Boolean =
    _isInterrupted || {
      if (Thread.interrupted()) {
        addInterruptedCause(Cause.interrupt(FiberId.None))

        true
      } else false
    }

  private[zio] def isInterruptible(): Boolean =
    RuntimeFlags.interruptible(_runtimeFlags)

  private[zio] def log(
    message: () => String,
    cause: Cause[Any],
    overrideLogLevel: Option[LogLevel],
    trace: Trace
  ): Unit = {
    val contextMap = getFiberRefs(false)
    val loggers    = contextMap.getOrDefault(FiberRef.currentLoggers)

    if (!loggers.isEmpty) {
      val logLevel =
        if (overrideLogLevel.isDefined) overrideLogLevel.get
        else contextMap.getOrDefault(FiberRef.currentLogLevel)

      val spans       = contextMap.getOrDefault(FiberRef.currentLogSpan)
      val annotations = contextMap.getOrDefault(FiberRef.currentLogAnnotations)

      val it = loggers.iterator
      while (it.hasNext) {
        it.next()(trace, fiberId, logLevel, message, cause, contextMap, spans, annotations)
      }
    }
  }

  private def processStatefulMessage(onFiber: FiberRuntime[_, _] => Unit): Unit =
    try {
      onFiber(self)
    } catch {
      case ex if nonFatal(ex) =>
        log(
          () => s"An unexpected error was encountered while processing stateful fiber message with callback ${onFiber}",
          Cause.die(ex),
          ZIO.someError,
          id.location
        )
      case fatal => handleFatalError(fatal)
    }

  private def patchRuntimeFlags[E0, A0](
    patch: RuntimeFlags.Patch,
    cause: Cause[E0],
    continueEffect: Exit[E0, A0]
  ): Exit[E0, A0] =
    patchRuntimeFlagsCause(patch, cause) match {
      case null => continueEffect
      case c    => Exit.Failure(c)
    }

  private def patchRuntimeFlagsCause[E0](
    patch: RuntimeFlags.Patch,
    cause: Cause[E0]
  ): Cause[E0] = {
    val changed          = patchRuntimeFlagsOnly(patch)
    val interruptEnabled = RuntimeFlags.Patch.isEnabled(patch, RuntimeFlag.Interruption.mask)

    if (changed && interruptEnabled && shouldInterrupt()) {
      if (cause ne null) cause ++ getInterruptedCause()
      else getInterruptedCause()
    } else cause
  }

  private def patchRuntimeFlagsOnly(patch: RuntimeFlags.Patch): Boolean = {
    import RuntimeFlags.Patch.{isDisabled, isEnabled}

    val oldFlags = _runtimeFlags
    val newFlags = RuntimeFlags.patch(patch)(oldFlags)
    val changed  = oldFlags != newFlags
    if (changed) {
      if (isEnabled(patch, RuntimeFlag.CurrentFiber.mask)) {
        Fiber._currentFiber.set(self)
      } else if (isDisabled(patch, RuntimeFlag.CurrentFiber.mask)) {
        Fiber._currentFiber.set(null)
      }

      _runtimeFlags = newFlags
    }
    changed
  }

  @inline
  private[this] def popStackFrame(nextStackIndex: Int): Unit = {
    if (nextStackIndex >= FiberRuntime.StackIdxGcThreshold) {
      _stack(nextStackIndex) = null
    }

    _stackSize = nextStackIndex
  }

  private[this] def gcStack(): Unit = {
    val fromIndex = _stackSize
    if (fromIndex == 0) {
      _stack = null
    } else {
      val stack   = _stack.asInstanceOf[Array[Object]]
      val toIndex = math.min(FiberRuntime.StackIdxGcThreshold, stack.length)

      if (fromIndex < toIndex && (stack(fromIndex) ne null)) {
        java.util.Arrays.fill(stack, fromIndex, toIndex, null)
      }
    }
  }

  private def processNewInterruptSignal(cause: Cause[Nothing]): Unit = {
    self.addInterruptedCause(cause)
    self.sendInterruptSignalToAllChildren(_children)

    val k = self._asyncContWith
    self._asyncContWith = AsyncContWith.`null`

    val callback = k.callback

    if (callback eq null) return

    k.onInterrupt match {
      case null => callback.completeCause(cause)

      case sync: Sync[Any] =>
        if (callback.completeCause(cause)) {
          updateLastTrace(sync.trace)
          try {
            sync.eval()
          } catch {
            case ex if nonFatal(ex) => addInterruptedCause(Cause.die(ex))
            case fatal              => handleFatalError(fatal)
          }
        }

      case onInterrupt =>
        val f = onInterrupt.foldCauseZIO(
          c => {
            addInterruptedCause(c.asInstanceOf[Cause[Nothing]])
            FiberRuntime.enableInterruptionAfterAsync
          },
          _ => FiberRuntime.enableInterruptionAfterAsync
        )(Trace.empty)

        if (callback.completeZIO(f))
          patchRuntimeFlagsOnly(RuntimeFlags.disableInterruption)
    }
  }

  @inline
  private def pushStackFrame(k: Continuation, stackIndex: Int): Int = {
    val newSize = stackIndex + 1

    ensureStackCapacity(newSize)

    _stack(stackIndex) = k
    _stackSize = newSize

    newSize
  }

  private def removeChild(child: FiberRuntime[_, _]): Unit = {
    val children = _children
    if (children ne null) {
      children.remove(child)
      ()
    }
  }

  private[zio] def removeObserver(observer: Exit[E, A] => Unit): Unit =
    observers = observers.filter(_ ne observer)

  private[this] def ignoreFlagsUpdate(update: RuntimeFlags.Patch, stackIndex: Int) = {
    def isInterruptionDisabledInNextFrame(stackIndex: Int) = {
      assert(DisableAssertions || stackIndex == _stackSize)
      _stack(stackIndex - 1) match {
        case v: UpdateRuntimeFlags => v.update == RuntimeFlags.disableInterruption
        case _                     => false
      }
    }

    (
      update == RuntimeFlags.enableInterruption
      && stackIndex > 0
      && isInterruptionDisabledInNextFrame(stackIndex)
    )
  }

  private def runLoop(
    effect: ZIO.Erased,
    minStackIndex: Int,
    startStackIndex: Int,
    currentDepth: Int,
    currentOps: Int
  ): Exit[Any, Any] = {
    assert(DisableAssertions || running.get)

    var cur        = effect
    var ops        = currentOps
    var stackIndex = startStackIndex

    if (currentDepth >= FiberRuntime.MaxDepthBeforeTrampoline) {
      inbox.offer(FiberMessage.Resume(effect))

      return null
    }

    while (true) {
      if (RuntimeFlags.opSupervision(_runtimeFlags)) {
        self.getSupervisor().onEffect(self, cur)(Unsafe)
      }

      cur = drainQueueWhileRunning(cur)

      ops += 1

      if (ops > FiberRuntime.MaxOperationsBeforeYield && RuntimeFlags.cooperativeYielding(_runtimeFlags)) {
        updateLastTrace(cur.trace)
        inbox.offer(FiberMessage.Resume(cur))

        return null
      } else {
        try {
          cur match {
            case success: Exit.Success[Any] =>
              var value = success.value

              cur = null

              while ((cur eq null) && stackIndex > minStackIndex) {
                stackIndex -= 1

                val continuation = _stack(stackIndex)

                popStackFrame(stackIndex)

                continuation match {
                  case flatMap: ZIO.FlatMap[Any, Any, Any, Any] =>
                    cur = flatMap.successK(value)

                  case foldZIO: ZIO.FoldZIO[Any, Any, Any, Any, Any] =>
                    cur = foldZIO.successK(value)

                  case map: ZIO.Mapped[Any, Any, Any, Any] =>
                    value = map.successK(value)

                  case update =>
                    val updateFlags = update.asInstanceOf[ZIO.UpdateRuntimeFlags]
                    if (!ignoreFlagsUpdate(updateFlags.update, stackIndex)) {
                      cur = patchRuntimeFlags(updateFlags.update, null, null)
                    }
                }
              }

              if (cur eq null) {
                return {
                  if (success.value.asInstanceOf[AnyRef] eq value.asInstanceOf[AnyRef]) success
                  else Exit.succeed(value)
                }
              }

            case sync: Sync[Any] =>
              updateLastTrace(sync.trace)
              var value = sync.eval()

              cur = null

              while ((cur eq null) && stackIndex > minStackIndex) {
                stackIndex -= 1

                val continuation = _stack(stackIndex)

                popStackFrame(stackIndex)

                continuation match {
                  case flatMap: ZIO.FlatMap[Any, Any, Any, Any] =>
                    cur = flatMap.successK(value)

                  case foldZIO: ZIO.FoldZIO[Any, Any, Any, Any, Any] =>
                    cur = foldZIO.successK(value)

                  case map: ZIO.Mapped[Any, Any, Any, Any] =>
                    value = map.successK(value)

                  case update =>
                    val updateFlags = update.asInstanceOf[ZIO.UpdateRuntimeFlags]
                    if (!ignoreFlagsUpdate(updateFlags.update, stackIndex)) {
                      cur = patchRuntimeFlags(updateFlags.update, null, null)
                    }
                }
              }

              if (cur eq null) {
                return Exit.succeed(value)
              }

            case flatmap: FlatMap[Any, Any, Any, Any] =>
              updateLastTrace(flatmap.trace)

              val first = flatmap.first

              if (first eq ZIO.unit) cur = flatmap.successK(())
              else {
                stackIndex = pushStackFrame(flatmap, stackIndex)
                cur = first
              }

            case fold: FoldZIO[Any, Any, Any, Any, Any] =>
              updateLastTrace(fold.trace)

              stackIndex = pushStackFrame(fold, stackIndex)
              cur = fold.first

            case map: Mapped[Any, Any, Any, Any] =>
              updateLastTrace(map.trace)

              stackIndex = pushStackFrame(map, stackIndex)
              cur = map.first

            case stateful: Stateful[Any, Any, Any] =>
              val trace = stateful.trace
              updateLastTrace(trace)

              cur = stateful.onState(
                self.asInstanceOf[FiberRuntime[Any, Any]],
                Fiber.Status.Running(_runtimeFlags, trace)
              )

            case async: Async[Any, Any, Any] =>
              updateLastTrace(async.trace)
              cur = initiateAsync(async.registerCallback)

              if (cur eq null) {
                cur = drainQueueAfterAsync()
              }

              if (cur eq null) {
                self._blockingOn = async.blockingOn
                return null
              }

              self._asyncContWith = AsyncContWith.`null`

              if (shouldInterrupt()) {
                cur = Exit.failCause(getInterruptedCause())
              }

            case update0: UpdateRuntimeFlagsWithin.DynamicNoBox[Any, Any, Any] =>
              val trace = update0.trace
              updateLastTrace(trace)
              val updateFlags     = update0.update
              val oldRuntimeFlags = _runtimeFlags
              val newRuntimeFlags = RuntimeFlags.patch(updateFlags)(oldRuntimeFlags)

              if (oldRuntimeFlags == newRuntimeFlags) {
                cur = update0.f(oldRuntimeFlags)
              } else if (RuntimeFlags.interruptible(newRuntimeFlags) && isInterrupted()) {
                cur = Exit.Failure(getInterruptedCause())
              } else {
                patchRuntimeFlagsOnly(updateFlags)
                val revertFlags = RuntimeFlags.diff(newRuntimeFlags, oldRuntimeFlags)

                val k = ZIO.UpdateRuntimeFlags(trace, revertFlags)

                stackIndex = pushStackFrame(k, stackIndex)
                cur = update0.f(oldRuntimeFlags)
              }

            case iterate: WhileLoop[Any, Any, Any] =>
              updateLastTrace(iterate.trace)

              val check = iterate.check

              stackIndex = pushStackFrame(iterate.k, stackIndex)

              val nextDepth = currentDepth + 1

              cur = null

              while ((cur eq null) && check()) {
                runLoop(iterate.body(), stackIndex, stackIndex, nextDepth, ops) match {
                  case s: Success[Any] =>
                    iterate.process(s.value)
                  case null =>
                    return null
                  case failure =>
                    cur = failure
                }
                ops += 1
              }

              stackIndex -= 1
              popStackFrame(stackIndex)

              if (cur eq null) cur = Exit.unit

            case yieldNow: ZIO.YieldNow =>
              updateLastTrace(yieldNow.trace)
              inbox.offer(FiberMessage.resumeUnit)
              return null

            case failure: Exit.Failure[Any] =>
              var cause = failure.cause

              cur = null

              while ((cur eq null) && stackIndex > minStackIndex) {
                stackIndex -= 1

                val continuation = _stack(stackIndex)

                popStackFrame(stackIndex)

                continuation match {
                  case foldZIO: ZIO.FoldZIO[Any, Any, Any, Any, Any] =>
                    if (shouldInterrupt()) {
                      cause = cause.stripFailures
                    } else {
                      cur = foldZIO.failureK(cause)
                    }

                  case updateFlags: ZIO.UpdateRuntimeFlags if !ignoreFlagsUpdate(updateFlags.update, stackIndex) =>
                    cause = patchRuntimeFlagsCause(updateFlags.update, cause)

                  case _ => ()
                }
              }

              if (cur eq null) {
                val f =
                  if (cause eq failure.cause) failure
                  else Exit.Failure(cause)
                return f
              }

            case updateRuntimeFlags: UpdateRuntimeFlags =>
              updateLastTrace(updateRuntimeFlags.trace)
              cur = patchRuntimeFlags(updateRuntimeFlags.update, null, Exit.unit)

            case effect =>
              throw new MatchError(effect)
          }
        } catch {
          case interruptedException: InterruptedException =>
            updateLastTrace(cur.trace)
            cur = drainQueueWhileRunning(Exit.Failure(Cause.interrupt(FiberId.None) ++ Cause.die(interruptedException)))
        }
      }
    }

    assert(DisableAssertions, "runLoop must exit with a return statement from within the while loop.")
    null
  }

  private def sendInterruptSignalToAllChildren(
    children: JavaSet[Fiber.Runtime[_, _]]
  ): Boolean =
    if ((children eq null) || children.isEmpty) false
    else {
      val iterator = children.iterator()
      var told     = false
      val cause    = Cause.interrupt(fiberId)

      while (iterator.hasNext) {
        val next = iterator.next()

        if ((next ne null) && next.isAlive()) {
          next.tellInterrupt(cause)

          told = true
        }
      }

      told
    }

  private def setExitValue(e: Exit[E, A]): Unit = {
    _exitValue = e

    val runtimeMetricsEnabled = RuntimeFlags.runtimeMetrics(_runtimeFlags)

    if (runtimeMetricsEnabled) {
      val startTimeMillis = fiberId.startTimeMillis
      val endTimeMillis   = java.lang.System.currentTimeMillis()
      val lifetime        = (endTimeMillis - startTimeMillis) / 1000.0

      val tags = getFiberRef(FiberRef.currentTags)
      Metric.runtime.fiberLifetimes.unsafe.update(lifetime, tags)(Unsafe)
    }

    e match {
      case f: Exit.Failure[Any] =>
        try {
          val cause = f.cause
          if (!cause.isInterruptedOnly) {
            log(
              () => s"Fiber ${fiberId.threadName} did not handle an error",
              cause,
              getFiberRef(FiberRef.unhandledErrorLogLevel),
              id.location
            )
          }

          if (runtimeMetricsEnabled) {
            val filteredCause = cause.filter(_.traces.exists(_.fiberId eq fiberId))
            if (!filteredCause.isEmpty) {
              val tags = getFiberRef(FiberRef.currentTags)
              Metric.runtime.fiberFailures.unsafe.update(1, tags)(Unsafe)
              filteredCause.foldContext(tags)(FiberRuntime.fiberFailureTracker)
            }
          }
        } catch {
          case ex if nonFatal(ex) =>
            println("An exception was thrown by a logger:")
            ex.printStackTrace()
          case fatal => handleFatalError(fatal)
        }
      case _ =>
        if (runtimeMetricsEnabled) {
          val tags = getFiberRef(FiberRef.currentTags)
          Metric.runtime.fiberSuccesses.unsafe.update(1, tags)(Unsafe)
        }
    }

    val obs = observers
    if (obs ne Nil) {
      val it = obs.reverseIterator
      while (it.hasNext) {
        it.next().apply(e)
      }

      observers = Nil
    }
  }

  private[zio] def setFiberRef[@specialized(SpecializeInt) A](fiberRef: FiberRef[A], value: A): Unit =
    _fiberRefs = _fiberRefs.updatedAs(fiberId)(fiberRef, value)

  private[zio] def resetFiberRef(fiberRef: FiberRef[?]): Unit =
    _fiberRefs = _fiberRefs.delete(fiberRef)

  private[zio] def setFiberRefs(fiberRefs0: FiberRefs): Unit =
    this._fiberRefs = fiberRefs0

  private[zio] def shouldInterrupt(): Boolean = isInterruptible() && isInterrupted()

  private[zio] def start[R](effect: ZIO[R, E, A]): Exit[E, A] = {
    var result = null.asInstanceOf[Exit[E, A]]
    if (running.compareAndSet(false, true)) {
      var previousFiber = null.asInstanceOf[Fiber.Runtime[_, _]]
      try {
        if (RuntimeFlags.currentFiber(_runtimeFlags)) {
          previousFiber = Fiber._currentFiber.get()
          Fiber._currentFiber.set(self)
        }

        result = evaluateEffect(0, effect.asInstanceOf[ZIO.Erased])
      } finally {
        if ((previousFiber ne null) || RuntimeFlags.currentFiber(_runtimeFlags)) Fiber._currentFiber.set(previousFiber)

        running.set(false)

        if (!inbox.isEmpty && running.compareAndSet(false, true)) {
          drainQueueLaterOnExecutor(result eq null)
        }
      }
    } else {
      tell(FiberMessage.Resume(effect))
    }
    result
  }

  private[zio] def startConcurrently(effect: ZIO[_, E, A]): Unit =
    tell(FiberMessage.Resume(effect))

  private[zio] def startSuspended()(implicit unsafe: Unsafe): ZIO[_, E, A] => Any = {
    val callback = new AsyncContWith.Callback(self)

    self._asyncContWith = AsyncContWith(callback)
    self._blockingOn = FiberRuntime.notBlockingOn

    callback.asInstanceOf[ZIO[_, E, A] => Any]
  }

  private[zio] def tell(message: FiberMessage): Unit = {
    inbox.offer(message)

    if (running.compareAndSet(false, true)) drainQueueLaterOnExecutor(false)
  }

  private[zio] def tellAddChild(child: Fiber.Runtime[_, _]): Unit =
    tell(FiberMessage.Stateful(parentFiber => parentFiber.addChild(child)))

  private[zio] def tellAddChildren(children: Iterable[Fiber.Runtime[_, _]]): Unit =
    tell(FiberMessage.Stateful(parentFiber => parentFiber.addChildren(children)))

  private[zio] def tellInterrupt(cause: Cause[Nothing]): Unit =
    tell(FiberMessage.InterruptSignal(cause))

  private[zio] def transferChildren(scope: FiberScope): Unit = {
    val children = _children
    if ((children ne null) && !children.isEmpty) {
      val childs = childrenChunk(children)
      _children = null

      if (!childs.isEmpty) {
        val flags = _runtimeFlags
        scope.addAll(self, flags, childs)(location, Unsafe)
      }
    }
  }

  private[zio] def updateFiberRef[A](fiberRef: FiberRef[A])(f: A => A): Unit =
    setFiberRef(fiberRef, f(getFiberRef(fiberRef)))

  private def updateLastTrace(newTrace: Trace): Unit =
    if ((newTrace ne null) && (newTrace ne emptyTrace) && (_lastTrace ne newTrace)) _lastTrace = newTrace

  def unsafe: UnsafeAPI =
    new UnsafeAPI {
      def addObserver(observer: Exit[E, A] => Unit)(implicit unsafe: Unsafe): Unit = {
        val exitValue = self._exitValue
        if (exitValue ne null) observer(exitValue)
        else self.tell(FiberMessage.Stateful(_.asInstanceOf[FiberRuntime[E, A]].addObserver(observer)))
      }

      def deleteFiberRef(ref: FiberRef[_])(implicit unsafe: Unsafe): Unit =
        self.tell(FiberMessage.Stateful(_.deleteFiberRef(ref)))

      def getFiberRefs()(implicit unsafe: Unsafe): FiberRefs =
        self.getFiberRefs()

      def removeObserver(observer: Exit[E, A] => Unit)(implicit unsafe: Unsafe): Unit =
        if (self._exitValue eq null)
          self.tell(FiberMessage.Stateful(_.asInstanceOf[FiberRuntime[E, A]].removeObserver(observer)))

      def poll(implicit unsafe: Unsafe): Option[Exit[E, A]] =
        Option(self.exitValue())

      override def interrupt(cause: Cause[Nothing])(implicit unsafe: Unsafe): Unit =
        self.tellInterrupt(cause)
    }

  private[this] val _hashCode: Int = fiberId.hashCode()

  override def hashCode(): Int = _hashCode
}

object FiberRuntime {
  private val emptyTrace = Trace.empty

  private final val MaxForksBeforeYield      = 128
  private final val MaxOperationsBeforeYield = 1024 * 10
  private final val MaxDepthBeforeTrampoline = 300

  private final val InitialStackSize    = 16
  private final val StackIdxGcThreshold = 128

  private final val DisableAssertions = BuildInfo.optimizationsEnabled

  private type EvaluationSignal = Int
  private object EvaluationSignal {
    final val Continue = 1
    final val YieldNow = 2
    final val Done     = 3
  }

  import java.util.concurrent.atomic.AtomicBoolean

  def apply[E, A](fiberId: FiberId.Runtime, fiberRefs: FiberRefs, runtimeFlags: RuntimeFlags): FiberRuntime[E, A] =
    new FiberRuntime(fiberId, fiberRefs, runtimeFlags)

  private[zio] val catastrophicFailure: AtomicBoolean = new AtomicBoolean(false)

  private val fiberFailureTracker: Cause.Folder[Set[MetricLabel], Any, Unit] =
    new Cause.Folder[Set[MetricLabel], Any, Unit] {
      def empty(context: Set[MetricLabel]): Unit = ()
      def failCase(context: Set[MetricLabel], error: Any, stackTrace: StackTrace): Unit =
        Metric.runtime.fiberFailureCauses.unsafe.update(error.getClass.getName, context)(Unsafe)

      def dieCase(context: Set[MetricLabel], t: Throwable, stackTrace: StackTrace): Unit =
        Metric.runtime.fiberFailureCauses.unsafe.update(t.getClass.getName, context)(Unsafe)

      def interruptCase(context: Set[MetricLabel], fiberId: FiberId, stackTrace: StackTrace): Unit = ()
      def bothCase(context: Set[MetricLabel], left: Unit, right: Unit): Unit                       = ()
      def thenCase(context: Set[MetricLabel], left: Unit, right: Unit): Unit                       = ()
      def stacklessCase(context: Set[MetricLabel], value: Unit, stackless: Boolean): Unit          = ()
    }

  private def patchExcludeNonInheritable(patch: RuntimeFlags.Patch): RuntimeFlags.Patch =
    RuntimeFlags.Patch.exclude(
      RuntimeFlags.Patch.exclude(patch, RuntimeFlag.Interruption.notMask),
      RuntimeFlag.WindDown.notMask
    )

  private def excludeNonInheritable(flags: RuntimeFlags): RuntimeFlags =
    RuntimeFlags.patch(inheritableFlagsPatch)(flags)

  private[this] val inheritableFlagsPatch: RuntimeFlags.Patch =
    RuntimeFlags.Patch.both(
      RuntimeFlags.disable(RuntimeFlag.Interruption),
      RuntimeFlags.disable(RuntimeFlag.WindDown)
    )

  private val enableInterruptionAfterAsync: ZIO.Erased =
    ZIO.UpdateRuntimeFlagsWithin.DynamicNoBox[Any, Any, Any](
      Trace.empty,
      RuntimeFlags.enableInterruption,
      _ => Exit.unit
    )

  private val notBlockingOn: () => FiberId = () => FiberId.None

  private val stackTraceBuilderPool: ThreadLocal[StackTraceBuilder] = new ThreadLocal[StackTraceBuilder] {
    override def initialValue(): StackTraceBuilder = StackTraceBuilder.make()(Unsafe)
  }

  private val syncResumptionErrorMessage = () =>
    "Async operation attempted synchronous resumption, but its callback was already invoked; synchronous value will be discarded"

  private class AsyncContWith private (private val value: AnyRef) extends AnyVal {
    import AsyncContWith.Callback

    def callback: Callback = value match {
      case null             => null
      case x: Callback      => x
      case x: (Callback, ?) => x._1
    }

    def onInterrupt: ZIO.Erased = value match {
      case x: (?, ZIO.Erased) => x._2
      case _                  => null
    }
  }

  private object AsyncContWith {

    final class Callback(fiber: FiberRuntime[?, ?]) extends AtomicBoolean(false) with (ZIO.Erased => Unit) {

      def apply(effect: ZIO.Erased): Unit =
        completeZIO(effect)

      def completeZIO(effect: ZIO.Erased): Boolean =
        if (compareAndSet(false, true)) {
          fiber.tell(FiberMessage.Resume(effect))
          true
        } else {
          false
        }

      def completeCause(cause: Cause[Nothing]): Boolean =
        if (compareAndSet(false, true)) {
          fiber.tell(FiberMessage.Resume(Exit.Failure(cause)))
          true
        } else {
          false
        }
    }

    @inline def `null`: AsyncContWith =
      new AsyncContWith(null)

    @inline def apply(callback: Callback): AsyncContWith =
      new AsyncContWith(callback)

    def apply(callback: Callback, onInterrupt: ZIO.Erased): AsyncContWith =
      new AsyncContWith((callback, onInterrupt))
  }

}