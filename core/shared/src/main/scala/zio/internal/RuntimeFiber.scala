package zio.internal

import scala.concurrent._
import scala.annotation.tailrec
import scala.util.control.NoStackTrace

import java.util.{Set => JavaSet}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import zio._

class RuntimeFiber[E, A](fiberId: FiberId.Runtime, fiberRefs: FiberRefs)
    extends FiberState[E, A](fiberId, fiberRefs) { self =>
  type Erased = ZIO[Any, Any, Any]

  import ZIO._
  import EvaluationStep._
  import ReifyStack.{AsyncJump, Trampoline, GenerateTrace}

  def await(implicit trace: Trace): UIO[Exit[E, A]] =
    ZIO.async[Any, Nothing, Exit[E, A]] { cb =>
      val exit = self.unsafeEvalOn[Exit[E, A]](
        ZIO.succeed(self.unsafeAddObserver(exit => cb(ZIO.succeed(exit)))),
        self.unsafeExitValue()
      )

      if (exit ne null) cb(ZIO.succeed(exit))
    }

  def children(implicit trace: Trace): UIO[Chunk[RuntimeFiber[_, _]]] =
    evalOnZIO(ZIO.succeed(Chunk.fromJavaIterable(unsafeGetChildren())), ZIO.succeed(Chunk.empty))

  def evalOnZIO[R, E2, A2](effect: ZIO[R, E2, A2], orElse: ZIO[R, E2, A2])(implicit
    trace: Trace
  ): ZIO[R, E2, A2] = ???
  //   for {
  //     r <- ZIO.environment[R]
  //     p <- Promise.make[E2, A2]
  //     _ <- evalOn(effect.provideEnvironment(r).intoPromise(p), orElse.provideEnvironment(r).intoPromise(p))
  //     a <- p.await
  //   } yield a

  def id: FiberId.Runtime = fiberId

  def inheritRefs(implicit trace: Trace): UIO[Unit] = ??? // fiberRefs.setAll

  def interruptAsFork(fiberId: FiberId)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed {
      val cause = Cause.interrupt(fiberId).traced(StackTrace(fiberId, Chunk(trace)))

      unsafeAddInterruptedCause(cause)

      val selfInterrupt = ZIO.InterruptSignal(cause, trace)

      if (unsafeAsyncInterruptOrAddMessage(selfInterrupt)) {
        asyncResume(selfInterrupt, Chunk.empty, 1000)
      }

      ()
    }

  def interruptAs(fiberId: FiberId)(implicit trace: Trace): UIO[Exit[E, A]] =
    interruptAsFork(fiberId) *> await

  final def location: Trace = fiberId.location

  final def poll(implicit trace: Trace): UIO[Option[Exit[E, A]]] =
    ZIO.succeed {
      if (self.unsafeIsDone()) Some(self.unsafeExitValue()) else None
    }

  def status(implicit trace: Trace): UIO[zio.Fiber.Status] = ZIO.succeed(self.unsafeStatus())

  def trace(implicit trace: Trace): UIO[StackTrace] =
    evalOnZIO(ZIO.trace, ZIO.succeed(StackTrace(fiberId, Chunk.empty)))

  private def assertNonNull(a: Any, message: String, location: Trace): Unit =
    if (a == null) {
      throw new NullPointerException(message + ": " + location.toString)
    }

  private def assertNonNullContinuation(a: Any, location: Trace): Unit =
    assertNonNull(a, "The return value of a success or failure handler must be non-null", location)

  def asyncResume(
    effect: ZIO[Any, Any, Any],
    stack: Chunk[EvaluationStep],
    maxDepth: Int
  ): Unit =
    unsafeGetCurrentExecutor().unsafeSubmitOrThrow { () =>
      outerRunLoop(effect, stack, maxDepth)
      ()
    }

  @tailrec
  final def outerRunLoop(
    effect0: ZIO[Any, Any, Any],
    stack: Chunk[EvaluationStep],
    maxDepth: Int
  ): Exit[E, A] =
    try {
      val mailbox = self.unsafeDrainMailbox()
      val effect  = if (mailbox eq ZIO.unit) effect0 else mailbox *> effect0

      val interruptible = self.unsafeGetInterruptible()

      val exit: Exit[Nothing, A] = Exit.succeed(runLoop(effect, maxDepth, stack, interruptible).asInstanceOf[A])

      val remainingWork = self.unsafeAttemptDone(exit)

      if (remainingWork ne null) throw Trampoline(remainingWork *> ZIO.done(exit), ChunkBuilder.make())

      exit
    } catch {
      case trampoline: Trampoline =>
        outerRunLoop(trampoline.effect, trampoline.stack.result(), maxDepth)

      case asyncJump: AsyncJump =>
        val epoch = unsafeEnterSuspend()

        asyncJump.registerCallback { value =>
          if (unsafeAttemptResume(epoch)) {
            asyncResume(value, asyncJump.stack.result(), maxDepth)
          }
        }

        null

      case zioError: ZIOError =>
        val cause = zioError.cause.asInstanceOf[Cause[E]]

        val exit = Exit.failCause(cause ++ unsafeGetInterruptedCause())

        val remainingWork = self.unsafeAttemptDone(exit)

        if (remainingWork ne null) outerRunLoop(remainingWork, Chunk.empty, maxDepth)
        else exit

      case traceGen: GenerateTrace =>
        val stack = traceGen.stack.result() // TODO: Don't build it, just iterate over it!

        val builder = StackTraceBuilder.unsafeMake()

        stack.foreach(k => builder += k.trace)

        val trace = StackTrace(self.fiberId, builder.result())

        outerRunLoop(ZIO.succeed(trace), stack, maxDepth)
    }

  def runLoop(
    effect: ZIO[Any, Any, Any],
    remainingDepth: Int,
    stack: Chunk[ZIO.EvaluationStep],
    interruptible0: Boolean
  ): AnyRef = {
    var cur           = effect
    var done          = null.asInstanceOf[AnyRef]
    var stackIndex    = 0
    var interruptible = interruptible0
    var lastTrace     = null.asInstanceOf[Trace] // TODO: Rip out???

    if (remainingDepth <= 0) {
      // Save local variables to heap:
      self.unsafeSetInterruptible(interruptible)

      val builder = ChunkBuilder.make[EvaluationStep]()

      builder ++= stack

      throw Trampoline(effect, builder)
    }

    while (done eq null) {
      val nextTrace = cur.trace
      if (nextTrace ne Trace.empty) lastTrace = nextTrace

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
            // Save local variables to heap:
            self.unsafeSetInterruptible(interruptible)

            throw AsyncJump(effect.registerCallback, ChunkBuilder.make())

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

            // Save local variables to heap:
            self.unsafeSetInterruptible(interruptible)

            throw GenerateTrace(builder)

          case stateful: Stateful[_, _, _] =>
            cur = stateful.erase.onState(self.asInstanceOf[FiberState[Any, Any]], interruptible, lastTrace)

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
          cur = Refail(Cause.interrupt(FiberId.None))

        case throwable: Throwable => // TODO: If non-fatal
          cur = Refail(Cause.die(throwable))
      }
    }

    done
  }
}

final case class FiberSuspension(blockingOn: FiberId, location: Trace)

import java.util.{HashMap => JavaMap, Set => JavaSet}

abstract class FiberState[E, A](fiberId0: FiberId.Runtime, fiberRefs0: FiberRefs) extends Fiber.Runtime.Internal[E, A] {
  // import FiberStatusIndicator.Status

  private val mailbox          = new AtomicReference[UIO[Any]](ZIO.unit)
  private[zio] val statusState = new FiberStatusState(new AtomicInteger(FiberStatusIndicator.initial))

  private var _children  = null.asInstanceOf[JavaSet[RuntimeFiber[_, _]]]
  private var fiberRefs  = fiberRefs0
  private var observers  = Nil: List[Exit[E, A] => Unit]
  private var suspension = null.asInstanceOf[FiberSuspension]

  protected val fiberId = fiberId0

  @volatile private var _exitValue = null.asInstanceOf[Exit[E, A]]

  final def evalOn(effect: UIO[Any], orElse: UIO[Any])(implicit trace: Trace): UIO[Unit] =
    ZIO.suspendSucceed {
      if (unsafeAddMessage(effect)) ZIO.unit else orElse.unit
    }

  // FIXME: Remove cast
  final def scope: FiberScope = FiberScope.unsafeMake(this.asInstanceOf[RuntimeFiber[Any, Any]])

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
    val oldSC = unsafeGetFiberRef(FiberRef.suppressedCause)

    unsafeSetFiberRef(FiberRef.suppressedCause, oldSC ++ cause)
  }

  /**
   * Adds a message to the mailbox and returns true if the state is not done.
   * Otherwise, returns false to indicate the fiber cannot accept messages.
   */
  final def unsafeAddMessage(effect: UIO[Any]): Boolean = {
    @tailrec
    def runLoop(message: UIO[Any]): Unit = {
      val oldMessages = mailbox.get

      val newMessages =
        if (oldMessages eq ZIO.unit) message else (oldMessages *> message)(Trace.empty)

      if (!mailbox.compareAndSet(oldMessages, newMessages)) runLoop(message)
      else ()
    }

    if (statusState.beginAddMessage()) {
      try {
        runLoop(effect)
        true
      } finally statusState.endAddMessage()
    } else false
  }

  /**
   * Adds an observer to the list of observers.
   */
  final def unsafeAddObserver(observer: Exit[E, A] => Unit): Unit =
    observers = observer :: observers

  final def unsafeAddObserverMaybe(k: Exit[E, A] => Unit): Exit[E, A] =
    unsafeEvalOn(ZIO.succeed(unsafeAddObserver(k)), unsafeExitValue())

  /**
   * Attempts to place the state of the fiber in interruption, but only if the
   * fiber is currently asynchronously suspended (hence, "async
   * interruption").
   */
  final def unsafeAttemptAsyncInterrupt(): Boolean =
    statusState.attemptAsyncInterrupt()

  /**
   * Attempts to place the state of the fiber in interruption, if the fiber is
   * currently suspended, but otherwise, adds the message to the mailbox of
   * the non-suspended fiber.
   *
   * @return
   *   True if the interruption was successful, or false otherwise.
   */
  final def unsafeAsyncInterruptOrAddMessage(message: UIO[Any]): Boolean =
    if (statusState.beginAddMessage()) {
      try {
        if (statusState.attemptAsyncInterrupt()) true
        else {
          unsafeAddMessage(message)
          false
        }
      } finally {
        statusState.endAddMessage()
      }
    } else false

  /**
   * Attempts to set the state of the fiber to done. This may fail if there
   * are pending messages in the mailbox, in which case those messages will be
   * returned. If the method succeeds, it will notify any listeners, who are
   * waiting for the exit value of the fiber.
   *
   * This method should only be called by the main runLoop of the fiber.
   *
   * @return
   *   `null` if the state of the fiber was set to done, or the pending
   *   messages, otherwise.
   */
  final def unsafeAttemptDone(e: Exit[E, A]): UIO[Any] = {
    _exitValue = e

    if (statusState.attemptDone()) {
      val iterator = observers.iterator

      while (iterator.hasNext) {
        val observer = iterator.next()

        observer(e)
      }
      null.asInstanceOf[UIO[Any]]
    } else unsafeDrainMailbox()
  }

  /**
   * Attempts to place the state of the fiber into running, from a previous
   * suspended state, identified by the async count.
   *
   * Returns `true` if the state was successfully transitioned, or `false` if
   * it cannot be transitioned, because the fiber state was already resumed or
   * even completed.
   */
  final def unsafeAttemptResume(asyncs: Int): Boolean = {
    val resumed = statusState.attemptResume(asyncs)

    if (resumed) suspension = null

    resumed
  }

  // FIXME:
  final def unsafeDescriptor(): Fiber.Descriptor = ???

  /**
   * Drains the mailbox of all messages. If the mailbox is empty, this will
   * return `ZIO.unit`.
   */
  final def unsafeDrainMailbox(): UIO[Any] = {
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
  final def unsafeEnterSuspend(): Int =
    statusState.enterSuspend()

  final def unsafeEvalOn[A](effect: UIO[Any], orElse: => A)(implicit trace: Trace): A =
    if (unsafeAddMessage(effect)) null.asInstanceOf[A] else orElse

  /**
   * Retrieves the exit value of the fiber state, which will be `null` if not
   * currently set.
   */
  final def unsafeExitValue(): Exit[E, A] = _exitValue

  /**
   * Retrieves the current number of async suspensions of the fiber, which can
   * be used to uniquely identify each suspeension.
   */
  final def unsafeGetAsyncs(): Int = statusState.getAsyncs()

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

  final def unsafeGetFiberRefs(): FiberRefs = fiberRefs

  /**
   * Retrieves the interruptibility status of the fiber state.
   */
  final def unsafeGetInterruptible(): Boolean = statusState.getInterruptible()

  final def unsafeGetInterruptedCause(): Cause[Nothing] = unsafeGetFiberRef(FiberRef.suppressedCause)

  final def unsafeGetLoggers(): Set[ZLogger[String, Any]] =
    unsafeGetFiberRef(FiberRef.currentLoggers)

  final def unsafeGetReportFatal(): Throwable => Nothing =
    unsafeGetFiberRef(FiberRef.currentReportFatal)

  final def unsafeGetRuntimeFlags(): Set[RuntimeFlag] =
    unsafeGetFiberRef(FiberRef.currentRuntimeFlags)

  /**
   * Determines if the fiber state contains messages to process by the fiber
   * run runLoop. Due to race conditions, if this method returns true, it
   * means only that, if the messages were not drained, there will be some
   * messages at some point later, before the fiber state transitions to done.
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

  final def unsafeIsInterrupted(): Boolean = !unsafeGetFiberRef(FiberRef.suppressedCause).isEmpty

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

  final def unsafeSetFiberRefs(fiberRefs: FiberRefs): Unit = {
    this.fiberRefs = fiberRefs
  }

  /**
   * Sets the interruptibility status of the fiber to the specified value.
   */
  final def unsafeSetInterruptible(interruptible: Boolean): Unit =
    statusState.setInterruptible(interruptible)

  /**
   * Retrieves a snapshot of the status of the fibers.
   */
  final def unsafeStatus(): zio.Fiber.Status = {
    import FiberStatusIndicator.Status

    val indicator = statusState.getIndicator()

    val status       = FiberStatusIndicator.getStatus(indicator)
    val interrupting = FiberStatusIndicator.getInterrupting(indicator)

    if (status == Status.Done) zio.Fiber.Status.Done
    else if (status == Status.Running) zio.Fiber.Status.Running(interrupting)
    else {
      val interruptible = FiberStatusIndicator.getInterruptible(indicator)
      val asyncs        = FiberStatusIndicator.getAsyncs(indicator)
      val blockingOn    = if (suspension eq null) FiberId.None else suspension.blockingOn
      val asyncTrace    = if (suspension eq null) Trace.empty else suspension.location

      zio.Fiber.Status.Suspended(interrupting, interruptible, asyncs.toLong, blockingOn, asyncTrace)
    }
  }
}
object RuntimeFiber {
  def apply[E, A](fiberId: FiberId.Runtime, fiberRefs: FiberRefs): RuntimeFiber[E, A] =
    new RuntimeFiber(fiberId, fiberRefs)
}

/*

def evalAsync[R, E, A](
    effect: ZIO[R, E, A],
    onDone: Exit[E, A] => Unit,
    maxDepth: Int = 1000,
    fiberRefs0: FiberRefs = FiberRefs.empty
  )(implicit trace0: Trace): Exit[E, A] = {
    val fiber = RuntimeFiber[E, A](FiberId.unsafeMake(trace0), fiberRefs0)

    fiber.unsafeAddObserver(onDone)

    fiber.outerRunLoop(effect.asInstanceOf[Erased], Chunk.empty, maxDepth)
  }

  def evalToFuture[A](effect: ZIO[Any, Throwable, A], maxDepth: Int = 1000): scala.concurrent.Future[A] = {
    val promise = Promise[A]()

    evalAsync[Any, Throwable, A](effect, exit => promise.complete(exit.toTry), maxDepth) match {
      case null => promise.future

      case exit => Future.fromTry(exit.toTry)
    }
  }

  def eval[A](effect: ZIO[Any, Throwable, A], maxDepth: Int = 1000): A = {
    import java.util.concurrent._
    import scala.concurrent.duration._

    val future = evalToFuture(effect, maxDepth)

    Await.result(future, Duration(1, TimeUnit.HOURS))
  }

*/
