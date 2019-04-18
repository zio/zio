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

import scalaz.zio._

import scala.annotation.{ switch, tailrec }

/**
 * An implementation of Fiber that maintains context necessary for evaluation.
 */
private[zio] final class FiberContext[E, A](
  platform: Platform,
  startEnv: AnyRef
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
        case InterruptExit => interruptStatus.popDrop(())

        case fold: ZIO.Fold[_, _, _, _, _] if allowRecovery =>
          // Push error handler back onto the stack and halt iteration:
          stack.push(fold.failure.asInstanceOf[Any => ZIO[Any, Any, Any]])
          unwinding = false
        case _ =>
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

                  // A mini interpreter for the left side of FlatMap that evaluates
                  // anything that is 1-hop away. This eliminates heap usage for the
                  // happy path.
                  (nested.tag: @switch) match {
                    case ZIO.Tags.Succeed =>
                      val io2 = nested.asInstanceOf[ZIO.Succeed[Any]]

                      curIo = io.k(io2.value)

                    case ZIO.Tags.EffectTotalWith =>
                      val io2 = nested.asInstanceOf[ZIO.EffectTotalWith[Any]]

                      curIo = io.k(io2.effect(platform))

                    case ZIO.Tags.EffectTotal =>
                      val io2 = nested.asInstanceOf[ZIO.EffectTotal[Any]]

                      curIo = io.k(io2.effect())

                    case ZIO.Tags.EffectPartial =>
                      val io2 = nested.asInstanceOf[ZIO.EffectPartial[Any]]

                      var nextIo = null.asInstanceOf[IO[E, Any]]
                      val value = try io2.effect()
                      catch {
                        case t: Throwable if !platform.fatal(t) =>
                          nextIo = ZIO.fail(t.asInstanceOf[E])
                      }
                      if (nextIo eq null) curIo = io.k(value)
                      else curIo = nextIo

                    case _ =>
                      // Fallback case. We couldn't evaluate the LHS so we have to
                      // use the stack:
                      curIo = nested

                      stack.push(io.k)
                  }

                case ZIO.Tags.Succeed =>
                  val io = curIo.asInstanceOf[ZIO.Succeed[Any]]

                  val value = io.value

                  curIo = nextInstr(value)

                case ZIO.Tags.EffectTotal =>
                  val io = curIo.asInstanceOf[ZIO.EffectTotal[Any]]

                  curIo = nextInstr(io.effect())

                case ZIO.Tags.Fail =>
                  val io = curIo.asInstanceOf[ZIO.Fail[E, Any]]

                  unwindStack()

                  if (stack.isEmpty) {
                    // Error not caught, stack is empty:
                    curIo = null

                    val cause =
                      if (interrupted && !io.cause.interrupted) io.cause ++ Exit.Cause.interrupt
                      else io.cause

                    done(Exit.halt(cause))
                  } else {
                    // Error caught, next continuation on the stack will deal
                    // with it, so we just have to compute it here:
                    curIo = nextInstr(io.cause)
                  }

                case ZIO.Tags.Fold =>
                  val io = curIo.asInstanceOf[ZIO.Fold[Any, E, Any, Any, Any]]

                  curIo = io.value

                  stack.push(io)

                case ZIO.Tags.InterruptStatus =>
                  val io = curIo.asInstanceOf[ZIO.InterruptStatus[Any, E, Any]]

                  interruptStatus.push(io.flag)
                  stack.push(InterruptExit)

                  curIo = io.zio

                case ZIO.Tags.CheckInterrupt =>
                  val io = curIo.asInstanceOf[ZIO.CheckInterrupt[Any, E, Any]]

                  curIo = io.k(interruptible)

                case ZIO.Tags.EffectPartial =>
                  val io = curIo.asInstanceOf[ZIO.EffectPartial[Any]]

                  var nextIo = null.asInstanceOf[IO[E, Any]]
                  val value = try io.effect()
                  catch {
                    case t: Throwable if !platform.fatal(t) =>
                      nextIo = ZIO.fail(t.asInstanceOf[E])
                  }
                  if (nextIo eq null) curIo = nextInstr(value)
                  else curIo = nextIo

                case ZIO.Tags.EffectTotalWith =>
                  val io = curIo.asInstanceOf[ZIO.EffectTotalWith[Any]]

                  val value = io.effect(platform)

                  curIo = nextInstr(value)

                case ZIO.Tags.EffectAsync =>
                  val io = curIo.asInstanceOf[ZIO.EffectAsync[E, Any]]

                  // Enter suspended state:
                  curIo = if (enterAsync()) {
                    io.register(resumeAsync) match {
                      case Some(io) => if (exitAsync()) io else null
                      case None     => null
                    }
                  } else IO.interrupt

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

                  curIo = io.k(getDescriptor)

                case ZIO.Tags.Lock =>
                  val io = curIo.asInstanceOf[ZIO.Lock[Any, E, Any]]

                  curIo = lock(io.executor).bracket_(unlock, io.zio)

                case ZIO.Tags.Yield =>
                  evaluateLater(IO.unit)

                  curIo = null

                case ZIO.Tags.Access =>
                  val io = curIo.asInstanceOf[ZIO.Read[Any, E, Any]]

                  curIo = io.k(environment.peek())

                case ZIO.Tags.Provide =>
                  val io = curIo.asInstanceOf[ZIO.Provide[Any, E, Any]]

                  val push = ZIO.effectTotal(environment.push(io.r.asInstanceOf[AnyRef]))
                  val pop  = ZIO.effectTotal(environment.pop())

                  curIo = push.bracket_(pop, io.next)
              }
            }
          } else {
            // Fiber was interrupted
            curIo = IO.interrupt
          }

          opcount = opcount + 1
        }
      } catch {
        case _: InterruptedException =>
          Thread.interrupted
          curIo = IO.interrupt

        // Catastrophic error handler. Any error thrown inside the interpreter is
        // either a bug in the interpreter or a bug in the user's code. Let the
        // fiber die but attempt finalization & report errors.
        case t: Throwable if !platform.fatal(t) =>
          curIo = IO.die(t)
      }
    }
  }

  private[this] final def lock(executor: Executor): UIO[Unit] =
    IO.effectTotal { locked.push(executor) } *> IO.yieldNow

  private[this] final def unlock: UIO[Unit] =
    IO.effectTotal { locked.pop() } *> IO.yieldNow

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
    val context = new FiberContext[E, A](platform, environment.peek())

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

  final def interrupt: UIO[Exit[E, A]] = IO.effectAsyncMaybe[Nothing, Exit[E, A]] { k =>
    kill0(x => k(IO.done(x)))
  }

  final def await: UIO[Exit[E, A]] = IO.effectAsyncMaybe[Nothing, Exit[E, A]] { k =>
    observe0(x => k(IO.done(x)))
  }

  final def poll: UIO[Option[Exit[E, A]]] = IO.effectTotal(poll0)

  private[this] final def enterSupervision: IO[E, Unit] = IO.effectTotal {
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
    IO.effectTotal {
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
    if (!stack.isEmpty) stack.pop()(value).asInstanceOf[IO[E, Any]]
    else {
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

          evaluateLater(IO.interrupt)

          None
        }

      case Executing(status, observers0) =>
        val observers = k :: observers0

        if (!state.compareAndSet(oldState, Executing(status, observers))) kill0(k)
        else {
          interrupted = true
          None
        }

      case Done(e) => Some(IO.succeed(e))
    }
  }

  private[this] final def observe0(
    k: Callback[Nothing, Exit[E, A]]
  ): Option[IO[Nothing, Exit[E, A]]] =
    register0(k) match {
      case null => None
      case x    => Some(IO.succeed(x))
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
