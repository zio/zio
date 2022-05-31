package zio.internal

import scala.concurrent._
import scala.annotation.tailrec
import scala.util.control.NoStackTrace

import java.util.{Set => JavaSet}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}

import zio._

class RuntimeFiber[E, A](fiberId: FiberId.Runtime, fiberRefs: FiberRefs) extends FiberState[E, A](fiberId, fiberRefs) with Runnable {
  self =>
  type Erased = ZIO[Any, Any, Any]

  import ZIO._
  import EvaluationStep._
  import ReifyStack.{AsyncJump, Trampoline, GenerateTrace}

  def await(implicit trace: Trace): UIO[Exit[E, A]] =
    ZIO.async[Any, Nothing, Exit[E, A]] { cb =>
      self.unsafeEvalOn[Exit[E, A]] { fiber =>
        if (fiber.unsafeIsDone()) cb(ZIO.succeed(fiber.unsafeExitValue().asInstanceOf[Exit[E, A]]))
        else fiber.unsafeAddObserver(exit => cb(ZIO.succeed(exit.asInstanceOf[Exit[E, A]])))
      }
    }

  def children(implicit trace: Trace): UIO[Chunk[RuntimeFiber[_, _]]] =
    ask[Chunk[RuntimeFiber[_, _]]](fiber => Chunk.fromJavaIterable(fiber.unsafeGetChildren()))

  def id: FiberId.Runtime = fiberId

  def inheritRefs(implicit trace: Trace): UIO[Unit] =
    ZIO.fiberIdWith { parentFiberId =>
      ZIO.getFiberRefs.flatMap { parentFiberRefs =>
        val childFiberRefs   = self.asInstanceOf[FiberState[Any, Any]].unsafeGetFiberRefs()
        val updatedFiberRefs = parentFiberRefs.joinAs(parentFiberId.asInstanceOf[FiberId.Runtime])(childFiberRefs)
        ZIO.unsafeStateful[Any, Nothing, Unit] { (state, _, _) =>
          state.unsafeSetFiberRefs(updatedFiberRefs)
          ZIO.unit
        }
      }
    }

  def interruptAsFork(fiberId: FiberId)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed {
      val cause = Cause.interrupt(fiberId).traced(StackTrace(fiberId, Chunk(trace)))

      unsafeAddInterruptedCause(cause)

      val interrupt = ZIO.refailCause(cause)

      unsafeGetInterruptors().foreach { interruptor =>
        interruptor(interrupt)
      }
    }
  // type Interruptor = Cause[Nothing] => Unit
  // case class Async[R, E, A](registerCallback: (ZIO[R, E, A] => Unit) => Unit)

  final def location: Trace = fiberId.location

  final def poll(implicit trace: Trace): UIO[Option[Exit[E, A]]] =
    ZIO.succeed {
      if (self.unsafeIsDone()) Some(self.unsafeExitValue()) else None
    }

  def status(implicit trace: Trace): UIO[zio.Fiber.Status] = ZIO.succeed(self.unsafeGetStatus())

  def trace(implicit trace: Trace): UIO[StackTrace] = 
    ZIO.suspendSucceed {
      val promise = zio.Promise.unsafeMake[Nothing, StackTrace](fiberId)

      tell(FiberMessage.GenStackTrace(trace => promise.unsafeDone(ZIO.succeedNow(trace))))

      promise.await 
    }

  def ask[A](f: RuntimeFiber[Any, Any] => A)(implicit trace: Trace): UIO[A] = 
    ZIO.suspendSucceed {
      val promise = zio.Promise.unsafeMake[Nothing, A](fiberId)

      tell(FiberMessage.Stateful(fiber => promise.unsafeDone(ZIO.succeedNow(f(fiber)))))

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

  final def tell(message: FiberMessage): Unit = ???

  final def start[R](effect: ZIO[R, E, A]): Unit = {
    queue.add(FiberMessage.Resume(effect.asInstanceOf[ZIO[Any, Any, Any]], Chunk.empty))

    runHere()
  }

  final def startBackground[R](effect: ZIO[R, E, A]): Unit = {
    queue.add(FiberMessage.Resume(effect.asInstanceOf[ZIO[Any, Any, Any]], Chunk.empty))

    run()
  }

  final def evaluateMessage(fiberMessage: FiberMessage): Unit = 
    fiberMessage match {
      case FiberMessage.InterruptSignal(cause)  => self.unsafeAddInterruptedCause(cause)
      case FiberMessage.GenStackTrace(onTrace)  => onTrace(StackTrace(self.id, self.asyncStack.map(_.trace)))
      case FiberMessage.Stateful(onFiber)       => onFiber(self.asInstanceOf[RuntimeFiber[Any, Any]])
      case FiberMessage.Resume(effect, stack)   => evaluateEffect(effect, stack)
    }

  final def evaluateEffect(
    effect0: ZIO[Any, Any, Any],
    stack0: Chunk[EvaluationStep]
  ): Unit = {
    var effect = effect0
    var stack  = stack0 

    while (effect ne null) {
      try {
        runLoop(effect, 1000, stack, self.unsafeGetInterruptible())

        effect = null 
        stack  = Chunk.empty 
      } catch {
        case end @ ZIO.End(exit) => 
          self.unsafeAttemptDone(exit.asInstanceOf[Exit[E, A]])

          effect = null 
          stack  = Chunk.empty 

        case trampoline: Trampoline => 
          effect = trampoline.effect 
          stack  = trampoline.stack.result()

        case asyncJump: AsyncJump =>
          val nextStack     = asyncJump.stack.result()
          val alreadyCalled = new AtomicBoolean(false)

          self.asyncStack = nextStack
          
          lazy val callback: ZIO[Any, Any, Any] => Unit = (effect: ZIO[Any, Any, Any]) => {
            if (alreadyCalled.compareAndSet(false, true)) {
              val removeInterruptor = ZIO.succeed(unsafeRemoveInterruptor(callback))

              tell(FiberMessage.Resume(removeInterruptor *> effect, nextStack))
            }
          }

          // FIXME: Make sure it's safe to do this here!!!
          unsafeAddInterruptor(callback)

          // FIXME: registerCallback throws
          asyncJump.registerCallback(callback)

          effect = null 
          stack  = Chunk.empty 

        case traceGen: GenerateTrace =>
          val nextStack = traceGen.stack.result() // TODO: Don't build it, just iterate over it!

          val builder = StackTraceBuilder.unsafeMake()

          nextStack.foreach(k => builder += k.trace)

          val trace = StackTrace(self.fiberId, builder.result())

          effect = ZIO.succeed(trace)(Trace.empty)
          stack  = nextStack

        case zioError: ZIOError =>
          // No error should escape to this level.
          self.unsafeLog(() => s"An unhandled error was encountered while executing ${id.threadName}", zioError.cause, ZIO.someError, Trace.empty)

          effect = null 
          stack  = Chunk.empty 
      }
    }
  }

  /**
   * On the current thread, executes all messages in the fiber's inbox.
   * This method may return before all work is done, in the event the 
   * fiber executes an asynchronous operation.
   */
  final def runHere(): Unit = {
    while (!queue.isEmpty()) {
      val head = queue.poll()

      evaluateMessage(head)
    }
  }

  /**
   * Schedules the execution of all messages in the fiber's inbox on the correct
   * thread pool. This method will return immediately after the scheduling 
   * operation is completed, but potentially before such messages have been 
   * executed.
   */
  final def run(): Unit = {
    val currentExecutor = self.unsafeGetCurrentExecutor()

    currentExecutor.unsafeSubmitOrThrow(() => runHere())
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
    var lastTrace     = null.asInstanceOf[Trace] // TODO: Rip out

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

      while (!queue.isEmpty()) {
        queue.poll() match {
          case FiberMessage.InterruptSignal(cause) => 
            self.unsafeAddInterruptedCause(cause)

            cur = if (interruptible) Refail(cause) else cur

          case FiberMessage.GenStackTrace(onTrace) => 
            val oldCur = cur 

            cur = ZIO.trace(Trace.empty).map(onTrace(_)) *> oldCur

          case FiberMessage.Stateful(onFiber) => onFiber(self.asInstanceOf[RuntimeFiber[Any, Any]])

          case FiberMessage.Resume(effect, stack) => 
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

          case end @ End(_) => throw end
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
}

object RuntimeFiber {
  import java.util.concurrent.atomic.AtomicBoolean

  def apply[E, A](fiberId: FiberId.Runtime, fiberRefs: FiberRefs): RuntimeFiber[E, A] =
    new RuntimeFiber(fiberId, fiberRefs)

  // FIXME: Make this work
  private[zio] val catastrophicFailure: AtomicBoolean = new AtomicBoolean(false)
}
