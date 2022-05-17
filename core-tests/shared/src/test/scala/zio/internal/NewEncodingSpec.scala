package zio.internal

import zio.test._

object zio2 {
  import zio.{Cause, Chunk, ChunkBuilder, Exit, Fiber, FiberRef, FiberRefs, FiberId, ZTrace, ZTraceElement}

  import scala.concurrent._
  import scala.annotation.tailrec
  import scala.util.control.NoStackTrace

  import java.util.{Set => JavaSet}
  import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

  type UIO[+A] = zio2.ZIO[Any, Nothing, A]

  class RuntimeFiber[E, A](location0: ZTraceElement, fiberRefs: FiberRefs)
      extends FiberState[E, A](location0, fiberRefs) {

    def await(implicit trace: ZTraceElement): UIO[Exit[E, A]] = ???

    def children(implicit trace: ZTraceElement): UIO[Chunk[Fiber.Runtime[_, _]]] = ???

    def id: FiberId.Runtime = fiberId

    def inheritRefs(implicit trace: ZTraceElement): UIO[Unit] = ???

    def interruptAs(fiberId: FiberId)(implicit trace: ZTraceElement): UIO[Exit[E, A]] = ???

    def location: ZTraceElement = fiberId.location

    def poll(implicit trace: ZTraceElement): UIO[Option[Exit[E, A]]] = ???

    def status(implicit trace: ZTraceElement): UIO[Fiber.Status] = ???

    def trace(implicit trace: ZTraceElement): UIO[ZTrace] = ???
  }

  final case class FiberSuspension(blockingOn: FiberId, location: ZTraceElement)

  object FiberState {
    def apply[E, A](location: ZTraceElement, refs: FiberRefs): FiberState[E, A] = new FiberState(location, refs)
  }
  class FiberState[E, A](location0: ZTraceElement, fiberRefs0: FiberRefs) {
    // import FiberStatusIndicator.Status

    val mailbox     = new AtomicReference[UIO[Any]](ZIO.unit)
    val statusState = new FiberStatusState(new AtomicInteger(FiberStatusIndicator.initial))

    private var _children = null.asInstanceOf[JavaSet[FiberContext[_, _]]]
    var fiberRefs         = fiberRefs0
    var observers         = Nil: List[Exit[Nothing, Exit[E, A]] => Unit]
    var suspension        = null.asInstanceOf[FiberSuspension]
    val fiberId           = FiberId.unsafeMake(location0)

    @volatile var _exitValue = null.asInstanceOf[Exit[E, A]]

    final def evalOn(effect: UIO[Any], orElse: UIO[Any])(implicit trace: ZTraceElement): UIO[Unit] =
      ZIO.suspendSucceed {
        if (unsafeAddMessage(effect)) ZIO.unit else orElse.unit
      }

    /**
     * Adds a weakly-held reference to the specified fiber inside the children
     * set.
     */
    final def unsafeAddChild(child: FiberContext[_, _]): Unit = {
      if (_children eq null) {
        _children = Platform.newWeakSet[FiberContext[_, _]]()
      }
      _children.add(child)
      ()
    }

    /**
     * Adds a message to the mailbox and returns true if the state is not done.
     * Otherwise, returns false to indicate the fiber cannot accept messages.
     */
    final def unsafeAddMessage(effect: UIO[Any]): Boolean = {
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

    /**
     * Adds an observer to the list of observers.
     */
    final def unsafeAddObserver(observer: Exit[Nothing, Exit[E, A]] => Unit): Unit =
      observers = observer :: observers

    /**
     * Attempts to place the state of the fiber in interruption, but only if the
     * fiber is currently asynchronously suspended (hence, "async
     * interruption").
     */
    final def unsafeAttemptAsyncInterrupt(asyncs: Int): Boolean =
      statusState.attemptAsyncInterrupt(asyncs)

    /**
     * Attempts to set the state of the fiber to done. This may fail if there
     * are pending messages in the mailbox, in which case those messages will be
     * returned. This method should only be called by the main loop of the
     * fiber.
     *
     * @return
     *   `null` if the state of the fiber was set to done, or the pending
     *   messages, otherwise.
     */
    final def unsafeAttemptDone(e: Exit[E, A]): UIO[Any] = {
      _exitValue = e

      if (statusState.attemptDone()) {
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
      statusState.enterSuspend(unsafeGetFiberRef(FiberRef.interruptible))

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

    /**
     * Retrieves the interruptibility status of the fiber state.
     */
    final def unsafeGetInterruptible(): Boolean = unsafeGetFiberRef(FiberRef.interruptible)

    /**
     * Determines if the fiber state contains messages to process by the fiber
     * run loop. Due to race conditions, if this method returns true, it means
     * only that, if the messages were not drained, there will be some messages
     * at some point later, before the fiber state transitions to done.
     */
    final def unsafeHasMessages(): Boolean = {
      val indicator = statusState.getIndicator()

      FiberStatusIndicator.getPendingMessages(indicator) > 0 || FiberStatusIndicator.getMessages(indicator)
    }

    /**
     * Removes the child from the children list.
     */
    final def unsafeRemoveChild(child: FiberContext[_, _]): Unit =
      if (_children ne null) {
        _children.remove(child)
        ()
      }

    /**
     * Removes the specified observer from the list of observers.
     */
    final def unsafeRemoveObserver(observer: Exit[Nothing, Exit[E, A]] => Unit): Unit =
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
      unsafeSetFiberRef(FiberRef.interruptible, interruptible)

    /**
     * Retrieves a snapshot of the status of the fibers.
     */
    final def unsafeStatus(): Fiber.Status = {
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

        Fiber.Status.Suspended(interrupting, interruptible, asyncs.toLong, blockingOn, asyncTrace)
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

    final def interruptible(implicit trace: ZTraceElement): ZIO[R, E, A] =
      ZIO.ChangeInterruptionWithin.Interruptible(trace, self)

    final def map[B](f: A => B)(implicit trace: ZTraceElement): ZIO[R, E, B] =
      self.flatMap(a => ZIO.succeed(f(a)))

    final def mapError[E2](f: E => E2)(implicit trace: ZTraceElement): ZIO[R, E2, A] =
      self.catchAll(e => ZIO.fail(f(e)))

    def trace: ZTraceElement

    final def uninterruptible(implicit trace: ZTraceElement): ZIO[R, E, A] =
      ZIO.ChangeInterruptionWithin.Uninterruptible(trace, self)

    final def unit(implicit trace: ZTraceElement): ZIO[R, E, Unit] = self.as(())
  }
  object ZIO {
    implicit class EffectThrowableSyntax[A](self: ZIO[Any, Throwable, A]) {
      def unsafeRun(maxDepth: Int = 1000): A = ZIO.eval(self, maxDepth)
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

    type Erased = ZIO[Any, Any, Any]

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

      final case class TraceGen(stack: ChunkBuilder[EvaluationStep]) extends ReifyStack
    }

    import ReifyStack.{AsyncJump, Trampoline, TraceGen}

    def async[R, E, A](registerCallback: (ZIO[R, E, A] => Unit) => Unit)(implicit
      trace: ZTraceElement
    ): ZIO[R, E, A] =
      Async(trace, registerCallback)

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

    private def assertNonNull(a: Any, message: String, location: ZTraceElement): Unit =
      if (a == null) {
        throw new NullPointerException(message + ": " + location.toString)
      }

    private def assertNonNullContinuation(a: Any, location: ZTraceElement): Unit =
      assertNonNull(a, "The return value of a success or failure handler must be non-null", location)

    def evalAsync[R, E, A](
      effect: ZIO[R, E, A],
      onDone: Exit[E, A] => Unit,
      maxDepth: Int = 1000,
      fiberRefs0: FiberRefs = FiberRefs.empty
    )(implicit trace0: ZTraceElement): Exit[E, A] = {
      def loop(
        fiberState: FiberState[Any, Any],
        effect: ZIO[Any, Any, Any],
        depth: Int,
        stack: Chunk[EvaluationStep],
        interruptible0: Boolean
      ): AnyRef = {
        import EvaluationStep._

        var cur           = effect
        var done          = null.asInstanceOf[AnyRef]
        var stackIndex    = 0
        var interruptible = interruptible0
        var lastTrace     = null.asInstanceOf[ZTraceElement]

        if (depth > maxDepth) {
          // Save local variables to heap:
          fiberState.unsafeSetInterruptible(interruptible)

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
                  cur = effect.onSuccess(loop(fiberState, effect.first, depth + 1, Chunk.empty, interruptible))
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

                      case k: ChangeInterruptibility => interruptible = k.interruptible
                      case k: UpdateTrace            => if (k.trace ne ZTraceElement.empty) lastTrace = k.trace
                    }
                  }

                  if (cur eq null) done = value.asInstanceOf[AnyRef]
                } catch {
                  case zioError: ZIOError =>
                    cur = Refail(zioError.cause)
                }

              case effect: Async[_, _, _] =>
                // Save local variables to heap:
                fiberState.unsafeSetInterruptible(interruptible)

                throw AsyncJump(effect.registerCallback, ChunkBuilder.make())

              case effect: ChangeInterruptionWithin[_, _, _] =>
                val oldInterruptible = interruptible

                interruptible = effect.newInterruptible

                cur =
                  try {
                    val value = loop(fiberState, effect.scope(oldInterruptible), depth + 1, Chunk.empty, interruptible)

                    interruptible = oldInterruptible

                    ZIO.succeed(value)
                  } catch {
                    case reifyStack: ReifyStack => reifyStack.changeInterruptibility(oldInterruptible)
                  }

              case generateStackTrace: GenerateStackTrace =>
                val builder = ChunkBuilder.make[EvaluationStep]()

                builder += EvaluationStep.UpdateTrace(generateStackTrace.trace)

                // Save local variables to heap:
                fiberState.unsafeSetInterruptible(interruptible)

                throw TraceGen(builder)

              case stateful: Stateful[_, _, _] =>
                cur = stateful.erase.onState(fiberState, interruptible, lastTrace)

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
                    case k: ChangeInterruptibility => interruptible = k.interruptible
                    case k: UpdateTrace            => if (k.trace ne ZTraceElement.empty) lastTrace = k.trace
                  }
                }

                if (cur eq null) throw ZIOError(cause)
            }
          } catch {
            case zioError: ZIOError => throw zioError

            case reifyStack: ReifyStack =>
              if (stackIndex < stack.length) reifyStack.stack ++= stack.drop(stackIndex)

              throw reifyStack

            case throwable: Throwable => // TODO: If non-fatal
              cur = Refail(Cause.die(throwable))
          }
        }

        done
      }

      def resumeOuterLoop(
        fiberState: FiberState[Any, Any],
        effect: ZIO[Any, Any, Any],
        stack: Chunk[EvaluationStep],
        onDone: Exit[Any, Any] => Unit
      ): Unit =
        scala.concurrent.ExecutionContext.global.execute { () =>
          outerLoop(fiberState, effect, stack, onDone)
          ()
        }

      @tailrec
      def outerLoop(
        fiberState: FiberState[Any, Any],
        effect: ZIO[Any, Any, Any],
        stack: Chunk[EvaluationStep],
        onDone: Exit[Any, Any] => Unit
      ): Exit[Any, Any] =
        try {
          val interruptible = fiberState.unsafeGetInterruptible()

          val exit: Exit[Nothing, Any] = Exit.succeed(loop(fiberState, effect, 0, stack, interruptible))

          onDone(exit)

          exit
        } catch {
          case trampoline: Trampoline =>
            outerLoop(fiberState, trampoline.effect, trampoline.stack.result(), onDone)

          case asyncJump: AsyncJump =>
            asyncJump.registerCallback { value =>
              resumeOuterLoop(fiberState, value, asyncJump.stack.result(), onDone)
            }

            null

          case zioError: ZIOError =>
            val exit = Exit.failCause(zioError.cause)

            onDone(exit)

            exit

          case traceGen: TraceGen =>
            val stack = traceGen.stack.result() // TODO: Don't build it, just iterate over it!

            val builder = StackTraceBuilder.unsafeMake()

            stack.foreach(k => builder += k.trace)

            val trace = ZTrace(fiberState.fiberId, builder.result())

            outerLoop(fiberState, ZIO.succeed(trace), stack, onDone)
        }

      val fiberState = FiberState[Any, Any](trace0, fiberRefs0)

      outerLoop(fiberState, effect.asInstanceOf[Erased], Chunk.empty, onDone.asInstanceOf[Exit[Any, Any] => Unit])
        .asInstanceOf[Exit[E, A]]
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

object NewEncodingSpec extends zio.ZIOBaseSpec {
  import zio.{Promise => _, _}

  import zio2.{ZIO => Effect}

  def newFib(n: Int): Effect[Any, Nothing, Int] =
    if (n <= 1) Effect.succeed(n)
    else
      for {
        a <- newFib(n - 1)
        b <- newFib(n - 2)
      } yield a + b

  def oldFib(n: Int): ZIO[Any, Nothing, Int] =
    if (n <= 1) ZIO.succeed(n)
    else
      for {
        a <- oldFib(n - 1)
        b <- oldFib(n - 2)
      } yield a + b

  def runFibTest(num: Int, maxDepth: Int = 1000) =
    test(s"fib(${num})") {
      for {
        actual   <- ZIO.succeed(newFib(num).unsafeRun(maxDepth))
        expected <- oldFib(num)
      } yield assertTrue(actual == expected)
    }

  def newSum(n: Int): Effect[Any, Nothing, Int] =
    Effect.succeed(n).flatMap(n => if (n <= 0) Effect.succeed(0) else newSum(n - 1).map(_ + n))

  def oldSum(n: Int): ZIO[Any, Nothing, Int] =
    ZIO.succeed(n).flatMap(n => if (n <= 0) ZIO.succeed(0) else oldSum(n - 1).map(_ + n))

  def runSumTest(num: Int, maxDepth: Int = 1000) =
    test(s"sum(${num})") {
      for {
        actual   <- ZIO.succeed(newSum(num).unsafeRun(maxDepth))
        expected <- oldSum(num)
      } yield assertTrue(actual == expected)
    }

  final case class Failed(value: Int) extends Exception

  def newFailAfter(n: Int): Effect[Any, Nothing, Int] = {
    def loop(i: Int): Effect[Any, Failed, Nothing] =
      if (i >= n) Effect.fail(Failed(i))
      else Effect.succeed(i).flatMap(j => loop(j + 1))

    loop(0).catchAll { case Failed(i) =>
      Effect.succeed(i)
    }
  }

  def oldFailAfter(n: Int): ZIO[Any, Nothing, Int] = {
    def loop(i: Int): ZIO[Any, Throwable, Nothing] =
      if (i >= n) ZIO.fail(Failed(i))
      else ZIO.succeed(i).flatMap(j => loop(j + 1))

    loop(0).catchAll {
      case Failed(i) => ZIO.succeed(i)
      case _         => ???
    }
  }

  def runFailAfterTest(num: Int, maxDepth: Int = 1000) =
    test(s"failAfter(${num})") {
      for {
        actual   <- ZIO.succeed(newFailAfter(num).unsafeRun(maxDepth))
        expected <- oldFailAfter(num)
      } yield assertTrue(actual == expected)
    }

  def newAsyncAfter(n: Int): Effect[Any, Nothing, Int] = {
    def loop(i: Int): Effect[Any, Nothing, Int] =
      if (i >= n) Effect.async[Any, Nothing, Int](k => k(Effect.succeed(i)))
      else Effect.succeed(i).flatMap(j => loop(j + 1)).map(_ + i)

    loop(0)
  }

  def oldAsyncAfter(n: Int): ZIO[Any, Nothing, Int] = {
    def loop(i: Int): ZIO[Any, Nothing, Int] =
      if (i >= n) ZIO.async[Any, Nothing, Int](k => k(ZIO.succeed(i)))
      else ZIO.succeed(i).flatMap(j => loop(j + 1)).map(_ + i)

    loop(0)
  }

  def newTerminalFail(n: Int): Effect[Any, Nothing, Exit[Failed, Int]] = {
    def loop(i: Int): Effect[Any, Failed, Nothing] =
      if (i >= n) Effect.fail(Failed(i))
      else Effect.succeed(i).flatMap(j => loop(j + 1))

    loop(0).exit
  }

  def oldTerminalFail(n: Int): ZIO[Any, Nothing, Exit[Failed, Int]] = {
    def loop(i: Int): ZIO[Any, Failed, Nothing] =
      if (i >= n) ZIO.fail(Failed(i))
      else ZIO.succeed(i).flatMap(j => loop(j + 1))

    loop(0).exit
  }

  def runTerminalFailTest(num: Int, maxDepth: Int = 1000) =
    test(s"terminalFail(${num})") {
      for {
        actual   <- ZIO.succeed(newTerminalFail(num).unsafeRun(maxDepth))
        expected <- oldTerminalFail(num)
      } yield assertTrue(actual == expected)
    }

  def runAsyncAfterTest(num: Int, maxDepth: Int = 1000) =
    test(s"asyncAfter(${num})") {
      for {
        actual   <- ZIO.succeed(newAsyncAfter(num).unsafeRun(maxDepth))
        expected <- oldAsyncAfter(num)
      } yield assertTrue(actual == expected)
    }

  def secondLevelCallStack =
    for {
      _ <- Effect.succeed(10)
      _ <- Effect.succeed(20)
      _ <- Effect.succeed(30)
      t <- Effect.trace
    } yield t

  def firstLevelCallStack =
    for {
      _ <- Effect.succeed(10)
      _ <- Effect.succeed(20)
      _ <- Effect.succeed(30)
      t <- secondLevelCallStack
    } yield t

  def stackTraceTest1 =
    for {
      _ <- Effect.succeed(10)
      _ <- Effect.succeed(20)
      _ <- Effect.succeed(30)
      t <- firstLevelCallStack
    } yield t

  def secondLevelCallStackFail =
    for {
      _ <- Effect.succeed(10)
      _ <- Effect.succeed(20)
      _ <- Effect.succeed(30)
      t <- Effect.fail("Uh oh!")
    } yield t

  def firstLevelCallStackFail =
    for {
      _ <- Effect.succeed(10)
      _ <- Effect.succeed(20)
      _ <- Effect.succeed(30)
      t <- secondLevelCallStackFail
    } yield t

  def stackTraceTest2 =
    for {
      _ <- Effect.succeed(10)
      _ <- Effect.succeed(20)
      _ <- Effect.succeed(30)
      t <- firstLevelCallStackFail
    } yield t

  def spec =
    suite("NewEncodingSpec") {
      suite("stack traces") {
        test("2nd-level trace") {
          for {
            t <- ZIO.succeed(stackTraceTest1.unsafeRun())
          } yield assertTrue(t.size == 3) &&
            assertTrue(t.stackTrace(0).toString().contains("secondLevelCallStack")) &&
            assertTrue(t.stackTrace(1).toString().contains("firstLevelCallStack")) &&
            assertTrue(t.stackTrace(2).toString().contains("stackTraceTest1"))
        } +
          test("2nd-level auto-trace through fail") {
            for {
              exit <- ZIO.succeed(stackTraceTest2.exit.unsafeRun())
              t     = exit.causeOption.get.trace
            } yield assertTrue(t.size == 4) &&
              assertTrue(t.stackTrace(0).toString().contains("secondLevelCallStackFail")) &&
              assertTrue(t.stackTrace(1).toString().contains("firstLevelCallStackFail")) &&
              assertTrue(t.stackTrace(2).toString().contains("stackTraceTest2")) &&
              assertTrue(t.stackTrace(3).toString().contains("spec"))
          }
      } +
        suite("fib") {
          runFibTest(0) +
            runFibTest(5) +
            runFibTest(10) +
            runFibTest(20)
        } +
        suite("fib - trampoline stress") {
          runFibTest(0, 2) +
            runFibTest(5, 2) +
            runFibTest(10, 2) +
            runFibTest(20, 2)
        } +
        suite("sum") {
          runSumTest(0) +
            runSumTest(100) +
            runSumTest(1000) +
            runSumTest(10000)
        } +
        suite("sum - trampoline stress") {
          runSumTest(0, 2) +
            runSumTest(100, 2) +
            runSumTest(1000, 2) +
            runSumTest(10000, 2)
        } +
        suite("failAfter") {
          runFailAfterTest(0) +
            runFailAfterTest(100) +
            runFailAfterTest(1000) +
            runFailAfterTest(10000)
        } +
        suite("failAfter - trampoline stress") {
          runFailAfterTest(0, 2) +
            runFailAfterTest(100, 2) +
            runFailAfterTest(1000, 2) +
            runFailAfterTest(10000, 2)
        } +
        suite("asyncAfter") {
          runAsyncAfterTest(0) +
            runAsyncAfterTest(100) +
            runAsyncAfterTest(1000) +
            runAsyncAfterTest(10000)
        } +
        suite("asyncAfter - trampoline stress") {
          runAsyncAfterTest(0, 2) +
            runAsyncAfterTest(100, 2) +
            runAsyncAfterTest(1000, 2) +
            runAsyncAfterTest(10000, 2)
        } +
        suite("terminalFail") {
          runTerminalFailTest(0) +
            runTerminalFailTest(100) +
            runTerminalFailTest(1000) +
            runTerminalFailTest(10000)
        } +
        suite("terminalFail - trampoline stress") {
          runTerminalFailTest(0, 2) +
            runTerminalFailTest(100, 2) +
            runTerminalFailTest(1000, 2) +
            runTerminalFailTest(10000, 2)
        } +
        suite("defects") {
          test("death in succeed") {
            for {
              result <- ZIO.succeed(Effect.succeed(throw TestException).exit.unsafeRun())
            } yield assertTrue(result.causeOption.get.defects(0) == TestException)
          } +
            test("death in succeed after async") {
              for {
                result <-
                  ZIO.succeed((Effect.unit *> Effect.yieldNow *> Effect.succeed(throw TestException)).exit.unsafeRun())
              } yield assertTrue(result.causeOption.get.defects(0) == TestException)
            } +
            suite("finalizers") {
              test("ensuring - success") {
                var finalized = false

                val finalize = Effect.succeed { finalized = true }

                for {
                  _ <- ZIO.succeed(Effect.succeed(()).ensuring(finalize).exit.unsafeRun())
                } yield assertTrue(finalized == true)
              } +
                test("ensuring - success after async") {
                  var finalized = false

                  val finalize = Effect.succeed { finalized = true }

                  for {
                    _ <- ZIO.succeed(
                           (Effect.unit *> Effect.yieldNow *> Effect.succeed(())).ensuring(finalize).exit.unsafeRun()
                         )
                  } yield assertTrue(finalized == true)
                } +
                test("ensuring - failure") {
                  var finalized = false

                  val finalize = Effect.succeed { finalized = true }

                  for {
                    _ <- ZIO.succeed(Effect.fail(()).ensuring(finalize).exit.unsafeRun())
                  } yield assertTrue(finalized == true)
                } +
                test("ensuring - failure after async") {
                  var finalized = false

                  val finalize = Effect.succeed { finalized = true }

                  for {
                    _ <- ZIO.succeed(
                           (Effect.unit *> Effect.yieldNow *> Effect.fail(())).ensuring(finalize).exit.unsafeRun()
                         )
                  } yield assertTrue(finalized == true)
                } +
                test("ensuring - double failure") {
                  var finalized = false

                  val finalize1 = Effect.succeed(throw TestException)
                  val finalize2 = Effect.succeed { finalized = true }

                  for {
                    _ <- ZIO.succeed(
                           Effect
                             .fail(())
                             .ensuring(finalize1)
                             .ensuring(finalize2)
                             .exit
                             .unsafeRun()
                         )
                  } yield assertTrue(finalized == true)
                } +
                test("foldCauseZIO finalization - success") {
                  var finalized = false

                  val finalize = Effect.succeed { finalized = true }

                  for {
                    _ <- ZIO.succeed(Effect.succeed(()).foldCauseZIO(_ => finalize, _ => finalize).exit.unsafeRun())
                  } yield assertTrue(finalized == true)
                } +
                test("foldCauseZIO finalization - failure") {
                  var finalized = false

                  val finalize = Effect.succeed { finalized = true }

                  for {
                    _ <- ZIO.succeed(Effect.fail(()).foldCauseZIO(_ => finalize, _ => finalize).exit.unsafeRun())
                  } yield assertTrue(finalized == true)
                } +
                test("foldCauseZIO nested finalization - double failure") {
                  var finalized = false

                  val finalize1 = Effect.succeed(throw TestException)
                  val finalize2 = Effect.succeed { finalized = true }

                  for {
                    _ <- ZIO.succeed(
                           Effect
                             .fail(())
                             .foldCauseZIO(_ => finalize1, _ => finalize1)
                             .foldCauseZIO(_ => finalize2, _ => finalize2)
                             .exit
                             .unsafeRun()
                         )
                  } yield assertTrue(finalized == true)
                }
            }
        }
    }
}

object TestException extends Exception("Test exception")
