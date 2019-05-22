/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

package scalaz.zio.internal

import java.util.concurrent.atomic.{ AtomicLong, AtomicReference }

import scalaz.zio.Exit.Cause
import scalaz.zio._
import scalaz.zio.internal.stacktracer.ZTraceElement
import scalaz.zio.internal.tracing.ZIOFn

import scala.annotation.{ switch, tailrec }

/**
 * An implementation of Fiber that maintains context necessary for evaluation.
 */
private[zio] final class FiberContext[E, A](
  platform: Platform,
  startEnv: AnyRef,
  parentTrace: Option[ZTrace],
  initialTracingStatus: Boolean
) extends Fiber[E, A] {
  import java.util.{ Collections, Set }

  import FiberContext._
  import FiberState._

  // Accessed from multiple threads:
  private[this] val state = new AtomicReference[FiberState[E, A]](FiberState.Initial[E, A])

  @volatile private[this] var interrupted = false

  // Accessed from within a single thread (not necessarily the same):
  @volatile private[this] var supervising = 0

  private[this] val fiberId         = FiberContext.fiberCounter.getAndIncrement()
  private[this] val interruptStatus = StackBool()
  private[this] val stack           = Stack[Any => IO[Any, Any]]()
  private[this] val environment     = Stack[AnyRef](startEnv)
  private[this] val locked          = Stack[Executor]()
  private[this] val supervised      = Stack[Set[FiberContext[_, _]]]()

  private[this] val tracingStatus =
    if (tracingEnabled) StackBool()
    else null
  private[this] val execTrace =
    if (traceExec) SingleThreadedRingBuffer[ZTraceElement](platform.tracingConfig.executionTraceLength)
    else null
  private[this] val stackTrace =
    if (traceStack) SingleThreadedRingBuffer[ZTraceElement](platform.tracingConfig.stackTraceLength)
    else null

  private[this] val tracer = platform.tracer

  @inline
  private[this] final def traceExec: Boolean =
    PlatformConstants.tracingSupported && platform.tracingConfig.traceExecution

  @inline
  private[this] final def traceStack: Boolean = PlatformConstants.tracingSupported && platform.tracingConfig.traceStack

  @inline
  private[this] final def tracingEnabled: Boolean = traceExec || traceStack

  @inline
  private[this] final def traceEffects: Boolean = traceExec && platform.tracingConfig.traceEffectOpsInExecution

  @noinline
  private[this] final def inTracingRegion: Boolean =
    if (tracingStatus ne null) tracingStatus.peekOrElse(initialTracingStatus) else false

  @noinline
  private[this] final def unwrap(lambda: AnyRef): AnyRef =
    // This is a huge hotspot, hiding loop under
    // the match allows a faster happy path
    lambda match {
      case fn: ZIOFn =>
        var unwrapped = fn.underlying
        while (unwrapped.isInstanceOf[ZIOFn]) {
          unwrapped = unwrapped.asInstanceOf[ZIOFn].underlying
        }
        unwrapped
      case _ => lambda
    }

  // FIXME: bench
  @noinline
  private[this] final def traceLocation(lambda: AnyRef): ZTraceElement =
    tracer.traceLocation(unwrap(lambda))

  @noinline
  private[this] final def addTrace(lambda: AnyRef): Unit =
    execTrace.put(traceLocation(lambda))

  @noinline
  private[this] final def addStackTrace(lambda: AnyRef): Unit =
    stackTrace.put(traceLocation(lambda))

  private[this] final def popStackTrace(): Unit =
    stackTrace.dropLast()

  private[this] final def captureTrace(lastStack: ZTraceElement): ZTrace = {
    val exec   = if (execTrace ne null) execTrace.toReversedList else Nil
    val stack  = {
      val stack0 = if (stackTrace ne null) stackTrace.toReversedList else Nil
      if (lastStack ne null) lastStack :: stack0 else stack0
    }
    ZTrace(fiberId, exec, stack, parentTrace)
  }

  private[this] final def cutAncestryTrace(trace: ZTrace): ZTrace = {
    val maxExecLength  = platform.tracingConfig.ancestorExecutionTraceLength
    val maxStackLength = platform.tracingConfig.ancestorStackTraceLength

    trace.copy(
      executionTrace = trace.executionTrace.take(maxExecLength),
      stackTrace = trace.stackTrace.take(maxStackLength)
    )
  }

  final def runAsync(k: Callback[E, A]): Unit =
    register0(xx => k(Exit.flatten(xx))) match {
      case null =>
      case v    => k(v)
    }

  private object InterruptExit extends Function[Any, IO[E, Any]] {
    final def apply(v: Any): IO[E, Any] = {
      val isInterruptible = interruptStatus.peekOrElse(true)

      if (isInterruptible) {
        interruptStatus.popDrop(())

        ZIO.succeed(v)
      } else {
        ZIO.effectTotal { interruptStatus.popDrop(v) }
      }
    }
  }

  /**
   * Unwinds the stack, looking for the first error handler, and exiting
   * interruptible / uninterruptible regions.
   */
  final def unwindStack(): Unit = {
    var unwinding = true

    // Unwind the stack, looking for an error handler:
    while (unwinding && !stack.isEmpty) {
      stack.pop() match {
        case InterruptExit =>
          // do not remove InterruptExit from stack trace as it was not added
          interruptStatus.popDrop(())

        case fold: ZIO.Fold[_, _, _, _, _] if allowRecovery =>
          // Push error handler back onto the stack and halt iteration:
          val k = fold.failure.asInstanceOf[Any => ZIO[Any, Any, Any]]

          if (traceStack && inTracingRegion) {
            popStackTrace(); addStackTrace(k)
          }
          stack.push(k)

          unwinding = false

        case _ =>
          if (traceStack && inTracingRegion) popStackTrace()
      }
    }
  }

  private[this] final def executor: Executor = locked.peekOrElse(platform.executor)

  /**
   * The main interpreter loop for `IO` actions. For purely synchronous actions,
   * this will run to completion unless required to yield to other fibers.
   * For mixed actions, the loop will proceed no further than the first
   * asynchronous boundary.
   *
   * @param io0 The `IO` to evaluate on the fiber.
   */
  final def evaluateNow(io0: IO[E, _]): Unit = {
    // Do NOT accidentally capture `curIo` in a closure, or Scala will wrap
    // it in `ObjectRef` and performance will plummet.
    var curIo: IO[E, Any] = io0

    // Put the stack reference on the stack:
    val stack = this.stack

    // Put the maximum operation count on the stack for fast access:
    val maxopcount = executor.yieldOpCount

    // Put tracing configuration on the stack:
    val traceExec    = this.traceExec
    val traceStack   = this.traceStack
    val traceEffects = this.traceEffects

    // Store the trace of the immediate future flatMap during evaluation
    // of a 1-hop left bind, to show a stack trace closer to the point of failure
    var fastPathFlatMapContinuationTrace: ZTraceElement = null

    @noinline def fastPathTrace(k: Any => ZIO[Any, E, Any], effect: AnyRef): ZTraceElement = {
      if (inTracingRegion) {
        val kTrace = traceLocation(k)

        if (traceEffects) addTrace(effect)
        // record the nearest continuation for a better trace in case of failure
        if (traceStack) fastPathFlatMapContinuationTrace = kTrace

        kTrace
      }
      else null
    }

    while (curIo ne null) {
      try {
        var opcount: Int = 0

        while (curIo ne null) {
          val tag = curIo.tag

          // Check to see if the fiber should continue executing or not:
          if (tag == ZIO.Tags.Fail || !shouldInterrupt) {
            // Fiber does not need to be interrupted, but might need to yield:
            if (opcount == maxopcount) {
              // Cannot capture `curIo` since it will be boxed into `ObjectRef`,
              // which destroys performance. So put `curIo` into a temp val:
              val tmpIo = curIo

              curIo = ZIO.yieldNow *> tmpIo

              opcount = 0
            } else {
              // Fiber is neither being interrupted nor needs to yield. Execute
              // the next instruction in the program:
              (tag: @switch) match {
                case ZIO.Tags.FlatMap =>
                  val io = curIo.asInstanceOf[ZIO.FlatMap[Any, E, Any, Any]]

                  val nested = io.zio
                  val k      = io.k

                  // A mini interpreter for the left side of FlatMap that evaluates
                  // anything that is 1-hop away. This eliminates heap usage for the
                  // happy path.
                  (nested.tag: @switch) match {
                    case ZIO.Tags.Succeed =>
                      val io2 = nested.asInstanceOf[ZIO.Succeed[Any]]

                      if (traceExec && inTracingRegion) addTrace(k)

                      curIo = k(io2.value)

                    case ZIO.Tags.EffectTotal =>
                      val io2    = nested.asInstanceOf[ZIO.EffectTotal[Any]]
                      val effect = io2.effect

                      val kTrace = fastPathTrace(k, effect)

                      val value = effect()

                      // delete continuation as it was "popped" after success
                      if (traceStack && (kTrace ne null)) fastPathFlatMapContinuationTrace = null
                      // record continuation in exec as we're just "passing" it
                      if (traceExec && (kTrace ne null)) execTrace.put(kTrace)

                      curIo = k(value)

                    case ZIO.Tags.EffectPartial =>
                      val io2    = nested.asInstanceOf[ZIO.EffectPartial[Any]]
                      val effect = io2.effect

                      val kTrace = fastPathTrace(k, effect)

                      var failIO = null.asInstanceOf[IO[E, Any]]
                      val value = try effect()
                      catch {
                        case t: Throwable if !platform.fatal(t) =>
                          failIO = ZIO.fail(t.asInstanceOf[E])
                      }

                      if (failIO eq null) {
                        // delete continuation as it was "popped" after success
                        if (traceStack && (kTrace ne null)) fastPathFlatMapContinuationTrace = null
                        // record continuation in exec as we're just "passing" it
                        if (traceExec && (kTrace ne null)) execTrace.put(kTrace)

                        curIo = k(value)
                      } else {
                        curIo = failIO
                      }

                    case _ =>
                      // Fallback case. We couldn't evaluate the LHS so we have to
                      // use the stack:
                      curIo = nested
                      if (traceStack && inTracingRegion) addStackTrace(k)
                      stack.push(k)
                  }

                case ZIO.Tags.Succeed =>
                  val io = curIo.asInstanceOf[ZIO.Succeed[Any]]

                  val value = io.value

                  curIo = nextInstr(value)

                case ZIO.Tags.EffectTotal =>
                  val io     = curIo.asInstanceOf[ZIO.EffectTotal[Any]]
                  val effect = io.effect

                  if (traceEffects && inTracingRegion) addTrace(effect)

                  curIo = nextInstr(effect())

                case ZIO.Tags.Fail =>
                  val io = curIo.asInstanceOf[ZIO.Fail[E, Any]]

                  // Put last trace into a val to avoid `ObjectRef` boxing.
                  val fastPathTrace = fastPathFlatMapContinuationTrace
                  fastPathFlatMapContinuationTrace = null

                  val cause0 = io.fill(() => captureTrace(fastPathTrace))

                  unwindStack()

                  if (stack.isEmpty) {
                    // Error not caught, stack is empty:
                    curIo = null

                    val cause =
                      if (interrupted && !cause0.interrupted) cause0 ++ Cause.interrupt
                      else cause0

                    done(Exit.halt(cause))
                  } else {
                    // Error caught, next continuation on the stack will deal
                    // with it, so we just have to compute it here:
                    curIo = nextInstr(cause0)
                  }

                case ZIO.Tags.Fold =>
                  val io = curIo.asInstanceOf[ZIO.Fold[Any, E, Any, Any, Any]]

                  curIo = io.value
                  if (traceStack && inTracingRegion) addStackTrace(io)
                  stack.push(io)

                case ZIO.Tags.InterruptStatus =>
                  val io = curIo.asInstanceOf[ZIO.InterruptStatus[Any, E, Any]]

                  interruptStatus.push(io.flag)
                  // do not add InterruptExit to the stack trace
                  stack.push(InterruptExit)

                  curIo = io.zio

                case ZIO.Tags.TracingStatus =>
                  val io = curIo.asInstanceOf[ZIO.TracingStatus[Any, E, Any]]

                  curIo = tracingRegion(io.flag).bracket_(endTracingRegion, io.zio)

                case ZIO.Tags.CheckInterrupt =>
                  val io = curIo.asInstanceOf[ZIO.CheckInterrupt[Any, E, Any]]

                  curIo = io.k(interruptible)

                case ZIO.Tags.EffectPartial =>
                  val io     = curIo.asInstanceOf[ZIO.EffectPartial[Any]]
                  val effect = io.effect

                  if (traceEffects && inTracingRegion) addTrace(effect)

                  var nextIo = null.asInstanceOf[IO[E, Any]]
                  val value = try effect()
                  catch {
                    case t: Throwable if !platform.fatal(t) =>
                      nextIo = ZIO.fail(t.asInstanceOf[E])
                  }
                  if (nextIo eq null) curIo = nextInstr(value)
                  else curIo = nextIo

                case ZIO.Tags.EffectTotalWith =>
                  val io     = curIo.asInstanceOf[ZIO.EffectTotalWith[Any]]
                  val effect = io.effect

                  if (traceEffects && inTracingRegion) addTrace(effect)

                  val value = effect(platform)

                  curIo = nextInstr(value)

                case ZIO.Tags.EffectAsync =>
                  val io = curIo.asInstanceOf[ZIO.EffectAsync[Any, E, Any]]

                  // Enter suspended state:
                  curIo = if (enterAsync()) {
                    val k = io.register

                    if (traceEffects && inTracingRegion) addTrace(k)

                    k(resumeAsync) match {
                      case Some(io) => if (exitAsync()) io else null
                      case None     => null
                    }
                  } else ZIO.interrupt

                case ZIO.Tags.Fork =>
                  val io = curIo.asInstanceOf[ZIO.Fork[Any, _, Any]]

                  val value: FiberContext[_, Any] = fork(io.value)

                  supervise(value)

                  curIo = nextInstr(value)

                case ZIO.Tags.Supervised =>
                  val io = curIo.asInstanceOf[ZIO.Supervised[Any, E, Any]]

                  curIo = enterSupervision.bracket_(exitSupervision, io.value)

                case ZIO.Tags.Descriptor =>
                  val io = curIo.asInstanceOf[ZIO.Descriptor[Any, E, Any]]

                  val k = io.k
                  if (traceExec && inTracingRegion) addTrace(k)

                  curIo = k(getDescriptor)

                case ZIO.Tags.Lock =>
                  val io = curIo.asInstanceOf[ZIO.Lock[Any, E, Any]]

                  curIo = lock(io.executor).bracket_(unlock, io.zio)

                case ZIO.Tags.Yield =>
                  evaluateLater(ZIO.unit)

                  curIo = null

                case ZIO.Tags.Access =>
                  val io = curIo.asInstanceOf[ZIO.Read[Any, E, Any]]

                  val k = io.k
                  if (traceExec && inTracingRegion) addTrace(k)

                  curIo = k(environment.peek())

                case ZIO.Tags.Provide =>
                  val io = curIo.asInstanceOf[ZIO.Provide[Any, E, Any]]

                  val push = ZIO.effectTotal(environment.push(io.r.asInstanceOf[AnyRef]))
                  val pop  = ZIO.effectTotal(environment.pop())

                  curIo = push.bracket_(pop, io.next)

                case ZIO.Tags.SuspendWith =>
                  val io = curIo.asInstanceOf[ZIO.SuspendWith[Any, E, Any]]

                  curIo = io.f(platform)

                case ZIO.Tags.Trace =>
                  curIo = nextInstr(captureTrace(null))
              }
            }
          } else {
            // Fiber was interrupted
            curIo = ZIO.interrupt
          }

          opcount = opcount + 1
        }
      } catch {
        case _: InterruptedException =>
          Thread.interrupted
          curIo = ZIO.interrupt

        // Catastrophic error handler. Any error thrown inside the interpreter is
        // either a bug in the interpreter or a bug in the user's code. Let the
        // fiber die but attempt finalization & report errors.
        case t: Throwable =>
          if (platform.fatal(t)) {
            t.printStackTrace()
            try System.exit(-1)
            catch { case _: SecurityException => throw t }
          } else curIo = ZIO.die(t)
      }
    }
  }

  private[this] final def lock(executor: Executor): UIO[Unit] =
    ZIO.effectTotal { locked.push(executor) } *> ZIO.yieldNow

  private[this] final def unlock: UIO[Unit] =
    ZIO.effectTotal { locked.pop() } *> ZIO.yieldNow

  private[this] final def tracingRegion(trace: Boolean): UIO[Unit] =
    IO.effectTotal { if (tracingStatus ne null) tracingStatus.push(trace) }

  private[this] final def endTracingRegion: UIO[Unit] =
    IO.effectTotal { if (tracingStatus ne null) tracingStatus.popDrop(()) }

  private[this] final def getDescriptor: Fiber.Descriptor =
    Fiber.Descriptor(fiberId, interrupted, interruptible, executor, getFibers)

  // We make a copy of the supervised fibers set as an array
  // to prevent mutations of the set from propagating to the caller.
  private[this] final def getFibers: UIO[IndexedSeq[Fiber[_, _]]] =
    UIO {
      val set = supervised.peekOrElse(null)

      if (set eq null) Array.empty[Fiber[_, _]]
      else {
        val arr = Array.ofDim[Fiber[_, _]](set.size)
        set.toArray[Fiber[_, _]](arr)
      }
    }

  /**
   * Forks an `IO` with the specified failure handler.
   */
  final def fork[E, A](io: IO[E, A]): FiberContext[E, A] = {
    val tracingRegion = inTracingRegion
    val ancestry =
      if (tracingEnabled && tracingRegion) Some(cutAncestryTrace(captureTrace(null)))
      else None

    val context = new FiberContext[E, A](platform, environment.peek(), ancestry, tracingRegion)

    platform.executor.submitOrThrow(() => context.evaluateNow(io))

    context
  }

  private[this] final def evaluateLater(io: IO[E, Any]): Unit =
    executor.submitOrThrow(() => evaluateNow(io))

  /**
   * Resumes an asynchronous computation.
   *
   * @param value The value produced by the asynchronous computation.
   */
  private[this] final val resumeAsync: IO[E, Any] => Unit =
    io => if (exitAsync()) evaluateLater(io)

  final def interrupt: UIO[Exit[E, A]] = ZIO.effectAsyncMaybe[Any, Nothing, Exit[E, A]] { k =>
    kill0(x => k(ZIO.done(x)))
  }

  final def await: UIO[Exit[E, A]] = ZIO.effectAsyncMaybe[Any, Nothing, Exit[E, A]] { k =>
    observe0(x => k(ZIO.done(x)))
  }

  final def poll: UIO[Option[Exit[E, A]]] = ZIO.effectTotal(poll0)

  private[this] final def enterSupervision: IO[E, Unit] = ZIO.effectTotal {
    supervising += 1

    def newWeakSet[A]: Set[A] = Collections.newSetFromMap[A](platform.newWeakHashMap[A, java.lang.Boolean]())

    val set = newWeakSet[FiberContext[_, _]]

    supervised.push(set)
  }

  private[this] final def supervise(child: FiberContext[_, _]): Unit =
    if (supervising > 0) {
      val set = supervised.peekOrElse(null)

      if (set ne null) {
        set.add(child); ()
      }
    }

  @tailrec
  private[this] final def enterAsync(): Boolean = {
    val oldState = state.get

    oldState match {
      case Executing(_, observers) =>
        val newState = Executing(FiberStatus.Suspended, observers)

        if (!state.compareAndSet(oldState, newState)) enterAsync()
        else if (shouldInterrupt) {
          // Fiber interrupted, so go back into running state:
          exitAsync()
          false
        } else true

      case _ => false
    }
  }

  @tailrec
  private[this] final def exitAsync(): Boolean = {
    val oldState = state.get

    oldState match {
      case Executing(FiberStatus.Suspended, observers) =>
        if (!state.compareAndSet(oldState, Executing(FiberStatus.Running, observers)))
          exitAsync()
        else true

      case _ => false
    }
  }

  private[this] final def exitSupervision: UIO[_] =
    ZIO.effectTotal {
      supervising -= 1
      supervised.pop()
    }

  @inline
  private[this] final def interruptible: Boolean =
    interruptStatus.peekOrElse(true)

  @inline
  private[this] final def shouldInterrupt: Boolean = interrupted && interruptible

  @inline
  private[this] final def allowRecovery: Boolean = !shouldInterrupt

  @inline
  private[this] final def nextInstr(value: Any): IO[E, Any] =
    if (!stack.isEmpty) {
      val k = stack.pop()

      if (inTracingRegion) {
        if (traceExec) addTrace(k)
        // do not remove InterruptExit from stack trace as it was not added
        if (traceStack && !k.isInstanceOf[InterruptExit.type]) popStackTrace()
      }

      k(value).asInstanceOf[IO[E, Any]]
    } else {
      done(Exit.succeed(value.asInstanceOf[A]))

      null
    }

  @tailrec
  private[this] final def done(v: Exit[E, A]): Unit = {
    val oldState = state.get

    oldState match {
      case Executing(_, observers: List[Callback[Nothing, Exit[E, A]]]) => // TODO: Dotty doesn't infer this properly
        if (!state.compareAndSet(oldState, Done(v))) done(v)
        else {
          notifyObservers(v, observers)
          reportUnhandled(v)
        }

      case Done(_) => // Huh?
    }
  }

  private[this] final def reportUnhandled(v: Exit[E, A]): Unit = v match {
    case Exit.Failure(cause) => platform.reportFailure(cause)

    case _ =>
  }

  @tailrec
  private[this] final def kill0(
    k: Callback[Nothing, Exit[E, A]]
  ): Option[IO[Nothing, Exit[E, A]]] = {

    val oldState = state.get

    oldState match {
      case Executing(FiberStatus.Suspended, observers0) if interruptible =>
        val observers = k :: observers0

        if (!state.compareAndSet(oldState, Executing(FiberStatus.Running, observers))) kill0(k)
        else {
          interrupted = true

          evaluateLater(ZIO.interrupt)

          None
        }

      case Executing(status, observers0) =>
        val observers = k :: observers0

        if (!state.compareAndSet(oldState, Executing(status, observers))) kill0(k)
        else {
          interrupted = true
          None
        }

      case Done(e) => Some(ZIO.succeed(e))
    }
  }

  private[this] final def observe0(
    k: Callback[Nothing, Exit[E, A]]
  ): Option[IO[Nothing, Exit[E, A]]] =
    register0(k) match {
      case null => None
      case x    => Some(ZIO.succeed(x))
    }

  @tailrec
  private[this] final def register0(k: Callback[Nothing, Exit[E, A]]): Exit[E, A] = {
    val oldState = state.get

    oldState match {
      case Executing(status, observers0) =>
        val observers = k :: observers0

        if (!state.compareAndSet(oldState, Executing(status, observers))) register0(k)
        else null

      case Done(v) => v
    }
  }

  private[this] final def poll0: Option[Exit[E, A]] =
    state.get match {
      case Done(r) => Some(r)
      case _       => None
    }

  private[this] final def notifyObservers(
    v: Exit[E, A],
    observers: List[Callback[Nothing, Exit[E, A]]]
  ): Unit = {
    val result = Exit.succeed(v)

    // To preserve fair scheduling, we submit all resumptions on the thread
    // pool in order of their submission.
    observers.reverse.foreach(
      k =>
        platform.executor
          .submitOrThrow(() => k(result))
    )
  }
}
private[zio] object FiberContext {
  val fiberCounter = new AtomicLong(0)

  sealed trait FiberStatus extends Serializable with Product
  object FiberStatus {
    case object Running   extends FiberStatus
    case object Suspended extends FiberStatus
  }

  sealed trait FiberState[+E, +A] extends Serializable with Product
  object FiberState extends Serializable {
    final case class Executing[E, A](
      status: FiberStatus,
      observers: List[Callback[Nothing, Exit[E, A]]]
    ) extends FiberState[E, A]
    final case class Done[E, A](value: Exit[E, A]) extends FiberState[E, A]

    def Initial[E, A] = Executing[E, A](FiberStatus.Running, Nil)
  }
}
