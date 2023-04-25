package zio.stream.internal

import zio._
import zio.internal._
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stream._

import scala.annotation.tailrec

final class ChannelFiberRuntime[-InErr, -InElem, -InDone, +OutErr, +OutElem, +OutDone](
  channel: ZChannel[Any, InErr, InElem, InDone, OutErr, OutElem, OutDone],
  initialScope: Option[Scope],
  environment: ZEnvironment[Any],
  fiberRefs0: FiberRefs,
  runtimeFlags0: RuntimeFlags,
  runtime: Runtime[Any],
  val fiberId: FiberId.Runtime,
  grafter: ZIO.Grafter
) extends ChannelFiber.Runtime.Internal[InErr, InElem, InDone, OutErr, OutElem, OutDone]
    with FiberRunnable { self =>

  import ChannelFiberRuntime.EvaluationSignal

  private[this] var currentChannel      = channel.asInstanceOf[ZChannel[Any, Any, Any, Any, Any, Any, Any]]
  private[this] var exit                = null.asInstanceOf[Exit[OutErr, OutDone]]
  private[this] var doneStack           = Stack[ZChannel.Fold[Any, Any, Any, Any, Any, Any, Any, Any, Any]]()
  private[this] var upstream            = Stack[Upstream]()
  private[this] var downstream          = Stack[Downstream]()
  private[this] val inbox               = new java.util.concurrent.ConcurrentLinkedQueue[ChannelFiberMessage]
  private[this] val running             = new java.util.concurrent.atomic.AtomicBoolean(false)
  private[this] var observers           = Set.empty[Exit[OutErr, OutDone] => Unit]
  private[this] var readers             = scala.collection.immutable.Queue.empty[ChannelFiberMessage.Read]
  private[this] var writers             = scala.collection.immutable.Queue.empty[ChannelFiberMessage.Write]
  private[this] var reads               = Set.empty[() => Unit]
  private[this] var lastAsyncEpoch      = 0L
  private[this] var currentAsyncEpoch   = 0L
  private[this] val asyncEpochs         = scala.collection.mutable.Map.empty[Long, AsyncState[OutErr, OutDone]]
  private[this] val id0                 = ChannelFiberRuntime.channelFiberIdCounter.getAndIncrement()
  private[this] var currentFiberRefs    = fiberRefs0
  private[this] var currentRuntimeFlags = runtimeFlags0
  private[this] var asyncInterruptor    = null.asInstanceOf[ZChannel[Any, Any, Any, Any, Any, Any, Any] => Unit]
  private[this] var internalScope = Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe.run(Scope.make(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()
  }
  private[this] var currentScope          = internalScope
  private var runningExecutor             = null.asInstanceOf[Executor]
  private[this] var suspendedOnAsync      = false
  private[this] var suspendedOnDownstream = true
  private[this] var winddown              = false

  def run(depth: Int): Unit =
    drainQueueOnCurrentThread()

  def run(): Unit =
    run(0)

  private def suspended =
    suspendedOnAsync || suspendedOnDownstream

  private[this] final case class AsyncState[OutErr, OutDone](
    currentChannel: ZChannel[Any, Any, Any, Any, Any, Any, Any],
    doneStack: Stack[ZChannel.Fold[Any, Any, Any, Any, Any, Any, Any, Any, Any]],
    upstream: Stack[Upstream],
    downstream: Stack[Downstream],
    exit: Exit[OutErr, OutDone],
    readers: scala.collection.immutable.Queue[ChannelFiberMessage.Read],
    observers: Set[Exit[OutErr, OutDone] => Unit]
  )

  final case class Upstream(
    channel: ZChannel[Any, Any, Any, Any, Any, Any, Any],
    doneStack: Stack[ZChannel.Fold[Any, Any, Any, Any, Any, Any, Any, Any, Any]],
    scope: Scope.Closeable
  )
  final case class Downstream(
    write: Either[Any, Exit[Any, Any]] => ZChannel[Any, Any, Any, Any, Any, Any, Any],
    doneStack: Stack[ZChannel.Fold[Any, Any, Any, Any, Any, Any, Any, Any, Any]],
    scope: Scope.Closeable
  )

  def children(implicit trace: Trace): UIO[Chunk[Fiber.Runtime[_, _]]] =
    ZIO.succeed(Chunk.empty)

  def id: FiberId.Runtime =
    fiberId

  def inheritAll(implicit trace: Trace): UIO[Unit] =
    ZIO.unit

  def interruptAsFork(fiberId: FiberId)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed(unsafeInterruptAsFork(fiberId))

  def poll(implicit trace: Trace): UIO[Option[Exit[OutErr, OutDone]]] =
    ZIO.succeed(Option(exit))

  private[zio] def addChild(child: Fiber.Runtime[_, _])(implicit unsafe: Unsafe): Unit =
    ()

  private[zio] def deleteFiberRef(ref: FiberRef[_])(implicit unsafe: Unsafe): Unit =
    currentFiberRefs = currentFiberRefs.delete(ref)

  def fiberRefs(implicit trace: Trace): UIO[FiberRefs] =
    ask((fiber, _) => fiber.getFiberRefs()(Unsafe.unsafe))

  private[zio] def getFiberRefs()(implicit unsafe: Unsafe): FiberRefs =
    currentFiberRefs

  private[zio] def isAlive()(implicit unsafe: Unsafe): Boolean =
    exit eq null

  def location: Trace =
    Trace.empty

  private[zio] def log(message: () => String, cause: Cause[Any], overrideLogLevel: Option[LogLevel], trace: Trace)(
    implicit unsafe: Unsafe
  ): Unit = {
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

  private[zio] def getLoggers()(implicit unsafe: Unsafe): Set[ZLogger[String, Any]] =
    getFiberRef(FiberRef.currentLoggers)

  def runtimeFlags(implicit trace: Trace): UIO[RuntimeFlags] =
    ask((fiber, _) => fiber.getRuntimeFlags(Unsafe.unsafe))

  private[zio] def scope: _root_.zio.internal.FiberScope =
    _root_.zio.internal.FiberScope.global

  private[zio] def setFiberRefs(fiberRefs: FiberRefs)(implicit unsafe: Unsafe): Unit =
    currentFiberRefs = fiberRefs

  def status(implicit trace: Trace): UIO[Fiber.Status] =
    ask((_, status) => status)

  private[zio] def tellAddChild(child: Fiber.Runtime[_, _])(implicit unsafe: Unsafe): Unit =
    ()

  private[zio] def tellInterrupt(cause: Cause[Nothing])(implicit unsafe: Unsafe): Unit =
    offerToInbox(ChannelFiberMessage.Interrupt(cause))

  def trace(implicit trace: Trace): UIO[StackTrace] =
    ZIO.succeed(StackTrace.none)

  def unsafe: UnsafeAPI =
    new UnsafeAPI {
      def addObserver(observer: Exit[OutErr, OutDone] => Unit)(implicit unsafe: Unsafe): Unit =
        self.addObserver(observer)

      def deleteFiberRef(ref: FiberRef[_])(implicit unsafe: Unsafe): Unit =
        self.deleteFiberRef(ref)

      def getFiberRefs()(implicit unsafe: Unsafe): FiberRefs =
        self.getFiberRefs()

      def removeObserver(observer: Exit[OutErr, OutDone] => Unit)(implicit unsafe: Unsafe): Unit =
        self.removeObserver(observer)
    }

  def addObserver(observer: Exit[OutErr, OutDone] => Unit): Unit =
    addObserver(observer, 0L)

  def addObserver(observer: Exit[OutErr, OutDone] => Unit, epoch: Long): Unit =
    offerToInbox(ChannelFiberMessage.AddObserver(observer.asInstanceOf[Exit[Any, Any] => Unit], epoch))

  def removeObserver(observer: Exit[OutErr, OutDone] => Unit): Unit =
    removeObserver(observer, 0L)

  def removeObserver(observer: Exit[OutErr, OutDone] => Unit, epoch: Long): Unit =
    offerToInbox(ChannelFiberMessage.RemoveObserver(observer.asInstanceOf[Exit[Any, Any] => Unit], epoch))

  private[stream] def unsafeInterruptAsFork(fiberId: FiberId): Unit =
    offerToInbox(ChannelFiberMessage.Interrupt(Cause.interrupt(fiberId)))

  def await(implicit trace: Trace): ZIO[Any, Nothing, Exit[OutErr, OutDone]] =
    ZIO.async(addObserver).exit

  private[stream] def read(
    epoch: Long
  )(implicit trace: Trace): ZChannel[Any, Any, Any, Any, OutErr, Nothing, Either[OutDone, OutElem]] =
    ZChannel.asyncOne { cb =>
      unsafeRead(
        err => cb(ZChannel.failCause(err)),
        elem => cb(ZChannel.succeed(Right(elem))),
        done => cb(ZChannel.succeed(Left(done))),
        epoch
      )
    }

  private[stream] def readZIO(epoch: Long)(implicit trace: Trace): ZIO[Any, OutErr, Either[OutDone, OutElem]] =
    ZIO.async { cb =>
      unsafeRead(
        err => cb(ZIO.failCause(err)),
        elem => cb(ZIO.succeed(Right(elem))),
        done => cb(ZIO.succeed(Left(done))),
        epoch
      )
    }

  def readZIO(implicit trace: Trace): ZIO[Any, OutErr, Either[OutDone, OutElem]] =
    readZIO(0L)

  def writeDone(done: InDone)(implicit trace: Trace): ZIO[Any, Nothing, Unit] =
    ZIO.succeed(unsafeWriteDone(done))

  def writeElem(elem: InElem)(implicit trace: Trace): ZIO[Any, Nothing, Unit] =
    ZIO.succeed(unsafeWriteElem(elem))

  def writeErr(err: Cause[InErr])(implicit trace: Trace): ZIO[Any, Nothing, Unit] =
    ZIO.succeed(unsafeWriteErr(err))

  def start(): Unit =
    offerToInbox(ChannelFiberMessage.Start)

  private def unsafeRead(onErr: Cause[OutErr] => Unit, onElem: OutElem => Unit, onDone: OutDone => Unit): Unit =
    unsafeRead(onErr, onElem, onDone, 0L)

  private[stream] def unsafeRead(
    onErr: Cause[OutErr] => Unit,
    onElem: OutElem => Unit,
    onDone: OutDone => Unit,
    epoch: Long
  ): Unit = {
    val message =
      ChannelFiberMessage.Read(
        onErr.asInstanceOf[Cause[Any] => Unit],
        onElem.asInstanceOf[Any => Unit],
        onDone.asInstanceOf[Any => Unit],
        epoch
      )
    offerToInbox(message)
  }

  private[stream] def unsafeWriteDone(done: InDone): Unit =
    offerToInbox(ChannelFiberMessage.WriteDone(done.asInstanceOf[Any]))

  private[stream] def unsafeWriteElem(elem: InElem): Unit =
    offerToInbox(ChannelFiberMessage.WriteElem(elem.asInstanceOf[Any], null))

  private[stream] def unsafeWriteErr(err: Cause[InErr]): Unit =
    offerToInbox(ChannelFiberMessage.WriteErr(err.asInstanceOf[Cause[Any]]))

  private[stream] def unsafeWriteElem(elem: InElem, onRead: => Unit): Unit =
    offerToInbox(ChannelFiberMessage.WriteElem(elem.asInstanceOf[Any], () => onRead))

  private def offerToInbox(message: ChannelFiberMessage): Unit = {
    inbox.offer(message)
    if (running.compareAndSet(false, true)) {
      drainQueueLaterOnExecutor()
    }
  }

  private def processMessageFromQueue(message: ChannelFiberMessage): EvaluationSignal = {
    message match {
      case ChannelFiberMessage.AddObserver(observer, epoch) =>
        if (epoch == currentAsyncEpoch) {
          if (exit ne null) {
            observer(exit)
          } else {
            observers += observer
          }
        } else {
          val oldAsyncEpoch = currentAsyncEpoch
          val oldAsyncState = AsyncState(currentChannel, doneStack, upstream, downstream, exit, readers, observers)
          asyncEpochs.put(currentAsyncEpoch, oldAsyncState)
          val asyncState = asyncEpochs(epoch)
          currentChannel = asyncState.currentChannel
          doneStack = asyncState.doneStack
          upstream = asyncState.upstream
          downstream = asyncState.downstream
          exit = asyncState.exit
          readers = asyncState.readers
          observers = asyncState.observers
          currentAsyncEpoch = epoch
          if (exit ne null) {
            observer(exit)
          } else {
            observers += observer
          }
          val newAsyncState = AsyncState(currentChannel, doneStack, upstream, downstream, exit, readers, observers)
          asyncEpochs.put(currentAsyncEpoch, newAsyncState)
          currentChannel = oldAsyncState.currentChannel
          doneStack = oldAsyncState.doneStack
          upstream = oldAsyncState.upstream
          downstream = oldAsyncState.downstream
          exit = oldAsyncState.exit
          readers = oldAsyncState.readers
          observers = oldAsyncState.observers
          currentAsyncEpoch = oldAsyncEpoch
        }
        EvaluationSignal.Continue
      case ChannelFiberMessage.RemoveObserver(observer, epoch) =>
        if (epoch == currentAsyncEpoch) {
          if (exit ne null) {
            ()
          } else {
            observers -= observer
          }
        } else {
          val oldAsyncEpoch = currentAsyncEpoch
          val oldAsyncState = AsyncState(currentChannel, doneStack, upstream, downstream, exit, readers, observers)
          asyncEpochs.put(currentAsyncEpoch, oldAsyncState)
          val asyncState = asyncEpochs(epoch)
          currentChannel = asyncState.currentChannel
          doneStack = asyncState.doneStack
          upstream = asyncState.upstream
          downstream = asyncState.downstream
          exit = asyncState.exit
          readers = asyncState.readers
          observers = asyncState.observers
          currentAsyncEpoch = epoch
          if (exit ne null) {
            ()
          } else {
            observers -= observer
          }
          val newAsyncState = AsyncState(currentChannel, doneStack, upstream, downstream, exit, readers, observers)
          asyncEpochs.put(currentAsyncEpoch, newAsyncState)
          currentChannel = oldAsyncState.currentChannel
          doneStack = oldAsyncState.doneStack
          upstream = oldAsyncState.upstream
          downstream = oldAsyncState.downstream
          exit = oldAsyncState.exit
          readers = oldAsyncState.readers
          observers = oldAsyncState.observers
          currentAsyncEpoch = oldAsyncEpoch
        }
        EvaluationSignal.Continue
      case read @ ChannelFiberMessage.Read(onErr, onElem, onDone, epoch) =>
        if (epoch == currentAsyncEpoch) {
          if (exit ne null) {
            exit.foldExit(onErr, onDone)
          } else {
            readers = readers.enqueue(read)
            // suspended = false
            runLoop()
          }
        } else {
          val oldAsyncEpoch = currentAsyncEpoch
          val oldAsyncState = AsyncState(currentChannel, doneStack, upstream, downstream, exit, readers, observers)
          asyncEpochs.put(currentAsyncEpoch, oldAsyncState)
          val asyncState = asyncEpochs(epoch)
          currentChannel = asyncState.currentChannel
          doneStack = asyncState.doneStack
          upstream = asyncState.upstream
          downstream = asyncState.downstream
          exit = asyncState.exit
          readers = asyncState.readers
          observers = asyncState.observers
          currentAsyncEpoch = epoch
          if (exit ne null) {
            exit.foldExit(onErr, onDone)
          } else {
            readers = readers.enqueue(read)
            runLoop()
          }
          val newAsyncState = AsyncState(currentChannel, doneStack, upstream, downstream, exit, readers, observers)
          asyncEpochs.put(currentAsyncEpoch, newAsyncState)
          currentChannel = oldAsyncState.currentChannel
          doneStack = oldAsyncState.doneStack
          upstream = oldAsyncState.upstream
          downstream = oldAsyncState.downstream
          exit = oldAsyncState.exit
          readers = oldAsyncState.readers
          observers = oldAsyncState.observers
          currentAsyncEpoch = oldAsyncEpoch
        }
        EvaluationSignal.Continue
      case ChannelFiberMessage.Interrupt(cause) =>
        if (exit eq null) {
          processNewInterruptSignal(cause)(Unsafe.unsafe)
          if (RuntimeFlags.interruptible(currentRuntimeFlags)) {
            currentChannel = ZChannel.refailCause(Cause.interrupt(fiberId))
            runLoop()
          }
        }
        EvaluationSignal.Continue
      case ChannelFiberMessage.Resume(channel, _, epoch) =>
        if (epoch == currentAsyncEpoch) {
          currentChannel = channel
          suspendedOnAsync = false
          runLoop()
        } else {
          val oldAsyncEpoch = currentAsyncEpoch
          val oldAsyncState = AsyncState(currentChannel, doneStack, upstream, downstream, exit, readers, observers)
          asyncEpochs.put(currentAsyncEpoch, oldAsyncState)
          val asyncState = asyncEpochs(epoch)
          currentChannel = asyncState.currentChannel
          doneStack = asyncState.doneStack
          upstream = asyncState.upstream
          downstream = asyncState.downstream
          exit = asyncState.exit
          readers = asyncState.readers
          observers = asyncState.observers
          currentAsyncEpoch = epoch
          currentChannel = channel
          suspendedOnAsync = false
          runLoop()
          val newAsyncState = AsyncState(currentChannel, doneStack, upstream, downstream, exit, readers, observers)
          asyncEpochs.put(currentAsyncEpoch, newAsyncState)
          currentChannel = oldAsyncState.currentChannel
          doneStack = oldAsyncState.doneStack
          upstream = oldAsyncState.upstream
          downstream = oldAsyncState.downstream
          exit = oldAsyncState.exit
          readers = oldAsyncState.readers
          observers = oldAsyncState.observers
          currentAsyncEpoch = oldAsyncEpoch
        }
        EvaluationSignal.Continue
      case ChannelFiberMessage.Start =>
        suspendedOnDownstream = false
        runLoop()
        EvaluationSignal.Continue
      case ChannelFiberMessage.Stateful(onFiber) =>
        if (exit ne null) processStatefulMessage(onFiber, Fiber.Status.Done)
        else if (suspended)
          processStatefulMessage(onFiber, Fiber.Status.Suspended(currentRuntimeFlags, Trace.empty, FiberId.None))
        else processStatefulMessage(onFiber, Fiber.Status.Running(currentRuntimeFlags, Trace.empty))
        EvaluationSignal.Continue
      case write: ChannelFiberMessage.Write =>
        writers = writers.enqueue(write)
        runLoop()
        EvaluationSignal.Continue
      case ChannelFiberMessage.YieldNow(fiberRefs, epoch) =>
        offerToInbox(ChannelFiberMessage.Resume(ZChannel.unit, fiberRefs, epoch))
        EvaluationSignal.YieldNow
    }
  }

  @tailrec
  private def drainQueueOnCurrentThread(): Unit = {
    var evaluationSignal: EvaluationSignal = EvaluationSignal.Continue
    while (evaluationSignal == EvaluationSignal.Continue) {
      val message = inbox.poll()
      if (message == null) {
        evaluationSignal = EvaluationSignal.Done
      } else {
        evaluationSignal = processMessageFromQueue(message)
      }
    }
    running.set(false)
    if (!inbox.isEmpty) {
      if (running.compareAndSet(false, true)) {
        if (evaluationSignal == EvaluationSignal.YieldNow) drainQueueLaterOnExecutor()
        else drainQueueOnCurrentThread()
      }
    }
  }

  def ask[A](
    f: (ChannelFiberRuntime[Nothing, Nothing, Nothing, Any, Any, Any], Fiber.Status) => A
  )(implicit trace: Trace): ZIO[Any, Nothing, A] =
    ZIO.async[Any, Nothing, A] { cb =>
      offerToInbox(ChannelFiberMessage.Stateful { (runtime, status) =>
        cb(Exit.succeed(f(runtime, status)))
      })
    }

  private def drainQueueLaterOnExecutor(): Unit = {
    runningExecutor = getCurrentExecutor()(Unsafe.unsafe)
    runningExecutor.submit(self)(Unsafe.unsafe)
  }

  private[zio] def getCurrentExecutor()(implicit unsafe: Unsafe): Executor =
    getFiberRef(FiberRef.overrideExecutor)(Unsafe.unsafe) match {
      case None        => Runtime.defaultExecutor
      case Some(value) => value
    }

  private[zio] def getRunningExecutor()(implicit unsafe: Unsafe): Option[Executor] =
    if (runningExecutor eq null) None else Some(runningExecutor)

  private[zio] def getFiberRef[A](fiberRef: FiberRef[A])(implicit unsafe: Unsafe): A =
    currentFiberRefs.getOrDefault(fiberRef)

  private[zio] def setFiberRef[A](fiberRef: FiberRef[A], value: A)(implicit unsafe: Unsafe): Unit =
    currentFiberRefs = currentFiberRefs.updatedAs(fiberId)(fiberRef, value)

  private[stream] def getRuntimeFlags(implicit unsafe: Unsafe): RuntimeFlags =
    currentRuntimeFlags

  private def addInterruptedCause(cause: Cause[Nothing])(implicit unsafe: Unsafe): Unit = {
    val interruptedCause = getFiberRef(FiberRef.interruptedCause)
    setFiberRef(FiberRef.interruptedCause, interruptedCause ++ cause)
  }

  private def isInterrupted()(implicit unsafe: Unsafe): Boolean =
    !getFiberRef(FiberRef.interruptedCause).isEmpty

  private def patchRuntimeFlags(runtimeFlags: RuntimeFlags, patch: RuntimeFlags.Patch): RuntimeFlags = {
    val newRuntimeFlags = RuntimeFlags.patch(patch)(runtimeFlags)
    currentRuntimeFlags = newRuntimeFlags
    newRuntimeFlags
  }

  private def makeConcatAllUpstream()(implicit trace: Trace): Upstream = {
    var concatAllUpstream = upstream
    lazy val channel: ZChannel[Any, Any, Any, Any, Any, Any, Any] =
      ZChannel.suspend {
        upstream = concatAllUpstream
        ZChannel.readWithCause(
          (elem: Any) => {
            concatAllUpstream = upstream
            ZChannel.write(elem) *> channel
          },
          (err: Cause[Any]) => {
            concatAllUpstream = upstream
            ZChannel.failCause(err)
          },
          (done: Any) => {
            concatAllUpstream = upstream
            ZChannel.succeedNow(done)
          }
        )
      }
    val childScope = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(currentScope.fork).getOrThrowFiberFailure()
    }
    Upstream(channel, Stack(), childScope)
  }

  private def processNewInterruptSignal(cause: Cause[Nothing])(implicit unsafe: Unsafe): Unit = {
    self.addInterruptedCause(cause)
    self.sendInterruptSignalToAllChildren()

    if (self.asyncInterruptor ne null) {
      self.asyncInterruptor(ZChannel.refailCause(cause))
    }
  }

  private def sendInterruptSignalToAllChildren()(implicit unsafe: Unsafe): Boolean =
    false

  private[zio] def getInterruptedCause()(implicit unsafe: Unsafe): Cause[Nothing] =
    getFiberRef(FiberRef.interruptedCause)

  private def processStatefulMessage(
    onFiber: (ChannelFiberRuntime[Nothing, Nothing, Nothing, Any, Any, Any], Fiber.Status) => Unit,
    status: Fiber.Status
  ): Unit =
    onFiber(self, status)

  private def initiateAsync(
    runtimeFlags: RuntimeFlags,
    asyncRegister: (ZChannel[Any, Any, Any, Any, Any, Any, Any] => Unit) => ZChannel[Any, Any, Any, Any, Any, Any, Any],
    fiberRefs: FiberRefs,
    asyncEpoch: Long
  )(implicit unsafe: Unsafe): ZChannel[Any, Any, Any, Any, Any, Any, Any] = {
    val alreadyCalled = new java.util.concurrent.atomic.AtomicBoolean(false)

    val callback = (channel: ZChannel[Any, Any, Any, Any, Any, Any, Any]) => {
      if (alreadyCalled.compareAndSet(false, true)) {
        offerToInbox(ChannelFiberMessage.Resume(channel, fiberRefs, asyncEpoch))
      }
    }

    if (RuntimeFlags.interruptible(runtimeFlags)) self.asyncInterruptor = callback

    try {
      val sync = asyncRegister(callback)

      if (sync ne null) {
        if (alreadyCalled.compareAndSet(false, true)) sync
        else {
          log(
            () =>
              s"Async operation attempted synchronous resumption, but its callback was already invoked; synchronous value will be discarded",
            Cause.empty,
            ZIO.someError,
            id.location
          )

          null.asInstanceOf[ZChannel[Any, Any, Any, Any, Any, Any, Any]]
        }
      } else null.asInstanceOf[ZChannel[Any, Any, Any, Any, Any, Any, Any]]
    } catch {
      case throwable: Throwable =>
        if (isFatal(throwable)) handleFatalError(throwable)
        else callback(ZChannel.refailCause(Cause.die(throwable)))

        null.asInstanceOf[ZChannel[Any, Any, Any, Any, Any, Any, Any]]
    }
  }

  private def handleFatalError(throwable: Throwable): Nothing = {
    FiberRuntime.catastrophicFailure.set(true)
    val errorReporter = getReportFatal()(Unsafe.unsafe)
    errorReporter(throwable)
  }

  private[zio] def getReportFatal()(implicit unsafe: Unsafe): Throwable => Nothing =
    getFiberRef(FiberRef.currentReportFatal)

  private def generateStackTrace(): StackTrace = {
    val stackTraceBuilder = StackTraceBuilder.make()(Unsafe.unsafe)
    doneStack.foreach { fold =>
      stackTraceBuilder += fold.trace
    }
    downstream.foreach { downstream =>
      downstream.doneStack.foreach { fold =>
        stackTraceBuilder += fold.trace
      }
    }
    StackTrace(self.fiberId, stackTraceBuilder.result())
  }

  private def runLoop(): Unit = {
    var loop      = currentChannel ne null
    var lastTrace = Trace.empty
    var ops       = 0
    while (loop) {
      ops += 1
      if (ops > ChannelFiberRuntime.MaxOperationsBeforeYield && RuntimeFlags.cooperativeYielding(currentRuntimeFlags)) {
        ops = 0
        val oldCurrentChannel = currentChannel
        currentChannel = ZChannel.yieldNow(Trace.empty).flatMap(_ => oldCurrentChannel)(Trace.empty)

      } else {
        val nextTrace = currentChannel.trace
        if (nextTrace ne null) lastTrace = nextTrace
        try {
          currentChannel match {
            case ZChannel.Async(_, registerCallback) =>
              val asyncEpoch = currentAsyncEpoch
              val channel = initiateAsync(
                currentRuntimeFlags,
                registerCallback,
                currentFiberRefs,
                asyncEpoch
              )(Unsafe.unsafe)
              if (channel ne null) {
                currentChannel = channel
              } else if (RuntimeFlags.interruptible(currentRuntimeFlags) && isInterrupted()(Unsafe.unsafe)) {
                currentChannel = ZChannel.refailCause(getInterruptedCause()(Unsafe.unsafe))
              } else {
                currentChannel = null
                loop = false
                suspendedOnAsync = true
              }

            case ZChannel.ConcatAll(trace0, channel, onElem, combineInner, combineOuter) =>
              implicit val trace: Trace = trace0
              val hasContinuations      = !doneStack.isEmpty
              var currentDone           = null.asInstanceOf[Any]
              val concatAllUpstream     = makeConcatAllUpstream()
              def concatAll(either: Either[Any, Exit[Any, Any]]): ZChannel[Any, Any, Any, Any, Any, Any, Any] =
                either match {
                  case Left(elem) =>
                    val parent         = upstream.pop()
                    val parentUpstream = upstream
                    upstream = Stack(concatAllUpstream)
                    val hasContinuations = parent.doneStack.nonEmpty
                    if (hasContinuations) {
                      onElem
                        .asInstanceOf[Any => ZChannel[Any, Any, Any, Any, Any, Any, Any]](elem)
                        .foldCauseChannel(
                          cause => ZChannel.failCause(cause),
                          done => {
                            if (currentDone == null) currentDone = done
                            else currentDone = combineInner.asInstanceOf[(Any, Any) => Any](currentDone, done)
                            downstream.push(Downstream(concatAll, doneStack, currentScope))
                            upstream = parentUpstream
                            currentChannel = parent.channel
                            currentScope = parent.scope
                            doneStack = parent.doneStack
                            ZChannel.succeedNow(done)
                          }
                        )
                    } else {
                      onElem.asInstanceOf[Any => ZChannel[Any, Any, Any, Any, Any, Any, Any]](elem).ensuringWith {
                        case Exit.Failure(cause) => ZIO.unit
                        case Exit.Success(done) => {
                          if (currentDone == null) currentDone = done
                          else currentDone = combineInner.asInstanceOf[(Any, Any) => Any](currentDone, done)
                          downstream.push(Downstream(concatAll, doneStack, currentScope))
                          upstream = parentUpstream
                          currentChannel = parent.channel
                          currentScope = parent.scope
                          doneStack = parent.doneStack
                          ZIO.unit
                        }
                      }
                    }
                  case Right(exit) =>
                    exit.foldExit(
                      cause => {
                        val currentUpstream = upstream.pop()
                        if (currentUpstream eq null) {
                          ZChannel.failCause(cause)
                        } else {
                          ZChannel.fromZIO(currentUpstream.scope.close(exit)) *> ZChannel.failCause(cause)
                        }
                      },
                      done => {
                        val currentUpstream = upstream.pop()
                        if (currentUpstream eq null) {
                          ZChannel.succeedNow(combineOuter.asInstanceOf[(Any, Any) => Any](currentDone, done))
                        } else {
                          ZChannel.fromZIO(currentUpstream.scope.close(Exit.unit).when(hasContinuations)) *>
                            ZChannel.succeedNow(combineOuter.asInstanceOf[(Any, Any) => Any](currentDone, done))
                        }
                      }
                    )
                }
              val childScope = Unsafe.unsafe { implicit unsafe =>
                Runtime.default.unsafe.run(currentScope.fork).getOrThrowFiberFailure()
              }
              upstream = Stack(concatAllUpstream)
              downstream.push(Downstream(concatAll, doneStack, currentScope))
              doneStack = Stack()
              currentScope = childScope
              currentChannel = channel

            case ZChannel.Emit(_, elem) =>
              val currentDownstream = downstream.pop()
              if (currentDownstream eq null) {
                if (readers.isEmpty) {
                  loop = false
                  suspendedOnDownstream = true
                } else {
                  val (read, updatedReaders) = readers.dequeue
                  readers = updatedReaders
                  read.onElem(elem)
                  currentChannel = ZChannel.unit
                  suspendedOnDownstream = true
                  loop = false
                }
              } else {
                upstream.push(
                  Upstream(
                    ZChannel.unit,
                    doneStack,
                    currentScope
                  )
                )
                doneStack = currentDownstream.doneStack
                currentScope = currentDownstream.scope
                currentChannel = currentDownstream.write(Left(elem))
              }

            case ensuring @ ZChannel.Ensuring(_, channel, finalizer) =>
              implicit val trace = ensuring.trace
              val fold           = doneStack.pop()
              if (fold eq null) {
                currentChannel = ZChannel.fromZIO(
                  currentScope.addFinalizerExit(finalizer.asInstanceOf[Exit[Any, Any] => ZIO[Any, Nothing, Any]])
                ) *> channel
              } else {
                val ref = Unsafe.unsafe { implicit unsafe =>
                  Ref.unsafe.make(true)
                }
                val finalizer0 = (exit: Exit[Any, Any]) =>
                  finalizer.asInstanceOf[Exit[Any, Any] => ZIO[Any, Nothing, Any]](exit).whenZIO(ref.getAndSet(false))
                currentChannel = ZChannel.fromZIO(currentScope.addFinalizerExit(finalizer0)) *> channel
                val updatedFold = ZChannel.Fold[Any, Any, Any, Any, Any, Any, Any, Any, Any](
                  Trace.empty,
                  ZChannel.unit,
                  cause => ZChannel.fromZIO(finalizer0(Exit.failCause(cause))) *> fold.onErr(cause),
                  done => ZChannel.fromZIO(finalizer0(Exit.succeed(done))) *> fold.onDone(done)
                )
                doneStack.push(updatedFold)

              }

            case ZChannel.Fail(cause) =>
              val fold = doneStack.pop()
              if (fold eq null) {
                val currentDownstream = downstream.pop()
                if (currentDownstream eq null) {
                  if (winddown) {
                    exit = Exit.failCause(cause.asInstanceOf[Cause[OutErr]])
                    loop = false
                  } else {
                    winddown = true
                    val failure = Exit.failCause(cause.asInstanceOf[Cause[OutErr]])
                    currentChannel = ZChannel.fromZIO {
                      initialScope.fold(internalScope.close(failure)(Trace.empty))(
                        _.addFinalizerExit(exit => internalScope.close(failure)(Trace.empty))(Trace.empty)
                      )
                    }(Trace.empty).foldCauseChannel(
                      finalizerCause => ZChannel.refailCause(cause ++ finalizerCause),
                      _ => ZChannel.refailCause(cause)
                    )(Trace.empty)
                  }
                } else {
                  upstream.push(
                    Upstream(
                      ZChannel.unit,
                      doneStack,
                      currentScope
                    )
                  )
                  doneStack = currentDownstream.doneStack
                  currentScope = currentDownstream.scope
                  currentChannel = currentDownstream.write(Right(Exit.failCause(cause)))
                }
              } else {
                currentChannel = fold.onErr(cause)
              }

            case fold @ ZChannel.Fold(_, channel, _, _) =>
              currentChannel = channel
              doneStack.push(fold.asInstanceOf[ZChannel.Fold[Any, Any, Any, Any, Any, Any, Any, Any, Any]])

            case ZChannel.FromZIO(trace, zio) =>
              zio match {

                case ZIO.Sync(trace, eval) =>
                  currentChannel = ZChannel.succeedNow(eval())(trace)

                case ZIO.Async(trace, register, blockingOn) =>
                  val asyncEpoch = currentAsyncEpoch
                  val channel = initiateAsync(
                    currentRuntimeFlags,
                    registerCallback => {
                      val adaptedRegisterCallback =
                        (zio: ZIO[Any, Any, Any]) => registerCallback(ZChannel.fromZIO(zio)(trace))
                      val zio = register(adaptedRegisterCallback)
                      if (zio eq null) null else ZChannel.fromZIO(zio)(trace)
                    },
                    currentFiberRefs,
                    asyncEpoch
                  )(Unsafe.unsafe)
                  if (channel ne null) {
                    currentChannel = channel
                  } else if (RuntimeFlags.interruptible(currentRuntimeFlags) && isInterrupted()(Unsafe.unsafe)) {
                    currentChannel = ZChannel.refailCause(getInterruptedCause()(Unsafe.unsafe))
                  } else {
                    currentChannel = null
                    loop = false
                    suspendedOnAsync = true
                  }

                case ZIO.OnSuccessAndFailure(trace, first, onSuccess, onFailure) =>
                  val channel = ZChannel.fromZIO(first)(trace)
                  currentChannel = channel
                  doneStack.push(
                    ZChannel.Fold(
                      Trace.empty,
                      channel,
                      err => ZChannel.fromZIO(onFailure.asInstanceOf[Cause[Any] => ZIO[Any, Any, Any]](err))(trace),
                      done => ZChannel.fromZIO(onSuccess.asInstanceOf[Any => ZIO[Any, Any, Any]](done))(trace)
                    )
                  )

                case ZIO.OnSuccess(trace, first, onSuccess) =>
                  val channel = ZChannel.fromZIO(first)(trace)
                  currentChannel = channel
                  doneStack.push(
                    ZChannel.Fold(
                      Trace.empty,
                      channel,
                      err => ZChannel.refailCause(err),
                      done => ZChannel.fromZIO(onSuccess.asInstanceOf[Any => ZIO[Any, Any, Any]](done))(trace)
                    )
                  )

                case ZIO.OnFailure(trace, first, onFailure) =>
                  val channel = ZChannel.fromZIO(first)(trace)
                  currentChannel = channel
                  doneStack.push(
                    ZChannel.Fold(
                      Trace.empty,
                      channel,
                      err => ZChannel.fromZIO(onFailure.asInstanceOf[Cause[Any] => ZIO[Any, Any, Any]](err))(trace),
                      done => ZChannel.succeedNow(done)(trace)
                    )
                  )

                case ZIO.UpdateRuntimeFlags(trace, update) =>
                  currentRuntimeFlags = patchRuntimeFlags(currentRuntimeFlags, update)
                  currentChannel = ZChannel.unit

                case updateRuntimeFlagsWithin: ZIO.UpdateRuntimeFlagsWithin[_, _, _] =>
                  val patch           = updateRuntimeFlagsWithin.update
                  val oldRuntimeFlags = currentRuntimeFlags
                  val newRuntimeFlags = RuntimeFlags.patch(patch)(oldRuntimeFlags)

                  if (newRuntimeFlags == oldRuntimeFlags) {
                    currentChannel = ZChannel
                      .fromZIO(updateRuntimeFlagsWithin.scope(oldRuntimeFlags))(updateRuntimeFlagsWithin.trace)
                      .asInstanceOf[ZChannel[Any, Any, Any, Any, Any, Any, Any]]
                  } else {
                    if (RuntimeFlags.interruptible(newRuntimeFlags) && isInterrupted()(Unsafe.unsafe)) {
                      currentChannel = ZChannel.refailCause(getFiberRef(FiberRef.interruptedCause)(Unsafe.unsafe))
                    } else {
                      currentRuntimeFlags = patchRuntimeFlags(currentRuntimeFlags, patch)
                      val revertFlags = RuntimeFlags.diff(newRuntimeFlags, oldRuntimeFlags)
                      currentChannel = ZChannel.fromZIO {
                        updateRuntimeFlagsWithin
                          .scope(oldRuntimeFlags)
                          .foldCauseZIO(
                            cause => {
                              currentRuntimeFlags = patchRuntimeFlags(currentRuntimeFlags, revertFlags)
                              if (RuntimeFlags.interruptible(currentRuntimeFlags) && isInterrupted()(Unsafe.unsafe)) {
                                Exit.failCause(getFiberRef(FiberRef.interruptedCause)(Unsafe.unsafe))
                              } else {
                                Exit.failCause(cause)
                              }
                            },
                            done => {
                              currentRuntimeFlags = patchRuntimeFlags(currentRuntimeFlags, revertFlags)
                              if (RuntimeFlags.interruptible(currentRuntimeFlags) && isInterrupted()(Unsafe.unsafe)) {
                                Exit.failCause(getFiberRef(FiberRef.interruptedCause)(Unsafe.unsafe))
                              } else {
                                Exit.succeed(done)
                              }
                            }
                          )(updateRuntimeFlagsWithin.trace)
                      }(updateRuntimeFlagsWithin.trace).asInstanceOf[ZChannel[Any, Any, Any, Any, Any, Any, Any]]
                    }
                  }

                case ZIO.GenerateStackTrace(trace) =>
                  currentChannel = ZChannel.GenerateStackTrace(trace)

                case ZIO.Stateful(trace, onState) =>
                  currentChannel = ZChannel.fromZIO(
                    onState.asInstanceOf[(Fiber.Runtime[Any, Any], Fiber.Status.Running) => ZIO[Any, Any, Any]](
                      self,
                      Fiber.Status.Running(currentRuntimeFlags, Trace.empty)
                    )
                  )(trace)

                case iterate @ ZIO.WhileLoop(_, check, body, process) =>
                  implicit val trace = iterate.trace
                  if (check()) {
                    currentChannel = ZChannel.fromZIO(body().map(process)) *> ZChannel.fromZIO(iterate)
                  } else {
                    currentChannel = ZChannel.unit
                  }

                case ZIO.YieldNow(trace, forceAsync) =>
                  currentChannel = null
                  loop = false
                  offerToInbox(ChannelFiberMessage.YieldNow(currentFiberRefs, currentAsyncEpoch))

                case Exit.Success(value) =>
                  currentChannel = ZChannel.succeedNow(value)(Trace.empty)

                case Exit.Failure(cause) =>
                  currentChannel = ZChannel.refailCause(cause)
              }

            case ZChannel.GenerateStackTrace(trace) =>
              val stackTrace = generateStackTrace()
              currentChannel = ZChannel.succeedNow(stackTrace)(Trace.empty)

            case mapOutZIOPar @ ZChannel.MapOutZIOPar(_, channel, n, f) =>
              implicit val trace = mapOutZIOPar.trace
              lastAsyncEpoch += 1
              val asyncEpoch      = lastAsyncEpoch
              val currentUpstream = upstream.pop()
              asyncEpochs.put(
                asyncEpoch,
                AsyncState(
                  if (currentUpstream != null) currentUpstream.channel else null,
                  if (currentUpstream != null) currentUpstream.doneStack else Stack(),
                  upstream,
                  Stack(),
                  null,
                  scala.collection.immutable.Queue.empty[ChannelFiberMessage.Read],
                  Set.empty
                )
              )
              currentChannel = ZChannel.unwrap {
                currentScope.extend {
                  for {
                    upstream <- (ZChannel.fromRuntimeFiber(self, asyncEpoch) >>> channel).zipWithIndex.forkUnstartedZIO
                    fibers <- ZIO.collectAll(
                                List.fill(n)(
                                  mapOutZIOIndexed(ZChannel.fromRuntimeFiber(upstream, 0L), f).forkUnstartedZIO
                                )
                              )
                  } yield ZChannel.mergeAllFibersOrdered(fibers)
                }
              }

            case mergeAllWith @ ZChannel.MergeAllWith(_, channels, n, bufferSize, mergeStrategy, f) =>
              implicit val trace = mergeAllWith.trace
              lastAsyncEpoch += 1
              val asyncEpoch      = lastAsyncEpoch
              val currentUpstream = upstream.pop()
              asyncEpochs.put(
                asyncEpoch,
                AsyncState(
                  if (currentUpstream != null) currentUpstream.channel else null,
                  if (currentUpstream != null) currentUpstream.doneStack else Stack(),
                  upstream,
                  Stack(),
                  null,
                  scala.collection.immutable.Queue.empty[ChannelFiberMessage.Read],
                  Set.empty
                )
              )
              currentChannel = ZChannel.unwrap {
                currentScope.extend {
                  for {
                    fibers <- (ZChannel.fromRuntimeFiber(self, asyncEpoch) >>> channels.mapOutZIO(channel =>
                                (ZChannel.fromRuntimeFiber(self, asyncEpoch) >>> channel).forkUnstartedZIO
                              )).forkScoped
                  } yield ZChannel.mergeAllFibers(fibers, n, bufferSize, mergeStrategy, f)
                }
              }

            case mergeWith @ ZChannel.MergeWith(_, left, right, leftDone, rightDone) =>
              implicit val trace = mergeWith.trace
              lastAsyncEpoch += 1
              val asyncEpoch      = lastAsyncEpoch
              val currentUpstream = upstream.pop()
              asyncEpochs.put(
                asyncEpoch,
                AsyncState(
                  if (currentUpstream != null) currentUpstream.channel else null,
                  if (currentUpstream != null) currentUpstream.doneStack else Stack(),
                  upstream,
                  Stack(),
                  null,
                  scala.collection.immutable.Queue.empty[ChannelFiberMessage.Read],
                  Set.empty
                )
              )
              currentChannel = ZChannel.unwrapScoped {
                for {
                  left  <- (ZChannel.fromRuntimeFiber(self, asyncEpoch) >>> left).forkScoped
                  right <- (ZChannel.fromRuntimeFiber(self, asyncEpoch) >>> right).forkScoped
                } yield ZChannel.mergeFibers(left, right)(leftDone, rightDone)
              }

            case ZChannel.PipeTo(trace, left, right) =>
              val childScope = Unsafe.unsafe { implicit unsafe =>
                Runtime.default.unsafe.run(currentScope.fork(trace))(trace, unsafe).getOrThrowFiberFailure()
              }

              upstream.push(
                Upstream(
                  left,
                  Stack(),
                  childScope
                )
              )
              currentChannel = right.ensuringWith { exit =>
                ZIO.suspendSucceed {
                  val currentUpstream = upstream.pop()
                  if (currentUpstream ne null) {
                    currentUpstream.scope.close(exit)(trace)
                  } else ZIO.unit
                }(trace)
              }(trace).asInstanceOf[ZChannel[Any, Any, Any, Any, Any, Any, Any]]

            case ZChannel.Provide(trace, environment, channel) =>
              val oldEnvironment = getFiberRef(FiberRef.currentEnvironment)(Unsafe.unsafe)
              setFiberRef(FiberRef.currentEnvironment, environment)(Unsafe.unsafe)
              currentChannel = channel
                .foldCauseChannel(
                  cause => {
                    setFiberRef(FiberRef.currentEnvironment, oldEnvironment)(Unsafe.unsafe)
                    ZChannel.refailCause(cause)
                  },
                  done => {
                    setFiberRef(FiberRef.currentEnvironment, oldEnvironment)(Unsafe.unsafe)
                    ZChannel.succeedNow(done)(Trace.empty)
                  }
                )(trace)
                .asInstanceOf[ZChannel[Any, Any, Any, Any, Any, Any, Any]]

            case ZChannel.Read(_, onElem, onErr, onDone) =>
              val currentUpstream = upstream.pop()
              if (currentUpstream eq null) {
                if (writers.isEmpty) {
                  reads.foreach(_())
                  reads = Set.empty
                  loop = false
                  suspendedOnDownstream = true
                } else {
                  val (write, updatedWriters) = writers.dequeue
                  writers = updatedWriters
                  write match {
                    case ChannelFiberMessage.WriteElem(elem, read) =>
                      if (read ne null) reads += read
                      currentChannel = onElem(elem)
                    case ChannelFiberMessage.WriteErr(err) =>
                      currentChannel = onErr(err)
                    case ChannelFiberMessage.WriteDone(done) =>
                      currentChannel = onDone(done)
                  }
                }
              } else {
                downstream.push(
                  Downstream(
                    {
                      case Left(elem)  => onElem(elem)
                      case Right(exit) => exit.foldExit(onErr, onDone)
                    },
                    doneStack,
                    currentScope
                  )
                )
                doneStack = currentUpstream.doneStack
                currentScope = currentUpstream.scope
                currentChannel = currentUpstream.channel

              }

            case ZChannel.Stateful(_, onState) =>
              currentChannel = onState.asInstanceOf[ChannelFiberRuntime[
                Nothing,
                Nothing,
                Nothing,
                Any,
                Any,
                Any
              ] => ZChannel[Any, Any, Any, Any, Any, Any, Any]](self)

            case ZChannel.Succeed(_, done) =>
              currentChannel = ZChannel.succeedNow(done())(Trace.empty)

            case ZChannel.SucceedNow(done) =>
              val fold = doneStack.pop()
              if (fold eq null) {
                val currentDownstream = downstream.pop()
                if (currentDownstream eq null) {
                  if (winddown) {
                    exit = Exit.succeed(done.asInstanceOf[OutDone])
                    loop = false
                  } else {
                    winddown = true
                    val success = Exit.succeed(done.asInstanceOf[OutDone])
                    currentChannel = ZChannel.fromZIO {
                      initialScope.fold(internalScope.close(success)(Trace.empty))(
                        _.addFinalizerExit(exit => internalScope.close(success)(Trace.empty))(Trace.empty)
                      )
                    }(Trace.empty).foldCauseChannel(
                      finalizerCause => ZChannel.refailCause(finalizerCause),
                      _ => ZChannel.succeedNow(done)(Trace.empty)
                    )(Trace.empty)
                  }
                } else {
                  upstream.push(
                    Upstream(
                      ZChannel.unit,
                      doneStack,
                      currentScope
                    )
                  )
                  currentChannel = currentDownstream.write(Right(Exit.succeed(done)))
                  currentScope = currentDownstream.scope
                  doneStack = currentDownstream.doneStack
                }
              } else {
                currentChannel = fold.onDone(done)
              }

            case ZChannel.Suspend(channel) =>
              currentChannel = channel()

            case ZChannel.UpdateRuntimeFlags(_, update) =>
              currentRuntimeFlags = patchRuntimeFlags(currentRuntimeFlags, update)
              currentChannel = ZChannel.unit

            case channel: ZChannel.UpdateRuntimeFlagsWithin[_, _, _, _, _, _, _] =>
              val patch           = channel.update
              val oldRuntimeFlags = currentRuntimeFlags
              val newRuntimeFlags = RuntimeFlags.patch(patch)(oldRuntimeFlags)

              if (newRuntimeFlags == oldRuntimeFlags) {
                currentChannel =
                  channel.scope(oldRuntimeFlags).asInstanceOf[ZChannel[Any, Any, Any, Any, Any, Any, Any]]
              } else {
                if (RuntimeFlags.interruptible(newRuntimeFlags) && isInterrupted()(Unsafe.unsafe)) {
                  currentChannel = ZChannel.refailCause(getFiberRef(FiberRef.interruptedCause)(Unsafe.unsafe))
                } else {
                  currentRuntimeFlags = patchRuntimeFlags(currentRuntimeFlags, patch)
                  val revertFlags = RuntimeFlags.diff(newRuntimeFlags, oldRuntimeFlags)
                  currentChannel = channel
                    .scope(oldRuntimeFlags)
                    .ensuringWith {
                      case Exit.Failure(cause) => {
                        currentRuntimeFlags = patchRuntimeFlags(currentRuntimeFlags, revertFlags)
                        if (RuntimeFlags.interruptible(currentRuntimeFlags) && isInterrupted()(Unsafe.unsafe)) {
                          Exit.failCause(getFiberRef(FiberRef.interruptedCause)(Unsafe.unsafe))
                        } else {
                          ZIO.unit
                        }
                      }
                      case Exit.Success(done) => {
                        currentRuntimeFlags = patchRuntimeFlags(currentRuntimeFlags, revertFlags)
                        if (RuntimeFlags.interruptible(currentRuntimeFlags) && isInterrupted()(Unsafe.unsafe)) {
                          Exit.failCause(getFiberRef(FiberRef.interruptedCause)(Unsafe.unsafe))
                        } else {
                          ZIO.unit
                        }
                      }
                    }(channel.trace)
                    .asInstanceOf[ZChannel[Any, Any, Any, Any, Any, Any, Any]]
                }
              }

            case withUpstream @ ZChannel.WithUpstream(_, f) =>
              implicit val trace = withUpstream.trace
              lastAsyncEpoch += 1
              val asyncEpoch      = lastAsyncEpoch
              val currentUpstream = upstream.pop()
              asyncEpochs.put(
                asyncEpoch,
                AsyncState(
                  if (currentUpstream != null) currentUpstream.channel else null,
                  if (currentUpstream != null) currentUpstream.doneStack else Stack(),
                  upstream,
                  Stack(),
                  null,
                  scala.collection.immutable.Queue.empty[ChannelFiberMessage.Read],
                  Set.empty
                )
              )
              currentChannel = ZChannel.unwrap {
                currentScope.extend {
                  ZIO.succeed(f(ZChannel.fromRuntimeFiber(self, asyncEpoch)))
                }
              }

            case ZChannel.YieldNow(_) =>
              currentChannel = null
              loop = false
              offerToInbox(ChannelFiberMessage.YieldNow(currentFiberRefs, currentAsyncEpoch))
          }
        } catch {
          case zioError: ZIO.ZIOError =>
            currentChannel = ZChannel.refailCause(zioError.cause)
          case t: Throwable =>
            currentChannel = ZChannel.failCause(Cause.die(t))(lastTrace)
        }
      }

      if (exit ne null) {
        observers.foreach(_(exit))
        observers = Set.empty
        readers.foreach(read => exit.foldExit(read.onErr, read.onDone))
        readers = scala.collection.immutable.Queue.empty
      }
    }
  }

  private def forkUnstarted[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    channel: ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  )(implicit
    trace: Trace
  ): ChannelFiberRuntime[
    InErr,
    InElem,
    InDone,
    OutErr,
    OutElem,
    OutDone
  ] = {
    val fiber = new ChannelFiberRuntime(
      channel.asInstanceOf[ZChannel[Any, InErr, InElem, InDone, OutErr, OutElem, OutDone]],
      None,
      environment,
      currentFiberRefs,
      currentRuntimeFlags,
      runtime,
      FiberId.make(trace)(Unsafe.unsafe),
      grafter
    )
    fiber

  }

  final def mapOutZIOIndexed[Env, InErr, InElem, InDone, OutErr, OutElem, OutElem2, OutDone](
    channel: ZChannel[Env, InErr, InElem, InDone, OutErr, (OutElem, Long), OutDone],
    onElem: OutElem => ZIO[Env, OutErr, OutElem2]
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, (OutElem2, Long), OutDone] = {
    lazy val reader: ZChannel[Env, OutErr, (OutElem, Long), OutDone, OutErr, (OutElem2, Long), OutDone] =
      ZChannel.readWith(
        elem => ZChannel.fromZIO(onElem(elem._1)).flatMap(elem2 => ZChannel.write((elem2, elem._2))) *> reader,
        err => ZChannel.fail(err),
        done => ZChannel.succeedNow(done)
      )

    channel >>> reader
  }

  private final def zipWithIndex[Err, Elem, Done](implicit
    trace: Trace
  ): ZChannel[Any, Err, Elem, Done, Err, (Elem, Long), Done] = {

    def loop(index: Long): ZChannel[Any, Err, Elem, Done, Err, (Elem, Long), Done] =
      ZChannel.readWithCause(
        elem => ZChannel.write((elem, index)) *> loop(index + 1),
        cause => ZChannel.failCause(cause),
        done => ZChannel.succeed(done)
      )

    loop(0L)
  }
}

object ChannelFiberRuntime {
  private val MaxOperationsBeforeYield = 1024

  private sealed trait EvaluationSignal
  private object EvaluationSignal {
    case object Continue extends EvaluationSignal
    case object Done     extends EvaluationSignal
    case object YieldNow extends EvaluationSignal
  }

  val channelFiberIdCounter =
    new java.util.concurrent.atomic.AtomicInteger(0)

  implicit val channelFiberRuntimeOrdering: Ordering[ChannelFiberRuntime[Nothing, Nothing, Nothing, Any, Any, Any]] =
    Ordering.by[ChannelFiberRuntime[Nothing, Nothing, Nothing, Any, Any, Any], (Long, Long)](fiber =>
      (fiber.fiberId.startTimeMillis, fiber.fiberId.id)
    )
}
