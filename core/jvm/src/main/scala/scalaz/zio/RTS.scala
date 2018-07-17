// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio

import scala.annotation.switch
import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ Executors, TimeUnit }

/**
 * This trait provides a high-performance implementation of a runtime system for
 * the `IO` monad on the JVM.
 */
trait RTS {
  import RTS._

  /**
   * Effectfully and synchronously interprets an `IO[E, A]`, either throwing an
   * error, running forever, or producing an `A`.
   */
  final def unsafeRun[E, A](io: IO[E, A]): A = unsafeRunSync(io) match {
    case ExitResult.Completed(v)       => v
    case ExitResult.Terminated(Nil)    => throw Errors.InterruptedFiber
    case ExitResult.Terminated(t :: _) => throw t
    case ExitResult.Failed(e)          => throw Errors.UnhandledError(e)
  }

  final def unsafeRunAsync[E, A](io: IO[E, A])(k: Callback[E, A]): Unit = {
    val context = new FiberContext[E, A](this, defaultHandler)
    context.evaluate(io)
    context.runAsync(k)
  }

  /**
   * Effectfully interprets an `IO`, blocking if necessary to obtain the result.
   */
  final def unsafeRunSync[E, A](io: IO[E, A]): ExitResult[E, A] = {
    val context = new FiberContext[E, A](this, defaultHandler)
    context.evaluate(io)
    context.runSync
  }

  final def unsafeShutdownAndWait(timeout: Duration): Unit = {
    scheduledExecutor.shutdown()
    scheduledExecutor.awaitTermination(timeout.toMillis, TimeUnit.MILLISECONDS)
    threadPool.shutdown()
    threadPool.awaitTermination(timeout.toMillis, TimeUnit.MILLISECONDS)
    ()
  }

  /**
   * The default handler for unhandled exceptions in the main fiber, and any
   * fibers it forks that recursively inherit the handler.
   */
  def defaultHandler[E]: List[Throwable] => IO[E, Unit] =
    (ts: List[Throwable]) => IO.sync(ts.foreach(_.printStackTrace()))

  /**
   * The main thread pool used for executing fibers.
   */
  val threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors().max(2))

  /**
   * This determines the maximum number of resumptions placed on the stack
   * before a fiber is shifted over to a new thread to prevent stack overflow.
   */
  val MaxResumptionDepth = 10

  /**
   * Determines the maximum number of operations executed by a fiber before
   * yielding to other fibers.
   *
   * FIXME: Replace this entirely with the new scheme.
   */
  final val YieldMaxOpCount = 1048576

  lazy val scheduledExecutor = Executors.newScheduledThreadPool(1)

  final def submit[A](block: => A): Unit = {
    threadPool.submit(new Runnable {
      def run: Unit = { block; () }
    })

    ()
  }

  final def schedule[E, A](block: => A, duration: Duration): Async[E, Unit] =
    if (duration == Duration.Zero) {
      submit(block)

      Async.later[E, Unit]
    } else {
      val future = scheduledExecutor.schedule(new Runnable {
        def run: Unit = submit(block)
      }, duration.toNanos, TimeUnit.NANOSECONDS)

      Async.maybeLater { _ =>
        future.cancel(true); ()
      }
    }

  final def impureCanceler(canceler: PureCanceler): Canceler =
    th => unsafeRun(canceler(th))
}

private object RTS {
  // Utility function to avoid catching truly fatal exceptions. Do not allocate
  // memory here since this would defeat the point of checking for OOME.
  def nonFatal(t: Throwable): Boolean =
    !t.isInstanceOf[InternalError] && !t.isInstanceOf[OutOfMemoryError]

  sealed trait RaceState
  object RaceState {
    case object Started     extends RaceState
    case object FirstFailed extends RaceState
    case object Finished    extends RaceState
  }

  @inline
  final def nextInstr[E](value: Any, stack: Stack): IO[E, Any] =
    if (!stack.isEmpty) stack.pop()(value).asInstanceOf[IO[E, Any]] else null

  object IdentityCont extends Function[Any, IO[Any, Any]] {
    final def apply(v: Any): IO[Any, Any] = IO.now(v)
  }

  final class Stack() {
    type Cont = Any => IO[_, Any]

    private[this] var array   = new Array[AnyRef](13)
    private[this] var size    = 0
    private[this] var nesting = 0

    def isEmpty: Boolean = size == 0

    def push(a: Cont): Unit =
      if (size == 13) {
        array = Array(array, a, null, null, null, null, null, null, null, null, null, null, null)
        size = 2
        nesting += 1
      } else {
        array(size) = a
        size += 1
      }

    def pop(): Cont = {
      val idx = size - 1
      var a   = array(idx)
      if (idx == 0 && nesting > 0) {
        array = a.asInstanceOf[Array[AnyRef]]
        a = array(12)
        array(12) = null // GC
        size = 12
        nesting -= 1
      } else {
        array(idx) = null // GC
        size = idx
      }
      a.asInstanceOf[Cont]
    }
  }

  /**
   * An implementation of Fiber that maintains context necessary for evaluation.
   */
  final class FiberContext[E, A](rts: RTS, val unhandled: PureCanceler) extends Fiber[E, A] {
    import FiberStatus._
    import java.util.{ Collections, Set, WeakHashMap }
    import rts.{ MaxResumptionDepth, YieldMaxOpCount }

    // Accessed from multiple threads:
    private[this] val status = new AtomicReference[FiberStatus[E, A]](FiberStatus.Initial[E, A])
    private[this] var killed = false

    // TODO: A lot can be pulled out of status to increase performance
    // Also the size of this structure should be minimized with laziness used
    // to optimize further, to make forking a cheaper operation.

    // Accessed from within a single thread (not necessarily the same):
    @volatile private[this] var noInterrupt                               = 0
    @volatile private[this] var supervised: List[Set[FiberContext[_, _]]] = Nil
    @volatile private[this] var supervising                               = 0

    private[this] val stack: Stack = new Stack()

    final def runAsync(k: Callback[E, A]): Unit =
      register(k) match {
        case Async.Now(v) => k(v)
        case _            =>
      }

    /**
     * Effectfully interprets an `IO`, blocking if necessary to obtain the result.
     */
    final def runSync: ExitResult[E, A] = {
      val result = new AtomicReference[ExitResult[E, A]](null)

      register { (r: ExitResult[E, A]) =>
        result.synchronized {
          result.set(r)

          result.notifyAll()
        }
      } match {
        case Async.Now(v) =>
          result.set(v)

        case _ =>
          while (result.get eq null) {
            result.synchronized {
              if (result.get eq null) result.wait()
            }
          }
      }

      result.get
    }

    private class Finalizer(val finalizer: Infallible[Unit]) extends Function[Any, IO[E, Any]] {
      final def apply(v: Any): IO[E, Any] = {
        noInterrupt += 1

        finalizer.widenError[E].flatMap(_ => IO.sync { noInterrupt -= 1; v })
      }
    }

    private final def collectDefect[E, A](e: ExitResult[E, A]): List[Throwable] =
      e match {
        case ExitResult.Terminated(ts) => ts
        case _                         => Nil
      }

    /**
     * Catches an exception, returning a (possibly null) finalizer action that
     * must be executed. It is painstakingly *guaranteed* that the stack will be
     * empty in the sole case the exception was not caught by any exception
     * handler—i.e. the exceptional case.
     */
    final def catchError: IO[Void, List[Throwable]] = {
      var errorHandler: Any => IO[Any, Any]    = null
      var finalizer: IO[Void, List[Throwable]] = null

      // Unwind the stack, looking for exception handlers and coalescing
      // finalizers.
      while ((errorHandler eq null) && !stack.isEmpty) {
        stack.pop() match {
          case a: IO.Attempt[_, _, _, _] =>
            errorHandler = a.err.asInstanceOf[Any => IO[Any, Any]]
          case f0: Finalizer =>
            val f: IO[Void, List[Throwable]] = f0.finalizer.run.map(collectDefect)
            if (finalizer eq null) finalizer = f
            else finalizer = finalizer.zipWith(f)(_ ++ _)
          case _ =>
        }
      }

      // We need to maintain the invariant that an empty stack means the
      // exception was *not* caught.
      // The stack will never be empty if the error was caught, because
      // the error handler will be pushed onto the stack.
      // This lets us return only the finalizer, which will be null for common cases,
      // and result in zero heap allocations for the happy path.
      if (errorHandler ne null) stack.push(errorHandler)

      finalizer
    }

    /**
     * Empties the stack, collecting all finalizers and coalescing them into an
     * action that produces a list (possibly empty) of errors during finalization.
     */
    final def interruptStack: IO[Void, List[Throwable]] = {
      // Use null to achieve zero allocs for the common case of no finalizers:
      var finalizer: IO[Void, List[Throwable]] = null

      while (!stack.isEmpty) {
        // Peel off all the finalizers, composing them into a single finalizer
        // that produces a possibly empty list of errors that occurred when
        // executing the finalizers. The order of errors is outer-to-inner
        // (reverse chronological).
        stack.pop() match {
          case f0: Finalizer =>
            val f: IO[Void, List[Throwable]] = f0.finalizer.run.map(collectDefect)
            if (finalizer eq null) finalizer = f
            else finalizer = finalizer.zipWith(f)(_ ++ _)
          case _ =>
        }
      }

      finalizer
    }

    /**
     * The main interpreter loop for `IO` actions. For purely synchronous actions,
     * this will run to completion unless required to yield to other fibers.
     * For mixed actions, the loop will proceed no further than the first
     * asynchronous boundary.
     *
     * @param io0 The `IO` to evaluate on the fiber.
     */
    final def evaluate(io0: IO[E, _]): Unit = {
      // Do NOT accidentally capture any of local variables in a closure,
      // or Scala will wrap them in ObjectRef and performance will plummet.
      var curIo: IO[E, Any] = io0.asInstanceOf[IO[E, Any]]

      while (curIo ne null) {
        try {
          // Put the maximum operation count on the stack for fast access:
          val maxopcount = YieldMaxOpCount

          var result: ExitResult[E, Any] = null
          var opcount: Int               = 0

          while (curIo ne null) {
            // Check to see if the fiber should continue executing or not:
            val die = shouldDie

            if (die eq None) {
              // Fiber does not need to be interrupted, but might need to yield:
              if (opcount == maxopcount) {
                // Cooperatively yield to other fibers currently suspended.
                // FIXME: Replace with the new design.
                opcount = 0

                // Cannot capture `curIo` since it will be boxed into `ObjectRef`,
                // which destroys performance, so we create a temp val here.
                val tmpIo = curIo

                rts.submit(evaluate(tmpIo))

                curIo = null
              } else {
                // Fiber is neither being interrupted nor needs to yield. Execute
                // the next instruction in the program:
                (curIo.tag: @switch) match {
                  case IO.Tags.FlatMap =>
                    val io = curIo.asInstanceOf[IO.FlatMap[E, Any, Any]]

                    val nested = io.io

                    // A mini interpreter for the left side of FlatMap that evaluates
                    // anything that is 1-hop away. This eliminates heap usage for the
                    // happy path.
                    (nested.tag: @switch) match {
                      case IO.Tags.Point =>
                        val io2 = nested.asInstanceOf[IO.Point[E, Any]]

                        curIo = io.flatMapper(io2.value())

                      case IO.Tags.Strict =>
                        val io2 = nested.asInstanceOf[IO.Strict[E, Any]]

                        curIo = io.flatMapper(io2.value)

                      case IO.Tags.SyncEffect =>
                        val io2 = nested.asInstanceOf[IO.SyncEffect[E, Any]]

                        curIo = io.flatMapper(io2.effect())

                      case _ =>
                        // Fallback case. We couldn't evaluate the LHS so we have to
                        // use the stack:
                        curIo = nested

                        stack.push(io.flatMapper)
                    }

                  case IO.Tags.Point =>
                    val io = curIo.asInstanceOf[IO.Point[E, Any]]

                    val value = io.value()

                    curIo = nextInstr[E](value, stack)

                    if (curIo eq null) {
                      result = ExitResult.Completed(value)
                    }

                  case IO.Tags.Strict =>
                    val io = curIo.asInstanceOf[IO.Strict[E, Any]]

                    val value = io.value

                    curIo = nextInstr[E](value, stack)

                    if (curIo eq null) {
                      result = ExitResult.Completed(value)
                    }

                  case IO.Tags.SyncEffect =>
                    val io = curIo.asInstanceOf[IO.SyncEffect[E, Any]]

                    val value = io.effect()

                    curIo = nextInstr[E](value, stack)

                    if (curIo eq null) {
                      result = ExitResult.Completed(value)
                    }

                  case IO.Tags.Fail =>
                    val io = curIo.asInstanceOf[IO.Fail[E, Any]]

                    val error = io.error

                    val finalizer = catchError

                    if (stack.isEmpty) {
                      // Error not caught, stack is empty:
                      if (finalizer eq null) {
                        // No finalizer, so immediately produce the error.
                        curIo = null
                        result = ExitResult.Failed(error)

                        // Report the uncaught error to the supervisor:
                        rts.submit(rts.unsafeRun(unhandled(Errors.UnhandledError(error) :: Nil)))
                      } else {
                        // We have finalizers to run. We'll resume executing with the
                        // uncaught failure after we have executed all the finalizers:
                        val finalization = finalizer.flatMap(ts => unhandled(ts).const(ts))
                        val completer    = io

                        curIo = doNotInterrupt(finalization).widenError[E].flatMap {
                          case Nil => completer
                          case ts  => IO.terminate0(Errors.UnhandledError(error) :: ts)
                        }
                      }
                    } else {
                      // Error caught:
                      val handled = nextInstr[E](error, stack)

                      if (finalizer eq null) {
                        curIo = handled
                      } else {
                        // Must run finalizer first:
                        val finalization = finalizer.flatMap(ts => unhandled(ts).const(ts))
                        val completer    = handled

                        curIo = doNotInterrupt(finalization).widenError[E].flatMap {
                          case Nil => completer
                          case ts  => IO.terminate0(Errors.UnhandledError(error) :: ts)
                        }
                      }
                    }

                  case IO.Tags.AsyncEffect =>
                    val io = curIo.asInstanceOf[IO.AsyncEffect[E, Any]]

                    val id = enterAsyncStart()

                    try {
                      io.register(resumeAsync) match {
                        case Async.Now(value) =>
                          // Value returned synchronously, callback will never be
                          // invoked. Attempt resumption now:
                          if (shouldResumeAsync()) {
                            value match {
                              case ExitResult.Completed(v) =>
                                curIo = nextInstr[E](v, stack)

                                if (curIo eq null) {
                                  result = value
                                }
                              case ExitResult.Terminated(ts) =>
                                curIo = IO.terminate0(ts)
                              case ExitResult.Failed(e) =>
                                curIo = IO.fail(e)
                            }
                          } else {
                            // Completion handled by interruptor:
                            curIo = null
                          }

                        case Async.MaybeLater(canceler) =>
                          // We have a canceler, attempt to store a reference to
                          // it in case the async computation is interrupted:
                          awaitAsync(id, canceler)

                          curIo = null

                        case Async.MaybeLaterIO(pureCancel) =>
                          // As for the case above this stores an impure canceler
                          // obtained performing the pure canceler on the same thread
                          awaitAsync(id, rts.impureCanceler(pureCancel))

                          curIo = null
                      }
                    } finally enterAsyncEnd()

                  case IO.Tags.AsyncIOEffect =>
                    val io = curIo.asInstanceOf[IO.AsyncIOEffect[E, Any]]

                    enterAsyncStart()

                    try {
                      val value = rts.unsafeRunSync(io.register(resumeAsync))

                      // Value returned synchronously, callback will never be
                      // invoked. Attempt resumption now:
                      if (shouldResumeAsync()) {
                        value match {
                          case ExitResult.Completed(v) =>
                            curIo = nextInstr[E](v, stack)

                            if (curIo eq null) {
                              result = value.asInstanceOf[ExitResult[E, Any]]
                            }
                          case ExitResult.Terminated(ts) =>
                            curIo = IO.terminate0(ts)
                          case ExitResult.Failed(e) =>
                            curIo = IO.fail(e)
                        }
                      } else {
                        // Completion handled by interruptor:
                        curIo = null
                      }
                    } finally enterAsyncEnd()

                  case IO.Tags.Attempt =>
                    val io = curIo.asInstanceOf[IO.Attempt[E, Any, Any, Any]]

                    curIo = io.value

                    stack.push(io)

                  case IO.Tags.Fork =>
                    val io = curIo.asInstanceOf[IO.Fork[_, E, Any]]

                    val optHandler = io.handler

                    val handler = if (optHandler eq None) unhandled else optHandler.get

                    val value: FiberContext[_, Any] = fork(io.value, handler)

                    supervise(value)

                    curIo = nextInstr[E](value, stack)

                    if (curIo eq null) {
                      result = ExitResult.Completed(value)
                    }

                  case IO.Tags.Race =>
                    val io = curIo.asInstanceOf[IO.Race[E, Any, Any, Any]]

                    curIo = raceWith(unhandled, io.left, io.right, io.finishLeft, io.finishRight)

                  case IO.Tags.Suspend =>
                    val io = curIo.asInstanceOf[IO.Suspend[E, Any]]

                    curIo = io.value()

                  case IO.Tags.Uninterruptible =>
                    val io = curIo.asInstanceOf[IO.Uninterruptible[E, Any]]

                    curIo = doNotInterrupt(io.io).widenError[E]

                  case IO.Tags.Sleep =>
                    val io = curIo.asInstanceOf[IO.Sleep[E]]

                    curIo = IO.async0 { callback =>
                      rts
                        .schedule(callback(SuccessUnit[E].asInstanceOf[ExitResult[E, Any]]), io.duration)
                        .asInstanceOf[Async[E, Any]]
                    }

                  case IO.Tags.Supervise =>
                    val io = curIo.asInstanceOf[IO.Supervise[E, Any]]

                    curIo = enterSupervision *>
                      io.value.ensuring(exitSupervision(io.error))

                  case IO.Tags.Terminate =>
                    val io = curIo.asInstanceOf[IO.Terminate[E, Any]]

                    val causes = io.causes

                    val finalizer = interruptStack

                    if (finalizer eq null) {
                      // No finalizers, simply produce error:
                      curIo = null
                      result = ExitResult.Terminated(causes)

                      // Report the termination cause to the supervisor:
                      rts.submit(rts.unsafeRun(unhandled(causes)))
                    } else {
                      // Must run finalizers first before failing:
                      val finalization = finalizer.flatMap(unhandled)
                      val completer    = io

                      curIo = doNotInterrupt(finalization).widenError[E] *> completer
                    }

                  case IO.Tags.Supervisor =>
                    val value = unhandled

                    curIo = nextInstr[E](value, stack)

                    if (curIo eq null) {
                      result = ExitResult.Completed(value)
                    }

                  case IO.Tags.Run =>
                    val io = curIo.asInstanceOf[IO.Run[E, _, Any]]

                    val value: FiberContext[E, Any] = fork(io.value, unhandled)

                    curIo = IO.async0[E, Any] { k =>
                      value.register { (v: ExitResult[E, Any]) =>
                        k(ExitResult.Completed(v))
                      } match {
                        case Async.Now(v) => Async.Now(ExitResult.Completed(v))
                        case x            => x
                      }
                    }

                  case IO.Tags.Ensuring =>
                    val io = curIo.asInstanceOf[IO.Ensuring[E, Any]]
                    stack.push(new Finalizer(io.finalizer))
                    curIo = io.io
                }
              }
            } else {
              // Interruption cannot be interrupted:
              this.noInterrupt += 1

              curIo = IO.terminate0[E, Any](die.get)
            }

            opcount = opcount + 1
          }

          if (result ne null) {
            done(result.asInstanceOf[ExitResult[E, A]])
          }

          curIo = null // Ensure termination of outer loop
        } catch {
          // Catastrophic error handler. Any error thrown inside the interpreter is
          // either a bug in the interpreter or a bug in the user's code. Let the
          // fiber die but attempt finalization & report errors.
          case t: Throwable if (nonFatal(t)) =>
            // Interruption cannot be interrupted:
            this.noInterrupt += 1

            curIo = IO.terminate[E, Any](t)
        }
      }
    }

    final def fork[E, A](io: IO[E, A], handler: PureCanceler): FiberContext[E, A] = {
      val context = new FiberContext[E, A](rts, handler)

      rts.submit(context.evaluate(io))

      context
    }

    /**
     * Resumes a synchronous evaluation given the newly produced value.
     *
     * @param value The value which will be used to resume the sync evaluation.
     */
    private final def resumeEvaluate(value: ExitResult[E, Any]): Unit =
      value match {
        case ExitResult.Completed(v) =>
          // Async produced a value:
          val io = nextInstr[E](v, stack)

          if (io eq null) done(value.asInstanceOf[ExitResult[E, A]])
          else evaluate(io)

        case ExitResult.Failed(t) => evaluate(IO.fail[E, Any](t))

        case ExitResult.Terminated(ts) => evaluate(IO.terminate0[E, Any](ts))
      }

    /**
     * Resumes an asynchronous computation.
     *
     * @param value The value produced by the asynchronous computation.
     */
    private final def resumeAsync[A](value: ExitResult[E, Any]): Unit =
      if (shouldResumeAsync()) {
        // Take care not to overflow the stack in cases of 'deeply' nested
        // asynchronous callbacks.
        if (this.reentrancy > MaxResumptionDepth) {
          rts.submit(resumeEvaluate(value))
        } else resumeEvaluate(value)
      }

    private final def raceCallback[A, B](resume: Callback[E, IO[E, B]],
                                         state: AtomicReference[RaceState],
                                         finish: A => IO[E, B]): Callback[E, A] =
      (tryA: ExitResult[E, A]) => {
        import RaceState._

        var loop = true
        var won  = false

        while (loop) {
          val oldStatus = state.get

          val newState = oldStatus match {
            case Finished =>
              won = false
              oldStatus
            case FirstFailed =>
              won = true
              Finished
            case Started =>
              tryA match {
                case ExitResult.Completed(_) =>
                  won = true
                  Finished
                case _ =>
                  won = false
                  FirstFailed
              }
          }

          loop = !state.compareAndSet(oldStatus, newState)
        }

        if (won) resume(tryA.map(finish))
      }

    private final def raceWith[A, B, C](unhandled: PureCanceler,
                                        leftIO: IO[E, A],
                                        rightIO: IO[E, B],
                                        finishLeft: (A, Fiber[E, B]) => IO[E, C],
                                        finishRight: (B, Fiber[E, A]) => IO[E, C]): IO[E, C] = {
      val left  = fork(leftIO, unhandled)
      val right = fork(rightIO, unhandled)

      // TODO: Interrupt raced fibers if parent is interrupted

      val leftWins  = (w: A) => finishLeft(w, right)
      val rightWins = (w: B) => finishRight(w, left)

      val state = new AtomicReference[RaceState](RaceState.Started)

      IO.flatten(IO.async0[E, IO[E, C]] { k =>
        val leftCallback  = raceCallback[A, C](k, state, leftWins)
        val rightCallback = raceCallback[B, C](k, state, rightWins)

        val c1: Canceler = left.register(leftCallback) match {
          case Async.Now(tryA)                => leftCallback(tryA); null
          case Async.MaybeLater(cancel)       => cancel
          case Async.MaybeLaterIO(pureCancel) => rts.impureCanceler(pureCancel)
        }

        val c2: Canceler = right.register(rightCallback) match {
          case Async.Now(tryA)                => rightCallback(tryA); null
          case Async.MaybeLater(cancel)       => cancel
          case Async.MaybeLaterIO(pureCancel) => rts.impureCanceler(pureCancel)
        }

        val canceler = combineCancelers(c1, c2)

        if (canceler eq null) Async.later[E, IO[E, C]]
        else Async.maybeLater(canceler)
      })
    }

    final def changeErrorUnit[E2](cb: Callback[E2, Unit]): Callback[E, Unit] =
      x => cb(x.mapError(_ => SuccessUnit[E2]))

    final def interrupt0[E2](ts: List[Throwable]): IO[E2, Unit] =
      IO.async0[E2, Unit](cb => kill0[E2](ts, changeErrorUnit[E2](cb)))

    final def join: IO[E, A] = IO.async0(join0)

    final def enterSupervision: IO[E, Unit] = IO.sync {
      supervising += 1

      def newWeakSet[A]: Set[A] = Collections.newSetFromMap[A](new WeakHashMap[A, java.lang.Boolean]())

      val set = newWeakSet[FiberContext[_, _]]

      supervised = set :: supervised
    }

    final def supervise(child: FiberContext[_, _]): Unit =
      if (supervising > 0) {
        supervised match {
          case Nil =>
          case set :: _ =>
            set.add(child)

            ()
        }
      }

    @tailrec
    final def enterAsyncStart(): Int = {
      val oldStatus = status.get

      oldStatus match {
        case AsyncRegion(ts, reentrancy, resume, cancel, joiners, killers) =>
          val newReentrancy = reentrancy + 1

          if (!status.compareAndSet(oldStatus, AsyncRegion(ts, newReentrancy, resume + 1, cancel, joiners, killers)))
            enterAsyncStart()
          else newReentrancy

        case Executing(ts, joiners, killers) =>
          val newReentrancy = 1

          if (!status.compareAndSet(oldStatus, AsyncRegion(ts, newReentrancy, 1, None, joiners, killers)))
            enterAsyncStart()
          else newReentrancy

        case _ =>
          // If this is hit, there's a bug somewhere.
          throw new Error("Defect: Fiber is in Done state")
      }
    }

    final def reentrancy: Int = status.get match {
      case s @ AsyncRegion(_, _, _, _, _, _) => s.reentrancy

      case _ => 0
    }

    @tailrec
    final def enterAsyncEnd(): Unit = {
      val oldStatus = status.get

      oldStatus match {
        case AsyncRegion(ts, 1, 0, _, joiners, killers) =>
          // No more resumptions left and exiting last async boundary initiation:
          if (!status.compareAndSet(oldStatus, Executing(ts, joiners, killers))) enterAsyncEnd()

        case AsyncRegion(ts, reentrancy, resume, cancel, joiners, killers) =>
          if (!status.compareAndSet(oldStatus, AsyncRegion(ts, reentrancy - 1, resume, cancel, joiners, killers)))
            enterAsyncEnd()

        case _ =>
      }
    }

    @tailrec
    final def awaitAsync(id: Int, c: Canceler): Unit = {
      val oldStatus = status.get

      oldStatus match {
        case AsyncRegion(ts, reentrancy, resume, _, joiners, killers) if (id == reentrancy) =>
          if (!status.compareAndSet(oldStatus, AsyncRegion(ts, reentrancy, resume, Some(c), joiners, killers)))
            awaitAsync(id, c)

        case _ =>
      }
    }

    @tailrec
    final def shouldResumeAsync(): Boolean = {
      val oldStatus = status.get

      oldStatus match {
        case AsyncRegion(ts, 0, 1, _, joiners, killers) =>
          // No more resumptions are left!
          if (!status.compareAndSet(oldStatus, Executing(ts, joiners, killers))) shouldResumeAsync()
          else true

        case AsyncRegion(ts, reentrancy, resume, _, joiners, killers) =>
          if (!status.compareAndSet(oldStatus, AsyncRegion(ts, reentrancy, resume - 1, None, joiners, killers)))
            shouldResumeAsync()
          else true

        case _ => false
      }
    }

    final def exitSupervision[E2](e: Throwable): IO[E2, Unit] =
      IO.flatten(IO.sync {
        supervising -= 1

        var action = IO.unit[E2]

        supervised = supervised match {
          case Nil => Nil
          case set :: tail =>
            val iterator = set.iterator()

            while (iterator.hasNext()) {
              val child = iterator.next()

              action = action *> child.interrupt[E2](e)
            }

            tail
        }

        action
      })

    @inline
    final def shouldDie: Option[List[Throwable]] =
      if (!killed || noInterrupt > 0) None else status.get.errors

    private final val exitUninterruptible: Infallible[Unit] = IO.sync { noInterrupt -= 1 }

    private final def doNotInterrupt[E, A](io: IO[E, A]): IO[E, A] = {
      this.noInterrupt += 1
      io.ensuring(exitUninterruptible)
    }

    final def register(cb: Callback[E, A]): Async[E, A] = join0(cb)

    @tailrec
    final def done(v: ExitResult[E, A]): Unit = {
      val oldStatus = status.get

      oldStatus match {
        case Executing(_, joiners, killers) =>
          if (!status.compareAndSet(oldStatus, Done(v))) done(v)
          else purgeJoinersKillers(v, joiners, killers)

        case AsyncRegion(_, _, _, _, joiners, killers) =>
          // TODO: Guard against errant `done` or not?
          if (!status.compareAndSet(oldStatus, Done(v))) done(v)
          else purgeJoinersKillers(v, joiners, killers)

        case Done(_) => // Huh?
      }
    }

    @tailrec
    private final def kill0[E2](ts: List[Throwable], k: Callback[E, Unit]): Async[E2, Unit] = {
      killed = true

      val oldStatus = status.get

      oldStatus match {
        case Executing(ts0, joiners, killers) =>
          if (!status.compareAndSet(oldStatus, Executing(Some(ts0.getOrElse(Nil) ++ ts), joiners, k :: killers)))
            kill0(ts, k)
          else Async.later[E2, Unit]

        case AsyncRegion(None, _, resume, cancelOpt, joiners, killers) if (resume > 0 && noInterrupt == 0) =>
          val v = ExitResult.Terminated[E, A](ts)

          if (!status.compareAndSet(oldStatus, Done(v))) kill0(ts, k)
          else {
            // We interrupted async before it could resume. Now we have to
            // cancel the computation, if possible, and handle any finalizers.
            cancelOpt match {
              case None =>
              case Some(cancel) =>
                try cancel(ts)
                catch {
                  case t: Throwable if (nonFatal(t)) =>
                    supervise(fork(unhandled(t :: Nil)[E], unhandled))
                }
            }

            val finalizer = interruptStack

            if (finalizer ne null) {
              fork[Void, Unit](finalizer.flatMap(unhandled), unhandled)
                .runAsync((_: ExitResult[Void, Unit]) => purgeJoinersKillers(v, joiners, k :: killers))
              Async.later[E2, Unit]
            } else Async.now(SuccessUnit[E2])

          }

        case s @ AsyncRegion(_, _, _, _, _, _) =>
          val newStatus = s.copy(errors = Some(s.errors.getOrElse(Nil) ++ ts), killers = k :: s.killers)

          if (!status.compareAndSet(oldStatus, newStatus)) kill0(ts, k)
          else Async.later[E2, Unit]

        case Done(_) => Async.now(SuccessUnit[E2])
      }
    }

    @tailrec
    private final def join0(cb: Callback[E, A]): Async[E, A] = {
      val oldStatus = status.get

      oldStatus match {
        case s @ Executing(_, _, _) =>
          val newStatus = s.copy(joiners = cb :: s.joiners)

          if (!status.compareAndSet(oldStatus, newStatus)) join0(cb)
          else Async.later[E, A]

        case s @ AsyncRegion(_, _, _, _, _, _) =>
          val newStatus = s.copy(joiners = cb :: s.joiners)

          if (!status.compareAndSet(oldStatus, newStatus)) join0(cb)
          else Async.later[E, A]

        case Done(v) => Async.now(v)
      }
    }

    private final def purgeJoinersKillers(v: ExitResult[E, A],
                                          joiners: List[Callback[E, A]],
                                          killers: List[Callback[E, Unit]]): Unit = {
      // To preserve fair scheduling, we submit all resumptions on the thread
      // pool in (rough) order of their submission.
      killers.reverse.foreach(k => rts.submit(k(SuccessUnit[E])))
      joiners.foreach(k => rts.submit(k(v)))
    }
  }

  sealed trait FiberStatus[E, A] {
    def errors: Option[List[Throwable]]
  }
  object FiberStatus {
    final case class Executing[E, A](errors: Option[List[Throwable]],
                                     joiners: List[Callback[E, A]],
                                     killers: List[Callback[E, Unit]])
        extends FiberStatus[E, A]
    final case class AsyncRegion[E, A](errors: Option[List[Throwable]],
                                       reentrancy: Int,
                                       resume: Int,
                                       cancel: Option[Canceler],
                                       joiners: List[Callback[E, A]],
                                       killers: List[Callback[E, Unit]])
        extends FiberStatus[E, A]
    final case class Done[E, A](value: ExitResult[E, A]) extends FiberStatus[E, A] {
      override def errors: Option[List[Throwable]] = None
    }

    def Initial[E, A] = Executing[E, A](None, Nil, Nil)
  }

  val _SuccessUnit: ExitResult[Void, Unit] = ExitResult.Completed(())

  final def SuccessUnit[E]: ExitResult[E, Unit] = _SuccessUnit.asInstanceOf[ExitResult[E, Unit]]

  final def combineCancelers(c1: Canceler, c2: Canceler): Canceler =
    if (c1 eq null) {
      if (c2 eq null) null
      else c2
    } else if (c2 eq null) {
      c1
    } else
      (ts: List[Throwable]) => {
        c1(ts)
        c2(ts)
      }
}
