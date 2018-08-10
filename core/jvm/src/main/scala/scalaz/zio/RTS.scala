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
    case ExitResult.Terminated(Nil, Nil)    => throw Errors.TerminatedFiber
    case ExitResult.Terminated(Nil, t :: _) => throw t
    case ExitResult.Terminated(t :: _, _) => throw t
    case ExitResult.Failed(e, ts)      => throw Errors.UnhandledError(e, ts)
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
  def defaultHandler: List[Throwable] => IO[Nothing, Unit] =
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

      Async.maybeLater { () =>
        future.cancel(true); ()
      }
    }

  final def impureCanceler(canceler: PureCanceler): Canceler =
    () => unsafeRun(canceler())
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
  final class FiberContext[E, A](rts: RTS, val unhandled: List[Throwable] => IO[Nothing, Unit]) extends Fiber[E, A] {
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

    private class Finalizer(val finalizer: IO[Nothing, Unit]) extends Function[Any, IO[E, Any]] {
      final def apply(v: Any): IO[E, Any] = {
        noInterrupt += 1

        finalizer.flatMap(_ => IO.sync { noInterrupt -= 1; v })
      }
    }

    private final def collectDefect[E, A](e: ExitResult[E, A]): Option[List[Throwable]] =
      e match {
        case ExitResult.Terminated(_, ts @ _ :: _) => Some(ts)
        case ExitResult.Failed(_, ts @ _ :: _)  => Some(ts)
        case _                                  => None
      }

    /**
     * Catches an exception, returning a (possibly null) finalizer action that
     * must be executed. It is painstakingly *guaranteed* that the stack will be
     * empty in the sole case the exception was not caught by any exception
     * handler—i.e. the exceptional case.
     */
    final def catchError: IO[Nothing, Option[List[Throwable]]] = {
      var errorHandler: Any => IO[Any, Any]               = null
      var finalizer: IO[Nothing, Option[List[Throwable]]] = null

      // Unwind the stack, looking for exception handlers and coalescing
      // finalizers.
      while ((errorHandler eq null) && !stack.isEmpty) {
        stack.pop() match {
          case a: IO.Redeem[_, _, _, _] =>
            errorHandler = a.err.asInstanceOf[Any => IO[Any, Any]]
          case f0: Finalizer =>
            val f: IO[Nothing, Option[List[Throwable]]] = f0.finalizer.run[Nothing, Unit].map(collectDefect)
            if (finalizer eq null) finalizer = f
            else
              finalizer = finalizer.zipWith(f)(zipFailures)
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
    final def interruptStack: IO[Nothing, Option[List[Throwable]]] = {
      // Use null to achieve zero allocs for the common case of no finalizers:
      var finalizer: IO[Nothing, Option[List[Throwable]]] = null

      while (!stack.isEmpty) {
        // Peel off all the finalizers, composing them into a single finalizer
        // that produces a possibly empty list of errors that occurred when
        // executing the finalizers. The order of errors is outer-to-inner
        // (reverse chronological).
        stack.pop() match {
          case f0: Finalizer =>
            val f: IO[Nothing, Option[List[Throwable]]] = f0.finalizer.run[Nothing, Unit].map(collectDefect)
            if (finalizer eq null) finalizer = f
            else
              finalizer = finalizer.zipWith(f)(zipFailures)
          case _ =>
        }
      }

      finalizer
    }

    private final def zipFailures(ts1: Option[List[Throwable]], ts2: Option[List[Throwable]]): Option[List[Throwable]] =
      (ts1, ts2) match {
        case (Some(ts1), Some(ts2)) => Some(ts1 ++ ts2)
        case (ts @ Some(_), None)   => ts
        case (None, ts @ Some(_))   => ts
        case (None, None)           => None
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
      var curIo: IO[E, Any] = io0.as[Any]

      while (curIo ne null) {
        try {
          // Put the maximum operation count on the stack for fast access:
          val maxopcount = YieldMaxOpCount

          var result: ExitResult[E, Any] = null
          var opcount: Int               = 0

          while (curIo ne null) {
            // Check to see if the fiber should continue executing or not:
            if (!shouldDie) {
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
                        val io2 = nested.asInstanceOf[IO.Point[E]]

                        curIo = io.flatMapper(io2.value())

                      case IO.Tags.Strict =>
                        val io2 = nested.asInstanceOf[IO.Strict[Any]]

                        curIo = io.flatMapper(io2.value)

                      case IO.Tags.SyncEffect =>
                        val io2 = nested.asInstanceOf[IO.SyncEffect[Any]]

                        curIo = io.flatMapper(io2.effect())

                      case _ =>
                        // Fallback case. We couldn't evaluate the LHS so we have to
                        // use the stack:
                        curIo = nested

                        stack.push(io.flatMapper)
                    }

                  case IO.Tags.Point =>
                    val io = curIo.asInstanceOf[IO.Point[Any]]

                    val value = io.value()

                    curIo = nextInstr[E](value, stack)

                    if (curIo eq null) {
                      result = ExitResult.Completed(value)
                    }

                  case IO.Tags.Strict =>
                    val io = curIo.asInstanceOf[IO.Strict[Any]]

                    val value = io.value

                    curIo = nextInstr[E](value, stack)

                    if (curIo eq null) {
                      result = ExitResult.Completed(value)
                    }

                  case IO.Tags.SyncEffect =>
                    val io = curIo.asInstanceOf[IO.SyncEffect[Any]]

                    val value = io.effect()

                    curIo = nextInstr[E](value, stack)

                    if (curIo eq null) {
                      result = ExitResult.Completed(value)
                    }

                  case IO.Tags.Fail =>
                    val io = curIo.asInstanceOf[IO.Fail[E]]

                    val error = io.error

                    val finalizer = catchError

                    if (stack.isEmpty) {
                      // Error not caught, stack is empty:
                      if (finalizer eq null) {
                        // No finalizer, so immediately produce the error.
                        val defects = status.get.defects

                        curIo = null
                        result = ExitResult.Failed(error, defects)
                      } else {
                        // We have finalizers to run. We'll resume executing with the
                        // uncaught failure after we have executed all the finalizers:
                        val finalization = finalizer.flatMap(accumFailures)
                        val completer    = io

                        curIo = doNotInterrupt(finalization) *> completer
                      }
                    } else {
                      // Error caught:
                      val handled = nextInstr[E](error, stack)

                      if (finalizer eq null) {
                        curIo = handled
                      } else {
                        // Must run finalizer first:
                        val finalization = finalizer.flatMap(accumFailures)
                        val completer    = handled

                        curIo = doNotInterrupt(finalization) *> completer
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
                              case ExitResult.Terminated(ts, d) =>
                                curIo = IO.terminate0(ts ++ d)
                              case ExitResult.Failed(e, _) =>
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

                    curIo = IO.async[E, Any] { callback =>
                      rts.unsafeRunAsync(io.register(callback))(_ => ())
                    }

                  case IO.Tags.Redeem =>
                    val io = curIo.asInstanceOf[IO.Redeem[E, Any, Any, Any]]

                    curIo = io.value

                    stack.push(io)

                  case IO.Tags.Fork =>
                    val io = curIo.asInstanceOf[IO.Fork[_, Any]]

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

                    curIo = doNotInterrupt(io.io)

                  case IO.Tags.Sleep =>
                    val io = curIo.asInstanceOf[IO.Sleep]

                    curIo = IO.async0[E, Any] { callback =>
                      rts
                        .schedule(callback(SuccessUnit), io.duration)
                    }

                  case IO.Tags.Supervise =>
                    val io = curIo.asInstanceOf[IO.Supervise[E, Any]]

                    curIo = enterSupervision *>
                      io.value.ensuring(exitSupervision(io.supervisor))

                  case IO.Tags.Terminate =>
                    val io = curIo.asInstanceOf[IO.Terminate]

                    val finalizer = interruptStack

                    if (finalizer eq null) {
                      // No finalizers, simply produce error:
                      val causes    = status.get.terminationCauses.getOrElse(Nil)
                      val defects   = status.get.defects
                      val allCauses = io.causes ++ causes

                      curIo = null
                      result = ExitResult.Terminated(allCauses, defects)
                    } else {
                      // Must run finalizers first before failing:
                      val finalization = finalizer.flatMap(accumFailures)
                      val completer    = io

                      curIo = doNotInterrupt(finalization) *> completer
                    }

                  case IO.Tags.Supervisor =>
                    val value = unhandled

                    curIo = nextInstr[E](value, stack)

                    if (curIo eq null) {
                      result = ExitResult.Completed(value)
                    }

                  case IO.Tags.Run =>
                    val io = curIo.asInstanceOf[IO.Run[E, Any]]

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

              // At this point, all causes of interruption have been accumulated
              // in the fiber status and will be read during evalution of this
              // action:
              curIo = IO.terminate
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

            curIo = IO.terminate(t)
        }
      }
    }

    final def fork[E, A](io: IO[E, A], handler: List[Throwable] => IO[Nothing, Unit]): FiberContext[E, A] = {
      val context = new FiberContext[E, A](rts, handler)

      rts.submit(context.evaluate(io))

      context
    }

    private final def accumFailures: Option[List[Throwable]] => IO[Nothing, Unit] = {
      case None     => IO.unit
      case Some(ts) => IO.sync(addFailures(ts))
    }

    @tailrec
    private final def addFailures(ts: List[Throwable]): Unit = {
      val oldStatus = status.get
      oldStatus match {
        case x @ Executing(_, ts0, _, _) =>
          if (!status.compareAndSet(oldStatus, x.copy(defects = ts0 ++ ts))) addFailures(ts) else ()

        case x @ AsyncRegion(_, ts0, _, _, _, _, _) =>
          if (!status.compareAndSet(oldStatus, x.copy(defects = ts0 ++ ts))) addFailures(ts) else ()

        case _ =>
      }
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

        case ExitResult.Failed(t, _) => evaluate(IO.fail[E](t))

        case ExitResult.Terminated(ts, d) => evaluate(IO.terminate0(ts ++ d))
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

    private final def raceWith[A, B, C](unhandled: List[Throwable] => IO[Nothing, Unit],
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
      x => cb(x.mapError(_ => SuccessUnit))

    final def interrupt0(ts: List[Throwable]): IO[Nothing, Unit] =
      IO.async0[Nothing, Unit](cb => kill0[Nothing](ts, changeErrorUnit[Nothing](cb)))

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
        case AsyncRegion(terminationCauses, defects, reentrancy, resume, cancel, joiners, killers) =>
          val newReentrancy = reentrancy + 1

          if (!status.compareAndSet(
                oldStatus,
                AsyncRegion(terminationCauses, defects, newReentrancy, resume + 1, cancel, joiners, killers)
              ))
            enterAsyncStart()
          else newReentrancy

        case Executing(terminationCauses, defects, joiners, killers) =>
          val newReentrancy = 1

          if (!status.compareAndSet(oldStatus,
                                    AsyncRegion(terminationCauses, defects, newReentrancy, 1, None, joiners, killers)))
            enterAsyncStart()
          else newReentrancy

        case _ =>
          // If this is hit, there's a bug somewhere.
          throw new Error("Defect: Fiber is in Done state")
      }
    }

    final def reentrancy: Int = status.get match {
      case s @ AsyncRegion(_, _, _, _, _, _, _) => s.reentrancy

      case _ => 0
    }

    @tailrec
    final def enterAsyncEnd(): Unit = {
      val oldStatus = status.get

      oldStatus match {
        case AsyncRegion(terminationCauses, defects, 1, 0, _, joiners, killers) =>
          // No more resumptions left and exiting last async boundary initiation:
          if (!status.compareAndSet(oldStatus, Executing(terminationCauses, defects, joiners, killers))) enterAsyncEnd()

        case AsyncRegion(terminationCauses, defects, reentrancy, resume, cancel, joiners, killers) =>
          if (!status.compareAndSet(
                oldStatus,
                AsyncRegion(terminationCauses, defects, reentrancy - 1, resume, cancel, joiners, killers)
              ))
            enterAsyncEnd()

        case _ =>
      }
    }

    @tailrec
    final def awaitAsync(id: Int, c: Canceler): Unit = {
      val oldStatus = status.get

      oldStatus match {
        case AsyncRegion(terminationCauses, defects, reentrancy, resume, _, joiners, killers) if (id == reentrancy) =>
          if (!status.compareAndSet(
                oldStatus,
                AsyncRegion(terminationCauses, defects, reentrancy, resume, Some(c), joiners, killers)
              ))
            awaitAsync(id, c)

        case _ =>
      }
    }

    @tailrec
    final def shouldResumeAsync(): Boolean = {
      val oldStatus = status.get

      oldStatus match {
        case AsyncRegion(terminationCauses, defects, 0, 1, _, joiners, killers) =>
          // No more resumptions are left!
          if (!status.compareAndSet(oldStatus, Executing(terminationCauses, defects, joiners, killers)))
            shouldResumeAsync()
          else true

        case AsyncRegion(terminationCauses, defects, reentrancy, resume, _, joiners, killers) =>
          if (!status.compareAndSet(
                oldStatus,
                AsyncRegion(terminationCauses, defects, reentrancy, resume - 1, None, joiners, killers)
              ))
            shouldResumeAsync()
          else true

        case _ => false
      }
    }

    final def exitSupervision(supervisor: Iterable[Fiber[_, _]] => IO[Nothing, Unit]): IO[Nothing, Unit] = {
      import collection.JavaConverters._
      IO.flatten(IO.sync {
        supervising -= 1

        var action = IO.unit

        supervised = supervised match {
          case Nil => Nil
          case set :: tail =>
            action = supervisor(set.asScala)
            tail
        }

        action
      })
    }

    @inline
    final def shouldDie: Boolean = killed && noInterrupt == 0

    private final val exitUninterruptible: IO[Nothing, Unit] = IO.sync { noInterrupt -= 1 }

    private final def doNotInterrupt[E, A](io: IO[E, A]): IO[E, A] = {
      this.noInterrupt += 1
      io.ensuring(exitUninterruptible)
    }

    final def register(cb: Callback[E, A]): Async[E, A] = join0(cb)

    @tailrec
    final def done(v: ExitResult[E, A]): Unit = {
      val oldStatus = status.get

      oldStatus match {
        case Executing(_, _, joiners, killers) =>
          if (!status.compareAndSet(oldStatus, Done(v))) done(v)
          else {
            purgeJoinersKillers(v, joiners, killers)
            reportErrors(v)
          }

        case AsyncRegion(_, _, _, _, _, joiners, killers) =>
          // TODO: Guard against errant `done` or not?
          if (!status.compareAndSet(oldStatus, Done(v))) done(v)
          else {
            purgeJoinersKillers(v, joiners, killers)
            reportErrors(v)
          }

        case Done(_) => // Huh?
      }
    }

    final def reportErrors(v: ExitResult[E, A]): Unit =
      v match {
        case ExitResult.Failed(error, defects) =>
          // Report the uncaught error to the supervisor:
          rts.submit(
            rts.unsafeRun(unhandled(Errors.UnhandledError(error, defects) :: Nil))
          )

        case ExitResult.Terminated(causes, defects) =>
          // Report the termination cause to the supervisor:
          rts.submit(rts.unsafeRun(unhandled(causes ++ defects)))

        case _ =>
      }

    private final def kill0[E2](cs: List[Throwable], k: Callback[E, Unit]): Async[E2, Unit] = {

      val oldStatus = status.get

      oldStatus match {
        case Executing(terminationCauses, defects, joiners, killers) =>
          if (!status.compareAndSet(
                oldStatus,
                Executing(Some(terminationCauses.getOrElse(Nil) ++ cs), defects, joiners, k :: killers)
              ))
            kill0(cs, k)
          else {
            killed = true
            Async.later[E2, Unit]
          }

        case AsyncRegion(None, defects, _, resume, cancelOpt, joiners, killers) if (resume > 0 && noInterrupt == 0) =>
          val v = ExitResult.Terminated[E, A](defects ++ cs)

          if (!status.compareAndSet(oldStatus, Done(v))) kill0(cs, k)
          else {
            killed = true

            // We interrupted async before it could resume. Now we have to
            // cancel the computation, if possible, and handle any finalizers.
            cancelOpt match {
              case None =>
              case Some(cancel) =>
                try cancel()
                catch {
                  case t: Throwable if (nonFatal(t)) =>
                    fork(unhandled(t :: Nil), unhandled)
                }
            }

            val finalizer = interruptStack

            if (finalizer ne null) {
              fork(finalizer.flatMap {
                case None     => IO.unit
                case Some(ts) => unhandled(ts)
              }, unhandled)
                .runAsync((_: ExitResult[Nothing, Unit]) => purgeJoinersKillers(v, joiners, k :: killers))
              Async.later[E2, Unit]
            } else Async.now(SuccessUnit)

          }

        case s @ AsyncRegion(_, _, _, _, _, _, _) =>
          val newStatus =
            s.copy(terminationCauses = Some(s.terminationCauses.getOrElse(Nil) ++ cs), killers = k :: s.killers)

          if (!status.compareAndSet(oldStatus, newStatus)) kill0(cs, k)
          else {
            killed = true
            Async.later[E2, Unit]
          }

        case Done(_) =>
          killed = true
          Async.now(SuccessUnit)
      }
    }

    @tailrec
    private final def join0(cb: Callback[E, A]): Async[E, A] = {
      val oldStatus = status.get

      oldStatus match {
        case s @ Executing(_, _, _, _) =>
          val newStatus = s.copy(joiners = cb :: s.joiners)

          if (!status.compareAndSet(oldStatus, newStatus)) join0(cb)
          else Async.later[E, A]

        case s @ AsyncRegion(_, _, _, _, _, _, _) =>
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
      killers.reverse.foreach(k => rts.submit(k(SuccessUnit)))
      joiners.foreach(k => rts.submit(k(v)))
    }
  }

  sealed trait FiberStatus[E, A] {

    /** causes passed in when explicitly interrupting the fiber */
    def terminationCauses: Option[List[Throwable]]

    /** errors resulting from exceptions thrown during the execution of the fiber */
    def defects: List[Throwable]
  }
  object FiberStatus {
    final case class Executing[E, A](terminationCauses: Option[List[Throwable]],
                                     defects: List[Throwable],
                                     joiners: List[Callback[E, A]],
                                     killers: List[Callback[E, Unit]])
        extends FiberStatus[E, A]
    final case class AsyncRegion[E, A](terminationCauses: Option[List[Throwable]],
                                       defects: List[Throwable],
                                       reentrancy: Int,
                                       resume: Int,
                                       cancel: Option[Canceler],
                                       joiners: List[Callback[E, A]],
                                       killers: List[Callback[E, Unit]])
        extends FiberStatus[E, A]
    final case class Done[E, A](value: ExitResult[E, A]) extends FiberStatus[E, A] {
      override def terminationCauses: Option[List[Throwable]] = None
      override def defects: List[Throwable]                   = Nil
    }

    def Initial[E, A] = Executing[E, A](None, Nil, Nil, Nil)
  }

  val SuccessUnit: ExitResult[Nothing, Unit] = ExitResult.Completed(())

  final def combineCancelers(c1: Canceler, c2: Canceler): Canceler =
    if (c1 eq null) {
      if (c2 eq null) null
      else c2
    } else if (c2 eq null) {
      c1
    } else
      () => {
        c1()
        c2()
      }
}
