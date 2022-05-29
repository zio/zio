package zio.internal

import scala.concurrent._
import scala.annotation.tailrec
import scala.util.control.NoStackTrace

import java.util.{Set => JavaSet}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import zio._

class RuntimeFiber[E, A](fiberId: FiberId.Runtime, fiberRefs: FiberRefs) extends FiberState[E, A](fiberId, fiberRefs) {
  self =>
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

  def status(implicit trace: Trace): UIO[zio.Fiber.Status] = ZIO.succeed(self.unsafeGetStatus())

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

object RuntimeFiber {
  import java.util.concurrent.atomic.AtomicBoolean

  def apply[E, A](fiberId: FiberId.Runtime, fiberRefs: FiberRefs): RuntimeFiber[E, A] =
    new RuntimeFiber(fiberId, fiberRefs)

  // FIXME: Make this work
  private[zio] val catastrophicFailure: AtomicBoolean = new AtomicBoolean(false)
}
