package zio.internal

package zio2 {
  import zio.{Cause, Chunk, ChunkBuilder, Executor, Exit, Fiber, FiberRef, FiberRefs, FiberId, ZTrace, ZTraceElement}

  import scala.concurrent._
  import scala.annotation.tailrec
  import scala.util.control.NoStackTrace

  import java.util.{Set => JavaSet}
  import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

  sealed trait Fiber[+E, +A]
  object Fiber {
    sealed trait Runtime[+E, +A] extends Fiber[E, A]
  }

  object RuntimeFiber {
    def apply[E, A](fiberId: FiberId.Runtime, fiberRefs: FiberRefs): RuntimeFiber[E, A] =
      new RuntimeFiber(fiberId, fiberRefs)
  }
  class RuntimeFiber[E, A](fiberId: FiberId.Runtime, fiberRefs: FiberRefs)
      extends FiberState[E, A](fiberId, fiberRefs)
      with Fiber.Runtime[E, A] { self =>
    type Erased = ZIO[Any, Any, Any]

    import ZIO._
    import EvaluationStep._
    import ReifyStack.{AsyncJump, Trampoline, GenerateTrace}

    def await(implicit trace: ZTraceElement): UIO[Exit[E, A]] =
      ZIO.async[Any, Nothing, Exit[E, A]] { cb =>
        val exit = self.unsafeEvalOn[Exit[E, A]](
          ZIO.succeed(self.unsafeAddObserver(exit => cb(ZIO.succeed(exit)))),
          self.unsafeExitValue()
        )

        if (exit ne null) cb(ZIO.succeed(exit))
      }

    def children(implicit trace: ZTraceElement): UIO[Chunk[RuntimeFiber[_, _]]] =
      evalOnZIO(ZIO.succeed(Chunk.fromJavaIterable(unsafeGetChildren())), ZIO.succeed(Chunk.empty))

    def evalOnZIO[R, E2, A2](effect: ZIO[R, E2, A2], orElse: ZIO[R, E2, A2])(implicit
      trace: ZTraceElement
    ): ZIO[R, E2, A2] = ???
    //   for {
    //     r <- ZIO.environment[R]
    //     p <- Promise.make[E2, A2]
    //     _ <- evalOn(effect.provideEnvironment(r).intoPromise(p), orElse.provideEnvironment(r).intoPromise(p))
    //     a <- p.await
    //   } yield a

    def id: FiberId.Runtime = fiberId

    def inheritRefs(implicit trace: ZTraceElement): UIO[Unit] = ??? // fiberRefs.setAll

    def interruptAsFork(fiberId: FiberId)(implicit trace: ZTraceElement): UIO[Unit] =
      ZIO.succeed {
        val cause = Cause.interrupt(fiberId).traced(ZTrace(fiberId, Chunk(trace)))

        unsafeAddInterruptedCause(cause)

        val selfInterrupt = ZIO.InterruptSignal(cause, trace)

        if (unsafeAsyncInterruptOrAddMessage(selfInterrupt)) {
          asyncResume(selfInterrupt, Chunk.empty, 1000)
        }

        ()
      }

    def interruptAs(fiberId: FiberId)(implicit trace: ZTraceElement): UIO[Exit[E, A]] =
      interruptAsFork(fiberId) *> await

    final def location: ZTraceElement = fiberId.location

    final def poll(implicit trace: ZTraceElement): UIO[Option[Exit[E, A]]] =
      ZIO.succeed {
        if (self.unsafeIsDone()) Some(self.unsafeExitValue()) else None
      }

    def status(implicit trace: ZTraceElement): UIO[zio.Fiber.Status] = ZIO.succeed(self.unsafeStatus())

    def trace(implicit trace: ZTraceElement): UIO[ZTrace] =
      evalOnZIO(ZIO.trace, ZIO.succeed(ZTrace(fiberId, Chunk.empty)))

    private def assertNonNull(a: Any, message: String, location: ZTraceElement): Unit =
      if (a == null) {
        throw new NullPointerException(message + ": " + location.toString)
      }

    private def assertNonNullContinuation(a: Any, location: ZTraceElement): Unit =
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

          val trace = ZTrace(self.fiberId, builder.result())

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
      var lastTrace     = null.asInstanceOf[ZTraceElement] // TODO: Rip out???

      if (remainingDepth <= 0) {
        // Save local variables to heap:
        self.unsafeSetInterruptible(interruptible)

        val builder = ChunkBuilder.make[EvaluationStep]()

        builder ++= stack

        throw Trampoline(effect, builder)
      }

      while (done eq null) {
        val nextTrace = cur.trace
        if (nextTrace ne ZTraceElement.empty) lastTrace = nextTrace

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

                    case k: UpdateTrace => if (k.trace ne ZTraceElement.empty) lastTrace = k.trace
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

                  case k: UpdateTrace => if (k.trace ne ZTraceElement.empty) lastTrace = k.trace
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

  final case class FiberSuspension(blockingOn: FiberId, location: ZTraceElement)

  import java.util.{HashMap => JavaMap, Set => JavaSet}

  object FiberState {
    def apply[E, A](fiberId: FiberId.Runtime, refs: FiberRefs): FiberState[E, A] = new FiberState(fiberId, refs)
  }
  class FiberState[E, A](fiberId0: FiberId.Runtime, fiberRefs0: FiberRefs) {
    // import FiberStatusIndicator.Status

    private val mailbox          = new AtomicReference[UIO[Any]](ZIO.unit)
    private[zio] val statusState = new FiberStatusState(new AtomicInteger(FiberStatusIndicator.initial))

    private var _children  = null.asInstanceOf[JavaSet[RuntimeFiber[_, _]]]
    private var fiberRefs  = fiberRefs0
    private var observers  = Nil: List[Exit[E, A] => Unit]
    private var suspension = null.asInstanceOf[FiberSuspension]

    protected val fiberId = fiberId0

    @volatile private var _exitValue = null.asInstanceOf[Exit[E, A]]

    final def evalOn(effect: UIO[Any], orElse: UIO[Any])(implicit trace: ZTraceElement): UIO[Unit] =
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
          if (oldMessages eq ZIO.unit) message else (oldMessages *> message)(ZTraceElement.empty)

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

    final def unsafeEvalOn[A](effect: UIO[Any], orElse: => A)(implicit trace: ZTraceElement): A =
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

    final def unsafeGetCurrentExecutor(): Executor =
      unsafeGetFiberRef(FiberRef.currentExecutor).getOrElse(zio.Runtime.default.runtimeConfig.executor)

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

    final def unsafeIsInterrupted(): Boolean = !unsafeGetFiberRef(FiberRef.suppressedCause).isEmpty

    final def unsafeIsRunning(): Boolean = {
      val indicator = statusState.getIndicator()

      FiberStatusIndicator.getStatus(indicator) == FiberStatusIndicator.Status.Running
    }

    final def unsafeIsSuspended(): Boolean = {
      val indicator = statusState.getIndicator()

      FiberStatusIndicator.getStatus(indicator) == FiberStatusIndicator.Status.Suspended
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
        val asyncTrace    = if (suspension eq null) ZTraceElement.empty else suspension.location

        zio.Fiber.Status.Suspended(interrupting, interruptible, asyncs.toLong, blockingOn, asyncTrace)
      }
    }
  }

  sealed trait ZIO[-R, +E, +A] { self =>
    final def *>[R1 <: R, E1 >: E, B](that: => ZIO[R1, E1, B])(implicit trace: ZTraceElement): ZIO[R1, E1, B] =
      self.flatMap(_ => that)

    final def <*[R1 <: R, E1 >: E](that: => ZIO[R1, E1, Any])(implicit trace: ZTraceElement): ZIO[R1, E1, A] =
      self.flatMap(a => that.map(_ => a))

    final def as[B](b: => B)(implicit trace: ZTraceElement): ZIO[R, E, B] = self.map(_ => b)

    final def catchAll[R1 <: R, E2, A1 >: A](
      t: E => ZIO[R1, E2, A1]
    )(implicit trace: ZTraceElement): ZIO[R1, E2, A1] =
      self.catchAllCause { cause =>
        cause.failureOrCause.fold(t, ZIO.refailCause(_))
      }

    final def catchAllCause[R1 <: R, E2, A1 >: A](t: Cause[E] => ZIO[R1, E2, A1])(implicit
      trace: ZTraceElement
    ): ZIO[R1, E2, A1] =
      ZIO.OnFailure(trace, self, t)

    final def ensuring(finalizer: ZIO[Any, Nothing, Any])(implicit trace: ZTraceElement): ZIO[R, E, A] =
      ZIO.uninterruptibleMask { restore =>
        restore(self).foldCauseZIO(cause => finalizer *> ZIO.refailCause(cause), a => finalizer.map(_ => a))
      }

    final def exit(implicit trace: ZTraceElement): ZIO[R, Nothing, Exit[E, A]] =
      self.map(Exit.succeed(_)).catchAllCause(cause => ZIO.succeed(Exit.failCause(cause)))

    final def flatMap[R1 <: R, E1 >: E, B](f: A => ZIO[R1, E1, B])(implicit
      trace: ZTraceElement
    ): ZIO[R1, E1, B] =
      ZIO.OnSuccess(trace, self, f)

    final def foldCauseZIO[R1 <: R, E2, B](onError: Cause[E] => ZIO[R1, E2, B], onSuccess: A => ZIO[R1, E2, B])(implicit
      trace: ZTraceElement
    ): ZIO[R1, E2, B] =
      ZIO.OnSuccessAndFailure(trace, self, onSuccess, onError)

    /*
    final case class Stateful[R, E, A](
      trace: ZTraceElement,
      onState: (FiberState[E, A], Boolean, ZTraceElement) => ZIO[R, E, A]
    )
     */
    final def fork[E1 >: E, A1 >: A](implicit trace: ZTraceElement): ZIO[R, Nothing, RuntimeFiber[E1, A1]] =
      ZIO.Stateful[R, Nothing, RuntimeFiber[E1, A1]](
        trace,
        (fiberState, interruptible, _) => ZIO.succeed(ZIO.unsafeFork(trace, self, fiberState, interruptible))
      )

    final def interruptible(implicit trace: ZTraceElement): ZIO[R, E, A] =
      ZIO.ChangeInterruptionWithin.Interruptible(trace, self)

    final def map[B](f: A => B)(implicit trace: ZTraceElement): ZIO[R, E, B] =
      self.flatMap(a => ZIO.succeed(f(a)))

    final def mapError[E2](f: E => E2)(implicit trace: ZTraceElement): ZIO[R, E2, A] =
      self.catchAll(e => ZIO.fail(f(e)))

    // FIXME: Change from RuntimeFiber => Fiber.Runtime
    final def raceWith[R1 <: R, ER, E2, B, C](right0: ZIO[R1, ER, B])(
      leftWins: (Fiber.Runtime[E, A], Fiber.Runtime[ER, B]) => ZIO[R1, E2, C],
      rightWins: (Fiber.Runtime[ER, B], Fiber.Runtime[E, A]) => ZIO[R1, E2, C]
    )(implicit trace: ZTraceElement): ZIO[R1, E2, C] = ZIO.Stateful[R1, E2, C](
      trace,
      { (fiberState, interruptible, _) =>
        import java.util.concurrent.atomic.AtomicBoolean

        @inline def complete[E0, E1, A, B](
          winner: Fiber.Runtime[E0, A],
          loser: Fiber.Runtime[E1, B],
          cont: (Fiber.Runtime[E0, A], Fiber.Runtime[E1, B]) => ZIO[R1, E2, C],
          ab: AtomicBoolean,
          cb: ZIO[R1, E2, C] => Any
        ): Any =
          if (ab.compareAndSet(true, false)) {
            cb(cont(winner, loser))
          }

        val raceIndicator = new AtomicBoolean(true)

        val left: RuntimeFiber[E, A]   = ZIO.unsafeFork(trace, self, fiberState, interruptible)
        val right: RuntimeFiber[ER, B] = ZIO.unsafeFork(trace, right0, fiberState, interruptible)

        ZIO
          .async[R1, E2, C](
            { cb =>
              val leftRegister = left.unsafeAddObserverMaybe { _ =>
                complete(left, right, leftWins, raceIndicator, cb)
              }

              if (leftRegister ne null)
                complete(left, right, leftWins, raceIndicator, cb)
              else {
                val rightRegister = right.unsafeAddObserverMaybe { _ =>
                  complete(right, left, rightWins, raceIndicator, cb)
                }

                if (rightRegister ne null)
                  complete(right, left, rightWins, raceIndicator, cb)
              }
            } //FIXME, FiberId.combineAll(Set(left.fiberId, right.fiberId))
          )
      }
    )

    def trace: ZTraceElement

    final def uninterruptible(implicit trace: ZTraceElement): ZIO[R, E, A] =
      ZIO.ChangeInterruptionWithin.Uninterruptible(trace, self)

    final def unit(implicit trace: ZTraceElement): ZIO[R, E, Unit] = self.as(())
  }
  object ZIO {
    private def unsafeFork[R, E1, E2, A, B](
      trace: ZTraceElement,
      effect: ZIO[R, E1, A],
      fiberState: FiberState[E2, B],
      interruptible: Boolean
    ) = {
      val childId         = FiberId.unsafeMake(trace)
      val parentFiberRefs = fiberState.unsafeGetFiberRefs
      val childFiberRefs  = parentFiberRefs.forkAs(childId)

      val childFiber = RuntimeFiber[E1, A](childId, childFiberRefs)

      // Child inherits interruptibility status of parent fiber:
      childFiber.unsafeSetInterruptible(interruptible)

      // FIXME: Call the supervisor who can observe the fork of the child fiber
      val parentScope = fiberState.unsafeGetFiberRef(FiberRef.forkScopeOverride).getOrElse(fiberState.scope)

      // FIXME: Pass `enableFiberRoots` here:
      parentScope.unsafeAdd(true, childFiber)

      val currentExecutor = fiberState.unsafeGetCurrentExecutor()

      currentExecutor.unsafeSubmitOrThrow { () =>
        childFiber.outerRunLoop(effect.asInstanceOf[Erased], Chunk.empty, 1000)
      }

      childFiber
    }
    implicit class EffectThrowableSyntax[A](self: ZIO[Any, Throwable, A]) {
      def unsafeRun(maxDepth: Int = 1000): A = ZIO.eval(self, maxDepth)

      def unsafeRunToFuture(maxDepth: Int = 1000): scala.concurrent.Future[A] = ZIO.evalToFuture(self, maxDepth)
    }
    sealed trait EvaluationStep { self =>
      def trace: ZTraceElement
    }
    object EvaluationStep {
      sealed trait ChangeInterruptibility extends EvaluationStep {
        final def trace = ZTraceElement.empty

        def interruptible: Boolean
      }
      object ChangeInterruptibility {
        def apply(b: Boolean): ChangeInterruptibility = if (b) MakeInterruptible else MakeUninterruptible

        case object MakeInterruptible extends ChangeInterruptibility {
          def onSuccess(a: Any): ZIO[Any, Any, Any] = ZIO.succeed(a)

          def interruptible: Boolean = true
        }
        case object MakeUninterruptible extends ChangeInterruptibility {
          def onSuccess(a: Any): ZIO[Any, Any, Any] = ZIO.succeed(a)

          def interruptible: Boolean = false
        }
      }
      final case class UpdateTrace(trace: ZTraceElement) extends EvaluationStep
      sealed trait Continuation[R, E1, E2, A, B] extends EvaluationStep { self =>
        def trace: ZTraceElement

        def onSuccess(a: A): ZIO[R, E2, B]

        def onFailure(c: Cause[E1]): ZIO[R, E2, B]

        def erase: Continuation.Erased = self.asInstanceOf[Continuation.Erased]
      }
      object Continuation {
        type Erased = Continuation[Any, Any, Any, Any, Any]

        def ensuring[R, E, A](
          finalizer: ZIO[R, Nothing, Any]
        )(implicit trace0: ZTraceElement): Continuation[R, E, E, A, A] =
          new Continuation[R, E, E, A, A] {
            def trace                  = trace0
            def onSuccess(a: A)        = finalizer.flatMap(_ => ZIO.succeed(a))
            def onFailure(c: Cause[E]) = finalizer.flatMap(_ => ZIO.refailCause(c))
          }

        def fromSuccess[R, E, A, B](
          f: A => ZIO[R, E, B]
        )(implicit trace0: ZTraceElement): Continuation[R, E, E, A, B] =
          new Continuation[R, E, E, A, B] {
            def trace                  = trace0
            def onSuccess(a: A)        = f(a)
            def onFailure(c: Cause[E]) = ZIO.refailCause(c)
          }

        def fromFailure[R, E1, E2, A](
          f: Cause[E1] => ZIO[R, E2, A]
        )(implicit trace0: ZTraceElement): Continuation[R, E1, E2, A, A] =
          new Continuation[R, E1, E2, A, A] {
            def trace                   = trace0
            def onSuccess(a: A)         = ZIO.succeed(a)
            def onFailure(c: Cause[E1]) = f(c)
          }
      }
    }

    final case class Sync[A](trace: ZTraceElement, eval: () => A) extends ZIO[Any, Nothing, A]
    final case class Async[R, E, A](trace: ZTraceElement, registerCallback: (ZIO[R, E, A] => Unit) => Unit)
        extends ZIO[R, E, A]
    sealed trait OnSuccessOrFailure[R, E1, E2, A, B]
        extends ZIO[R, E2, B]
        with EvaluationStep.Continuation[R, E1, E2, A, B] {
      self =>
      def first: ZIO[R, E1, A]

      final override def erase: OnSuccessOrFailure[Any, Any, Any, Any, Any] =
        self.asInstanceOf[OnSuccessOrFailure[Any, Any, Any, Any, Any]]
    }
    final case class OnSuccessAndFailure[R, E1, E2, A, B](
      trace: ZTraceElement,
      first: ZIO[R, E1, A],
      successK: A => ZIO[R, E2, B],
      failureK: Cause[E1] => ZIO[R, E2, B]
    ) extends OnSuccessOrFailure[R, E1, E2, A, B] {
      def onFailure(c: Cause[E1]): ZIO[R, E2, B] = failureK(c)

      def onSuccess(a: A): ZIO[R, E2, B] = successK(a.asInstanceOf[A])
    }
    final case class OnSuccess[R, A, E, B](trace: ZTraceElement, first: ZIO[R, E, A], successK: A => ZIO[R, E, B])
        extends OnSuccessOrFailure[R, E, E, A, B] {
      def onFailure(c: Cause[E]): ZIO[R, E, B] = ZIO.refailCause(c)

      def onSuccess(a: A): ZIO[R, E, B] = successK(a.asInstanceOf[A])
    }
    final case class OnFailure[R, E1, E2, A](
      trace: ZTraceElement,
      first: ZIO[R, E1, A],
      failureK: Cause[E1] => ZIO[R, E2, A]
    ) extends OnSuccessOrFailure[R, E1, E2, A, A] {
      def onFailure(c: Cause[E1]): ZIO[R, E2, A] = failureK(c)

      def onSuccess(a: A): ZIO[R, E2, A] = ZIO.succeed(a)
    }
    sealed trait ChangeInterruptionWithin[R, E, A] extends ZIO[R, E, A] {
      def newInterruptible: Boolean

      def scope(oldInterruptible: Boolean): ZIO[R, E, A]
    }
    object ChangeInterruptionWithin {
      final case class Interruptible[R, E, A](trace: ZTraceElement, effect: ZIO[R, E, A])
          extends ChangeInterruptionWithin[R, E, A] {
        def newInterruptible: Boolean = true

        def scope(oldInterruptible: Boolean): ZIO[R, E, A] = effect
      }
      final case class Uninterruptible[R, E, A](trace: ZTraceElement, effect: ZIO[R, E, A])
          extends ChangeInterruptionWithin[R, E, A] {
        def newInterruptible: Boolean = false

        def scope(oldInterruptible: Boolean): ZIO[R, E, A] = effect
      }
      final case class Dynamic[R, E, A](trace: ZTraceElement, newInterruptible: Boolean, f: Boolean => ZIO[R, E, A])
          extends ChangeInterruptionWithin[R, E, A] {
        def scope(oldInterruptible: Boolean): ZIO[R, E, A] = f(oldInterruptible)
      }
    }
    final case class GenerateStackTrace(trace: ZTraceElement) extends ZIO[Any, Nothing, ZTrace]
    final case class Stateful[R, E, A](
      trace: ZTraceElement,
      onState: (FiberState[E, A], Boolean, ZTraceElement) => ZIO[R, E, A]
    ) extends ZIO[R, E, A] { self =>
      def erase: Stateful[Any, Any, Any] = self.asInstanceOf[Stateful[Any, Any, Any]]
    }
    final case class Refail[E](cause: Cause[E]) extends ZIO[Any, E, Nothing] {
      def trace: ZTraceElement = ZTraceElement.empty
    }
    final case class InterruptSignal(cause: Cause[Nothing], trace: ZTraceElement) extends ZIO[Any, Nothing, Unit]

    sealed abstract class ReifyStack extends Exception with NoStackTrace { self =>
      def addContinuation(continuation: EvaluationStep.Continuation[_, _, _, _, _]): Nothing =
        self.addAndThrow(continuation)

      def changeInterruptibility(interruptible: Boolean): Nothing =
        self.addAndThrow(EvaluationStep.ChangeInterruptibility(interruptible))

      def stack: ChunkBuilder[EvaluationStep]

      private final def addAndThrow(k: EvaluationStep): Nothing = {
        stack += (k)
        throw this
      }
    }
    object ReifyStack {
      final case class AsyncJump(
        registerCallback: (ZIO[Any, Any, Any] => Unit) => Unit,
        stack: ChunkBuilder[EvaluationStep]
      ) extends ReifyStack

      final case class Trampoline(effect: ZIO[Any, Any, Any], stack: ChunkBuilder[EvaluationStep]) extends ReifyStack

      final case class GenerateTrace(stack: ChunkBuilder[EvaluationStep]) extends ReifyStack
    }

    def async[R, E, A](registerCallback: (ZIO[R, E, A] => Unit) => Unit)(implicit
      trace: ZTraceElement
    ): ZIO[R, E, A] =
      Async(trace, registerCallback)

    def done[E, A](exit: => Exit[E, A]): ZIO[Any, E, A] =
      exit match {
        case Exit.Success(a) => ZIO.succeed(a)
        case Exit.Failure(c) => ZIO.failCause(c)
      }

    def fail[E](e: => E)(implicit trace: ZTraceElement): ZIO[Any, E, Nothing] = failCause(Cause.fail(e))

    def failCause[E](c: => Cause[E])(implicit trace0: ZTraceElement): ZIO[Any, E, Nothing] =
      ZIO.trace(trace0).flatMap(trace => refailCause(c.traced(trace)))

    def refailCause[E](cause: Cause[E])(implicit trace: ZTraceElement): ZIO[Any, E, Nothing] = Refail(cause)

    def succeed[A](a: => A)(implicit trace: ZTraceElement): ZIO[Any, Nothing, A] = Sync(trace, () => a)

    def suspendSucceed[R, E, A](effect: => ZIO[R, E, A])(implicit trace: ZTraceElement): ZIO[R, E, A] =
      ZIO.succeed(effect).flatMap(fastIdentity[ZIO[R, E, A]])

    private val identityFn: Any => Any  = identity
    private def fastIdentity[A]: A => A = identityFn.asInstanceOf[A => A]

    def trace(implicit trace: ZTraceElement): ZIO[Any, Nothing, ZTrace] =
      GenerateStackTrace(trace)

    def uninterruptibleMask[R, E, A](
      f: InterruptibilityRestorer => ZIO[R, E, A]
    )(implicit trace: ZTraceElement): ZIO[R, E, A] =
      ZIO.ChangeInterruptionWithin.Dynamic(
        trace,
        false,
        old =>
          if (old) f(InterruptibilityRestorer.MakeInterruptible)
          else f(InterruptibilityRestorer.MakeUninterruptible)
      )

    val unit: ZIO[Any, Nothing, Unit] = ZIO.succeed(())(ZTraceElement.empty)

    def yieldNow(implicit trace: ZTraceElement): ZIO[Any, Nothing, Unit] =
      async[Any, Nothing, Unit](k => k(ZIO.unit))

    final case class ZIOError(cause: Cause[Any]) extends Exception with NoStackTrace

    sealed trait InterruptibilityRestorer {
      def apply[R, E, A](effect: ZIO[R, E, A])(implicit trace: ZTraceElement): ZIO[R, E, A]
    }
    object InterruptibilityRestorer {
      case object MakeInterruptible extends InterruptibilityRestorer {
        def apply[R, E, A](effect: ZIO[R, E, A])(implicit trace: ZTraceElement): ZIO[R, E, A] =
          ZIO.ChangeInterruptionWithin.Interruptible(trace, effect)
      }
      case object MakeUninterruptible extends InterruptibilityRestorer {
        def apply[R, E, A](effect: ZIO[R, E, A])(implicit trace: ZTraceElement): ZIO[R, E, A] =
          ZIO.ChangeInterruptionWithin.Uninterruptible(trace, effect)
      }
    }

    def evalAsync[R, E, A](
      effect: ZIO[R, E, A],
      onDone: Exit[E, A] => Unit,
      maxDepth: Int = 1000,
      fiberRefs0: FiberRefs = FiberRefs.empty
    )(implicit trace0: ZTraceElement): Exit[E, A] = {
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
  }
}

package object zio2 {
  type UIO[+A] = ZIO[Any, Nothing, A]
  type Erased  = ZIO[Any, Any, Any]
}
